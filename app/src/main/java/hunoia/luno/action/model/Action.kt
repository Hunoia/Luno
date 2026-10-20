package hunoia.luno.action.model

import hunoia.luno.config.defaults.ActionSettingsDefaults.HideGestureButtonDelayMs
import hunoia.luno.config.defaults.ActionSettingsDefaults.VolumeScrubHorizontalEnabled
import hunoia.luno.config.defaults.ActionSettingsDefaults.VolumeScrubStepThresholdDp
import hunoia.luno.config.model.MiniWindowSettings
import android.media.AudioManager

data class QueryParam(val name: String, val value: String, val enabled: Boolean = true)

enum class SettingsPage {
    WIFI, BLUETOOTH, DISPLAY, BATTERY, APPS, NETWORK, ACCESSIBILITY, MAIN
}

/** 触钮显隐作用范围（沿用自动化旧 RuleScope 语义）。 */
enum class ButtonTarget { ALL, EXCEPT, ONLY }

enum class AudioStream { MUSIC, RING, NOTIFICATION, ALARM, VOICE, SYSTEM }

/** 动作层与运行时层共用，避免 AudioController / AudioVolumeTracker 各写一份 */
fun AudioStream.toStreamType(): Int = when (this) {
    AudioStream.MUSIC -> AudioManager.STREAM_MUSIC
    AudioStream.RING -> AudioManager.STREAM_RING
    AudioStream.NOTIFICATION -> AudioManager.STREAM_NOTIFICATION
    AudioStream.ALARM -> AudioManager.STREAM_ALARM
    AudioStream.VOICE -> AudioManager.STREAM_VOICE_CALL
    AudioStream.SYSTEM -> AudioManager.STREAM_SYSTEM
}

enum class VolumeDirection { UP, DOWN }

enum class ClipboardOperation { RANDOM_NAME, GENERATE_PASSWORD }

enum class MediaCommand { PLAY_PAUSE, NEXT, PREVIOUS, MUTE }

enum class SwipeDirection { UP, DOWN, LEFT, RIGHT, TO_TOP }

enum class SettingsNamespace { SYSTEM, GLOBAL, SECURE }

sealed interface Action {
    val typeId: String

    // App
    data class LaunchApp(
        val packageName: String,
        val miniWindow: Boolean = false,
        val className: String = "",
        val miniWindowSettings: MiniWindowSettings? = null,
    ) : Action {
        override val typeId: String = "app.launch"
    }
    data class OpenActivity(
        val packageName: String,
        val activityClassName: String,
        val miniWindow: Boolean = false,
        val miniWindowSettings: MiniWindowSettings? = null,
    ) : Action {
        override val typeId: String = "app.openActivity"
    }
    data class AppDetails(val packageName: String) : Action {
        override val typeId: String = "app.details"
    }
    data class Popup(val miniWindowSettings: MiniWindowSettings? = null) : Action {
        override val typeId: String = "app.popup"
    }
    data class LaunchShortcut(val data: String) : Action {
        override val typeId: String = "app.launchShortcut"
    }

    // Intent
    data class OpenUrl(
        val url: String,
        val queryParameters: List<QueryParam> = emptyList(),
        val miniWindowSettings: MiniWindowSettings? = null,
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
    data class Clipboard(
        val operation: ClipboardOperation,
        val length: Int? = null,
    ) : Action {
        override val typeId: String = "system.clipboard"
    }
    data class Media(val command: MediaCommand) : Action {
        override val typeId: String = "system.media"
    }
    data object Flashlight : Action {
        override val typeId: String = "system.flashlight"
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
    data class Swipe(val direction: SwipeDirection) : Action {
        override val typeId: String = "accessibility.swipe"
    }
    data class InputText(val text: String) : Action {
        override val typeId: String = "accessibility.inputText"
    }
    data object Screenshot : Action {
        override val typeId: String = "accessibility.screenshot"
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

    // Package
    data class PackageEnable(val packageName: String) : Action {
        override val typeId: String = "package.enable"
    }
    data class PackageDisable(val packageName: String) : Action {
        override val typeId: String = "package.disable"
    }
    data class PackageForceStop(val packageName: String) : Action {
        override val typeId: String = "package.forceStop"
    }
    data class PackageClearData(val packageName: String) : Action {
        override val typeId: String = "package.clearData"
    }
    data class PackageUninstall(val packageName: String) : Action {
        override val typeId: String = "package.uninstall"
    }

    // Settings
    data class SettingsGet(val namespace: SettingsNamespace, val key: String) : Action {
        override val typeId: String = "settings.get"
    }
    data class SettingsPut(
        val namespace: SettingsNamespace,
        val key: String,
        val value: String,
    ) : Action {
        override val typeId: String = "settings.put"
    }

    // SystemCmd
    data object Reboot : Action {
        override val typeId: String = "systemCmd.reboot"
    }
    data object Shutdown : Action {
        override val typeId: String = "systemCmd.shutdown"
    }

    // Shell
    data class CustomCommand(val command: String, val showToast: Boolean = false) : Action {
        override val typeId: String = "shell.custom"
    }

    // Internal
    data object None : Action {
        override val typeId: String = "internal.none"
    }
    data object SubGesture : Action {
        override val typeId: String = "internal.subGesture"
    }
    data class HideGestureButton(
        val delayMs: Long = HideGestureButtonDelayMs,
    ) : Action {
        override val typeId: String = "internal.hideGestureButton"
    }
    /**
     * 触钮显隐（替代自动化旧 HIDE/SHOW_BUTTONS 策略）：直接改按钮 enabled 存量，
     * 后求值/后执行的覆盖先生效（规则列表顺序即优先级）。
     */
    data class ButtonsVisibility(
        val visible: Boolean = true,
        val target: ButtonTarget = ButtonTarget.ALL,
        val buttonIds: List<String> = emptyList(),
    ) : Action {
        override val typeId: String = "internal.buttonsVisibility"
    }
    data object QuickAppLauncher : Action {
        override val typeId: String = "internal.quickAppLauncher"
    }
    /**
     * 悬浮球显隐（与 [ButtonsVisibility] 同物：直接改 enabled 存量，
     * 后求值/后执行的覆盖先生效）。`visible = null` 表示切换。
     */
    data class FloatBallVisibility(val visible: Boolean? = null) : Action {
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
        val horizontalEnabled: Boolean = VolumeScrubHorizontalEnabled,
        val stepThresholdDp: Int = VolumeScrubStepThresholdDp,
    ) : Action {
        override val typeId: String = "internal.volumeScrub"
    }
    /** 指针：开启光标模式，拖动定位、松手在光标处点击。无参数，设置在 GestureSettings.Pointer。 */
    data object Pointer : Action {
        override val typeId: String = "internal.pointer"
    }
    data class PreviousApp(
        val excludePackageNames: List<String> = emptyList(),
    ) : Action {
        override val typeId: String = "internal.previousApp"
    }
    /**
     * 运行规则（规则即动作）：只跑目标规则的 effect steps，不经过引擎仲裁，
     * 目标自身的触发条件/节流/优先级全部忽略。嵌套由执行层深度上限兜底。
     */
    data class RunRule(val ruleId: String) : Action {
        override val typeId: String = "internal.runRule"
    }
}

/** 动作所需能力（无障碍/Shizuku），执行前由 [hunoia.luno.action.execution.ActionExecutorRegistry] 校验。 */
enum class Capability {
    None,
    Accessibility,
    Shizuku
}

/** 动作执行结果。 */
sealed interface ActionResult {
    data class Success(val message: String? = null) : ActionResult
    data class Failed(val reason: ActionFailure, val message: String? = null) : ActionResult
    data class RequiresCapability(val capability: Capability) : ActionResult
}

/** 动作失败原因。 */
enum class ActionFailure {
    InvalidParameter,
    ExecutionFailed,
    PermissionDenied,
    Unsupported
}
