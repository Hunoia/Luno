package hunoia.luno.gridpanel.gesture

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
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
 * 编辑态长按阈值：系统的 `longPressTimeoutMillis`（~400ms）拖起不够跟手；
 * 编辑态本就是明确的「改布局」意图，缩到 300ms，同时不短于无障碍可接受的下限。
 */
private const val EditModeLongPressMillis = 300L

/**
 * 按住反馈：只跟踪「按下命中的是哪一项」，不消费事件、不影响主手势的命中逻辑。
 * 与 [gridLayerGestures] 并列挂同一节点（各自一个 pointerInput，都会收到事件）：
 * 主手势专心处理判定域，这里只负责让卡片在长按等待期先浮起来。
 */
@Composable
fun Modifier.gridItemPressFeedback(
    metrics: GridMetrics,
    itemsProvider: () -> List<GridPanelItem>,
    onPressChange: (GridPanelItem?) -> Unit,
): Modifier {
    val metricsRef = rememberUpdatedState(metrics)
    val itemsRef = rememberUpdatedState(itemsProvider)
    val onPressRef = rememberUpdatedState(onPressChange)
    return pointerInput(Unit) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            // 子层（删除角标、手柄）已经消费了这一下：那根手指不归「按下反馈」管，
            // 否则点角标时整项会无意义地浮一下。
            if (down.isConsumed) return@awaitEachGesture
            val hit = hitTest(itemsRef.value(), down.position, metricsRef.value)
                ?: return@awaitEachGesture
            onPressRef.value(hit.first)
            try {
                waitForUpOrCancellation()
            } finally {
                // 抬手、滑动、被子层消费、节点被回收都要收回去，不能留一张悬着的卡。
                onPressRef.value(null)
            }
        }
    }
}

/**
 * 网格层根节点的统一手势（编辑态）：手柄 > 长按拖拽 > 点按。
 * 必须挂在层根：项在拖拽中随 `offset{}` 逐帧移动，项内 pointerInput 的坐标系
 * 跟着动，`positionChange()` 会被节点自身位移抵掉（半速跟手、抖动）；层根在
 * 整个拖拽期间静止，位移即纯屏幕位移，1:1 跟手。
 * 命中测试只在按下时做一次，拖拽/缩放开始后锁定该项直到抬手。
 * 子层（删除角标）消费的事件表现为 isConsumed → 放弃，不会误起整项拖拽。
 */
@Composable
fun Modifier.gridLayerGestures(
    metrics: GridMetrics,
    handleHitPx: Float,
    onTap: (GridPanelItem) -> Unit,
    onDragStart: (GridPanelItem) -> Unit,
    onDrag: (Offset) -> Unit,
    onDragEnd: () -> Unit,
    /** [contentX]/[contentY] = 手指在网格内容坐标里的位置（层根 = 滚动内容）。 */
    onDragPosition: (GridPanelItem, contentX: Float, contentY: Float) -> Unit,
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
            // 不要 consume down：否则外层 verticalScroll / pager 的拖动起不来。
            val m = metricsRef.value
            val hit = hitTest(itemsRef.value(), down.position, m) ?: return@awaitEachGesture
            val (item, local) = hit
            val itemW = m.itemW(item.spanX.toFloat())
            val itemH = m.itemH(item.spanY.toFloat())

            // 1) 尺寸手柄：命中即 resize 并 return，不再往下走长按判定。
            //    命中区上限按边长 40% 再缩，小格（1×1 ≈ 80dp → 32dp）不盖掉半个项。
            val handleHit = minOf(hitRef.value, minOf(itemW, itemH) * 0.4f)
            if (inResizeHandleArea(
                    local,
                    IntSize(itemW.roundToInt(), itemH.roundToInt()),
                    handleHit,
                )
            ) {
                down.consume()
                // 和长按拖拽的 LONG_PRESS 对坐：抓住手柄也要有一下触感。
                view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                resizeStartRef.value(item)
                trackResizeDrag(down) { resizeDeltaRef.value(it) }
                resizeEndRef.value()
                return@awaitEachGesture
            }

            // 2) 长按等待：与 combinedClickable 共用 ViewConfiguration 超时。
            var upForTap: PointerInputChange? = null
            val outcome = withTimeoutOrNull(
                minOf(viewConfiguration.longPressTimeoutMillis, EditModeLongPressMillis),
            ) {
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
                        val delta = change.positionChange()
                        // 位置先报：位移为零的帧也要报，边缘滚动才能持续生效。
                        dragPosRef.value(item, change.position.x, change.position.y)
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
