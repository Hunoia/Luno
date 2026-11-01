package hunoia.luno.ui.component.selection

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size

/** 选区调整期的命中部位。 */
enum class SelectionHandle { TopLeft, TopRight, BottomLeft, BottomRight }

/** 按下点是否落在某个拖角上。 */
fun Rect.handleAt(p: Offset, radius: Float): SelectionHandle? = listOf(
    SelectionHandle.TopLeft to topLeft,
    SelectionHandle.TopRight to topRight,
    SelectionHandle.BottomLeft to bottomLeft,
    SelectionHandle.BottomRight to bottomRight,
).firstOrNull { (_, corner) -> (corner - p).getDistance() <= radius }?.first

/** 拖角缩放：只移动被拖的那条边，保持对角固定，并保证最小尺寸。 */
fun Rect.resized(handle: SelectionHandle, p: Offset, minSize: Float, bounds: Size): Rect {
    val x = p.x.coerceIn(0f, bounds.width)
    val y = p.y.coerceIn(0f, bounds.height)
    var left = left
    var top = top
    var right = right
    var bottom = bottom
    when (handle) {
        SelectionHandle.TopLeft -> { left = x; top = y }
        SelectionHandle.TopRight -> { right = x; top = y }
        SelectionHandle.BottomLeft -> { left = x; bottom = y }
        SelectionHandle.BottomRight -> { right = x; bottom = y }
    }
    if (right - left < minSize) {
        if (handle == SelectionHandle.TopLeft || handle == SelectionHandle.BottomLeft) {
            left = right - minSize
        } else {
            right = left + minSize
        }
    }
    if (bottom - top < minSize) {
        if (handle == SelectionHandle.TopLeft || handle == SelectionHandle.TopRight) {
            top = bottom - minSize
        } else {
            bottom = top + minSize
        }
    }
    return Rect(
        left.coerceIn(0f, bounds.width),
        top.coerceIn(0f, bounds.height),
        right.coerceIn(0f, bounds.width),
        bottom.coerceIn(0f, bounds.height),
    )
}

/** 平移选框并夹在 [bounds] 内。 */
fun Rect.moved(dx: Float, dy: Float, bounds: Size): Rect {
    val maxLeft = (bounds.width - width).coerceAtLeast(0f)
    val maxTop = (bounds.height - height).coerceAtLeast(0f)
    val left = (left + dx).coerceIn(0f, maxLeft)
    val top = (top + dy).coerceIn(0f, maxTop)
    return Rect(left, top, left + width, top + height)
}
