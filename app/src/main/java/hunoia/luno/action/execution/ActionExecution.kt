package hunoia.luno.action.execution

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.util.Log
import hunoia.luno.action.definitions.ActionDefinition
import hunoia.luno.action.definitions.ActionDefinitions
import hunoia.luno.action.definitions.ParameterDefinition
import hunoia.luno.action.definitions.excludedTemplateKeys
import hunoia.luno.action.model.Action
import hunoia.luno.action.model.ActionFailure
import hunoia.luno.action.model.ActionMapper
import hunoia.luno.action.model.InvalidActionParameterException
import hunoia.luno.action.model.UnknownActionTypeException
import hunoia.luno.action.model.ActionResult
import hunoia.luno.action.model.Capability
import hunoia.luno.action.model.StoredAction
import hunoia.luno.action.template.ParamTemplates
import hunoia.luno.config.model.ActionSettings
import hunoia.luno.config.model.AdvancedSettings
import hunoia.luno.config.model.VolumeScrubConfig
import hunoia.luno.runtime.condition.ConditionContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

interface ActionExecutor {
    val supportedTypes: Set<String>
    suspend fun execute(action: Action, context: ExecutorContext): ActionResult
}

class ActionExecutorRegistry(
    private val executors: List<ActionExecutor>,
    private val isCapabilityAvailable: (Capability) -> Boolean = { true },
) {
    private val executorMap: Map<String, ActionExecutor> = executors
        .flatMap { e -> e.supportedTypes.map { it to e } }
        .toMap()

    init {
        val seen = mutableSetOf<String>()
        for (executor in executors) {
            for (typeId in executor.supportedTypes) {
                check(seen.add(typeId)) {
                    "Duplicate typeId: $typeId registered by ${executor::class.simpleName}"
                }
            }
        }
    }

    suspend fun execute(action: Action, context: ExecutorContext): ActionResult {
        val executor = executorMap[action.typeId] ?: return ActionResult.Failed(ActionFailure.Unsupported)
        val capability = ActionDefinitions.byTypeId(action.typeId)?.capability ?: Capability.None
        if (capability != Capability.None && !isCapabilityAvailable(capability)) {
            return ActionResult.RequiresCapability(capability)
        }
        return runCatching {
            executor.execute(action, context)
        }.getOrElse { e ->
            // runCatching 会吞掉 CancellationException，必须放行，否则序列/协程取消失效。
            if (e is kotlinx.coroutines.CancellationException) throw e
            Log.w("LunoLauncher", "execute failed typeId=${action.typeId}", e)
            ActionResult.Failed(ActionFailure.ExecutionFailed, e.message ?: e.javaClass.name)
        }
    }
}

class ActionResolver(
    private val registry: ActionExecutorRegistry,
) {
    suspend fun resolveFromLibrary(
        stored: StoredAction,
        context: ExecutorContext,
    ): ActionResult {
        val definition = ActionDefinitions.byTypeId(stored.typeId)
        val resolved = runCatching { resolveParams(stored, definition, context) }
            .onFailure { error -> Log.w("LunoLauncher", "param template resolve failed", error) }
            .getOrNull()
        val params = resolved?.params ?: stored.params

        // 必填文本参数为空直接判参数无效（模板展开与否一致处理）。
        // Executor 不做参数校验，只执行。
        if (hasBlankRequiredText(definition, params)) {
            return ActionResult.Failed(ActionFailure.InvalidParameter)
        }
        // 映射层的受控异常（未知 typeId / 非法枚举参数）必须在这里转成结果：
        // 直接冒出会让 launch 协程异常、既没有提示还会连带崩进程。
        return try {
            registry.execute(ActionMapper.fromStored(StoredAction(stored.typeId, params)), context)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: UnknownActionTypeException) {
            Log.w("LunoLauncher", "unknown action type ${e.typeId}")
            ActionResult.Failed(ActionFailure.Unsupported)
        } catch (e: InvalidActionParameterException) {
            Log.w("LunoLauncher", "invalid param ${e.key}=${e.raw} typeId=${stored.typeId}")
            ActionResult.Failed(ActionFailure.InvalidParameter, e.message)
        } catch (e: Exception) {
            Log.w("LunoLauncher", "map/execute failed typeId=${stored.typeId}", e)
            ActionResult.Failed(ActionFailure.ExecutionFailed, e.message ?: e.javaClass.name)
        }
    }

    /** 无 token 时零分配；有 token 时才取值并展开，存储保持原文。 */
    private suspend fun resolveParams(
        stored: StoredAction,
        definition: ActionDefinition?,
        context: ExecutorContext,
    ): ResolvedParams {
        val params = stored.params
        val excluded = definition?.excludedTemplateKeys().orEmpty()

        if (excluded.isEmpty()) {
            val used = ParamTemplates.tokensIn(params)
            if (used.isEmpty()) return ResolvedParams(params, substituted = false)
            return ResolvedParams(
                ParamTemplates.resolveParams(params, context.templateValues(used)),
                substituted = true,
            )
        }

        val substitutable = JsonObject(params.filterKeys { it !in excluded })
        val used = ParamTemplates.tokensIn(substitutable)
        if (used.isEmpty()) return ResolvedParams(params, substituted = false)
        val replaced = ParamTemplates.resolveParams(substitutable, context.templateValues(used))
        return ResolvedParams(
            JsonObject(params.mapValues { (key, value) -> if (key in excluded) value else replaced[key] ?: value }),
            substituted = true,
        )
    }

    /** 模板展开后为空的必填文本参数（如手势触发时的 `${trigger.app}`）直接判为参数无效。 */
    private fun hasBlankRequiredText(definition: ActionDefinition?, params: JsonObject): Boolean =
        definition?.parameters.orEmpty().any { param ->
            val isText = param is ParameterDefinition.Text || param is ParameterDefinition.TextLarge
            isText && param.required &&
                (params[param.key] as? JsonPrimitive)?.contentOrNull?.isBlank() == true
        }

    private data class ResolvedParams(val params: JsonObject, val substituted: Boolean)
}

data class ExecutorContext(
    val accessibilityService: AccessibilityService,
    val appContext: Context,
    val scope: CoroutineScope,
    val actionSettings: ActionSettings = ActionSettings(),
    val advancedSettings: AdvancedSettings = AdvancedSettings(),
    val showToast: (String) -> Unit = {},
    val showLongToast: (String) -> Unit = {},
    val currentPackageName: () -> String? = { null },
    val nowInLauncher: () -> Boolean = { false },
    val requestEnableDisabledPackage: (String, (Boolean) -> Unit) -> Unit = { _, onResult -> onResult(false) },
    val toggleQuickAppLauncher: () -> Unit = {},
    val showQuickSearch: () -> Unit = {},
    /** 网格面板：锚点为触发触点（无触点路径由执行器退屏幕中心），Activity 侧再夹取屏内。 */
    val showGridPanel: (panelId: String, anchorX: Int, anchorY: Int) -> Unit = { _, _, _ -> },
    val showVolumeScrub: (VolumeScrubConfig) -> Boolean = { false },
    val showPointer: () -> Boolean = { false },
    val hideGestureButton: (Long) -> Unit = {},
    val showVersionTooLowToast: (Int) -> Unit = {},
    val previousApp: suspend (List<String>) -> Unit = {},
    val touchPosition: Pair<Int, Int>? = null,
    /** 参数模板取值器：入参是本次解析实际用到的 token 集合，返回对应运行时值。 */
    val templateValues: suspend (Set<String>) -> Map<String, String> = { emptyMap() },
    /** 本次分发的模板上下文：runRule 嵌套执行目标步骤时复用同一上下文，不重新快照。 */
    val templateContext: () -> ConditionContext? = { null },
    /**
     * 规则即动作回调（分发器注入）：只跑目标规则的 effect steps。
     * null = 非分发链路构造的上下文，runRule 报 Unsupported。
     */
    val runRule: (suspend (ruleId: String, ctx: ExecutorContext) -> ActionResult)? = null,
)
