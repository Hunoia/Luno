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
 * 小窗位置与大小，以屏幕比例表示（0..1 屏宽/屏高，与触钮 bounds 同坐标系）。
 * x/y 为左上角偏移；width/height 为相对屏幕的尺寸比例。
 */
@Serializable
@Keep
data class MiniWindowSettings(
    val x: Float = AdvancedSettingsDefaults.MiniWindowX,
    val y: Float = AdvancedSettingsDefaults.MiniWindowY,
    val width: Float = AdvancedSettingsDefaults.MiniWindowWidth,
    val height: Float = AdvancedSettingsDefaults.MiniWindowHeight,
)

fun AdvancedSettings.miniWindowSettings(): MiniWindowSettings = MiniWindowSettings(
    x = miniWindowX,
    y = miniWindowY,
    width = miniWindowWidth,
    height = miniWindowHeight,
)

fun AdvancedSettings.withMiniWindowSettings(settings: MiniWindowSettings): AdvancedSettings = copy(
    miniWindowX = settings.x,
    miniWindowY = settings.y,
    miniWindowWidth = settings.width,
    miniWindowHeight = settings.height,
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
    put("x", x.toString())
    put("y", y.toString())
    put("width", width.toString())
    put("height", height.toString())
}

fun JsonElement?.toMiniWindowSettings(): MiniWindowSettings? {
    val o = this as? JsonObject ?: return null
    val base = MiniWindowSettings()
    // 旧字段（horizontalBias 等）在读取时忽略，缺失的新字段用默认值（等同丢弃旧 override）。
    return MiniWindowSettings(
        x = o.floatOf("x") ?: base.x,
        y = o.floatOf("y") ?: base.y,
        width = o.floatOf("width") ?: base.width,
        height = o.floatOf("height") ?: base.height,
    )
}

private fun JsonObject.floatOf(key: String): Float? =
    this[key]?.jsonPrimitive?.contentOrNull?.toFloatOrNull()
