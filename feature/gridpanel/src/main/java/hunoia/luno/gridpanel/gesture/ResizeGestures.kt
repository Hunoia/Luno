package hunoia.luno.gridpanel.gesture

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.unit.IntSize

/**
 * 手柄命中区：右下角 [hitPx] × [hitPx] 的正方形（按无障碍下限放大）。
 * 必须先判这里再判长按：否则按手柄会先触发整项拖拽。
 */
internal fun inResizeHandleArea(position: Offset, size: IntSize, hitPx: Float): Boolean {
    if (size.width <= 0 || size.height <= 0) return false
    return position.x >= size.width - hitPx && position.y >= size.height - hitPx
}

/**
 * 手柄拖拽循环：输出相对按下点的总位移（不是逐帧增量）。
 * span 按「起手 span + 位移 / 步长」算；累加增量会把浮点误差和夹紧回退
 * 一起累积进去，回拖时寸步难行。
 */
internal suspend fun AwaitPointerEventScope.trackResizeDrag(
    down: PointerInputChange,
    onDelta: (Offset) -> Unit,
) {
    while (true) {
        val event = awaitPointerEvent()
        val change = event.changes.firstOrNull { it.id == down.id } ?: return
        if (!change.pressed) return
        change.consume()
        onDelta(change.position - down.position)
    }
}
