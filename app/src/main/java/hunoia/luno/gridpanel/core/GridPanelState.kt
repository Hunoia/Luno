package hunoia.luno.gridpanel.core

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.spring
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
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** 松手后「视觉位 → 落格位」的残差衰减，与渲染层的落定回压同源。 */
private val GlideSpring = spring<Offset>(dampingRatio = 0.70f, stiffness = 520f)

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
    /** 按页取几何：各页行数不同（页内占用各自上撑），拖拽/缩放钳制必须认当前页。 */
    private val metricsProvider: (Int) -> GridMetrics,
    private val pageItemsProvider: (Int) -> List<GridPanelItem>,
    private val onHaptic: () -> Unit,
    private val commitPage: (Int, List<GridPanelItem>) -> Unit,
    /** 拖到左右边缘松手：把项挪到相邻页（shift = ±1），本页保持原布局。 */
    private val moveItemToPage: (item: GridPanelItem, fromPage: Int, shift: Int) -> Unit,
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

    /** 拖到左右边缘：-1 / +1 = 松手后挪到相邻页，0 = 不挪。 */
    var dragPageShift by mutableStateOf(0)
        private set

    /** 上一次落格用的**夹取后**指针格。与 [hoverCell]（planReorder 的实际落点）
     *  分开记：项顶到边缘时两者可能持久不等，拿 hoverCell 去比会让每帧都
     *  重解一遍并连震（右边缘一直振的根因）。 */
    private var lastTargetCell: GridCell? = null

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

    /** 触感节流：目标格连跳时，80ms 内不重复振。 */
    private var lastHapticAt = 0L

    /** 最近一次位移来自边缘自动滚动：滚动跨格不振动（连续穿行时会一直振）。 */
    private var fromAutoScroll = false

    /** 松手归格残差动画：开始新拖拽要停掉旧的，否则两组动画会抢 glideFrom。 */
    private var glideJob: Job? = null

    private fun hapticTick() {
        val now = android.os.SystemClock.elapsedRealtime()
        if (now - lastHapticAt >= 80L) {
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
        glideJob?.cancel()
        glideJob = null
        dragBase = pageItemsProvider(page)
        resolved = dragBase
        originCell = GridCell(item.x, item.y)
        dragVisual = Offset.Zero
        hoverCell = null
        glideFrom = null
        dragPageShift = 0
        lastTargetCell = null
        draggingId = item.id
        dragPage = page
        // 触感状态跟拖拽走：上一轮的值留着会让本轮第一次落格漏振或误振。
        lastHapticAt = 0L
        fromAutoScroll = false
    }

    fun onDragDelta(action: Offset) {
        val id = draggingId ?: return
        val page = dragPage ?: return
        val m = metricsProvider(page)
        val moving = resolved.firstOrNull { it.id == id } ?: return
        fromAutoScroll = false
        dragVisual = clampDragVisual(dragVisual + action, m, moving)
        resolveTarget()
    }

    /**
     * 把拖拽位移夹到「卡整个留在网格内」。
     * 左右/上下对称：以前左侧用 halfW 允许半个卡探出网格、右侧只到内容边界，
     * 于是「右边缘会挡住、左边不会」，边缘处落点预览和卡片视觉也错开一格。
     */
    private fun clampDragVisual(
        raw: Offset,
        m: GridMetrics,
        moving: GridPanelItem,
    ): Offset {
        val minX = -(originCell.x * m.stepW)
        val minY = -(originCell.y * m.stepH)
        val maxX = (m.contentW - m.itemW(moving.spanX.toFloat()) - originCell.x * m.stepW)
            .coerceAtLeast(0f)
        val maxY = (m.contentH - m.itemH(moving.spanY.toFloat()) - originCell.y * m.stepH)
            .coerceAtLeast(0f)
        return Offset(
            raw.x.coerceIn(minX, maxX),
            raw.y.coerceIn(minY, maxY),
        )
    }

    /**
     * 边缘自动滚动消费了多少内容坐标。手指没动、内容滚走了，
     * 卡片要跟着内容一起走（dragVisual 增加），目标格才会随滚动推进。
     * 同样要过钳制：否则滚到底时卡会被推出内容底边，下次手指一动又弹回来。
     */
    fun onAutoScroll(consumed: Offset) {
        val id = draggingId ?: return
        val page = dragPage ?: return
        if (consumed == Offset.Zero) return
        val m = metricsProvider(page)
        val moving = resolved.firstOrNull { it.id == id } ?: return
        fromAutoScroll = true
        dragVisual = clampDragVisual(dragVisual + consumed, m, moving)
        resolveTarget()
    }

    private fun resolveTarget() {
        val id = draggingId ?: return
        val page = dragPage ?: return
        // 一进函数就取走并清掉：下面任何早退（同一目标格、求解失败）都不能把这个
        // flag 留到下一次手指位移，否则那一帧会被误判成自动滚动而跳过触感。
        val fromScroll = fromAutoScroll
        fromAutoScroll = false
        val m = metricsProvider(page)
        val moving = resolved.firstOrNull { it.id == id } ?: return
        // 用「左上格的中心」落格：格中心判定有半格迟滞，不会贴着格界来回跳；
        // 同时它和整数 span 严格对齐（整数 span 下 contentW − itemW 恰为 step ×
        // (cols − span)），多格项拖到最左/最上也能落到第 0 列，不会像整项中心
        // 那样被夹在第 1 列。
        val left = originCell.x * m.stepW + dragVisual.x
        val top = originCell.y * m.stepH + dragVisual.y
        val tx = m.dropCellX(left, moving.spanX)
        val ty = m.dropCellY(top, moving.spanY)
        // 只在指针格真的变了才重解：顶点到边缘后 tx/ty 已夹到上限，
        // 在这里早退，不会每帧重排 + 连震。
        if (lastTargetCell?.x == tx && lastTargetCell?.y == ty) return
        lastTargetCell = GridCell(tx, ty)
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
        // 自动滚动推进的格不算「手拖到的新格」：连续穿行时会一直振。
        if (fromScroll) return
        hapticTick()
    }

    fun endDrag() {
        val id = draggingId
        val page = dragPage
        val shift = dragPageShift
        if (id != null && page != null) {
            val source = pageItemsProvider(page).firstOrNull { it.id == id }
            if (shift != 0 && source != null) {
                // 横向边缘松手：整项挪到相邻页。本页保持**原**布局 —— 拖拽中的
                // 重排只是预览，没写盘，跟着提交会在源页留下被推开的空洞。
                moveItemToPage(source, page, shift)
            } else {
                val target = resolved.firstOrNull { it.id == id }
                // 原地松手（拖起又放回同一格）：不写盘、不做残差动画。
                // 否则每次长按误触都会碰一次 TuneConfig.updateAction。
                if (target != null && (target.x != originCell.x || target.y != originCell.y)) {
                    val m = metricsProvider(page)
                    val visualNow = Offset(
                        originCell.x * m.stepW + dragVisual.x,
                        originCell.y * m.stepH + dragVisual.y,
                    )
                    val targetBase = Offset(target.x * m.stepW, target.y * m.stepH)
                    settleGlide(id, visualNow - targetBase)
                    commitPage(page, resolved)
                }
            }
        }
        clearDragState()
    }

    /** 取消拖拽（返回键）：卡从当前位置滑回拖起格，不写盘。 */
    fun cancelDrag() {
        val id = draggingId
        val page = dragPage
        if (id != null && page != null) {
            val m = metricsProvider(page)
            val origin = Offset(originCell.x * m.stepW, originCell.y * m.stepH)
            val visualNow = Offset(origin.x + dragVisual.x, origin.y + dragVisual.y)
            settleGlide(id, visualNow - origin)
        }
        clearDragState()
    }

    private fun clearDragState() {
        draggingId = null
        dragPage = null
        dragVisual = Offset.Zero
        hoverCell = null
        dragBase = emptyList()
        resolved = emptyList()
        dragPageShift = 0
        lastTargetCell = null
    }

    /** 拖到左右边缘时由渲染层上报（-1/0/+1）：进出边缘时给一颗触感。 */
    fun onDragPageShift(shift: Int) {
        if (draggingId == null) return
        val next = shift.coerceIn(-1, 1)
        if (next == dragPageShift) return
        dragPageShift = next
        if (next != 0) hapticTick()
    }

    /** 无障碍「移动一格」：不走手势，直接按同一套落位规则重排并写盘。 */
    fun nudge(page: Int, itemId: String, dx: Int, dy: Int) {
        if (draggingId != null || resizingId != null) return
        val items = pageItemsProvider(page)
        val item = items.firstOrNull { it.id == itemId } ?: return
        val m = metricsProvider(page)
        val plan = GridPanelItemLayout.planReorder(
            items = items,
            movingId = itemId,
            targetX = item.x + dx,
            targetY = item.y + dy,
            columns = m.columns,
            rows = m.rows,
        ) ?: return
        val moved = plan.firstOrNull { it.id == itemId } ?: return
        // 顶到边界时 plan 会把目标夹回原位：别白写一次盘。
        if (moved.x == item.x && moved.y == item.y) return
        commitPage(page, plan)
        onHaptic()
    }

    /**
     * 开始一段松手归格：残差写进普通状态，首帧渲染就是正确位置；
     * 逐帧衰减到 0 后清空，后续让位才能回到 animateTo 分支。
     *
     * 不用渲染层的 Animatable 承载：那需要在 LaunchedEffect 里 snapTo(glide)，
     * 首帧会先画出旧位置再跳过去（松手闪回原位）。
     */
    private fun settleGlide(itemId: String, offset: Offset) {
        glideJob?.cancel()
        if (offset.getDistance() < 0.5f) {
            glideFrom = null
            return
        }
        glideFrom = itemId to offset
        glideJob = scope.launch {
            val anim = Animatable(offset, Offset.VectorConverter)
            anim.animateTo(Offset.Zero, GlideSpring) {
                if (glideFrom?.first == itemId) glideFrom = itemId to value
            }
            if (glideFrom?.first == itemId) glideFrom = null
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
        val page = resizePage ?: return
        val m = metricsProvider(page)
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
        val m = metricsProvider(page)
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

    /** 取消缩放（返回键）：丢弃连续 span，不写盘。吸附动画进行中不拦。 */
    fun cancelResize() {
        if (resizingId == null || resizeSettling) return
        clearResize()
    }

    private fun clearResize() {
        resizingId = null
        resizeSpanF = null
        resizeItem = null
        resizePage = null
        resizeSettling = false
    }
}
