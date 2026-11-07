package hunoia.luno.action.model

import hunoia.luno.core.domain.AudioStream
import hunoia.luno.core.domain.QueryParam
import hunoia.luno.core.domain.QuickToggleKind
import hunoia.luno.core.domain.SettingsPage
import hunoia.luno.core.domain.VolumeDirection

/**
 * 执行器输入：由 `ActionFactory` 把 StoredAction 映射成具体动作。
 *
 * 本类型是纯契约，住在 `:core:domain`，供执行口（Dispatcher / ExecutorRegistry）
 * 与动作目录（`:feature:actions` 的映射 / 展示元数据）共同引用。不得携带资源 id /
 * 配置默认值：展示文案与默认值由各自层提供（见 `SettingsPage.label` 的 `:feature:actions` 扩展）。
 */

/** 触钮显隐作用范围（沿用自动化旧 RuleScope 语义）。 */
enum class ButtonTarget { ALL, EXCEPT, ONLY }

/** 触钮显隐 target 解析：纯函数便于单测（与 Executors.ButtonsVisibility 同语义）。 */
fun resolveButtonTargetIds(target: ButtonTarget, allIds: List<String>, selectedIds: List<String>): Set<String> =
    when (target) {
        ButtonTarget.ALL -> allIds.toSet()
        ButtonTarget.EXCEPT -> allIds.filterNot { it in selectedIds }.toSet()
        ButtonTarget.ONLY -> selectedIds.toSet()
    }

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
        /** 0 = 主用户；其他值 = 分身。 */
        val userId: Int = 0,
    ) : Action {
        override val typeId: String = hunoia.luno.core.domain.AppLaunchAction.TYPE_ID
    }
    data class AppDetails(
        val packageName: String,
        /** 0 = 主用户；其他值 = 分身（详情页按用户展示对应实例）。 */
        val userId: Int = 0,
    ) : Action {
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
    /**
     * 分享文本：用户从选择页直接选时无参数，执行期读当前剪贴板；
     * 搜索的「分享」打开方式会把查询结果写进 [text]，两条来源共用同一 typeId。
     */
    data class ShareText(val text: String = "") : Action {
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
    /** 剪贴板随机名称；密码长度等全局配置在动作设置的密码生成里。 */
    data object ClipboardRandomName : Action {
        override val typeId: String = "system.clipboardRandomName"
    }
    /** 调起系统语音识别弹窗，优先写入当前输入框，失败后写入剪贴板。 */
    data object SpeechRecognize : Action {
        override val typeId: String = "system.speechRecognize"
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
    /** 系统快捷开关：一次执行翻转 [kind] 的当前状态（读状态 → 取反 → 写入 → 读回确认）。 */
    data class QuickToggle(val kind: QuickToggleKind) : Action {
        override val typeId: String = "system.toggle"
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
    /** 点击当前触点位置：注入前先隐藏触钮，点完再恢复。 */
    data object Tap : Action {
        override val typeId: String = "accessibility.tap"
    }
    data object ScrollForward : Action {
        override val typeId: String = "accessibility.scrollForward"
    }
    data object ScrollBackward : Action {
        override val typeId: String = "accessibility.scrollBackward"
    }
    data object ScrollToTop : Action {
        override val typeId: String = "accessibility.scrollToTop"
    }
    data object ScrollToBottom : Action {
        override val typeId: String = "accessibility.scrollToBottom"
    }
    data object Screenshot : Action {
        override val typeId: String = "accessibility.screenshot"
    }

    /** 识别屏幕内容：长按落点取文字/图片，幽灵卡拖到动作上执行。 */
    data object ScreenPick : Action {
        override val typeId: String = "accessibility.screenPick"
    }

    /** 屏幕标注：触发后截屏，进入冻结底图的浮层标注（可穿透）。 */
    data object ScreenAnnotate : Action {
        override val typeId: String = "screen.annotate"
    }

    /** 图片编辑：先走系统图片选择器，再进入全屏标注。 */
    data object ImageEdit : Action {
        override val typeId: String = "image.edit"
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
     * 触钮显隐。两个分支写的是**两份不同的存储**，不构成一个统一的“后写胜出”顺序：
     *
     * - `hideForMs = 0`：改 `GestureButton.enabled` 用户基线（DataStore，后写覆盖先写）；
     * - `hideForMs > 0`：内存临时隐藏（`ButtonHideRuntime`，到期自动恢复，不碰 enabled 存量）。
     *
     * 临时隐藏到期后回到的是**当时的** enabled，期间被别的规则改过就会看到新基线。
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

    /** 剪贴板分词：读剪贴板文本，送 OCR 结果页做分词视图。 */
    data object ClipboardSegment : Action {
        override val typeId: String = "internal.clipboardSegment"
    }

    /** 剪贴板 OCR：读剪贴板图片，送 OCR 结果页识别（懒加载，先看图片后出文本）。 */
    data object ClipboardOcr : Action {
        override val typeId: String = "internal.clipboardOcr"
    }

    companion object {
        /** 「无动作」的 typeId：判断/构造无动作时的命名常量，散落字面量已全部收口于此。 */
        const val NONE_TYPE_ID = "internal.none"
    }
}

/** 动作所需能力/结果类型已下沉契约层，见 ActionModelAliases.kt 与 `hunoia.luno.core.domain`。 */
