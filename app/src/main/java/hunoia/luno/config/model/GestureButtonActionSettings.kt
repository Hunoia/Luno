package hunoia.luno.config.model

import androidx.annotation.Keep
import hunoia.luno.config.defaults.AdvancedSettingsDefaults
import kotlinx.serialization.Serializable

@Serializable
@Keep
data class MiniWindowSettings(
    val horizontalBias: Float = AdvancedSettingsDefaults.MiniWindowHorizontalBias,
    val verticalBias: Float = AdvancedSettingsDefaults.MiniWindowVerticalBias,
    val verticalOffsetFraction: Float = AdvancedSettingsDefaults.MiniWindowVerticalOffsetFraction,
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
    verticalOffsetFraction = miniWindowVerticalOffsetFraction,
    widthFraction = miniWindowWidthFraction,
    heightFraction = miniWindowHeightFraction,
    overrideBounds = miniWindowOverrideBounds,
)

fun AdvancedSettings.withMiniWindowSettings(settings: MiniWindowSettings): AdvancedSettings = copy(
    miniWindowHorizontalBias = settings.horizontalBias,
    miniWindowVerticalBias = settings.verticalBias,
    miniWindowVerticalOffsetFraction = settings.verticalOffsetFraction,
    miniWindowWidthFraction = settings.widthFraction,
    miniWindowHeightFraction = settings.heightFraction,
    miniWindowOverrideBounds = settings.overrideBounds,
)

fun AdvancedSettings.effectiveFor(override: GestureButtonActionSettingsOverride?): AdvancedSettings {
    val miniWindow = override?.miniWindow ?: return this
    return withMiniWindowSettings(miniWindow)
}
