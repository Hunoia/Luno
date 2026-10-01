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

@Serializable
@Keep
data class MiniWindowSettings(
    val horizontalBias: Float = AdvancedSettingsDefaults.MiniWindowHorizontalBias,
    val verticalBias: Float = AdvancedSettingsDefaults.MiniWindowVerticalBias,
    val widthFraction: Float = AdvancedSettingsDefaults.MiniWindowWidthFraction,
    val heightFraction: Float = AdvancedSettingsDefaults.MiniWindowHeightFraction,
    val overrideBounds: Boolean = AdvancedSettingsDefaults.MiniWindowOverrideBounds,
)

@Serializable
@Keep
data class GestureButtonActionSettingsOverride(
    val miniWindow: MiniWindowSettings? = null,
)

fun AdvancedSettings.miniWindowSettings(): MiniWindowSettings = MiniWindowSettings(
    horizontalBias = miniWindowHorizontalBias,
    verticalBias = miniWindowVerticalBias,
    widthFraction = miniWindowWidthFraction,
    heightFraction = miniWindowHeightFraction,
    overrideBounds = miniWindowOverrideBounds,
)

fun AdvancedSettings.withMiniWindowSettings(settings: MiniWindowSettings): AdvancedSettings = copy(
    miniWindowHorizontalBias = settings.horizontalBias,
    miniWindowVerticalBias = settings.verticalBias,
    miniWindowWidthFraction = settings.widthFraction,
    miniWindowHeightFraction = settings.heightFraction,
    miniWindowOverrideBounds = settings.overrideBounds,
)

fun AdvancedSettings.effectiveFor(override: GestureButtonActionSettingsOverride?): AdvancedSettings {
    val miniWindow = override?.miniWindow ?: return this
    return withMiniWindowSettings(miniWindow)
}

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
    put("horizontalBias", horizontalBias.toString())
    put("verticalBias", verticalBias.toString())
    put("widthFraction", widthFraction.toString())
    put("heightFraction", heightFraction.toString())
    put("overrideBounds", overrideBounds.toString())
}

fun JsonElement?.toMiniWindowSettings(): MiniWindowSettings? {
    val o = this as? JsonObject ?: return null
    val base = MiniWindowSettings()
    return MiniWindowSettings(
        horizontalBias = o.floatOf("horizontalBias") ?: base.horizontalBias,
        verticalBias = o.floatOf("verticalBias") ?: base.verticalBias,
        widthFraction = o.floatOf("widthFraction") ?: base.widthFraction,
        heightFraction = o.floatOf("heightFraction") ?: base.heightFraction,
        overrideBounds = o.boolOf("overrideBounds") ?: base.overrideBounds,
    )
}

private fun JsonObject.floatOf(key: String): Float? =
    this[key]?.jsonPrimitive?.contentOrNull?.toFloatOrNull()

private fun JsonObject.boolOf(key: String): Boolean? =
    this[key]?.jsonPrimitive?.contentOrNull?.toBoolean()
