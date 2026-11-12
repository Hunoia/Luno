package hunoia.luno.execute

import android.content.Context
import android.net.Uri
import hunoia.luno.action.model.Action
import hunoia.luno.action.model.ButtonTarget
import hunoia.luno.core.domain.QueryParam
import hunoia.luno.core.domain.ActionMetadata
import hunoia.luno.core.domain.LaunchSpec
import hunoia.luno.core.domain.ActionFailure
import hunoia.luno.core.domain.ActionRequest
import hunoia.luno.core.domain.ActionResult
import hunoia.luno.core.domain.Capability
import hunoia.luno.core.domain.StoredAction
/**
 * 执行口的对外契约（`:feature:execute`）。
 *
 * 执行口只做 Execute：找定义 → 校验参数 → 查能力闸 → 调执行。它不认识 Trigger（手势、
 * 面板、自动化），也不认识宿主实现 —— 一切需要的能力都由这里定义的端口从 `:app` 注入。
 * 这样 `:feature:execute` 的依赖仅为 `core:common / core:domain / core:bridge`。
 */

/** 执行口需要的宿主能力（context / 无障碍服务 / 前台应用查询）。 */
interface ExecuteEnvironment {
    val appContext: Context

    /** 当前前台包名；读不到返回 null。 */
    fun currentPackageName(): String?
}

/**
 * 动作执行产生的 Surface 副作用与启动基础设施（面板 / 搜索 / 球 / 触钮 / 指针 /
 * 音量刮擦 / 启动应用 / 打开链接）。
 *
 * 实现方在 `:app`：执行口只声明「要弹哪个面、要起谁」，不碰窗口与启动细节，
 * 也不持有配置模型（小窗设置等由实现侧读）。
 */
interface SurfaceHost {
    fun toggleQuickAppLauncher(anchorX: Int, anchorY: Int)
    fun showQuickSearch()
    /** 调起系统语音识别弹窗；结果由 Surface Activity 处理。 */
    fun showSpeechRecognition(): Boolean
    fun showGridPanel(panelId: String, anchorX: Int, anchorY: Int)

    /** 网格面板是否还存在（面板被删时按无动作静默收口）。 */
    fun gridPanelExists(panelId: String): Boolean

    /** 音量刮擦 overlay；参数是每档位移阈值（dp），null = 用实现侧默认值。 */
    fun showVolumeScrub(stepThresholdDp: Int?): Boolean
    fun showPointer(): Boolean

    /** 临时隐藏触发钮（触发钮才隐藏；非手势链路无触发钮）。 */
    fun hideGestureButton(buttonId: String, delayMs: Long)

    /** 切到最近访问的非黑名单应用。 */
    suspend fun previousApp()

    /** 启动应用（小窗模式等全局设置由实现侧读）。 */
    suspend fun launchApp(spec: LaunchSpec): Boolean

    /** 打开链接（Query 参数与小窗设置由实现侧处理）。 */
    suspend fun openUrl(
        url: String,
        miniWindow: Boolean,
        queryParameters: List<QueryParam>,
        anchor: Pair<Int, Int>? = null,
    ): Boolean

    /** 启动已声明的 App Shortcut（入参是落库的快捷方式 JSON）。 */
    suspend fun launchShortcut(dataJson: String): Boolean

    /** 打开应用详情页；userId 非 0 时按对应分身用户打开。 */
    suspend fun openAppDetails(packageName: String, userId: Int = 0): ActionResult

    /** 当前应用以小窗打开；包名 / Activity 解析与全局设置由实现侧负责。 */
    suspend fun launchInPopup(packageName: String, anchor: Pair<Int, Int>? = null): ActionResult

    /** 当前应用挂成 MIUI 迷你小窗；位置/互换设置由实现侧读。 */
    suspend fun launchMiniWindow(packageName: String, anchor: Pair<Int, Int>? = null): ActionResult

    /** 分享文件：挑文件页（Activity）归 Surface 层。 */
    fun shareFile(): ActionResult

    /** 屏幕取点（transient Surface）：挂在无障碍服务窗口上。 */
    fun showScreenPick(): Boolean

    /**
     * 全屏手势的识屏入口：直接落到幽灵卡，锚点为按下点。
     * [sessionId] 用于取回按下瞬间的预捕获快照（同进程短期上下文）。
     */
    fun showScreenPickPortal(x: Int, y: Int, sessionId: String?): Boolean

    /** 屏幕标注：截屏 + 浮层；返回 false = 未接管（服务未就绪 / 窗口被拒）。 */
    fun showScreenAnnotate(): Boolean

    /** 图片编辑：系统图片选择器 → 全屏标注页；返回 false = 未启动。 */
    fun openImageAnnotate(): Boolean

    /** 剪贴板分词：把文本送进 OCR 结果页做分词视图；返回是否成功弹起。 */
    fun showOcrSegmentText(text: String): Boolean

    /** 剪贴板 OCR：解码图片并送 OCR 结果页识别；失败返回带因结果（含模型未就绪）。 */
    suspend fun showClipboardOcr(uri: Uri, mimeType: String): ActionResult

    /**
     * 悬浮球显隐：`hideForMs > 0` 走临时隐藏（只摘窗口、到期恢复），否则改 enabled 存量。
     * 配置写入与 FloatBallService 都留在 Surface 层。
     */
    suspend fun setFloatBallVisible(visible: Boolean?, hideForMs: Long): ActionResult

    /** 触钮显隐：`hideForMs > 0` 走临时隐藏（按 target/buttonIds 选作用钮），否则改 enabled 存量。 */
    suspend fun setButtonsVisible(
        visible: Boolean,
        target: ButtonTarget,
        buttonIds: List<String>,
        hideForMs: Long,
    ): ActionResult
}

/** 三态反馈文案与 Toast 出口：资源归属执行口之外，执行口只拿已本地化的字符串。 */
interface ExecuteMessages {
    fun failureText(reason: ActionFailure): String
    fun capabilityText(capability: Capability): String
    fun showToast(text: String)
}

/** 动作目录端口：typeId → 元数据。实现方在 `:feature:actions`。 */
fun interface ActionCatalog {
    fun definition(typeId: String): ActionMetadata?
}

/**
 * StoredAction → 执行器输入（[Action]）的映射端口。实现方在 `:feature:actions`。
 *
 * 映射失败只能抛 [ActionMappingException]：执行口据此返回三态，不允许异常冒出协程。
 */
fun interface ActionFactory {
    fun fromStored(stored: StoredAction): Action
}

/** 动作映射失败：`unsupported` = 未知 typeId；否则为参数非法。 */
class ActionMappingException(
    val unsupported: Boolean,
    message: String? = null,
) : Exception(message)

/**
 * 一次分发调用：`ActionRequest` 是唯一输入，其余是触发侧在提交前补好的上下文。
 *
 * 触发源专属状态（按钮、锚点、触点）不允许进入 Executor 的选择或执行分支。
 */
data class ExecuteCall(
    val request: ActionRequest,
    /** 手势触点（px）：点按类动作没填坐标时的兜底，也是面板锚点。 */
    val touchPosition: Pair<Int, Int>? = null,
    /** 触发按钮 id：`internal.buttonsVisibility` 的临时隐藏作用于它。 */
    val sourceButtonId: String? = null,
)

/** 分发落定回执：由调用方决定怎么用（手势侧据此发事件、刷新可见性）。 */
data class ExecuteSettlement(
    val request: ActionRequest,
    /** 落定的动作 typeId：动作库引用已就地解析为条目动作。 */
    val typeId: String,
    val result: ActionResult,
) {
    val succeeded: Boolean get() = result is ActionResult.Success
}
