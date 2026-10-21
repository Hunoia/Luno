package hunoia.luno.config.model

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

/** 小窗位置与大小，竖屏横屏各存一套：转屏后比例几乎必然不合理，所以按方向分开。 */
@Serializable
@Keep
data class MiniWindowSettings(
    val portrait: WindowBounds = WindowBounds(),
    val landscape: WindowBounds = DefaultLandscapeBounds,
) {
    fun forOrientation(landscape: Boolean): WindowBounds =
        if (landscape) this.landscape else portrait

    fun withOrientation(landscape: Boolean, bounds: WindowBounds): MiniWindowSettings =
        if (landscape) copy(landscape = bounds)
        else copy(portrait = bounds)
}

/** 生效的小窗设置：未配置时用默认值。 */
fun AdvancedSettings.miniWindowSettings(): MiniWindowSettings = miniWindow ?: MiniWindowSettings()

fun AdvancedSettings.withMiniWindowSettings(settings: MiniWindowSettings): AdvancedSettings =
    copy(miniWindow = settings)

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
 * 动作参数里的 `miniWindowSettings` 读取，形状 `{portrait:{...}, landscape:{...}}`。
 * 缺失的子字段各自回落到出厂默认，不整体丢弃。
 */
fun JsonElement?.toMiniWindowSettings(): MiniWindowSettings? {
    val o = this as? JsonObject ?: return null
    if (!o.containsKey("portrait") && !o.containsKey("landscape")) return null
    return MiniWindowSettings(
        portrait = o["portrait"].toWindowBounds() ?: WindowBounds(),
        landscape = o["landscape"].toWindowBounds() ?: DefaultLandscapeBounds,
    )
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
