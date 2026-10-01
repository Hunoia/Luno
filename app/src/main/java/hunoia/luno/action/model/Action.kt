package hunoia.luno.action.model

import hunoia.luno.config.defaults.ActionSettingsDefaults.HideGestureButtonDelayMs
import hunoia.luno.config.defaults.ActionSettingsDefaults.VolumeScrubHorizontalEnabled
import hunoia.luno.config.defaults.ActionSettingsDefaults.VolumeScrubStepThresholdDp

data class QueryParam(val name: String, val value: String, val enabled: Boolean = true)

enum class SettingsPage {
    WIFI, BLUETOOTH, DISPLAY, BATTERY, APPS, NETWORK, ACCESSIBILITY, MAIN
}

enum class AudioStream { MUSIC, RING, NOTIFICATION, ALARM, VOICE, SYSTEM }

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
    ) : Action {
        override val typeId: String = "app.launch"
    }
    data class OpenActivity(
        val packageName: String,
        val activityClassName: String,
        val miniWindow: Boolean = false,
    ) : Action {
        override val typeId: String = "app.openActivity"
    }
    data class AppDetails(val packageName: String) : Action {
        override val typeId: String = "app.details"
    }
    data object Popup : Action {
        override val typeId: String = "app.popup"
    }
    data class LaunchShortcut(val data: String) : Action {
        override val typeId: String = "app.launchShortcut"
    }

    // Intent
    data class OpenUrl(val url: String, val queryParameters: List<QueryParam> = emptyList()) : Action {
        override val typeId: String = "intent.openUrl"
    }
    data class ShareText(val text: String, val mimeType: String = "text/plain") : Action {
        override val typeId: String = "intent.shareText"
    }
    data class ShareFile(val filePath: String, val mimeType: String = "") : Action {
        override val typeId: String = "intent.shareFile"
    }
    data class OpenFile(val filePath: String, val mimeType: String = "") : Action {
        override val typeId: String = "intent.openFile"
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
    data object Tap : Action {
        override val typeId: String = "accessibility.tap"
    }
    data object LongPress : Action {
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
    data object QuickAppLauncher : Action {
        override val typeId: String = "internal.quickAppLauncher"
    }
    data class VolumeScrub(
        val horizontalEnabled: Boolean = VolumeScrubHorizontalEnabled,
        val stepThresholdDp: Int = VolumeScrubStepThresholdDp,
    ) : Action {
        override val typeId: String = "internal.volumeScrub"
    }
    data class PreviousApp(
        val excludePackageNames: List<String> = emptyList(),
    ) : Action {
        override val typeId: String = "internal.previousApp"
    }
}
