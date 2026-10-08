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

/** 存储里的 typeId 在定义表中不存在（数据损坏、外部构造的备份）。 */
class UnknownActionTypeException(val typeId: String) :
    IllegalArgumentException("unknown action type: $typeId")

/** 枚举参数缺失或取值不在枚举内。 */
class InvalidActionParameterException(val key: String, val raw: String) :
    IllegalArgumentException("invalid enum param $key=\"$raw\"")

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
            "system.openSettings" -> Action.OpenSettings(p.enumValue("page"))
            "system.volume" -> Action.Volume(
                p.enumValue("stream"),
                p.enumValue("direction"),
            )
            "system.vibrate" -> Action.Vibrate(p.str("pattern", "0,500"))
            "system.clipboard" -> Action.Clipboard(
                p.enumValue("operation"),
                p.intOrNull("length"),
            )
            "system.media" -> Action.Media(p.enumValue("command"))
            "system.flashlight" -> Action.Flashlight
            "accessibility.back" -> Action.Back
            "accessibility.home" -> Action.Home
            "accessibility.recents" -> Action.Recents
            "accessibility.tap" -> Action.Tap
            "accessibility.longPress" -> Action.LongPress
            "accessibility.swipe" -> Action.Swipe(p.enumValue("direction"))
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
                p.enumValue("namespace"),
                p.str("key"),
            )
            "settings.put" -> Action.SettingsPut(
                p.enumValue("namespace"),
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
                p.enumValueOr("target", ButtonTarget.ALL),
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
            "internal.runRule" -> Action.RunRule(p.str("ruleId"))
            else -> throw UnknownActionTypeException(stored.typeId)
        }
    }


    private fun JsonObject.miniWindowSettings(): MiniWindowSettings? =
        this["miniWindowSettings"]?.toMiniWindowSettings()

    /** 枚举参数解析：缺失/非法值抛受控异常（由执行层转成 InvalidParameter），不再裸 valueOf 崩协程。 */
    private inline fun <reified T : Enum<T>> JsonObject.enumValue(key: String): T {
        val raw = str(key)
        return enumValues<T>().firstOrNull { it.name == raw }
            ?: throw InvalidActionParameterException(key, raw)
    }

    /** 同 [enumValue]，但缺 key 时用默认值（值存在却非法仍然报错）。 */
    private inline fun <reified T : Enum<T>> JsonObject.enumValueOr(key: String, default: T): T =
        if (this[key] == null) default else enumValue(key)

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
