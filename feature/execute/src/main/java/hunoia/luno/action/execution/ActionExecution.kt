package hunoia.luno.action.execution

import android.content.Context
import android.net.Uri
import hunoia.luno.core.LunoLog as Log
import hunoia.luno.action.model.Action
import hunoia.luno.action.model.ButtonTarget
import hunoia.luno.action.model.QueryParam
import hunoia.luno.core.LOG_TAG
import hunoia.luno.core.domain.ActionFailure
import hunoia.luno.core.domain.ActionResult
import hunoia.luno.core.domain.Capability
import hunoia.luno.core.domain.StoredAction
import hunoia.luno.execute.ActionCatalog
import hunoia.luno.execute.ActionFactory
import hunoia.luno.execute.ActionMappingException
import hunoia.luno.execute.LaunchSpec
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
            registry.execute(factory.fromStored(StoredAction(stored.typeId, params)), context)
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
        definition: hunoia.luno.execute.ActionMetadata?,
        params: JsonObject,
    ): Boolean = definition?.requiredTextKeys.orEmpty().any { key ->
        (params[key] as? JsonPrimitive)?.contentOrNull?.isBlank() == true
    }
}

/**
 * 执行器运行上下文：只携带执行真正需要的东西。
 *
 * 不持有配置模型（`config.model.*`）：需要全局设置时走端口（启动、面板、音量刮擦），
 * 这两条是 `execute → data / runtime` 禁令的落地方式。
 */
data class ExecutorContext(
    val appContext: Context,
    val scope: CoroutineScope,
    val showToast: (String) -> Unit = {},
    val showLongToast: (String) -> Unit = {},
    val currentPackageName: () -> String? = { null },
    val nowInLauncher: () -> Boolean = { false },
    val requestEnableDisabledPackage: (String, (Boolean) -> Unit) -> Unit = { _, onResult -> onResult(false) },
    /** 快速启动面板：锚点为触发触点（无触点路径由执行器退屏幕中心），面板侧再夹取屏内。 */
    val toggleQuickAppLauncher: (anchorX: Int, anchorY: Int) -> Unit = { _, _ -> },
    val showQuickSearch: () -> Unit = {},
    val showSpeechRecognition: () -> Boolean = { false },
    /** 网格面板：锚点为触发触点（无触点路径由执行器退屏幕中心），Activity 侧再夹取屏内。 */
    val showGridPanel: (panelId: String, anchorX: Int, anchorY: Int) -> Unit = { _, _, _ -> },
    /** 网格面板是否还存在（面板被删时按无动作静默收口）。 */
    val gridPanelExists: (panelId: String) -> Boolean = { true },
    /** 音量刮擦 overlay：参数是每档位移阈值（dp），null = 用实现侧默认值。 */
    val showVolumeScrub: (stepThresholdDp: Int?) -> Boolean = { false },
    val showPointer: () -> Boolean = { false },
    val hideGestureButton: (Long) -> Unit = {},
    /** 触钮临时隐藏（按 target/buttonIds 选作用钮）：到期自动恢复，不碰 enabled 存量。 */
    val hideGestureButtons: (ButtonTarget, List<String>, Long) -> Unit = { _, _, _ -> },
    val previousApp: suspend () -> Unit = {},
    val touchPosition: Pair<Int, Int>? = null,
    /** 启动应用（小窗等全局设置由实现侧读）。 */
    val launchApp: suspend (LaunchSpec) -> Boolean = { false },
    /** 打开链接（含 Query 参数与小窗）。 */
    val openUrl: suspend (url: String, miniWindow: Boolean, queryParameters: List<QueryParam>, anchor: Pair<Int, Int>?) -> Boolean =
        { _, _, _, _ -> false },
    /** 启动已声明的 App Shortcut（入参是落库的快捷方式 JSON）。 */
    val launchShortcut: suspend (dataJson: String) -> Boolean = { false },
    val openAppDetails: suspend (packageName: String, userId: Int) -> ActionResult = { _, _ ->
        ActionResult.Failed(ActionFailure.Unsupported)
    },
    /** 当前应用以小窗打开；包名/Activity 解析与全局设置由实现侧负责。 */
    val launchInPopup: suspend (packageName: String, anchor: Pair<Int, Int>?) -> ActionResult = { _, _ ->
        ActionResult.Failed(ActionFailure.Unsupported)
    },
    /** 分享文件：挑文件页（Activity）归 `:app`，执行口只声明「要分享文件」。 */
    val shareFile: () -> ActionResult = { ActionResult.Failed(ActionFailure.Unsupported) },

    /** 屏幕取点（transient Surface）。 */
    val showScreenPick: () -> Boolean = { false },

    /** 屏幕标注（a11y 浮层 + 截屏）。 */
    val showScreenAnnotate: () -> Boolean = { false },

    /** 图片编辑（系统图片选择器 → 全屏标注页）。 */
    val openImageAnnotate: () -> Boolean = { false },

    /** 剪贴板分词：把文本送进 OCR 结果页做分词视图。 */
    val showOcrSegmentText: (String) -> Boolean = { false },

    /** 剪贴板 OCR：解码图片并送 OCR 结果页识别；失败返回带因结果（含模型未就绪）。 */
    val showClipboardOcr: suspend (Uri, String) -> ActionResult = { _, _ ->
        ActionResult.Failed(ActionFailure.Unsupported)
    },
    /**
     * 悬浮球显隐：`hideForMs > 0` 走临时隐藏（只摘窗口、到期恢复），否则改 enabled 存量。
     * 配置写入与 FloatBallService 都归 `:app`。
     */
    val setFloatBallVisible: suspend (visible: Boolean?, hideForMs: Long) -> ActionResult =
        { _, _ -> ActionResult.Failed(ActionFailure.Unsupported) },
    /**
     * 触钮显隐：`hideForMs > 0` 走临时隐藏（按 target/buttonIds 选作用钮），否则改 enabled 存量。
     */
    val setButtonsVisible: suspend (Boolean, ButtonTarget, List<String>, Long) -> ActionResult =
        { _, _, _, _ -> ActionResult.Failed(ActionFailure.Unsupported) },
)
