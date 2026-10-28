@file:Suppress("ConstPropertyName")

package hunoia.luno.config.defaults

import hunoia.luno.bridge.ui.DensityProvider

object SettingsUiDefaults {

    val MinSlideTriggerDistance = DensityProvider.dp2px(16f)
    val MaxSlideTriggerDistance = DensityProvider.dp2px(80f)
    val MinLongSlideTriggerDistance = DensityProvider.dp2px(48f)
    val MaxLongSlideTriggerDistance = DensityProvider.dp2px(240f)
    val MinGestureSlideDistanceGap = DensityProvider.dp2px(16f)
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
}
