package hunoia.luno.action.model

import hunoia.luno.core.domain.AudioStream
import hunoia.luno.core.domain.QueryParam
import hunoia.luno.core.domain.VolumeDirection

/**
 * 执行器输入：由 [hunoia.luno.execute.ActionFactory] 把 StoredAction 映射成具体动作。
 *
 * 本类型住在执行口（`:feature:execute`），因为它就是 Executor 的入参；动作目录、
 * 参数编辑器元数据仍在 `:feature:actions`。不得携带资源 id / 配置默认值：
 * 展示文案与默认值由各自层提供（见 `SettingsPage.labelRes` 的 app 侧扩展）。
 */

enum class SettingsPage {
    WIFI, BLUETOOTH, DISPLAY, BATTERY, APPS, NETWORK, ACCESSIBILITY, MAIN
}

/** 触钮显隐作用范围（沿用自动化旧 RuleScope 语义）。 */
enum class ButtonTarget { ALL, EXCEPT, ONLY }

/** 触钮显隐 target 解析：纯函数便于单测（与 Executors.ButtonsVisibility 同语义）。 */
fun resolveButtonTargetIds(target: ButtonTarget, allIds: List<String>, selectedIds: List<String>): Set<String> =
    when (target) {
        ButtonTarget.ALL -> allIds.toSet()
        ButtonTarget.EXCEPT -> allIds.filterNot { it in selectedIds }.toSet()
        ButtonTarget.ONLY -> selectedIds.toSet()
    }

enum class SwipeDirection { UP, DOWN, LEFT, RIGHT, TO_TOP }

enum class MediaCommand { PLAY_PAUSE, NEXT, PREVIOUS, MUTE }

sealed interface Action {
    val typeId: String

    // App
    data class LaunchApp(
        val packageName: String,
        val miniWindow: Boolean = false,
        val className: String = "",
        /** 空 = 不覆盖，保持系统的默认启动行为。 */
        val intentAction: String = "",
        val data: String = "",
        val extras: Map<String, String> = emptyMap(),
    ) : Action {
        override val typeId: String = "app.launch"
    }
    data class AppDetails(val packageName: String) : Action {
        override val typeId: String = "app.details"
    }
    /** 弹出当前应用：小窗尺寸/位置一律用全局小窗设置（动作设置→小窗）。 */
    data object Popup : Action {
        override val typeId: String = "app.popup"
    }
    data class LaunchShortcut(val data: String) : Action {
        override val typeId: String = "app.launchShortcut"
    }

    // Intent
    data class OpenUrl(
        val url: String,
        val queryParameters: List<QueryParam> = emptyList(),
        val miniWindow: Boolean = false,
    ) : Action {
        override val typeId: String = "intent.openUrl"
    }
    data class ShareText(val text: String, val mimeType: String = "text/plain") : Action {
        override val typeId: String = "intent.shareText"
    }
    data object ShareFile : Action {
        override val typeId: String = "intent.shareFile"
    }
    data object Assist : Action {
        override val typeId: String = "intent.assist"
    }

    // System
    data class OpenSettings(val page: SettingsPage) : Action {
        override val typeId: String = "system.openSettings"
    }
    data class Volume(val stream: AudioStream, val direction: VolumeDirection) : Action {
        override val typeId: String = "system.volume"
    }
    data class Vibrate(val pattern: String = "0,500") : Action {
        override val typeId: String = "system.vibrate"
    }
    /** 剪贴板随机名称；密码长度等全局配置在动作设置的密码生成里。 */
    data object ClipboardRandomName : Action {
        override val typeId: String = "system.clipboardRandomName"
    }
    data object MediaPlayPause : Action {
        override val typeId: String = "system.mediaPlayPause"
    }
    data object MediaNext : Action {
        override val typeId: String = "system.mediaNext"
    }
    data object MediaPrevious : Action {
        override val typeId: String = "system.mediaPrevious"
    }
    data object MediaMute : Action {
        override val typeId: String = "system.mediaMute"
    }
    data object Flashlight : Action {
        override val typeId: String = "system.flashlight"
    }
    /** HTTP 请求：GET/POST/...，headers 按行 `Key: Value`，正文按 UTF-8。 */
    data class HttpRequest(
        val url: String,
        val method: String = "GET",
        val headers: String = "",
        val body: String = "",
    ) : Action {
        override val typeId: String = "http.request"
    }
    /** 轻量 Toast（自动化里不依赖动作执行结果提示）。 */
    data class Toast(val text: String) : Action {
        override val typeId: String = "system.toast"
    }
    /** 发系统通知（Android 13+ 受 POST_NOTIFICATIONS 控制）。 */
    data class Notify(val title: String, val text: String) : Action {
        override val typeId: String = "system.notify"
    }
    /** 写剪贴板（与“剪贴板随机名称”区分，内容由用户/模板提供）。 */
    data class SetClipboard(val text: String) : Action {
        override val typeId: String = "system.clipboardSet"
    }

    // Accessibility
    data object Back : Action {
        override val typeId: String = "accessibility.back"
    }
    data object Home : Action {
        override val typeId: String = "accessibility.home"
    }
    data object Recents : Action {
        override val typeId: String = "accessibility.recents"
    }
    data class Tap(
        val xDp: Int? = null,
        val yDp: Int? = null,
        /** 用触发位置坐标：忽略上面的固定坐标，点在手指按下的位置（隐藏触钮后即穿透到下层）。 */
        val useTriggerPoint: Boolean = false,
    ) : Action {
        override val typeId: String = "accessibility.tap"
    }
    data class LongPress(
        val xDp: Int? = null,
        val yDp: Int? = null,
        val durationMs: Long = 1000L,
        /** 用触发位置坐标：忽略固定坐标，长按在手指按下的位置。 */
        val useTriggerPoint: Boolean = false,
    ) : Action {
        override val typeId: String = "accessibility.longPress"
    }
    data class DoubleTap(
        val xDp: Int? = null,
        val yDp: Int? = null,
        /** 用触发位置坐标：忽略固定坐标，双击在手指按下的位置。 */
        val useTriggerPoint: Boolean = false,
    ) : Action {
        override val typeId: String = "accessibility.doubleTap"
    }
    data class ScrollFocused(val forward: Boolean) : Action {
        override val typeId: String = "accessibility.scroll"
    }
    data class Swipe(val direction: SwipeDirection) : Action {
        override val typeId: String = "accessibility.swipe"
    }
    data object Screenshot : Action {
        override val typeId: String = "accessibility.screenshot"
    }

    /** 识别屏幕内容：全屏拾取层，点文本复制文字、点图片/框选复制图片。 */
    data object ScreenPick : Action {
        override val typeId: String = "accessibility.screenPick"
    }

    /** 识屏取链：扫当前应用的无障碍文本，抽出链接列表后按链接分发规则直达。 */
    data object ScreenLinks : Action {
        override val typeId: String = "accessibility.screenLinks"
    }
    data object SplitScreen : Action {
        override val typeId: String = "accessibility.splitScreen"
    }
    data object PowerButton : Action {
        override val typeId: String = "accessibility.powerButton"
    }
    data object NotificationPanel : Action {
        override val typeId: String = "accessibility.notificationPanel"
    }
    data object QuickPanel : Action {
        override val typeId: String = "accessibility.quickPanel"
    }
    data object LockScreen : Action {
        override val typeId: String = "accessibility.lockScreen"
    }

    // Shell
    data class CustomCommand(
        val command: String,
        val showToast: Boolean = false,
        val copyOutput: Boolean = false,
    ) : Action {
        override val typeId: String = "shell.custom"
    }

    // Internal
    data object None : Action {
        override val typeId: String = NONE_TYPE_ID
    }
    data object SubGesture : Action {
        override val typeId: String = "internal.subGesture"
    }
    /**
     * 触钮显隐：直接改按钮 enabled 存量，后求值/后执行的覆盖先生效
     *（规则列表顺序即优先级）。
     * `hideForMs > 0` 为临时隐藏（走 ButtonHideRuntime，到期自动恢复，不碰 enabled 存量），
     * 原「隐藏触钮」动作合并于此。
     */
    data class ButtonsVisibility(
        val visible: Boolean = true,
        val target: ButtonTarget = ButtonTarget.ALL,
        val buttonIds: List<String> = emptyList(),
        val hideForMs: Long = 0,
    ) : Action {
        override val typeId: String = "internal.buttonsVisibility"
    }
    data object QuickAppLauncher : Action {
        override val typeId: String = "internal.quickAppLauncher"
    }
    /**
     * 悬浮球显隐：`hideForMs = 0` 直接改 enabled 存量，后求值/后执行的覆盖先生效。
     * `visible = null` 表示切换。`hideForMs > 0` 为临时隐藏（只摘运行时窗口，到期自动恢复，
     * 不碰 enabled 存量 —— 进程重启即失效）。
     */
    data class FloatBallVisibility(
        val visible: Boolean? = null,
        val hideForMs: Long = 0,
    ) : Action {
        override val typeId: String = "internal.floatBallVisibility"
    }
    /** 网格面板：触点附近弹出网格快捷中心（panelId 引用全局具名面板，见 GridPanel）。 */
    data class GridPanel(val panelId: String) : Action {
        override val typeId: String = "internal.gridPanel"
    }
    data object QuickSearch : Action {
        override val typeId: String = "internal.quickSearch"
    }
    data class VolumeScrub(
        /** 每档位移阈值（dp）；null = 未指定，由实现侧用配置默认值。本层不持默认值。 */
        val stepThresholdDp: Int? = null,
    ) : Action {
        override val typeId: String = "internal.volumeScrub"
    }
    /** 指针：开启光标模式，拖动定位、松手在光标处点击。无参数，设置在 GestureSettings.Pointer。 */
    data object Pointer : Action {
        override val typeId: String = "internal.pointer"
    }
    /** 上一个应用：切换到最近访问的非黑名单应用，黑名单在动作设置全局配置。 */
    data object PreviousApp : Action {
        override val typeId: String = "internal.previousApp"
    }
    /**
     * 运行规则（规则即动作）：只跑目标规则的 effect steps，目标自身的触发条件/
     * 节流/优先级全部忽略；嵌套由执行层深度上限兜底。
     */
    data class RunRule(
        val ruleId: String,
        /** `param.<name>` 子规则参数，已由参数模板展开。 */
        val params: Map<String, String> = emptyMap(),
    ) : Action {
        override val typeId: String = "internal.runRule"
    }

    companion object {
        /** 「无动作」的 typeId：判断/构造无动作时的命名常量，散落字面量已全部收口于此。 */
        const val NONE_TYPE_ID = "internal.none"
    }
}

/** 动作所需能力/结果类型已下沉契约层，见 ActionModelAliases.kt 与 `hunoia.luno.core.domain`。 */
