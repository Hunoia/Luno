package hunoia.luno.gridpanel.core

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import hunoia.luno.config.model.GridCell
import hunoia.luno.config.model.GridPanelItem
import hunoia.luno.config.model.GridPanelItemLayout
import hunoia.luno.gridpanel.geometry.GridMetrics
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** 松手后 span 吸附到整格的动画时长。 */
private const val ResizeSettleMillis = 400

/**
 * 网格面板的拖拽 / 缩放状态机。
 *
 * 拖拽每帧都从拖起快照 dragBase 重解，resolved 只写不读（除取被拖项自己的
 * span）：把 planReorder 的结果回灌为输入，被推走的项不回位，来回拖会累积漂移。
 * dragVisual 是网格内容坐标里相对拖起位置的位移，含自动滚动补偿；
 * glideFrom 是松手瞬间「视觉位 − 落格基位」的残差，交给渲染层衰减到 0。
 */
@Stable
class GridPanelState(
    private val metricsProvider: () -> GridMetrics,
    private val pageItemsProvider: (Int) -> List<GridPanelItem>,
    private val onHaptic: () -> Unit,
    private val commitPage: (Int, List<GridPanelItem>) -> Unit,
    private val scope: CoroutineScope,
) {
    var draggingId by mutableStateOf<String?>(null)
        private set
    var dragPage by mutableStateOf<Int?>(null)
        private set
    var dragVisual by mutableStateOf(Offset.Zero)
        private set
    var hoverCell by mutableStateOf<GridCell?>(null)
        private set

    /** 松手惯性：(itemId, 视觉残差 px)。下次拖起清空。 */
    var glideFrom by mutableStateOf<Pair<String, Offset>?>(null)
        private set

    private var dragBase: List<GridPanelItem> = emptyList()
    private var resolved by mutableStateOf<List<GridPanelItem>>(emptyList())
    private var originCell = GridCell(0, 0)

    var resizingId by mutableStateOf<String?>(null)
        private set

    /** resize 期间的连续 span（Float，不吸附）。null = 未在 resize。 */
    var resizeSpanF by mutableStateOf<Offset?>(null)
        private set

    private var resizeStartSpan = Offset.Zero
    private var resizeItem: GridPanelItem? = null
    private var resizePage: Int? = null
    /** 松手吸附动画进行中：禁止二次抓手柄（resizingId 仍非空）。 */
    private var resizeSettling = false

    /** 触感节流：同一次拖拽/缩放里目标格连跳时，120ms 内不重复振。 */
    private var lastHapticAt = 0L

    /** 最近一次位移来自边缘自动滚动：滚动跨格不振动（连续穿行时会一直振）。 */
    private var fromAutoScroll = false

    /** 上次振动时的 dragVisual：目标格变化但净位移不够（贴着格界抖）不振动。 */
    private var lastHapticVisual = Offset.Zero

    private fun hapticTick() {
        val now = android.os.SystemClock.elapsedRealtime()
        if (now - lastHapticAt >= 120L) {
            lastHapticAt = now
            onHaptic()
        }
    }

    fun pageItems(page: Int): List<GridPanelItem> = pageItemsProvider(page)

    /** 渲染列表：拖拽中返回重解后的布局，平时返回落盘布局。 */
    fun renderItems(page: Int): List<GridPanelItem> =
        if (draggingId != null && dragPage == page) resolved else pageItemsProvider(page)

    fun beginDrag(page: Int, item: GridPanelItem) {
        if (resizingId != null) return
        dragBase = pageItemsProvider(page)
        resolved = dragBase
        originCell = GridCell(item.x, item.y)
        dragVisual = Offset.Zero
        hoverCell = null
        glideFrom = null
        draggingId = item.id
        dragPage = page
    }

    fun onDragDelta(action: Offset) {
        val id = draggingId ?: return
        val m = metricsProvider()
        val moving = resolved.firstOrNull { it.id == id } ?: return
        fromAutoScroll = false
        val raw = dragVisual + action
        // 钳制必须配合「中心落格」的判定域：中心要能到达目标格的中心，
        // 否则占多格的项往左挪一格时永远够不着（占 2-4 拖不到 1-3）。
        val halfW = m.itemW(moving.spanX.toFloat()) / 2f
        val halfH = m.itemH(moving.spanY.toFloat()) / 2f
        val minX = -(originCell.x * m.stepW) - halfW
        val minY = -(originCell.y * m.stepH) - halfH
        val maxX = (m.contentW - m.itemW(moving.spanX.toFloat()) - originCell.x * m.stepW)
            .coerceAtLeast(0f)
        val maxY = (m.contentH - m.itemH(moving.spanY.toFloat()) - originCell.y * m.stepH)
            .coerceAtLeast(0f)
        dragVisual = Offset(
            raw.x.coerceIn(minX, maxX),
            raw.y.coerceIn(minY, maxY),
        )
        resolveTarget()
    }

    /**
     * 边缘自动滚动消费了多少内容坐标。手指没动、内容滚走了，
     * 卡片要跟着内容一起走（dragVisual 增加），目标格才会随滚动推进。
     */
    fun onAutoScroll(consumed: Offset) {
        if (draggingId == null || consumed == Offset.Zero) return
        fromAutoScroll = true
        dragVisual += consumed
        resolveTarget()
    }

    private fun resolveTarget() {
        val id = draggingId ?: return
        val m = metricsProvider()
        val moving = resolved.firstOrNull { it.id == id } ?: return
        // 按项**中心**落格（不是左上角）：中心要拖过半格才换目标，
        // 拖到格边界附近时不会在相邻格间来回跳（1×1 项尤甚）。
        val centerX = originCell.x * m.stepW + dragVisual.x + m.itemW(moving.spanX.toFloat()) / 2f
        val centerY = originCell.y * m.stepH + dragVisual.y + m.itemH(moving.spanY.toFloat()) / 2f
        val tx = m.cellX(centerX)
        val ty = m.cellY(centerY)
        if (hoverCell?.x == tx && hoverCell?.y == ty) return
        // 只从 dragBase 求解：输出不再反馈为输入，来回拖不会累积漂移。
        val plan = GridPanelItemLayout.planReorder(
            items = dragBase,
            movingId = id,
            targetX = tx,
            targetY = ty,
            columns = m.columns,
            rows = m.rows,
        )
        if (plan == null) {
            // 求解失败（出界/超轮数）：保持上一个有效目标。
            return
        }
        val dropped = plan.firstOrNull { it.id == id } ?: return
        resolved = plan
        // hoverCell 记的是**实际落点**（planReorder 会夹目标格），不是指针格：
        // 下一帧同一指针格不会再触发一次触感。
        hoverCell = GridCell(dropped.x, dropped.y)
        // 双重去抖：①自动滚动跨格不振动；②目标格虽变，但手指净位移不足 0.3 格不振动。
        if (fromAutoScroll) {
            fromAutoScroll = false
            return
        }
        if ((dragVisual - lastHapticVisual).getDistance() < m.stepW * 0.3f) return
        lastHapticVisual = dragVisual
        hapticTick()
    }

    fun endDrag() {
        val id = draggingId
        val page = dragPage
        if (id != null && page != null) {
            val m = metricsProvider()
            val target = resolved.firstOrNull { it.id == id }
            if (target != null) {
                val visualNow = Offset(
                    originCell.x * m.stepW + dragVisual.x,
                    originCell.y * m.stepH + dragVisual.y,
                )
                val targetBase = Offset(target.x * m.stepW, target.y * m.stepH)
                glideFrom = id to (visualNow - targetBase)
                commitPage(page, resolved)
            }
        }
        draggingId = null
        dragPage = null
        dragVisual = Offset.Zero
        hoverCell = null
        dragBase = emptyList()
        resolved = emptyList()
        // 残差动画走完后清空 glide：否则后续任何让位都会走「base.snapTo」分支，
        // 表现为别的项一被推就瞬移。450ms > SettleSpring 的收敛时间。
        if (id != null) {
            scope.launch {
                delay(450L)
                if (glideFrom?.first == id) glideFrom = null
            }
        }
    }

    fun beginResize(page: Int, item: GridPanelItem) {
        if (resizingId != null || draggingId != null) return
        resizingId = item.id
        resizePage = page
        resizeItem = item
        resizeStartSpan = Offset(item.spanX.toFloat(), item.spanY.toFloat())
        resizeSpanF = resizeStartSpan
    }

    /**
     * [total] 是手柄从按下到当前的总位移；span 连续跟手、不做碰撞判定，
     * 只在松手时校验，扩张撞墙先退化成「保宽/保高」候选。
     */
    fun onResizeDelta(total: Offset) {
        if (resizeSettling) return
        val item = resizeItem ?: return
        val m = metricsProvider()
        val maxX = (m.columns - item.x).coerceAtLeast(1).toFloat()
        val maxY = (m.rows - item.y).coerceAtLeast(1).toFloat()
        // 下限：默认 1×1，媒体部件 2×1（一格宽连歌名都放不下）。
        val minSpan = (item.kind as? hunoia.luno.config.model.GridPanelItem.Kind.Internal)
            ?.let { hunoia.luno.gridpanel.internal.internalWidgetMinSpan(it.widgetId) }
            ?: (1 to 1)
        val sx = (resizeStartSpan.x + total.x / m.stepW).coerceIn(minSpan.first.toFloat(), maxX)
        val sy = (resizeStartSpan.y + total.y / m.stepH).coerceIn(minSpan.second.toFloat(), maxY)
        val next = Offset(sx, sy)
        val before = resizeSpanF
        resizeSpanF = next
        // 跨过整格边界时给一颗触感（与拖拽目标格变化同款），连续跟手不连震。
        if (before != null && (before.x.roundToInt() != sx.roundToInt() ||
                before.y.roundToInt() != sy.roundToInt())
        ) {
            hapticTick()
        }
    }

    fun endResize() {
        if (resizeSettling) return
        val id = resizingId ?: return
        resizeSettling = true
        val item = resizeItem ?: run { clearResize(); return }
        val page = resizePage ?: run { clearResize(); return }
        val m = metricsProvider()
        val current = pageItemsProvider(page)
        val raw = resizeSpanF ?: resizeStartSpan
        val startX = resizeStartSpan.x.roundToInt()
        val startY = resizeStartSpan.y.roundToInt()
        val maxX = (m.columns - item.x).coerceAtLeast(1)
        val maxY = (m.rows - item.y).coerceAtLeast(1)
        val rawX = raw.x.roundToInt().coerceIn(
            (item.kind as? hunoia.luno.config.model.GridPanelItem.Kind.Internal)
                ?.let { hunoia.luno.gridpanel.internal.internalWidgetMinSpan(it.widgetId).first } ?: 1,
            maxX,
        )
        val rawY = raw.y.roundToInt().coerceIn(1, maxY)
        // 扩张撞墙时按「raw → 保宽 → 保高 → 原尺寸」降级，取第一个合法布局。
        val candidates = listOf(
            rawX to rawY,
            rawX to startY,
            startX to rawY,
            startX to startY,
        )
        val chosen = candidates.firstOrNull { (w, h) ->
            GridPanelItemLayout.isFree(current, item.x, item.y, w, h, ignoreId = id)
        } ?: (startX to startY)
        // 原地点一下手柄（或缩回去又回到原位）：不写盘、不做 400ms 空动画。
        if (raw == resizeStartSpan && chosen.first == startX && chosen.second == startY) {
            clearResize()
            return
        }
        val from = raw
        val to = Offset(chosen.first.toFloat(), chosen.second.toFloat())
        scope.launch {
            val anim = Animatable(from, Offset.VectorConverter)
            // 动画结束后才落盘：提前写会让 lastWritten 的抑制规则在动画期间错乱。
            anim.animateTo(to, tween(ResizeSettleMillis)) { resizeSpanF = value }
            val next = current.map {
                if (it.id == id) it.copy(spanX = chosen.first, spanY = chosen.second) else it
            }
            commitPage(page, next)
            clearResize()
        }
    }

    private fun clearResize() {
        resizingId = null
        resizeSpanF = null
        resizeItem = null
        resizePage = null
        resizeSettling = false
    }
}
