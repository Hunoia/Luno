package hunoia.luno.gesture

import androidx.compose.ui.geometry.Offset
import hunoia.luno.config.model.GestureButton
import hunoia.luno.config.model.GestureDirection
import kotlin.math.hypot

fun calcDirection(
    button: GestureButton,
    origin: Offset,
    finger: Offset,
    mirrorHorizontal: Boolean = false,
): GestureDirection? {
    val rawOffset = finger - origin
    val offset = if (mirrorHorizontal) Offset(-rawOffset.x, rawOffset.y) else rawOffset
    if (offset.x == 0f && offset.y == 0f) return null
    return button.angle.directionOf(offset)
}

fun GestureDirection.mirrorHorizontal(): GestureDirection {
    return when (this) {
        GestureDirection.Left -> GestureDirection.Right
        GestureDirection.UpLeft -> GestureDirection.UpRight
        GestureDirection.Up -> GestureDirection.Up
        GestureDirection.UpRight -> GestureDirection.UpLeft
        GestureDirection.Right -> GestureDirection.Left
        GestureDirection.DownRight -> GestureDirection.DownLeft
        GestureDirection.Down -> GestureDirection.Down
        GestureDirection.DownLeft -> GestureDirection.DownRight
    }
}

fun canDistanceTriggered(
    button: GestureButton,
    origin: Offset,
    finger: Offset,
    triggerDirection: GestureDirection,
    isLongSlide: Boolean,
    stickySlideValue: Float,
    judgeAction: Boolean = true,
    configButton: GestureButton = button,
): Boolean {
    val distance = hypot(finger.x - origin.x, finger.y - origin.y)
    val threshold = if (isLongSlide) configButton.longSlideTriggerDistance else configButton.slideTriggerDistance
    val canTrigger = distance >= threshold
    if (!judgeAction) return canTrigger
    val actionList = if (isLongSlide) {
        configButton.longSlideActions.actionsBy(triggerDirection)
    } else {
        configButton.slideActions.actionsBy(triggerDirection)
    }
    return canTrigger && actionList.isEmptyOrNone().not()
}

fun getStickySlideValue(button: GestureButton, stickySlideValue: Float, isX: Boolean): Float = stickySlideValue

fun stickySlideValue(): Float = 0f

fun triggerRotationOffset(triggerDirection: GestureDirection): Float {
    return when (triggerDirection) {
        GestureDirection.Right -> 0f
        GestureDirection.DownRight -> 45f
        GestureDirection.Down -> 90f
        GestureDirection.DownLeft -> 135f
        GestureDirection.Left -> 180f
        GestureDirection.UpLeft -> -135f
        GestureDirection.Up -> -90f
        GestureDirection.UpRight -> -45f
    }
}
