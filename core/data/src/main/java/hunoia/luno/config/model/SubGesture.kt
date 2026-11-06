package hunoia.luno.config.model

import androidx.annotation.Keep
import hunoia.luno.bridge.ui.DensityProvider
import hunoia.luno.config.defaults.GestureSettingsDefaults.SubGestureTimeoutMs
import hunoia.luno.core.domain.StoredAction
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
    val color: Int = 0,
    val colorSource: ColorSource = ColorSource.Custom,
    val colorThemeKey: ThemeColorKey = ThemeColorKey.Primary,
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

    val colorSpec: ColorSpec
        get() = ColorSpec(colorSource, colorThemeKey, color)

    val captureRadius: Int
        get() = (effectiveLongSlideTriggerDistance + DensityProvider.dp2px(24f))
            .coerceAtMost(DensityProvider.dp2px(220f))

    fun slideActionFor(slot: Int): StoredAction? = slideActions.actionBy(slot)

    fun slideHoldActionFor(slot: Int): StoredAction? = slideHoldActions.actionBy(slot)

    fun longSlideActionFor(slot: Int): StoredAction? = longSlideActions.actionBy(slot)

    fun longSlideHoldActionFor(slot: Int): StoredAction? =
        longSlideHoldActions.actionBy(slot)

    fun withSlideAction(slot: Int, action: StoredAction?): SubGesture =
        copy(slideActions = slideActions.withAction(slot, action))

    fun withSlideHoldAction(slot: Int, action: StoredAction?): SubGesture =
        copy(slideHoldActions = slideHoldActions.withAction(slot, action))

    fun withLongSlideAction(slot: Int, action: StoredAction?): SubGesture =
        copy(longSlideActions = longSlideActions.withAction(slot, action))

    fun withLongSlideHoldAction(slot: Int, action: StoredAction?): SubGesture =
        copy(longSlideHoldActions = longSlideHoldActions.withAction(slot, action))
}

/** 子手势集合（SubGesture 的容器）。 */
@Serializable
@Keep
data class SubGestureSettings(
    val subGestures: List<SubGesture> = emptyList()
)
