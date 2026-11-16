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
    /** 冻结一键名单：对名单内全部应用置 DISABLED_USER。 */
    data object FreezeOneKey : Action {
        override val typeId: String = "app.freezeOneKey"
    }

    /** 解冻一键名单：只处理「名单 ∩ 当前 disabled」，suspend 包不进入目标。 */
    data object UnfreezeOneKey : Action {
        override val typeId: String = "app.unfreezeOneKey"
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
    /**
     * 迷你小窗当前应用：走 MIUI 私有事务挂成系统迷你浮窗，与小窗（freeform）相互独立。
     * 位置与是否互换用动作设置→小窗里的「迷你小窗」分组。
     */
    data object MiniWindow : Action {
        override val typeId: String = "system.mini_window"
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
    /** 拍照识别：直接调系统相机，拍完进 OCR 结果页框选识别，不依赖无障碍。 */
    data object PhotoRecognize : Action {
        override val typeId: String = "image.recognize"
    }
    /** 从相册选图：系统相册选图，再框选区域 OCR（结果页内完成），不依赖无障碍。 */
    data object ImageRecognizeGallery : Action {
        override val typeId: String = "image.recognizeGallery"
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
    /** 网格面板：触点附近弹出网格快捷中心（panelId 引用全局具名面板，见 GridPanel）。 */
    data class GridPanel(val panelId: String) : Action {
        override val typeId: String = "internal.gridPanel"
    }
    data object QuickSearch : Action {
        override val typeId: String = "internal.quickSearch"
    }
    /**
     * 临时隐藏触发本次动作的触钮。
     *
     * 只认执行上下文里的 `sourceButtonId`；时长由全局「手势工具」设置决定，
     * 不写进动作参数，保证改设置立刻作用于已有绑定。
     */
    data object HideTriggerButton : Action {
        override val typeId: String = "internal.hideTriggerButton"
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
    /**
     * 最近应用滚动选择器：全屏滚轮列出最近应用，滑动预览、点选启动、点空白/返回取消。
     * 触发方式（按住/抬手）由手势类型自动决定（与 volumeScrub 同构）；
     * 排列方向是动作参数，在动作选择页行尾设置里配。
     */
    data class RecentsPicker(
        /** 滚轮排列方向：竖向 / 横向。未指定时由实现侧用配置默认值。 */
        val layout: RecentsPickerLayout = RecentsPickerLayout.VERTICAL,
        /** 列表外滑动灵敏度（百分比，100 = 1.0x）。null = 用实现侧默认。 */
        val blankScrollSensitivityPercent: Int? = null,
        /** 图标边长 dp。null = 用实现侧默认。 */
        val iconSizeDp: Int? = null,
    ) : Action {
        override val typeId: String = "internal.recentsPicker"
    }
    /** 上一个应用：切换到最近访问的非黑名单应用，黑名单在动作设置全局配置。 */
    data object PreviousApp : Action {
        override val typeId: String = "internal.previousApp"
    }

    /** 识别剪贴板：读剪贴板，文本送分词视图、图片送 OCR 结果页（懒加载，先看图后出文本）。 */
    data object ClipboardRecognize : Action {
        override val typeId: String = "internal.clipboardRecognize"
    }

    companion object {
        /** 「无动作」的 typeId：判断/构造无动作时的命名常量，散落字面量已全部收口于此。 */
        const val NONE_TYPE_ID = "internal.none"
    }
}

/** 动作所需能力/结果类型已下沉契约层，见 ActionModelAliases.kt 与 `hunoia.luno.core.domain`。 */
