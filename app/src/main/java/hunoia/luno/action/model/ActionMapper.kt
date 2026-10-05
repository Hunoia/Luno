package hunoia.luno.action.model

import hunoia.luno.config.defaults.ActionSettingsDefaults.HideGestureButtonDelayMs
import hunoia.luno.config.defaults.ActionSettingsDefaults.VolumeScrubHorizontalEnabled
import hunoia.luno.config.defaults.ActionSettingsDefaults.VolumeScrubStepThresholdDp
import hunoia.luno.config.model.MiniWindowSettings
import hunoia.luno.config.model.toMiniWindowSettings
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

object ActionMapper {

    fun fromStored(stored: StoredAction): Action {
        val p = stored.params
        return when (stored.typeId) {
            "app.launch" -> Action.LaunchApp(
                p.str("packageName"),
                p.bool("miniWindow"),
                p.str("className"),
                p.miniWindowSettings(),
            )
            "app.openActivity" -> Action.OpenActivity(
                p.str("packageName"),
                p.str("activityClassName"),
                p.bool("miniWindow"),
                p.miniWindowSettings(),
            )
            "app.details" -> Action.AppDetails(p.str("packageName"))
            "app.popup" -> Action.Popup(p.miniWindowSettings())
            "app.launchShortcut" -> Action.LaunchShortcut(p.str("data"))
            "intent.openUrl" -> Action.OpenUrl(
                p.str("url"),
                p["queryParameters"]?.jsonArray?.map { e ->
                    val o = e.jsonObject
                    QueryParam(o.str("name"), o.str("value"), o.bool("enabled", true))
                } ?: emptyList(),
                p.miniWindowSettings(),
            )
            "intent.shareText" -> Action.ShareText(p.str("text"), p.str("mimeType", "text/plain"))
            "intent.shareFile" -> Action.ShareFile
            "intent.assist" -> Action.Assist
            "system.openSettings" -> Action.OpenSettings(SettingsPage.valueOf(p.str("page")))
            "system.volume" -> Action.Volume(
                AudioStream.valueOf(p.str("stream")),
                VolumeDirection.valueOf(p.str("direction")),
            )
            "system.vibrate" -> Action.Vibrate(p.str("pattern", "0,500"))
            "system.clipboard" -> Action.Clipboard(
                ClipboardOperation.valueOf(p.str("operation")),
                p.intOrNull("length"),
            )
            "system.media" -> Action.Media(MediaCommand.valueOf(p.str("command")))
            "system.flashlight" -> Action.Flashlight
            "accessibility.back" -> Action.Back
            "accessibility.home" -> Action.Home
            "accessibility.recents" -> Action.Recents
            "accessibility.tap" -> Action.Tap
            "accessibility.longPress" -> Action.LongPress
            "accessibility.swipe" -> Action.Swipe(SwipeDirection.valueOf(p.str("direction")))
            "accessibility.inputText" -> Action.InputText(p.str("text"))
            "accessibility.screenshot" -> Action.Screenshot
            "accessibility.splitScreen" -> Action.SplitScreen
            "accessibility.powerButton" -> Action.PowerButton
            "accessibility.notificationPanel" -> Action.NotificationPanel
            "accessibility.quickPanel" -> Action.QuickPanel
            "accessibility.lockScreen" -> Action.LockScreen
            "package.enable" -> Action.PackageEnable(p.str("packageName"))
            "package.disable" -> Action.PackageDisable(p.str("packageName"))
            "package.forceStop" -> Action.PackageForceStop(p.str("packageName"))
            "package.clearData" -> Action.PackageClearData(p.str("packageName"))
            "package.uninstall" -> Action.PackageUninstall(p.str("packageName"))
            "settings.get" -> Action.SettingsGet(
                SettingsNamespace.valueOf(p.str("namespace")),
                p.str("key"),
            )
            "settings.put" -> Action.SettingsPut(
                SettingsNamespace.valueOf(p.str("namespace")),
                p.str("key"),
                p.str("value"),
            )
            "systemCmd.reboot" -> Action.Reboot
            "systemCmd.shutdown" -> Action.Shutdown
            "shell.custom" -> Action.CustomCommand(
                p.str("command"),
                p.bool("showToast"),
            )
            "internal.none" -> Action.None
            "internal.subGesture" -> Action.SubGesture
            "internal.hideGestureButton" -> Action.HideGestureButton(
                p.long("delayMs", HideGestureButtonDelayMs),
            )
            "internal.buttonsVisibility" -> Action.ButtonsVisibility(
                p.bool("visible", true),
                runCatching { ButtonTarget.valueOf(p.str("target", "ALL")) }.getOrDefault(ButtonTarget.ALL),
                p.arrayOfStr("buttonIds"),
            )
            "internal.quickAppLauncher" -> Action.QuickAppLauncher
            "internal.quickSearch" -> Action.QuickSearch
            "internal.volumeScrub" -> Action.VolumeScrub(
                horizontalEnabled = p.bool(
                    "horizontalEnabled",
                    VolumeScrubHorizontalEnabled,
                ),
                stepThresholdDp = p.int(
                    "stepThresholdDp",
                    VolumeScrubStepThresholdDp,
                ),
            )
            "internal.previousApp" -> Action.PreviousApp(p.arrayOfStr("excludePackageNames"))
            else -> Action.None
        }
    }


    private fun JsonObject.miniWindowSettings(): MiniWindowSettings? =
        this["miniWindowSettings"]?.toMiniWindowSettings()

    private fun JsonObject.str(key: String, default: String = ""): String =
        this[key]?.jsonPrimitive?.contentOrNull ?: default

    private fun JsonObject.bool(key: String, default: Boolean = false): Boolean =
        this[key]?.jsonPrimitive?.contentOrNull?.toBoolean() ?: default

    private fun JsonObject.intOrNull(key: String): Int? =
        this[key]?.jsonPrimitive?.contentOrNull?.toIntOrNull()

    private fun JsonObject.int(key: String, default: Int): Int =
        intOrNull(key) ?: default

    private fun JsonObject.long(key: String, default: Long): Long =
        this[key]?.jsonPrimitive?.contentOrNull?.toLongOrNull() ?: default

    private fun JsonObject.arrayOfStr(key: String): List<String> =
        this[key]?.jsonArray?.mapNotNull { it.jsonPrimitive?.contentOrNull } ?: emptyList()
}
