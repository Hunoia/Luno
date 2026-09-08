@file:Suppress("ConstPropertyName")

package hunoia.luno.config.defaults

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import hunoia.luno.bridge.DensityProvider
import hunoia.luno.R
import hunoia.luno.bridge.vibration.VibrationEffects

object SettingsUiDefaults {

    val MinGestureButtonWidth = DensityProvider.dp2px(1f)
    val MaxGestureButtonWidth = DensityProvider.dp2px(60f)
    val MinSlideTriggerDistance = DensityProvider.dp2px(16f)
    val MaxSlideTriggerDistance = DensityProvider.dp2px(80f)
    val MinLongSlideTriggerDistance = DensityProvider.dp2px(48f)
    val MaxLongSlideTriggerDistance = DensityProvider.dp2px(240f)
    val MinGestureSlideDistanceGap = DensityProvider.dp2px(16f)
    const val MinGestureButtonPosition = 0f
    const val MaxGestureButtonPosition = 1f
    const val MinGestureButtonLength = 0.01f
    const val MaxGestureButtonArea = 0.20f
    const val MinHoldTriggerDelayMs = 0L
    const val MaxHoldTriggerDelayMs = 1000L
    const val MinDoubleTapTriggerDelayMs = 100L
    const val MaxDoubleTapTriggerDelayMs = 800L
    const val MinLongPressTriggerDelayMs = 100L
    const val MaxLongPressTriggerDelayMs = 1000L
    const val MinSubGestureTimeoutMs = 1000L
    const val MaxSubGestureTimeoutMs = 15000L
    val MinSubGestureTriggerDistance = DensityProvider.dp2px(16f)
    val MaxSubGestureTriggerDistance = DensityProvider.dp2px(80f)
    val MinSubGestureLongSlideTriggerDistance = DensityProvider.dp2px(48f)
    val MaxSubGestureLongSlideTriggerDistance = DensityProvider.dp2px(240f)
    const val GestureButtonColorAlpha = 0.36f
    val DisabledAlpha: Float get() = GestureButtonColorAlpha
    const val DimAlpha = 0.5f

    val MinGridColumns = 2
    val MaxGridColumns = 8
    val MinGridRows = 1
    val MaxGridRows = 6
    val MinGridCornerRadius = DensityProvider.dp2px(4f)
    val MaxGridCornerRadius = DensityProvider.dp2px(40f)

    const val MinArcLength = 30
    const val MaxArcLength = 270
    const val MinSpacing = 0.8f
    const val MaxSpacing = 2.0f
    val MinItemSize = DensityProvider.dp2px(24f)
    val MaxItemSize = DensityProvider.dp2px(72f)

    @Composable
    fun getPredefinedVibrationEffectText(effect: VibrationEffects): String {
        return when (effect) {
            VibrationEffects.None -> stringResource(id = R.string.custom)
            VibrationEffects.Tick -> stringResource(id = R.string.vibration_tick)
            VibrationEffects.Click -> stringResource(id = R.string.vibration_click)
            VibrationEffects.HeavyClick -> stringResource(id = R.string.vibration_heavy_click)
        }
    }

}
