package hunoia.luno.gridpanel.gesture

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalViewConfiguration
import hunoia.luno.config.model.GridPanelItem
import hunoia.luno.gridpanel.geometry.GridMetrics
import kotlin.math.roundToInt

/** 长按超时前的结局：抬手 = 点按，滑动超 slop / 被子层消费 = 放弃。 */
private enum class LayerGestureOutcome { TAP, GIVE_UP }

/**
 * 网格层根节点的统一手势（编辑态）：手柄 > 长按拖拽 > 点按。
 *
 * **为什么挂在层根而不是每个项上**：项在拖拽中会随 `offset{}` 逐帧移动，挂在
 * 项节点内部的 pointerInput 的坐标系跟着动 —— positionChange() 报出的位移被
 * 节点自身位移抵掉（收敛在真值一半附近震荡），表现为项半速跟手、一顿一顿，
 * 按同一坐标算的高亮自然也错位。层根在整个拖拽期间静止（滚动/翻页早被 slop
 * 判定让路），位移就是纯粹的屏幕位移，1:1 跟手。
 *
 * 命中测试按下时做一次：此时无拖拽进行，项都在静止位。拖拽/缩放一旦开始就
 * 锁定该项直到抬手，不做逐帧命中。
 *
 * 子层（删除角标）消费的事件在这里表现为 isConsumed → 直接放弃，所以点角标
 * 不会误起整项拖拽。
 */
@Composable
fun Modifier.gridLayerGestures(
    metrics: GridMetrics,
    handleHitPx: Float,
    onTap: (GridPanelItem) -> Unit,
    onDragStart: (GridPanelItem) -> Unit,
    onDrag: (Offset) -> Unit,
    onDragEnd: () -> Unit,
    /** [contentY] = 手指在网格内容坐标里的 y（层根 = 滚动内容，无需再换算）。 */
    onDragPosition: (GridPanelItem, contentY: Float) -> Unit,
    onResizeStart: (GridPanelItem) -> Unit,
    onResizeDelta: (Offset) -> Unit,
    onResizeEnd: () -> Unit,
    itemsProvider: () -> List<GridPanelItem>,
): Modifier {
    val view = LocalView.current
    val viewConfiguration = LocalViewConfiguration.current
    val metricsRef = rememberUpdatedState(metrics)
    val hitRef = rememberUpdatedState(handleHitPx)
    val itemsRef = rememberUpdatedState(itemsProvider)
    val tapRef = rememberUpdatedState(onTap)
    val dragStartRef = rememberUpdatedState(onDragStart)
    val dragRef = rememberUpdatedState(onDrag)
    val dragEndRef = rememberUpdatedState(onDragEnd)
    val dragPosRef = rememberUpdatedState(onDragPosition)
    val resizeStartRef = rememberUpdatedState(onResizeStart)
    val resizeDeltaRef = rememberUpdatedState(onResizeDelta)
    val resizeEndRef = rememberUpdatedState(onResizeEnd)

    return pointerInput(Unit) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            // **不要 consume down**：吃掉会让外层 verticalScroll / pager 的拖动
            // 起不来（项铺满网格时整个面板就滑不动了）。
            val m = metricsRef.value
            val hit = hitTest(itemsRef.value(), down.position, m) ?: return@awaitEachGesture
            val (item, local) = hit
            val itemW = m.itemW(item.spanX.toFloat())
            val itemH = m.itemH(item.spanY.toFloat())

            // 1) 尺寸手柄：命中即 resize，并且 return —— 不再往下走长按判定。
            //    （层根坐标系静止，总位移直接可信。）命中区上限按边长 40% 再缩，
            //    小格（1×1 ≈ 80dp → 32dp）不盖掉半个项。**所有项都有手柄**，
            //    不再按尺寸门槛隐藏。
            val handleHit = minOf(hitRef.value, minOf(itemW, itemH) * 0.4f)
            if (inResizeHandleArea(
                    local,
                    IntSize(itemW.roundToInt(), itemH.roundToInt()),
                    handleHit,
                )
            ) {
                down.consume()
                resizeStartRef.value(item)
                trackResizeDrag(down) { resizeDeltaRef.value(it) }
                resizeEndRef.value()
                return@awaitEachGesture
            }

            // 2) 长按等待：与 combinedClickable 用同一套 ViewConfiguration 超时。
            var upForTap: PointerInputChange? = null
            val outcome = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                var result = LayerGestureOutcome.GIVE_UP
                while (true) {
                    val event = awaitPointerEvent()
                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                    when {
                        // 删除角标等子层已消费 = 这根手指不归我们管。
                        change.isConsumed -> break
                        !change.pressed -> {
                            upForTap = change
                            result = LayerGestureOutcome.TAP
                            break
                        }
                        // 长按前滑出 slop = 放弃（父层翻页/滚动接管）。
                        (change.position - down.position).getDistance() > viewConfiguration.touchSlop -> break
                    }
                }
                result
            }

            when (outcome) {
                LayerGestureOutcome.TAP -> {
                    // 消费抬手，阻断根层的「点空白」判定。
                    upForTap?.consume()
                    tapRef.value(item)
                }

                LayerGestureOutcome.GIVE_UP -> Unit

                // null = 超时：长按成立，进入整项拖拽。
                null -> {
                    view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                    dragStartRef.value(item)
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (change.isConsumed) {
                            dragEndRef.value()
                            return@awaitEachGesture
                        }
                        if (!change.pressed) break
                        // 层根静止，positionChange() 就是纯屏幕位移。
                        val delta = change.positionChange()
                        // 位置先报：位移为零的帧也要报，边缘滚动才能持续生效。
                        dragPosRef.value(item, change.position.y)
                        if (delta != Offset.Zero) {
                            change.consume()
                            dragRef.value(delta)
                        }
                    }
                    dragEndRef.value()
                }
            }
        }
    }
}

/** 按下点命中的项（后画的在上，倒序找）与其项内局部坐标。 */
private fun hitTest(
    items: List<GridPanelItem>,
    position: Offset,
    m: GridMetrics,
): Pair<GridPanelItem, Offset>? {
    for (item in items.asReversed()) {
        val n = item.normalized(m.columns, m.rows)
        val topLeft = Offset(n.x * m.stepW, n.y * m.stepH)
        val rect = Rect(
            topLeft,
            Offset(
                topLeft.x + m.itemW(n.spanX.toFloat()),
                topLeft.y + m.itemH(n.spanY.toFloat()),
            ),
        )
        if (rect.contains(position)) return item to (position - topLeft)
    }
    return null
}
