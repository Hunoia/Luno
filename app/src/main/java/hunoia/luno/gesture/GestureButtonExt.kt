package hunoia.luno.gesture

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import hunoia.luno.bridge.window.rootSize
import hunoia.luno.config.model.GestureButton

data class GestureTouchTarget(
    val sourceButton: GestureButton,
    val effectiveButton: GestureButton,
    val bounds: Rect,
    val isMirror: Boolean,
)

fun List<GestureButton>.find(offset: Offset, imePadding: Int = 0): GestureButton? {
    return find { it.contains(offset, imePadding) }
}

fun List<GestureButton>.findTouchTarget(offset: Offset, imePadding: Int = 0): GestureTouchTarget? {
    for (button in this) {
        if (button.contains(offset, imePadding)) {
            return GestureTouchTarget(
                sourceButton = button,
                effectiveButton = button,
                bounds = button.bounds(imePadding),
                isMirror = false,
            )
        }
        val mirroredButton = button.mirroredButton() ?: continue
        if (button.mirrorHorizontal && mirroredButton.contains(offset, imePadding)) {
            return GestureTouchTarget(
                sourceButton = button,
                effectiveButton = mirroredButton,
                bounds = mirroredButton.bounds(imePadding),
                isMirror = true,
            )
        }
    }
    return null
}

fun GestureButton.contains(offset: Offset, imePadding: Int = 0): Boolean {
    val bounds = bounds(imePadding)
    return bounds.contains(offset)
}

fun GestureButton.bounds(imePadding: Int = 0): Rect {
    val topLeft = Offset(
        x = rootSize.width * this.bounds.x,
        y = rootSize.height * this.bounds.y - imePadding,
    )
    val boundsSize = Size(
        width = rootSize.width * this.bounds.width,
        height = rootSize.height * this.bounds.height,
    )
    return Rect(topLeft, boundsSize)
}

val GestureButton.fraction: Float get() = bounds.height
val GestureButton.isVertical: Boolean get() = bounds.height >= bounds.width

fun <T> GestureButton.whenVertical(vertical: T, horizontal: T): T = if (isVertical) vertical else horizontal

fun GestureButton.whenVerticalFloat(vertical: () -> Float, horizontal: () -> Float): Float = if (isVertical) vertical() else horizontal()

fun GestureButton.horizontalMirror(pos: Float, neg: Float): Float = pos

fun GestureButton.mirroredButton(): GestureButton? {
    if (!mirrorHorizontal) return null
    return copy(bounds = bounds.copy(x = 1f - bounds.x - bounds.width), mirrorHorizontal = false)
}
