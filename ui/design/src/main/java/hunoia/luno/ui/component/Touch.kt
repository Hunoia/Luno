package hunoia.luno.ui.component

import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput

/**
 * 浮层矩形内的空白区域只吃事件、不做任何事：浮层之间、浮层与下层列表之间的间隙
 * 没有 `pointerInput`，触摸会漏到下面的列表行 / 手势区。
 *
 * 放在 Main pass 消费，子树里的按钮、tab、索引条先处理（它们在 Main pass 之前的
 * 命中链上），因此不会抢掉子内容的点击。
 */
fun Modifier.swallowTouches(): Modifier = pointerInput(Unit) {
    awaitPointerEventScope {
        while (true) {
            awaitPointerEvent(PointerEventPass.Main).changes.forEach { it.consume() }
        }
    }
}
