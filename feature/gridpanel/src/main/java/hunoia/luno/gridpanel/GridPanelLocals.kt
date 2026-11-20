package hunoia.luno.gridpanel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntSize
import hunoia.luno.ui.component.overlay.EdgeBarSlot
import hunoia.luno.ui.overlay.EdgeBar
import hunoia.luno.ui.overlay.EdgeBarEdge
import hunoia.luno.ui.overlay.EdgeBarLayout

/**
 * 网格面板内「打开外部应用/页面」后请求关闭面板。自绘小部件自己处理点击，
 * 不经过 `ActionItemCell` 的 `execute → closeWithAnim()`；不补这次关闭，
 * 启动的应用会被全屏面板盖住，看起来像「点了没反应」。
 */
val LocalGridPanelClose = staticCompositionLocalOf<() -> Unit> { {} }

/**
 * 网格面板打开时的屏幕触点：面板内自绘部件（应用抽屉等）启动小窗时作为落点。
 * 用 CompositionLocal 下发，避免把 anchor 一路穿到每个内部部件。
 */
val LocalGridPanelAnchor = staticCompositionLocalOf<Pair<Int, Int>?> { null }

/**
 * 面板内「拿起一个内容拖到别的应用」的拖拽幽灵状态：内容区（部件）写入，面板根绘制。
 *
 * 位置是**窗口坐标**（面板根 Box 的 local 坐标）：内容区用
 * `LayoutCoordinates.positionInWindow()` 换算，落点再补窗口原点到屏幕坐标。
 * 幽灵必须画在根层 —— 格子里的内容被裁剪，拖到面板外就看不见了。
 *
 * 剪贴板部件的拖拽动作条复用同一状态：动作集与手指屏幕坐标写在这里，面板根据此
 * 在屏幕边缘浮出动作条；松手由 [resolveDrag] 按纯几何命中动作，落空回退默认插入。
 */
class GridPanelDragGhost {
    var text by mutableStateOf("")
        private set

    var position by mutableStateOf(Offset.Zero)
        private set

    /** 拖过阈值后把面板本体淡出：窗口留着接手势，视觉上「面板关了」能看见下面的应用。 */
    var chromeHidden by mutableStateOf(false)
        private set

    // ---- 拖拽动作条（剪贴板等条目用） ----

    /** 动作条的槽位（空 = 无动作条）。文案由调用方本地化后传入，这里不认具体动作枚举。 */
    var dragSlots by mutableStateOf<List<EdgeBarSlot>>(emptyList())
        private set

    /** 手指的屏幕坐标（动作条锚定 + 命中用）。 */
    var dragScreenPos by mutableStateOf(Offset.Zero)
        private set

    // 面板根（全屏）尺寸 / dp→px 系数 / 系统栏避让量（px，顶+底）：由根层经
    // [setDragScreenMetrics] 一次写入，写入即重算条体（渲染与结算共用同一份几何）。
    private var dragScreenSize by mutableStateOf(IntSize.Zero)
    private var dragDensity by mutableStateOf(1f)
    private var dragInsets by mutableStateOf(EdgeBarLayout.Insets())

    /** 当前动作条几何（[moveDrag] 计算；渲染 + 卡片让位 + 松手结算共用同一份）。 */
    var dragBar by mutableStateOf<EdgeBar?>(null)
        private set

    /** 锚点锁在哪条边 / 哪个手指位置（条体首次浮出时锁定，沿边不跟手）。 */
    private var dragBarEdge: EdgeBarEdge? = null
    private var dragBarAnchor: Offset? = null

    val active: Boolean get() = text.isNotEmpty()

    /** 是否处于「带动作条的拖拽」。 */
    val dragActive: Boolean get() = dragSlots.isNotEmpty()

    fun begin(text: String, windowPosition: Offset) {
        if (text.isEmpty()) return
        this.text = text
        position = windowPosition
        chromeHidden = false
    }

    fun move(windowPosition: Offset) {
        if (active) position = windowPosition
    }

    fun hideChrome() {
        if (active) chromeHidden = true
    }

    fun showChrome() {
        chromeHidden = false
    }

    /** 只清幽灵，**不**恢复卡面：拖拽落点后要接着播关闭动画，提前恢复会闪一下。 */
    fun end() {
        text = ""
    }

    /**
     * 根层写入屏幕几何（尺寸 / 密度 / 系统栏避让）并立即重算条体。
     *
     * 首次拖拽时 [beginDrag] 早于动作条层组合，读到的是 Zero 尺寸；只写不算是
     * 「越过阈值后条体要等下一次移动才浮出」的根因，所以写入后统一重算一次。
     */
    fun setDragScreenMetrics(size: IntSize, density: Float, insets: EdgeBarLayout.Insets) {
        dragScreenSize = size
        dragDensity = density
        dragInsets = insets
        refreshDragBar()
    }

    /** 拖拽成立（越过阈值）：挂上动作条槽位并重算条体。 */
    fun beginDrag(slots: List<EdgeBarSlot>) {
        dragSlots = slots
        dragBarEdge = null
        dragBarAnchor = null
        refreshDragBar()
    }

    /** 拖拽中更新手指屏幕坐标（动作条按最近边浮出 / 换边重锁 / 移回中间收起）。 */
    fun moveDrag(screenPosition: Offset) {
        dragScreenPos = screenPosition
        refreshDragBar()
    }

    /**
     * 松手：按手指屏幕坐标解析动作条命中；未命中返回 null（调用方按默认处理）。
     * 返回命中槽位的 [EdgeBarSlot.id]，由调用方映射回自己的动作。
     * 无论命中与否都清掉槽位（一次拖拽只结算一次），条体随即消失。
     */
    fun resolveDrag(screenPosition: Offset): String? {
        if (dragSlots.isEmpty()) {
            endDrag()
            return null
        }
        val size = dragScreenSize
        if (size.width <= 0 || size.height <= 0) {
            endDrag()
            return null
        }
        dragScreenPos = screenPosition
        refreshDragBar()
        val bar = dragBar
        if (bar == null) {
            endDrag()
            return null
        }
        val hit = EdgeBarLayout.hit(bar, screenPosition)
        val id = hit?.let { dragSlots.getOrNull(it)?.id }
        endDrag()
        return id
    }

    /** 清掉动作条状态（取消或已结算）。 */
    fun endDrag() {
        dragSlots = emptyList()
        dragBar = null
        dragBarEdge = null
        dragBarAnchor = null
    }

    /**
     * 按当前手指位置重算动作条：锚点只在条体**浮出那一刻**（或换了另一条边）锁定一次，
     * 之后不动 —— 手指沿边滑动只改变指到哪一格，不改变整条的位置（同识屏口径）。
     * 手指移回屏幕中间（带宽外）时条体收起、锚点作废，下次浮出重新锁。
     */
    private fun refreshDragBar() {
        val size = dragScreenSize
        if (size.width <= 0 || size.height <= 0 || dragSlots.isEmpty()) {
            dragBar = null
            dragBarEdge = null
            dragBarAnchor = null
            return
        }
        val edge = EdgeBarLayout.edgeFor(
            finger = dragScreenPos,
            screenWidth = size.width.toFloat(),
            screenHeight = size.height.toFloat(),
            density = dragDensity,
        )
        if (edge == null) {
            dragBar = null
            dragBarEdge = null
            dragBarAnchor = null
            return
        }
        if (edge != dragBarEdge) {
            dragBarEdge = edge
            dragBarAnchor = dragScreenPos
        }
        val axis = dragBarAnchor?.let {
            if (edge == EdgeBarEdge.LEFT || edge == EdgeBarEdge.RIGHT) it.y else it.x
        }
        dragBar = EdgeBarLayout.bar(
            finger = dragScreenPos,
            screenWidth = size.width.toFloat(),
            screenHeight = size.height.toFloat(),
            count = dragSlots.size,
            density = dragDensity,
            anchorAxis = axis,
            insets = dragInsets,
        )
    }
}

/** 当前面板的拖拽幽灵；不在网格面板内（或不需要该能力）时为 null。 */
val LocalGridPanelDragGhost = staticCompositionLocalOf<GridPanelDragGhost?> { null }

/**
 * 网格面板「加项 / 换动作」请求。只带面板自身的参数，选择页路由由宿主映射，
 * 面板模块不认识 `:app` 的导航类型。
 */
data class GridPanelItemPick(
    val gridPanelId: String,
    val pageIndex: Int,
    /** 非空 = 替换该面板项（保留位置/span，只改 kind/params），否则新增。 */
    val replaceItemId: String? = null,
)

/**
 * 宿主注入的选择页入口（`:app` 在 service 初始化时挂上）。
 * 返回 false = 未能启动，面板会恢复自身显示。
 */
object GridPanelCommand {
    var openItemPicker: ((GridPanelItemPick) -> Boolean)? = null
}
