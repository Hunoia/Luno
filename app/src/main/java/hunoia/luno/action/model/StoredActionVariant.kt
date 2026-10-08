package hunoia.luno.action.model

import hunoia.luno.core.JsonSerializer
import hunoia.luno.quicklaunch.model.qualifiedNameWithIntents
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull

/**
 * 面板长按变体：内嵌在 StoredAction params 里的 `__longPress` 键（值为变体 JSON 文本）。
 * 面板读取时剥离，执行层永远看不到该键。
 */
const val LONG_PRESS_VARIANT_KEY = "__longPress"

fun StoredAction.withLongPressVariant(variant: StoredAction): StoredAction {
    val encoded = JsonSerializer.encodeToString(variant)
    return copy(params = buildJsonObject {
        for ((k, v) in this@withLongPressVariant.params) put(k, v)
        put(LONG_PRESS_VARIANT_KEY, JsonPrimitive(encoded))
    })
}

fun StoredAction.longPressVariant(): StoredAction? {
    val encoded = (params[LONG_PRESS_VARIANT_KEY] as? JsonPrimitive)?.contentOrNull
        ?: return null
    return runCatching { JsonSerializer.decodeFromString<StoredAction>(encoded) }.getOrNull()
}

fun StoredAction.withoutVariantKey(): StoredAction {
    if (LONG_PRESS_VARIANT_KEY !in params) return this
    return copy(params = buildJsonObject {
        for ((k, v) in this@withoutVariantKey.params) {
            if (k != LONG_PRESS_VARIANT_KEY) put(k, v)
        }
    })
}

/** app.launch 的应用标识（免 PackageManager 查询，用于去重与比对）。 */
fun StoredAction.launchQualifiedName(): String? {
    if (typeId != "app.launch") return null
    val pkg = (params["packageName"] as? JsonPrimitive)?.contentOrNull ?: return null
    val cls = (params["className"] as? JsonPrimitive)?.contentOrNull.orEmpty()
    return "$pkg/$cls"
}

fun StoredAction.launchMiniWindow(): Boolean =
    (params["miniWindow"] as? JsonPrimitive)?.contentOrNull.toBoolean()

/** 动作库引用 entryId（internal.libraryRef），非引用返回 null。 */
fun StoredAction.libraryEntryId(): String? {
    if (typeId != "internal.libraryRef") return null
    return (params["entryId"] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }
}

/** 规则即动作的类型与参数 key（定义表注册/映射/引用判断共用，避免字面量漂移）。 */
const val RUN_RULE_TYPE_ID = "internal.runRule"
const val RUN_RULE_PARAM_RULE_ID = "ruleId"

/** 规则引用 id（internal.runRule），非引用或空 id 返回 null。 */
fun StoredAction.runRuleId(): String? {
    if (typeId != RUN_RULE_TYPE_ID) return null
    return (params[RUN_RULE_PARAM_RULE_ID] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }
}

/**
 * 点击/长按是否自带参数坐标（xDp+yDp 双全且合法）：是则不依赖手势触点，
 * 库测试/自动化/搜索/runRule 等无触点路径可直接执行。
 */
fun StoredAction.hasPointParams(): Boolean {
    if (typeId != "accessibility.tap" && typeId != "accessibility.longPress") return false
    val x = (params["xDp"] as? JsonPrimitive)?.contentOrNull?.toIntOrNull()
    val y = (params["yDp"] as? JsonPrimitive)?.contentOrNull?.toIntOrNull()
    return x != null && y != null && x >= 0 && y >= 0
}

/** internal.volumeScrub 的配置解码（读不到落默认值）。 */
fun StoredAction.volumeScrubConfigOrDefault(): hunoia.luno.config.model.VolumeScrubConfig {
    if (typeId != "internal.volumeScrub") return hunoia.luno.config.model.VolumeScrubConfig()
    // 注意不能用 String?.toBoolean()：key 缺失时它返回 false 而不是回退默认值，
    // 那会让手势路径的"水平音量调节"实际是关的（UI/模型/ActionMapper 都默认 true）。
    val horizontal = (params["horizontalEnabled"] as? JsonPrimitive)?.contentOrNull?.toBooleanStrictOrNull()
        ?: hunoia.luno.config.defaults.ActionSettingsDefaults.VolumeScrubHorizontalEnabled
    val step = (params["stepThresholdDp"] as? JsonPrimitive)?.contentOrNull?.toIntOrNull()
    return hunoia.luno.config.model.VolumeScrubConfig(
        horizontalEnabled = horizontal,
        stepThresholdDp = step ?: hunoia.luno.config.defaults.ActionSettingsDefaults.VolumeScrubStepThresholdDp,
    )
}

/** app.launch 的包名。 */
fun StoredAction.launchPackageName(): String? {
    if (typeId != "app.launch") return null
    return (params["packageName"] as? JsonPrimitive)?.contentOrNull
}

/** app.launch 的应用引用（label 用包名兜底，仅供去重与比对）。 */
fun StoredAction.launchAppRef(): hunoia.luno.quicklaunch.model.AppInfo? {
    if (typeId != "app.launch") return null
    val pkg = (params["packageName"] as? JsonPrimitive)?.contentOrNull ?: return null
    val cls = (params["className"] as? JsonPrimitive)?.contentOrNull.orEmpty()
    return hunoia.luno.quicklaunch.model.AppInfo(
        packageName = pkg,
        className = cls,
        label = pkg,
        miniWindow = launchMiniWindow(),
    )
}

/** app.launchShortcut 内嵌的快捷方式标识（解 data JSON，用于去重与比对）。 */
fun StoredAction.shortcutQualifiedName(): String? {
    if (typeId != "app.launchShortcut") return null
    val data = (params["data"] as? JsonPrimitive)?.contentOrNull ?: return null
    return runCatching {
        JsonSerializer.decodeFromString<hunoia.luno.quicklaunch.model.LauncherInfo.ShortcutInfo>(data)
    }.getOrNull()?.qualifiedNameWithIntents
}

fun StoredAction.withLaunchMiniWindow(miniWindow: Boolean): StoredAction {
    if (typeId != "app.launch") return this
    return copy(params = buildJsonObject {
        for ((k, v) in this@withLaunchMiniWindow.params) put(k, v)
        put("miniWindow", JsonPrimitive(miniWindow.toString()))
    })
}
