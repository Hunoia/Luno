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
    val hideGestureButton: ActionSettings.HideGestureButton? = null,
    val volumeScrub: ActionSettings.VolumeScrub? = null,
    val miniWindow: MiniWindowSettings? = null,
    val pointerContinuousMode: Boolean? = null,
)

fun ActionSettings.effectiveFor(override: GestureButtonActionSettingsOverride?): ActionSettings {
    override ?: return this
    return copy(
        hideGestureButton = override.hideGestureButton ?: hideGestureButton,
        volumeScrub = override.volumeScrub ?: volumeScrub,
    )
}

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

fun GestureSettings.effectiveFor(override: GestureButtonActionSettingsOverride?): GestureSettings {
    val continuousMode = override?.pointerContinuousMode ?: return this
    return copy(pointer = pointer.copy(continuousMode = continuousMode))
}
