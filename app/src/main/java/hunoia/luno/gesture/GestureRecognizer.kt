package hunoia.luno.gesture

import androidx.compose.ui.geometry.Offset
import hunoia.luno.config.model.GestureButtonAngle
import hunoia.luno.config.model.GestureDirection
import hunoia.luno.config.model.GestureTriggerType
import kotlin.math.hypot

data class GestureConfig(
    val buttonId: String,
    val angle: GestureButtonAngle,
    val thresholds: GestureThresholds,
    val isMirrorHorizontal: Boolean,
    val hasTapActions: Boolean,
    val hasDoubleTapActions: Boolean,
    val hasLongPressActions: Boolean,
    val hasActionInDirection: (triggerType: GestureTriggerType, direction: GestureDirection) -> Boolean,
)

class GestureRecognizer {

    var origin: Offset = Offset.Unspecified
        private set
    var finger: Offset = Offset.Unspecified
        private set
    var triggerDirection: GestureDirection = GestureDirection.Right
        private set
    var actionDirection: GestureDirection = GestureDirection.Right
        private set

    private var downTimeMs = 0L
    private var directTriggered = false
    private var isLongPressCanceled = false
    private var slideHoldFirstReachedMs = 0L
    private var longSlideHoldFirstReachedMs = 0L

    private var pendingButtonId: String? = null

    fun onDown(timeMs: Long, position: Offset, config: GestureConfig): GestureDecision {
        origin = position
        finger = position
        downTimeMs = timeMs
        directTriggered = false
        isLongPressCanceled = false
        slideHoldFirstReachedMs = 0L
        longSlideHoldFirstReachedMs = 0L

        if (!config.hasDoubleTapActions || pendingButtonId != config.buttonId) {
            pendingButtonId = null
        }

        return GestureDecision.Noop
    }

    fun onMove(
        timeMs: Long,
        delta: Offset,
        config: GestureConfig,
    ): GestureDecision {
        finger = Offset(finger.x + delta.x, finger.y + delta.y)

        val distance = hypot(
            (finger.x - origin.x).toDouble(),
            (finger.y - origin.y).toDouble(),
        ).toFloat()
        if (distance > config.thresholds.touchSlop) {
            isLongPressCanceled = true
            pendingButtonId = null
        }

        val rawOffset = finger - origin
        if (rawOffset.x != 0f || rawOffset.y != 0f) {
            triggerDirection = config.angle.directionOf(rawOffset)
            val actionOffset = if (config.isMirrorHorizontal) Offset(-rawOffset.x, rawOffset.y) else rawOffset
            actionDirection = config.angle.directionOf(actionOffset)
        }

        val canTriggerSlide = distance >= config.thresholds.slideTriggerDistance
        val canTriggerLong = distance >= config.thresholds.longSlideTriggerDistance

        if (canTriggerLong) {
            if (longSlideHoldFirstReachedMs == 0L) {
                longSlideHoldFirstReachedMs = timeMs
            } else if (!directTriggered &&
                timeMs - longSlideHoldFirstReachedMs >= config.thresholds.longSlideHoldDelayMs
            ) {
                if (config.hasActionInDirection(GestureTriggerType.LongSlideHold, actionDirection)) {
                    directTriggered = true
                    return GestureDecision.Trigger(
                        triggerType = GestureTriggerType.LongSlideHold,
                        direction = triggerDirection,
                        actionDirection = actionDirection,
                    )
                }
            }
        } else {
            longSlideHoldFirstReachedMs = 0L
        }

        if (canTriggerSlide && !canTriggerLong) {
            if (slideHoldFirstReachedMs == 0L) {
                slideHoldFirstReachedMs = timeMs
            } else if (!directTriggered &&
                timeMs - slideHoldFirstReachedMs >= config.thresholds.slideHoldDelayMs
            ) {
                if (config.hasActionInDirection(GestureTriggerType.SlideHold, actionDirection)) {
                    directTriggered = true
                    return GestureDecision.Trigger(
                        triggerType = GestureTriggerType.SlideHold,
                        direction = triggerDirection,
                        actionDirection = actionDirection,
                    )
                }
            }
        } else {
            slideHoldFirstReachedMs = 0L
        }

        return GestureDecision.Noop
    }

    fun onUp(timeMs: Long, config: GestureConfig): GestureDecision {
        if (directTriggered) {
            reset()
            return GestureDecision.Noop
        }

        val distance = hypot(
            (finger.x - origin.x).toDouble(),
            (finger.y - origin.y).toDouble(),
        ).toFloat()

        var result: GestureDecision = GestureDecision.Noop

        if (distance >= config.thresholds.longSlideTriggerDistance) {
            if (config.hasActionInDirection(GestureTriggerType.LongSlide, actionDirection)) {
                result = GestureDecision.Trigger(
                    triggerType = GestureTriggerType.LongSlide,
                    direction = triggerDirection,
                    actionDirection = actionDirection,
                )
            }
        } else if (distance >= config.thresholds.slideTriggerDistance) {
            if (config.hasActionInDirection(GestureTriggerType.Slide, actionDirection)) {
                result = GestureDecision.Trigger(
                    triggerType = GestureTriggerType.Slide,
                    direction = triggerDirection,
                    actionDirection = actionDirection,
                )
            }
        }

        if (result is GestureDecision.Noop && distance <= config.thresholds.touchSlop) {
            if (pendingButtonId == config.buttonId && config.hasDoubleTapActions) {
                pendingButtonId = null
                result = GestureDecision.Trigger(
                    triggerType = GestureTriggerType.DoubleTap,
                    direction = GestureDirection.Right,
                    actionDirection = GestureDirection.Right,
                )
            } else if (config.hasTapActions || config.hasDoubleTapActions) {
                val tapDecision = GestureDecision.Trigger(
                    triggerType = GestureTriggerType.Tap,
                    direction = GestureDirection.Right,
                    actionDirection = GestureDirection.Right,
                )
                if (config.hasDoubleTapActions) {
                    pendingButtonId = config.buttonId
                    result = GestureDecision.PendingDoubleTap(tapDecision)
                } else {
                    result = tapDecision
                }
            }
        }

        reset()
        return result
    }

    fun checkTime(timeMs: Long, config: GestureConfig): GestureDecision {
        if (directTriggered) return GestureDecision.Noop
        if (isLongPressCanceled) return GestureDecision.Noop
        if (!config.hasLongPressActions) return GestureDecision.Noop

        val elapsed = timeMs - downTimeMs
        if (elapsed >= config.thresholds.longPressDelayMs) {
            directTriggered = true
            return GestureDecision.Trigger(
                triggerType = GestureTriggerType.LongPress,
                direction = GestureDirection.Right,
                actionDirection = GestureDirection.Right,
            )
        }
        return GestureDecision.Noop
    }

    fun onCancel(): GestureDecision {
        reset()
        return GestureDecision.Cancel
    }

    fun clearPendingDoubleTap() {
        pendingButtonId = null
    }

    fun reset() {
        origin = Offset.Unspecified
        finger = Offset.Unspecified
        triggerDirection = GestureDirection.Right
        actionDirection = GestureDirection.Right
        downTimeMs = 0L
        directTriggered = false
        isLongPressCanceled = false
        slideHoldFirstReachedMs = 0L
        longSlideHoldFirstReachedMs = 0L
    }
}
