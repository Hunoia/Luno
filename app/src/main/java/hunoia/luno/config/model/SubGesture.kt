package hunoia.luno.config.model

import android.graphics.Color
import androidx.annotation.Keep
import hunoia.luno.bridge.DensityProvider
import hunoia.luno.config.defaults.GestureSettingsDefaults.SubGestureTimeoutMs
import hunoia.luno.bridge.vibration.VibrationEffects
import kotlinx.serialization.Serializable

@Serializable
@Keep
data class SubGesture(
    val id: String,
    val name: String = "",
    val angle: SubGestureAngle = SubGestureAngle(),
    val slideActions: DirectionActions = DirectionActions(),
    val slideHoldActions: DirectionActions = DirectionActions(),
    val longSlideActions: DirectionActions = DirectionActions(),
    val longSlideHoldActions: DirectionActions = DirectionActions(),
    val longSlideActionPanelStyles: LongSlideActionPanelStyles = LongSlideActionPanelStyles(),
    val enabled: Boolean = true,
    val color: Int = Color.TRANSPARENT,
    val slideVibrate: Boolean = true,
    val longSlideVibrate: Boolean = true,
    val vibrateImmediately: Boolean = false,
    val vibrationEffect: VibrationEffects = VibrationEffects.Click,
    val customVibrationMs: Long = 50L,
    val actionSettingsOverride: GestureButtonActionSettingsOverride = GestureButtonActionSettingsOverride(),
    val timeoutMs: Long = SubGestureTimeoutMs,
    val triggerDistance: Int = DensityProvider.dp2px(30f),
    val longSlideTriggerDistance: Int = DensityProvider.dp2px(96f),
    val slideHoldTriggerDelayMs: Long = 120L,
    val longSlideHoldTriggerDelayMs: Long = 120L,
) {
    val effectiveLongSlideTriggerDistance: Int
        get() = longSlideTriggerDistance.coerceAtLeast(triggerDistance)

    val captureRadius: Int
        get() = (effectiveLongSlideTriggerDistance + DensityProvider.dp2px(24f))
            .coerceAtMost(DensityProvider.dp2px(220f))

    fun slideActionsFor(direction: GestureDirection): List<Action> = slideActions.actionsBy(direction)

    fun slideHoldActionsFor(direction: GestureDirection): List<Action> = slideHoldActions.actionsBy(direction)

    fun longSlideActionsFor(direction: GestureDirection): List<Action> = longSlideActions.actionsBy(direction)

    fun longSlideHoldActionsFor(direction: GestureDirection): List<Action> = longSlideHoldActions.actionsBy(direction)

    fun withSlideActions(direction: GestureDirection, newActions: List<Action>): SubGesture =
        copy(slideActions = slideActions.withActions(direction, newActions))

    fun withSlideHoldActions(direction: GestureDirection, newActions: List<Action>): SubGesture =
        copy(slideHoldActions = slideHoldActions.withActions(direction, newActions))

    fun withLongSlideActions(direction: GestureDirection, newActions: List<Action>): SubGesture =
        copy(longSlideActions = longSlideActions.withActions(direction, newActions))

    fun withLongSlideHoldActions(direction: GestureDirection, newActions: List<Action>): SubGesture =
        copy(longSlideHoldActions = longSlideHoldActions.withActions(direction, newActions))
}
