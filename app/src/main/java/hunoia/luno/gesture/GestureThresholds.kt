package hunoia.luno.gesture

data class GestureThresholds(
    val touchSlop: Float,
    val longPressDelayMs: Long,
    val doubleTapDelayMs: Long,
    val slideHoldDelayMs: Long,
    val longSlideHoldDelayMs: Long,
    val slideTriggerDistance: Float,
    val longSlideTriggerDistance: Float,
)
