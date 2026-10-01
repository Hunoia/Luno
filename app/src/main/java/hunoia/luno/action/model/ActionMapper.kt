package hunoia.luno.action.model

import hunoia.luno.config.defaults.ActionSettingsDefaults.HideGestureButtonDelayMs
import hunoia.luno.config.defaults.ActionSettingsDefaults.VolumeScrubHorizontalEnabled
import hunoia.luno.config.defaults.ActionSettingsDefaults.VolumeScrubStepThresholdDp
import hunoia.luno.config.model.MiniWindowSettings
import hunoia.luno.config.model.toMiniWindowSettings
import hunoia.luno.config.model.toJsonObject
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.add
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject

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
            "intent.shareFile" -> Action.ShareFile(p.str("filePath"), p.str("mimeType"))
            "intent.openFile" -> Action.OpenFile(p.str("filePath"), p.str("mimeType"))
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
            "internal.quickAppLauncher" -> Action.QuickAppLauncher
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

    fun toStored(action: Action): StoredAction {
        val params = buildJsonObject {
            when (action) {
                is Action.LaunchApp -> {
                    put("packageName", action.packageName)
                    put("miniWindow", action.miniWindow.toString())
                    if (action.className.isNotBlank()) put("className", action.className)
                    putMiniWindowSettings(action.miniWindowSettings)
                }
                is Action.OpenActivity -> {
                    put("packageName", action.packageName)
                    put("activityClassName", action.activityClassName)
                    put("miniWindow", action.miniWindow.toString())
                    putMiniWindowSettings(action.miniWindowSettings)
                }
                is Action.AppDetails -> put("packageName", action.packageName)
                is Action.Popup -> putMiniWindowSettings(action.miniWindowSettings)
                is Action.LaunchShortcut -> put("data", action.data)
                is Action.OpenUrl -> {
                    put("url", action.url)
                    if (action.queryParameters.isNotEmpty()) {
                        put("queryParameters", buildJsonArray {
                            action.queryParameters.forEach { qp ->
                                add(buildJsonObject {
                                    put("name", qp.name)
                                    put("value", qp.value)
                                    put("enabled", qp.enabled.toString())
                                })
                            }
                        })
                    }
                    putMiniWindowSettings(action.miniWindowSettings)
                }
                is Action.ShareText -> {
                    put("text", action.text)
                    put("mimeType", action.mimeType)
                }
                is Action.ShareFile -> {
                    put("filePath", action.filePath)
                    put("mimeType", action.mimeType)
                }
                is Action.OpenFile -> {
                    put("filePath", action.filePath)
                    put("mimeType", action.mimeType)
                }
                is Action.Assist -> {}
                is Action.OpenSettings -> put("page", action.page.name)
                is Action.Volume -> {
                    put("stream", action.stream.name)
                    put("direction", action.direction.name)
                }
                is Action.Vibrate -> put("pattern", action.pattern)
                is Action.Clipboard -> {
                    put("operation", action.operation.name)
                    action.length?.let { put("length", it.toString()) }
                }
                is Action.Media -> put("command", action.command.name)
                is Action.Flashlight -> {}
                is Action.Back -> {}
                is Action.Home -> {}
                is Action.Recents -> {}
                is Action.Tap -> {}
                is Action.LongPress -> {}
                is Action.Swipe -> put("direction", action.direction.name)
                is Action.InputText -> put("text", action.text)
                is Action.Screenshot -> {}
                is Action.SplitScreen -> {}
                is Action.PowerButton -> {}
                is Action.NotificationPanel -> {}
                is Action.QuickPanel -> {}
                is Action.LockScreen -> {}
                is Action.PackageEnable -> put("packageName", action.packageName)
                is Action.PackageDisable -> put("packageName", action.packageName)
                is Action.PackageForceStop -> put("packageName", action.packageName)
                is Action.PackageClearData -> put("packageName", action.packageName)
                is Action.PackageUninstall -> put("packageName", action.packageName)
                is Action.SettingsGet -> {
                    put("namespace", action.namespace.name)
                    put("key", action.key)
                }
                is Action.SettingsPut -> {
                    put("namespace", action.namespace.name)
                    put("key", action.key)
                    put("value", action.value)
                }
                is Action.Reboot -> {}
                is Action.Shutdown -> {}
                is Action.CustomCommand -> {
                    put("command", action.command)
                    put("showToast", action.showToast.toString())
                }
                is Action.None -> {}
                is Action.SubGesture -> {}
            is Action.HideGestureButton -> put("delayMs", action.delayMs.toString())
            is Action.QuickAppLauncher -> {}
            is Action.VolumeScrub -> {
                put("horizontalEnabled", action.horizontalEnabled.toString())
                put("stepThresholdDp", action.stepThresholdDp.toString())
            }
            is Action.PreviousApp -> {
                if (action.excludePackageNames.isNotEmpty()) {
                    put("excludePackageNames", buildJsonArray {
                        action.excludePackageNames.forEach { add(it) }
                    })
                }
            }
            }
        }
        return StoredAction(action.typeId, params)
    }

    private fun JsonObject.miniWindowSettings(): MiniWindowSettings? =
        this["miniWindowSettings"]?.toMiniWindowSettings()

    private fun JsonObjectBuilder.putMiniWindowSettings(settings: MiniWindowSettings?) {
        settings?.let { put("miniWindowSettings", it.toJsonObject()) }
    }

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
