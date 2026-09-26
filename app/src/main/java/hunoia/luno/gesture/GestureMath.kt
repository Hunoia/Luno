package hunoia.luno.gesture

import androidx.compose.ui.geometry.Offset
import hunoia.luno.config.model.GestureButton
import hunoia.luno.config.model.GestureDirection

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
