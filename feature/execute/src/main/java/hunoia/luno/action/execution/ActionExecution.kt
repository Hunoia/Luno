package hunoia.luno.action.execution

import hunoia.luno.core.LunoLog as Log
import hunoia.luno.action.model.Action
import hunoia.luno.core.LOG_TAG
import hunoia.luno.core.domain.ActionFailure
import hunoia.luno.core.domain.ActionResult
import hunoia.luno.core.domain.Capability
import hunoia.luno.core.domain.StoredAction
import hunoia.luno.execute.ActionCatalog
import hunoia.luno.execute.ActionFactory
import hunoia.luno.execute.ActionMappingException
import hunoia.luno.execute.ExecuteEnvironment
import hunoia.luno.execute.ExecuteMessages
import hunoia.luno.execute.SurfaceHost
import kotlinx.coroutines.CoroutineScope
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * 执行口核心（`:feature:execute`）。
 *
 * 分工：Dispatcher 负责「找定义 → 校验参数 → 查能力闸 → 调执行」；Executor 只执行，
 * 不校验、不鉴权、不碰 UI、不查动作。动作目录（[ActionCatalog]）与 StoredAction 映射
 * （[ActionFactory]）由 `:feature:actions` 实现并通过端口注入。
 */

interface ActionExecutor {
    val supportedTypes: Set<String>
    suspend fun execute(action: Action, context: ExecutorContext): ActionResult
}

class ActionExecutorRegistry(
    private val executors: List<ActionExecutor>,
    private val catalog: ActionCatalog,
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
        val metadata = catalog.definition(action.typeId)
        val capabilities = metadata?.capabilities ?: setOf(Capability.None)
        // 组内任一可用即可：无障碍不在位时，back/home 这类动作可由 shell 注入完成。
        if (!capabilities.any(isCapabilityAvailable)) {
            return ActionResult.RequiresCapability(
                capabilities.firstOrNull { it != Capability.None } ?: Capability.None,
            )
        }
        return runCatching {
            executor.execute(action, context)
        }.getOrElse { e ->
            // runCatching 会吞掉 CancellationException，必须放行，否则序列/协程取消失效。
            if (e is kotlinx.coroutines.CancellationException) throw e
            Log.w(LOG_TAG, "execute failed typeId=${action.typeId}", e)
            ActionResult.Failed(ActionFailure.ExecutionFailed, e.message ?: e.javaClass.name)
        }
    }
}

class ActionResolver(
    private val registry: ActionExecutorRegistry,
    private val catalog: ActionCatalog,
    private val factory: ActionFactory,
) {
    suspend fun resolveFromLibrary(
        stored: StoredAction,
        context: ExecutorContext,
    ): ActionResult {
        val definition = catalog.definition(stored.typeId)
        val params = stored.params

        // 必填文本参数为空直接判参数无效。Executor 不做参数校验，只执行。
        if (hasBlankRequiredText(definition, params)) {
            return ActionResult.Failed(ActionFailure.InvalidParameter)
        }
        // 映射层的受控异常（未知 typeId / 非法枚举参数）必须在这里转成结果：
        // 直接冒出会让 launch 协程异常、既没有提示还会连带崩进程。
        return try {
            registry.execute(factory.fromStored(stored), context)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: ActionMappingException) {
            if (e.unsupported) {
                Log.w(LOG_TAG, "unknown action type ${stored.typeId}")
                ActionResult.Failed(ActionFailure.Unsupported)
            } else {
                Log.w(LOG_TAG, "invalid param typeId=${stored.typeId}: ${e.message}")
                ActionResult.Failed(ActionFailure.InvalidParameter, e.message)
            }
        } catch (e: Exception) {
            Log.w(LOG_TAG, "map/execute failed typeId=${stored.typeId}", e)
            ActionResult.Failed(ActionFailure.ExecutionFailed, e.message ?: e.javaClass.name)
        }
    }

    /** 必填文本参数为空直接判为参数无效。 */
    private fun hasBlankRequiredText(
        definition: hunoia.luno.core.domain.ActionMetadata?,
        params: JsonObject,
    ): Boolean = definition?.requiredTextKeys.orEmpty().any { key ->
        (params[key] as? JsonPrimitive)?.contentOrNull?.isBlank() == true
    }
}

/**
 * 执行器运行上下文：只携带执行真正需要的东西。
 *
 * 宿主能力全部经 [environment] / [surfaces] / [messages] 端口读取，不再把同一组函数
 * 复制成字段；新增一个端口只需改端口接口与实现。
 *
 * 不持有配置模型（`config.model.*`）：需要全局设置时走端口（启动、面板、音量刮擦），
 * 这两条是 `execute → data / runtime` 禁令的落地方式。
 */
data class ExecutorContext(
    val environment: ExecuteEnvironment,
    val surfaces: SurfaceHost,
    val messages: ExecuteMessages,
    val scope: CoroutineScope,
    val touchPosition: Pair<Int, Int>? = null,
    /** 触发按钮 id：`internal.buttonsVisibility` 的临时隐藏作用于它。 */
    val sourceButtonId: String? = null,
    /** 触发上下文（全屏手势锚点 / 会话 id）；非全屏手势链路为 null。 */
    val triggerContext: hunoia.luno.core.domain.TriggerContext? = null,
) {
    /** 只对触发钮生效的临时隐藏；非手势链路没有来源按钮时静默跳过。 */
    fun hideGestureButton(delayMs: Long) {
        sourceButtonId?.let { surfaces.hideGestureButton(it, delayMs) }
    }
}
