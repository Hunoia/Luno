package hunoia.luno.config.model

import android.graphics.Color
import androidx.annotation.Keep
import hunoia.luno.bridge.ui.DensityProvider
import hunoia.luno.config.defaults.GestureSettingsDefaults.SubGestureTimeoutMs
import hunoia.luno.action.model.StoredAction
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
    val enabled: Boolean = true,
    val color: Int = Color.TRANSPARENT,
    val slideVibrate: Boolean = true,
    val longSlideVibrate: Boolean = true,
    val slideHoldVibrate: Boolean = true,
    val longSlideHoldVibrate: Boolean = true,
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

    fun slideActionFor(direction: GestureDirection): StoredAction? = slideActions.actionBy(direction)

    fun slideHoldActionFor(direction: GestureDirection): StoredAction? = slideHoldActions.actionBy(direction)

    fun longSlideActionFor(direction: GestureDirection): StoredAction? = longSlideActions.actionBy(direction)

    fun longSlideHoldActionFor(direction: GestureDirection): StoredAction? =
        longSlideHoldActions.actionBy(direction)

    fun withSlideAction(direction: GestureDirection, action: StoredAction?): SubGesture =
        copy(slideActions = slideActions.withAction(direction, action))

    fun withSlideHoldAction(direction: GestureDirection, action: StoredAction?): SubGesture =
        copy(slideHoldActions = slideHoldActions.withAction(direction, action))

    fun withLongSlideAction(direction: GestureDirection, action: StoredAction?): SubGesture =
        copy(longSlideActions = longSlideActions.withAction(direction, action))

    fun withLongSlideHoldAction(direction: GestureDirection, action: StoredAction?): SubGesture =
        copy(longSlideHoldActions = longSlideHoldActions.withAction(direction, action))
}

/** 子手势集合（SubGesture 的容器，与 SubGesture 同生命周期）。 */
@Serializable
@Keep
data class SubGestureSettings(
    val subGestures: List<SubGesture> = emptyList()
)
