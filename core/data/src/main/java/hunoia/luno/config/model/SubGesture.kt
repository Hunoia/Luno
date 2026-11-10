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
    /**
     * 四个「会话反馈」振动（与方向动作的上面四个开关分开，按手指状态区分）：
     * - [holdFireVibrate]：**手指还按着**就进入会话时（触钮停留/滑动进入子手势、面板项长按点火），
     *   触摸窗只做视觉、不是触摸源。
     * - [overlayVibrate]：手指已经抬起（或无手指）才进入时（面板项点选、悬浮球点按），
     *   这时触摸窗才真正成为触摸源，两段式的第一段反馈。
     * - [pressVibrate]：子手势会话内按下（触摸窗里再按）时。
     * - [timeoutVibrate]：会话空闲超时（[timeoutMs]）自动结束时。
     */
    val holdFireVibrate: Boolean = true,
    val overlayVibrate: Boolean = true,
    val pressVibrate: Boolean = true,
    val timeoutVibrate: Boolean = true,
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
