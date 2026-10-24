package hunoia.luno.action.model

import hunoia.luno.config.defaults.ActionSettingsDefaults.VolumeScrubStepThresholdDp
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive

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
                packageName = p.str("packageName"),
                miniWindow = p.bool("miniWindow"),
                className = p.str("className"),
                intentAction = p.str("intentAction"),
                data = p.str("data"),
                extras = p.mapOfStr("extras"),
            )
            "app.details" -> Action.AppDetails(p.str("packageName"))
            "app.popup" -> Action.Popup
            "app.launchShortcut" -> Action.LaunchShortcut(p.str("data"))
            "intent.openUrl" -> Action.OpenUrl(
                url = p.str("url"),
                queryParameters = p.queryParams(),
                miniWindow = p.bool("miniWindow"),
            )
            "intent.shareText" -> Action.ShareText(p.str("text"), p.str("mimeType", "text/plain"))
            "intent.shareFile" -> Action.ShareFile
            "intent.assist" -> Action.Assist
            "system.openSettings" -> Action.OpenSettings(p.enumValueOr("page", SettingsPage.MAIN))
            "system.volume" -> Action.Volume(
                p.enumValueOr("stream", AudioStream.MUSIC),
                p.enumValueOr("direction", VolumeDirection.UP),
            )
            "system.vibrate" -> Action.Vibrate(p.str("pattern", "0,500"))
            "system.clipboardRandomName" -> Action.ClipboardRandomName
            "system.mediaPlayPause" -> Action.MediaPlayPause
            "system.mediaNext" -> Action.MediaNext
            "system.mediaPrevious" -> Action.MediaPrevious
            "system.mediaMute" -> Action.MediaMute
            "system.flashlight" -> Action.Flashlight
            "accessibility.back" -> Action.Back
            "accessibility.home" -> Action.Home
            "accessibility.recents" -> Action.Recents
            "accessibility.tap" -> Action.Tap(
                p.intOrNull(PARAM_X_DP),
                p.intOrNull(PARAM_Y_DP),
                p.bool(USE_TRIGGER_POINT_KEY),
            )
            "accessibility.longPress" -> Action.LongPress(
                p.intOrNull(PARAM_X_DP),
                p.intOrNull(PARAM_Y_DP),
                p.long("durationMs", 1000L),
                p.bool(USE_TRIGGER_POINT_KEY),
            )
            "accessibility.doubleTap" -> Action.DoubleTap(
                p.intOrNull(PARAM_X_DP),
                p.intOrNull(PARAM_Y_DP),
                p.bool(USE_TRIGGER_POINT_KEY),
            )
            "accessibility.scroll" -> Action.ScrollFocused(
                p.str("direction") != "BACKWARD",
            )
            "accessibility.swipe" -> Action.Swipe(p.enumValue("direction"))
            "accessibility.screenshot" -> Action.Screenshot
            "accessibility.splitScreen" -> Action.SplitScreen
            "accessibility.powerButton" -> Action.PowerButton
            "accessibility.notificationPanel" -> Action.NotificationPanel
            "accessibility.quickPanel" -> Action.QuickPanel
            "accessibility.lockScreen" -> Action.LockScreen
            "shell.custom" -> Action.CustomCommand(
                p.str("command"),
                p.bool("showToast"),
                p.bool("copyOutput"),
            )
            "internal.none" -> Action.None
            "internal.subGesture" -> Action.SubGesture
            "internal.buttonsVisibility" -> Action.ButtonsVisibility(
                p.bool("visible", false),
                p.enumValueOr("target", ButtonTarget.ALL),
                p.arrayOfStr("buttonIds"),
                p.long("hideForMs", 0),
            )
            "internal.quickAppLauncher" -> Action.QuickAppLauncher
            "internal.floatBallVisibility" -> Action.FloatBallVisibility(
                when (p.str("visible", "toggle")) {
                    "true" -> true
                    "false" -> false
                    else -> null
                },
                p.long("hideForMs", 0),
            )
            "internal.gridPanel" -> Action.GridPanel(p.str(GRID_PANEL_PARAM_PANEL_ID))
            "internal.quickSearch" -> Action.QuickSearch
            "internal.volumeScrub" -> Action.VolumeScrub(
                stepThresholdDp = p.int(
                    "stepThresholdDp",
                    VolumeScrubStepThresholdDp,
                ),
            )
            "internal.pointer" -> Action.Pointer
            "internal.previousApp" -> Action.PreviousApp
            "internal.runRule" -> Action.RunRule(p.str("ruleId"))
            // 动作面板由 SideGestureContainer.runGestureActions 就地展开成面板项，正常路径
            // 不会流到执行层。能走到这里说明面板被删或内容为空 —— 按「无动作」收口，
            // 而不是抛 UnknownActionTypeException 让手势链路报错。
            ACTION_PANEL_TYPE_ID -> Action.None
            else -> throw UnknownActionTypeException(stored.typeId)
        }
    }


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

    /** 键值对参数：存成 JSON 对象；空键忽略（编辑器允许留空行）。 */
    private fun JsonObject.mapOfStr(key: String): Map<String, String> {
        val obj = this[key] as? JsonObject ?: return emptyMap()
        return obj.entries.mapNotNull { (k, v) ->
            if (k.isBlank()) null else k to ((v as? JsonPrimitive)?.contentOrNull.orEmpty())
        }.toMap()
    }

    /** Query 参数：存成 JSON 对象 `{key: value}`，空键忽略。 */
    private fun JsonObject.queryParams(): List<QueryParam> {
        val obj = this["queryParameters"] as? JsonObject ?: return emptyList()
        return obj.entries
            .map { (key, value) -> QueryParam(key, (value as? JsonPrimitive)?.contentOrNull.orEmpty()) }
            .filter { it.name.isNotBlank() }
    }
}
