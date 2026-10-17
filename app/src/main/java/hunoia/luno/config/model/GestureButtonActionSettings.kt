package hunoia.luno.config.model

import android.content.res.Configuration
import androidx.annotation.Keep
import hunoia.luno.action.model.Action
import hunoia.luno.config.defaults.AdvancedSettingsDefaults
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/**
 * 小窗在某个屏幕方向下的位置与大小，以屏幕比例表示（0..1 屏宽/屏高，与触钮 bounds 同坐标系）。
 * x/y 为左上角偏移；width/height 为相对屏幕的尺寸比例。
 */
@Serializable
@Keep
data class WindowBounds(
    val x: Float = AdvancedSettingsDefaults.MiniWindowX,
    val y: Float = AdvancedSettingsDefaults.MiniWindowY,
    val width: Float = AdvancedSettingsDefaults.MiniWindowWidth,
    val height: Float = AdvancedSettingsDefaults.MiniWindowHeight,
)

/** 横屏那套默认。竖屏用 [WindowBounds] 的默认即可。 */
val DefaultLandscapeBounds: WindowBounds = WindowBounds(
    x = AdvancedSettingsDefaults.MiniWindowLandscapeX,
    y = AdvancedSettingsDefaults.MiniWindowLandscapeY,
    width = AdvancedSettingsDefaults.MiniWindowLandscapeWidth,
    height = AdvancedSettingsDefaults.MiniWindowLandscapeHeight,
)

/**
 * 小窗位置与大小，**竖屏横屏各存一套**。
 *
 * 同一块屏幕转过来之后，之前调的 x/y 比例几乎必然不合理（竖屏的 y=0.14 在横屏是贴着顶边），
 * 所以按方向分开存，而不是一个值两处用。
 *
 * 迁移：旧的单套扁平数据读进来时作为 [portrait]，[landscape] 留默认。
 */
@Serializable
@Keep
data class MiniWindowSettings(
    val portrait: WindowBounds = WindowBounds(),
    val landscape: WindowBounds = DefaultLandscapeBounds,
) {
    fun forOrientation(orientation: Int): WindowBounds =
        if (orientation == Configuration.ORIENTATION_LANDSCAPE) landscape else portrait

    fun withOrientation(orientation: Int, bounds: WindowBounds): MiniWindowSettings =
        if (orientation == Configuration.ORIENTATION_LANDSCAPE) copy(landscape = bounds)
        else copy(portrait = bounds)
}

/**
 * 取生效的小窗设置。
 *
 * [AdvancedSettings.miniWindow] 非空即用新结构；否则回落到旧的四个扁平字段
 * （`miniWindowX` 等），把旧值整体当作竖屏，横屏留出厂默认。
 */
@Suppress("DEPRECATION")
fun AdvancedSettings.miniWindowSettings(): MiniWindowSettings = miniWindow ?: MiniWindowSettings(
    portrait = WindowBounds(
        x = miniWindowX ?: AdvancedSettingsDefaults.MiniWindowX,
        y = miniWindowY ?: AdvancedSettingsDefaults.MiniWindowY,
        width = miniWindowWidth ?: AdvancedSettingsDefaults.MiniWindowWidth,
        height = miniWindowHeight ?: AdvancedSettingsDefaults.MiniWindowHeight,
    ),
    landscape = DefaultLandscapeBounds,
)

/**
 * 写回时把四个旧字段一并置 null。
 *
 * `JsonSerializer.globalJson` 是 `Json { ignoreUnknownKeys = true }`，`encodeDefaults`
 * 取默认值 false，所以 null 字段不会再序列化出去 —— 首次写回后旧字段就从 JSON 里消失了，
 * 之后一直走 [AdvancedSettings.miniWindow]。
 */
fun AdvancedSettings.withMiniWindowSettings(settings: MiniWindowSettings): AdvancedSettings = copy(
    miniWindow = settings,
    miniWindowX = null,
    miniWindowY = null,
    miniWindowWidth = null,
    miniWindowHeight = null,
)

fun AdvancedSettings.effectiveForAction(action: Action): AdvancedSettings {
    val miniWindow = when (action) {
        is Action.LaunchApp -> action.miniWindowSettings
        is Action.OpenActivity -> action.miniWindowSettings
        is Action.OpenUrl -> action.miniWindowSettings
        is Action.Popup -> action.miniWindowSettings
        else -> null
    }
    return miniWindow?.let { withMiniWindowSettings(it) } ?: this
}

fun MiniWindowSettings.toJsonObject(): JsonObject = buildJsonObject {
    put("portrait", portrait.toJsonObject())
    put("landscape", landscape.toJsonObject())
}

private fun WindowBounds.toJsonObject(): JsonObject = buildJsonObject {
    put("x", x.toString())
    put("y", y.toString())
    put("width", width.toString())
    put("height", height.toString())
}

/**
 * 动作参数里的 `miniWindowSettings` 读取，兼容两种形状：
 *
 * - 新形状 `{portrait:{...}, landscape:{...}}`
 * - 旧形状扁平 `{x,y,width,height}` → 整体当竖屏，横屏留出厂默认
 *
 * 旧形状的判断依据是「有没有 portrait 键」，而不是顶层有没有 x —— 顶层 x 在新形状里不存在，
 * 两者不会混淆。缺失的子字段各自回落到出厂默认，不整体丢弃。
 */
fun JsonElement?.toMiniWindowSettings(): MiniWindowSettings? {
    val o = this as? JsonObject ?: return null
    if (o.containsKey("portrait") || o.containsKey("landscape")) {
        return MiniWindowSettings(
            portrait = o["portrait"].toWindowBounds() ?: WindowBounds(),
            landscape = o["landscape"].toWindowBounds() ?: DefaultLandscapeBounds,
        )
    }
    val base = WindowBounds()
    val legacy = WindowBounds(
        x = o.floatOf("x") ?: base.x,
        y = o.floatOf("y") ?: base.y,
        width = o.floatOf("width") ?: base.width,
        height = o.floatOf("height") ?: base.height,
    )
    return MiniWindowSettings(portrait = legacy, landscape = DefaultLandscapeBounds)
}

private fun JsonElement?.toWindowBounds(): WindowBounds? {
    val o = this as? JsonObject ?: return null
    val base = WindowBounds()
    return WindowBounds(
        x = o.floatOf("x") ?: base.x,
        y = o.floatOf("y") ?: base.y,
        width = o.floatOf("width") ?: base.width,
        height = o.floatOf("height") ?: base.height,
    )
}

private fun JsonObject.floatOf(key: String): Float? =
    this[key]?.jsonPrimitive?.contentOrNull?.toFloatOrNull()
