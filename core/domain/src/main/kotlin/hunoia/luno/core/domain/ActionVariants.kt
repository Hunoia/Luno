package hunoia.luno.core.domain

import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull

/**
 * 面板长按变体：内嵌在 StoredAction params 的 `__longPress` 键（变体 JSON 文本）。
 * 面板读取时剥离，执行层看不到该键。
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
    if (typeId != AppLaunchAction.TYPE_ID) return null
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

/** 动作面板的类型与参数 key（引用全局面板列表里的某个具名面板）。 */
const val ACTION_PANEL_TYPE_ID = "internal.actionPanel"
const val ACTION_PANEL_PARAM_PANEL_ID = "panelId"

/** 网格面板的类型与参数 key（引用全局网格面板列表里的某个具名面板）。 */
const val GRID_PANEL_TYPE_ID = "internal.gridPanel"
const val GRID_PANEL_PARAM_PANEL_ID = "panelId"

/**
 * 动作面板引用 panelId（`internal.actionPanel`），非引用或空 id 返回 null。
 * 触点路径就地展开成面板项，不会流到执行层；这里给搜索/自动化等无面板上下文的地方一个判据。
 */
fun StoredAction.actionPanelId(): String? {
    if (typeId != ACTION_PANEL_TYPE_ID) return null
    return (params[ACTION_PANEL_PARAM_PANEL_ID] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }
}

/** 网格面板引用 panelId（`internal.gridPanel`），非引用或空 id 返回 null。 */
fun StoredAction.gridPanelId(): String? {
    if (typeId != GRID_PANEL_TYPE_ID) return null
    return (params[GRID_PANEL_PARAM_PANEL_ID] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }
}

fun StoredAction.launchPackageName(): String? {
    if (typeId != AppLaunchAction.TYPE_ID) return null
    return (params["packageName"] as? JsonPrimitive)?.contentOrNull
}

/** app.launch 的应用引用（label 用包名兜底，仅供去重与比对）。 */
fun StoredAction.launchAppRef(): AppInfo? {
    if (typeId != AppLaunchAction.TYPE_ID) return null
    val pkg = (params["packageName"] as? JsonPrimitive)?.contentOrNull ?: return null
    val cls = (params["className"] as? JsonPrimitive)?.contentOrNull.orEmpty()
    return AppInfo(
        packageName = pkg,
        className = cls,
        label = pkg,
        miniWindow = launchMiniWindow(),
    )
}

fun StoredAction.withLaunchMiniWindow(miniWindow: Boolean): StoredAction {
    if (typeId != AppLaunchAction.TYPE_ID) return this
    return copy(params = buildJsonObject {
        for ((k, v) in this@withLaunchMiniWindow.params) put(k, v)
        put("miniWindow", JsonPrimitive(miniWindow))
    })
}
