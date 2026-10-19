package hunoia.luno.gridpanel

import android.view.HapticFeedbackConstants
import android.view.WindowInsets
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.ScrollState
import kotlinx.coroutines.Job
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionOnScreen
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import coil.compose.AsyncImage
import coil.imageLoader
import com.aaron.compose.ktx.toDp
import hunoia.luno.R
import hunoia.luno.action.model.NewActionLibraryEntry
import hunoia.luno.action.model.StoredAction
import hunoia.luno.action.model.withoutVariantKey
import hunoia.luno.bridge.ui.showToast
import hunoia.luno.config.model.GridCell
import hunoia.luno.config.model.GridPanel
import hunoia.luno.config.model.GridPanelDefaults
import hunoia.luno.config.model.GridPanelItem
import hunoia.luno.config.model.GridPanelItemLayout
import hunoia.luno.domain.tune.TuneConfig
import hunoia.luno.gridpanel.widget.PanelAppWidgetHost
import hunoia.luno.gridpanel.widget.PanelAppWidgetView
import hunoia.luno.gridpanel.widget.WidgetGoneListener
import hunoia.luno.gridpanel.widget.WidgetLoadState
import hunoia.luno.gridpanel.widget.addWidgetGoneListener
import hunoia.luno.gridpanel.widget.removeWidgetGoneListener
import hunoia.luno.ui.component.TextIcon
import hunoia.luno.ui.component.TextIconGlyph
import hunoia.luno.ui.component.actionIcon
import hunoia.luno.ui.component.actionText
import hunoia.luno.ui.navigation.ActionSelect
import hunoia.luno.ui.navigation.LocalActionLibraryEntries
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import kotlin.math.floor
import kotlin.math.roundToInt

private val CardMargin = 12.dp
private val CardCorner = 20.dp
private val HeaderHeight = 46.dp

/**
 * 格间距随列数收放。固定间距在两端都不对：2 列时格子很大、6dp 显得松散漂；
 * 6 列时 6dp 又让格子挤成一团（跨格项的内部 padding 会被吃掉）。
 */
private fun gapFor(columns: Int): Dp = when {
    columns <= 3 -> 10.dp
    columns <= 4 -> 8.dp
    columns <= 5 -> 6.dp
    else -> 4.dp
}
/** 多页时页指示点行高（固定预算，held in cardH 计算）。 */
private val IndicatorHeight = 12.dp
/** 拖拽到距可视区上下沿这么多 dp 内时开始自动滚动（一墨 nz1 同款）。 */
private val EdgeScrollBand = 56.dp
/** 自动滚动每帧步进（dp/帧，约 540dp/s）。 */
private val EdgeScrollStep = 9.dp

/**
 * 网格面板：固定网格 + 跨格项 + 简版多页。
 * 运行态 —— 点项执行（无 a11y 时转发失败弹提示不关面板）；长按空白（或长按小部件）进编辑；
 * 编辑态 —— 每项常显删除角标 + 尺寸手柄、点「+」加项、长按项拖拽落位（整页让位重排）。
 * 翻页是 [HorizontalPager]：跟手 + 松手按位移/速度吸附，取代旧版「屏宽/3 阈值瞬切」。
 *
 * 项执行统一走 [dispatch]（宿主转发进容器 runGestureActions），本组合不私自分发。
 */
@Composable
fun GridPanelRoot(
    panel: GridPanel?,
    anchorX: Int,
    anchorY: Int,
    dispatch: (action: StoredAction, screenX: Int, screenY: Int) -> Boolean,
    /** 跳动作选择页；返回 false 表示没跳起来，调用方负责把面板放回可见。 */
    onPickItem: (ActionSelect) -> Boolean,
    onClose: () -> Unit,
    onCloseAnimated: () -> Unit,
    onRegisterCloseAnimated: ((() -> Unit) -> Unit)?,
) {
    val libraryEntries = LocalActionLibraryEntries.current

    // 进出场一张 Animatable 管 alpha/scale（球窗结论：显式 Animatable 语义可证伪，
    // 不依赖服务进程里 animate*AsState 的收敛）。
    val appearance = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    var closing by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { appearance.animateTo(1f, tween(170)) }
    fun closeWithAnim() {
        if (closing) return
        closing = true
        scope.launch {
            appearance.animateTo(0f, tween(140))
            onCloseAnimated()
        }
    }
    LaunchedEffect(Unit) { onRegisterCloseAnimated?.invoke { closeWithAnim() } }

    if (panel == null) {
        // 执行层已拦空面板；能到这里只有「打开后面板被删」的极端路径。给个落点不静默。
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.32f))
                .pointerInput(Unit) { detectTapGestures(onTap = { onClose() }) },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = stringResource(R.string.action_panel_missing),
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.titleMedium,
            )
        }
        return
    }

    val columns = panel.columnCount.coerceIn(GridPanelDefaults.MinColumnCount, GridPanelDefaults.MaxColumnCount)
    val rows = panel.rowCount.coerceIn(GridPanelDefaults.MinRowCount, GridPanelDefaults.MaxRowCount)
    val pageCount = panel.pageCount.coerceAtLeast(1)
    val cardFraction = panel.cardWidthFraction
        .coerceIn(GridPanelDefaults.MinCardWidthFraction, GridPanelDefaults.MaxCardWidthFraction)
    val cardAlpha = panel.cardAlpha.coerceIn(GridPanelDefaults.MinCardAlpha, GridPanelDefaults.MaxCardAlpha)
    // 翻页状态：currentPage 随手指实时变（页指示点跟手），settledPage 是落定的页
    //（加项 / 落盘都认它）。页数被改小时 PagerState 自己夹住当前页。
    val pagerState = rememberPagerState(pageCount = { pageCount })
    var editMode by remember(panel.id) { mutableStateOf(false) }

    // 项列表的本地真身：拖拽要连续多次落位，DataStore 回流有延迟，直接渲染
    // panel.items 会在快速拖动时被旧快照回跳。回流同步只认「与 lastWritten
    // 不同」的变更（自己写出去的回声不覆盖本地）。
    var localItems by remember(panel.id) { mutableStateOf(panel.items) }
    var lastWritten by remember(panel.id) { mutableStateOf(panel.items) }
    LaunchedEffect(panel.items) {
        if (panel.items != lastWritten) {
            localItems = panel.items
            lastWritten = panel.items
        }
    }

    /** [pageIndex] 页的项。拖拽/渲染/落点解算全吃这份，落盘再拼回整体。 */
    fun pageItemsAt(pageIndex: Int): List<GridPanelItem> = localItems.filter { it.pageIndex == pageIndex }
    fun commitPageItems(pageIndex: Int, next: List<GridPanelItem>) {
        val rebuilt = localItems.filter { it.pageIndex != pageIndex } + next
        localItems = rebuilt
        lastWritten = rebuilt
        scope.launch {
            TuneConfig.updateAction { settings ->
                val latest = settings.actionGridPanels.panelById(panel.id) ?: return@updateAction settings
                settings.copy(actionGridPanels = settings.actionGridPanels.withPanel(latest.copy(items = rebuilt)))
            }
        }
    }

    // 卡片几何：宽 = 屏宽比例；高 = 页眉 + 行数 × 格高（固定网格，不再由宽高比推算）。
    val density = LocalDensity.current
    val view = LocalView.current
    // 走 LocalConfiguration 而不是 displayMetrics：组合必须订阅配置变化才会在转屏后
    // 重算（只读 metrics 的组合不会重组）。
    val configuration = LocalConfiguration.current
    val screenW = with(density) { configuration.screenWidthDp.dp.toPx() }
    val screenH = with(density) { configuration.screenHeightDp.dp.toPx() }
    val marginPx = with(density) { CardMargin.toPx() }
    // 系统栏余量按 configuration 取：转屏（导航栏换边）后不能沿用首帧的值。
    val insetsTop = remember(configuration) {
        runCatching { view.rootWindowInsets.getInsets(WindowInsets.Type.statusBars()).top }.getOrDefault(0)
    }
    val insetsBottom = remember(configuration) {
        runCatching { view.rootWindowInsets.getInsets(WindowInsets.Type.navigationBars()).bottom }.getOrDefault(0)
    }
    val gridPadPx = with(density) { 10.dp.toPx() }
    val gapPx = with(density) { gapFor(columns).toPx() }
    val innerW = screenW * cardFraction - gridPadPx * 2
    val cellW = (innerW - gapPx * (columns - 1)) / columns
    val cellH = cellW
    val gridH = cellH * rows + gapPx * (rows - 1)
    val headerPx = with(density) { HeaderHeight.toPx() }
    val cardW = screenW * cardFraction
    // 多页时页眉下多一行指示点（固定 12dp，与 GridPanelHeader 的渲染对齐）。
    val indicatorPx = if (pageCount > 1) with(density) { IndicatorHeight.toPx() } else 0f
    val cardH = (gridH + headerPx + indicatorPx).coerceAtMost(screenH * 0.82f) + gridPadPx * 2
    val targetPosX = (anchorX - cardW / 4f).coerceIn(marginPx, (screenW - cardW - marginPx).coerceAtLeast(marginPx))
    val targetPosY = (anchorY - cardH / 4f).coerceIn(
        insetsTop + marginPx,
        (screenH - insetsBottom - cardH - marginPx).coerceAtLeast(insetsTop + marginPx),
    )
    // 转屏 / 改网格尺寸时卡片要挪位置，瞬移会让用户看不出「它重新落位到哪了」。
    // 走 Animatable 平滑过渡；首帧（appearance 还在入场）直接对齐，不要从 0 飞过来。
    val posAnimX = remember { Animatable(targetPosX) }
    val posAnimY = remember { Animatable(targetPosY) }
    LaunchedEffect(targetPosX, targetPosY) {
        if (appearance.value < 0.99f) {
            posAnimX.snapTo(targetPosX)
            posAnimY.snapTo(targetPosY)
        } else {
            posAnimX.animateTo(
                targetPosX,
                spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow),
            )
            posAnimY.animateTo(
                targetPosY,
                spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow),
            )
        }
    }

    var draggingId by remember { mutableStateOf<String?>(null) }
    var dragVisual by remember { mutableStateOf(Offset.Zero) }
    var hoverCell by remember { mutableStateOf<GridCell?>(null) }

    // 一墨式整页重排（§11.3）：拖拽期间的真布局。被拖项钉在指针格，其余项让位 ——
    // render 列表用它，松手整份落盘。dragOrigCell 是拖起瞬间的原位（视觉回滑终点）。
    var dragOrigCell by remember { mutableStateOf<GridCell?>(null) }
    var dragOrderItems by remember { mutableStateOf<List<GridPanelItem>>(emptyList()) }
    // 拖拽发生在哪一页（翻页与拖拽互斥，落盘认这一页）。
    var dragPage by remember { mutableStateOf<Int?>(null) }
    // 松手惯性：落盘后仍以「指下视觉位」为起点滑向落格（Animatable 帮 controller），
    // 形如 (itemId, 起点基坐标px)。下次拖起清空。
    var glideFrom by remember { mutableStateOf<Pair<String, Offset>?>(null) }

    // 尺寸手柄（编辑态每项右下角）：实时改 span，扩到碰撞即停。
    var resizingId by remember { mutableStateOf<String?>(null) }
    var resizeSpan by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var resizePage by remember { mutableStateOf<Int?>(null) }

    // 边缘自动滚动：手指停在网格上下沿带内时启动的协程，以及它正在服务的那个
    // ScrollState / 方向。记录后两者是为了「方向或页变化才重启」—— 同一方向
    // 持续按住不该每秒重启一次协程（会把滚动打断成一顿一顿的）。
    var autoScrollJob by remember { mutableStateOf<Job?>(null) }
    var edgeScrollTarget by remember { mutableStateOf<ScrollState?>(null) }
    var edgeScrollDir by remember { mutableIntStateOf(0) }

    /** 手柄拖拽中：项内指针坐标 → 目标 span（跟随指针，可扩可缩），扩张遇阻回退。 */
    fun resizeItemTo(item: GridPanelItem, itemX: Float, itemY: Float) {
        if (resizingId != null && resizingId != item.id) return
        resizingId = item.id
        val pageIndex = resizePage ?: pagerState.settledPage
        val current = pageItemsAt(pageIndex)
        val base = resizeSpan ?: (item.spanX to item.spanY)
        val stepW = cellW + gapPx
        val stepH = cellH + gapPx
        // 下限取项自己的 minSpan（小部件 = provider 声明的尺寸，动作项 = 1）。
        // 以前硬编码 1，能把一个 2×2 的小部件缩到 1×1 —— options 会被夹成 MIN==MAX，
        // provider 只能交一张紧凑布局甚至空白。
        val minX = item.minSpanX.coerceIn(1, columns - item.x)
        val minY = item.minSpanY.coerceIn(1, rows - item.y)
        val tw = ((itemX + stepW * 0.5f) / stepW).toInt().coerceIn(minX, columns - item.x)
        val th = ((itemY + stepH * 0.5f) / stepH).toInt().coerceIn(minY, rows - item.y)
        val candidate = when {
            // 收缩恒合法（自身子区）；扩张目标合法直接用
            GridPanelItemLayout.isFree(current, item.x, item.y, tw, th, ignoreId = item.id) -> tw to th
            tw > base.first && th <= base.second &&
                GridPanelItemLayout.isFree(current, item.x, item.y, base.first, th, ignoreId = item.id) -> base.first to th
            th > base.second && tw <= base.first &&
                GridPanelItemLayout.isFree(current, item.x, item.y, tw, base.second, ignoreId = item.id) -> tw to base.second
            else -> base
        }
        if (candidate != base) {
            // 档位切换给一颗触感（一墨 nz1 目标格变化同款 TEXT_HANDLE_MOVE）
            view.performHapticFeedback(HapticFeedbackConstants.TEXT_HANDLE_MOVE)
        }
        resizeSpan = candidate
    }

    fun commitResize() {
        val id = resizingId ?: return
        val span = resizeSpan ?: run { resizingId = null; return }
        val pageIndex = resizePage ?: pagerState.settledPage
        commitPageItems(
            pageIndex,
            pageItemsAt(pageIndex).map { if (it.id == id) it.copy(spanX = span.first, spanY = span.second) else it },
        )
        resizingId = null
        resizeSpan = null
        resizePage = null
    }

    fun commitAllItems(next: List<GridPanelItem>) {
        localItems = next
        lastWritten = next
        scope.launch {
            TuneConfig.updateAction { settings ->
                val latest = settings.actionGridPanels.panelById(panel.id) ?: return@updateAction settings
                settings.copy(actionGridPanels = settings.actionGridPanels.withPanel(latest.copy(items = next)))
            }
        }
    }

    // provider 被卸载 / 用户在系统里删掉小部件 → 宿主回调 onAppWidgetRemoved。
    // 那时格子会永远空白（AppWidgetHostView 拿不到 RemoteViews），所以要把这项收掉。
    DisposableEffect(panel.id) {
        val listener = WidgetGoneListener { goneId ->
            val left = localItems.filterNot {
                (it.kind as? GridPanelItem.Kind.AppWidget)?.appWidgetId == goneId
            }
            if (left.size != localItems.size) commitAllItems(left)
        }
        addWidgetGoneListener(listener)
        onDispose { removeWidgetGoneListener(listener) }
    }

    fun execute(action: StoredAction, screenX: Int, screenY: Int) {
        // 执行链要求 a11y 容器（子手势/刮擦/嵌套面板都靠它分类）。没开无障碍时
        // 转发失败：**别关面板也别装没事** —— 否则面板开着、点项却毫无反应。
        if (!dispatch(action, screenX, screenY)) {
            showToast(R.string.toast_accessibility_required)
            return
        }
        closeWithAnim()
    }

    // 根 Box：点按 + 长按空白进出编辑。
    //
    // 长按只收**空白**的事件：项的手势层（运行态 combinedClickable 的长按、编辑态
    // 手写手势）都会在触发时消费同一路事件，detectTapGestures 的 long-press 判定
    // 见 consumed 即取消 —— 所以想让根层长按只赢空白，不必缩小判定区域，
    // 消费链天然就是判定域。运行态长按小部件没人消费，会走到这里进编辑 ——
    // 这是编辑小部件的入口（不然没有别的路径能拖走它），保留。
    Box(
        Modifier
            .fillMaxSize()
            .pointerInput(editMode) {
                detectTapGestures(
                    onTap = { if (editMode) editMode = false else onClose() },
                    onLongPress = {
                        // 长按空白进编辑（一墨语义）。已被项消费的事件到不了这里，
                        // 所以这分支只在真正的空白按压上触发。
                        if (!editMode) {
                            view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                            editMode = true
                        }
                    },
                )
            },
    ) {
        Box(
            Modifier
                .offset { IntOffset(posAnimX.value.roundToInt(), posAnimY.value.roundToInt()) }
                .size(with(density) { cardW.toDp() }, with(density) { cardH.toDp() })
                .graphicsLayer {
                    alpha = appearance.value
                    scaleX = 0.92f + 0.08f * appearance.value
                    scaleY = scaleX
                }
                .background(
                    MaterialTheme.colorScheme.surface.copy(alpha = cardAlpha),
                    RoundedCornerShape(CardCorner),
                )
                .clip(RoundedCornerShape(CardCorner)),
        ) {
            Column(Modifier.fillMaxSize()) {
                GridPanelHeader(
                    editMode = editMode,
                    panelName = panel.name,
                    pageCount = pageCount,
                    activePage = pagerState.currentPage,
                    onSelectPage = { index -> scope.launch { pagerState.animateScrollToPage(index) } },
                    onDoneEdit = { editMode = false },
                    onAdd = {
                        // 加项落点交给落盘层决定（首个空位），这里不预选格子 ——
                        // 网格会随拖动变化，预选一个格反而可能正好被占。
                        val started = onPickItem(
                            ActionSelect(gridPanelId = panel.id, gridPanelPageIndex = pagerState.settledPage),
                        )
                        if (!started) showToast(R.string.toast_select_page_failed)
                    },
                )
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp),
                ) {
                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier.fillMaxSize(),
                    ) { pageIndex ->
                        val pageItems = remember(localItems, pageIndex) { pageItemsAt(pageIndex) }
                        // 滚动状态提到组合层：拖到网格上下沿时要能自动滚动，
                        // 而自动滚动必须由手势侧驱动，项内部拿不到这个 ScrollState。
                        val vScroll = remember { ScrollState(0) }
                        Box(Modifier.fillMaxWidth().verticalScroll(vScroll)) {
                            GridCanvas(
                                // 拖拽中渲染重排中的布局（其余项让位滑动），平时渲染落盘布局
                                items = if (draggingId != null && dragPage == pageIndex) dragOrderItems else pageItems,
                                columns = columns,
                                rows = rows,
                                cellSize = IntSize(cellW.roundToInt(), cellH.roundToInt()),
                                gapPx = gapPx,
                                editMode = editMode,
                                draggingId = draggingId,
                                hoverCell = hoverCell,
                                dragVisual = dragVisual,
                                glideFrom = glideFrom,
                                libraryEntries = libraryEntries,
                                onTapItem = { item, center ->
                                    if (!editMode) {
                                        // 动作在 Kind.Action.items 里（面板项只进不出，
                                        // 一项一个动作），小部件项没有可执行动作。
                                        val action = (item.kind as? GridPanelItem.Kind.Action)
                                            ?.items
                                            ?.firstOrNull()
                                        if (action != null) {
                                            execute(action.withoutVariantKey(), center.x, center.y)
                                        }
                                    }
                                    // 编辑态点项无语义（角标/手柄常显），手势层已消费，不会到这里。
                                },
                                onDeleteItem = { item ->
                                    // 小部件项被删 = 配置里不再引用这个 id，必须回收
                                    //（宿主具名资源，漏收会一直占槽位）。这不是组合销毁
                                    //（onRelease 不做回收，翻页/关面板只是视图走人）。
                                    (item.kind as? GridPanelItem.Kind.AppWidget)?.let {
                                        PanelAppWidgetHost.get().release(it.appWidgetId)
                                    }
                                    commitAllItems(localItems.filterNot { it.id == item.id })
                                },
                                onDragStartItem = { item ->
                                    glideFrom = null
                                    draggingId = item.id
                                    dragPage = pageIndex
                                    dragOrigCell = GridCell(item.x, item.y)
                                    dragVisual = Offset.Zero
                                    hoverCell = null
                                    // 重排初态 = 当前页原布局（首个 plan 之前保持原样渲染）
                                    dragOrderItems = pageItems
                                    view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                                },
                                onDrag = { amount ->
                                    // 视觉位移夹在网格内容区内：planReorder 只夹了**目标格**，
                                    // dragVisual 本身是无界累加的，不夹的话卡片能飞出面板外
                                    // 再被 CardCorner clip 切掉半截（看着像渲染错位）。
                                    // 单调钳制：只在越界那一侧生效，松手回原位时仍跟得回来。
                                    val activeId = draggingId ?: return@GridCanvas
                                    val orig = dragOrigCell ?: return@GridCanvas
                                    val stepW = cellW + gapPx
                                    val stepH = cellH + gapPx
                                    // 被拖项的 span 取重排表里的最新值（重排不改 span，
                                    // 但取表内值可避免依赖外部陈旧快照）。
                                    val moving = dragOrderItems.firstOrNull { it.id == activeId }
                                    val activeIdSpanX = moving?.spanX ?: 1
                                    val activeIdSpanY = moving?.spanY ?: 1
                                    val rawX = dragVisual.x + amount.x
                                    val rawY = dragVisual.y + amount.y
                                    val contentW = cellW * columns + gapPx * (columns - 1)
                                    val contentH = cellH * rows + gapPx * (rows - 1)
                                    val dragW = cellW * activeIdSpanX + gapPx * (activeIdSpanX - 1)
                                    val dragH = cellH * activeIdSpanY + gapPx * (activeIdSpanY - 1)
                                    dragVisual = Offset(
                                        rawX.coerceIn(
                                            -(orig.x * stepW),
                                            (contentW - dragW - orig.x * stepW).coerceAtLeast(0f),
                                        ),
                                        rawY.coerceIn(
                                            -(orig.y * stepH),
                                            (contentH - dragH - orig.y * stepH).coerceAtLeast(0f),
                                        ),
                                    )
                                    // 目标格 = 拖起格 + 跟指位移（dragVisual 是手势期内
                                    // change.position 的差分累加 —— 这套坐标在手指按下时
                                    // 定基，不随项自身的移动回卷，直接可用）。
                                    // **必须 floor**：`.toInt()` 对负数是向零截断，往左/往上拖时
                                    // 边界那一格会差一格（-0.5 被截成 0），手感上是一次跳变。
                                    val tx = floor((orig.x * stepW + dragVisual.x) / stepW).toInt()
                                    val ty = floor((orig.y * stepH + dragVisual.y) / stepH).toInt()
                                    // 只有目标格变了才重排（一墨逐帧 resolve 的收敛版），
                                    // 重排负结果维持现状 = 最近一次合法目标。
                                    if (hoverCell?.x != tx || hoverCell?.y != ty) {
                                        val plan = GridPanelItemLayout.planReorder(
                                            items = dragOrderItems,
                                            movingId = activeId,
                                            targetX = tx,
                                            targetY = ty,
                                            columns = columns,
                                            rows = rows,
                                        )
                                        if (plan != null) {
                                            hoverCell = plan.first { it.id == activeId }.let {
                                                GridCell(it.x, it.y)
                                            }
                                            dragOrderItems = plan
                                            view.performHapticFeedback(HapticFeedbackConstants.TEXT_HANDLE_MOVE)
                                        }
                                    }
                                },
                                onDragEnd = {
                                    val dropId = draggingId
                                    val orig = dragOrigCell
                                    val dropPage = dragPage
                                    val target = dragOrderItems.firstOrNull { it.id == dropId }
                                    if (dropId != null && orig != null && dropPage != null && target != null) {
                                        // 松手瞬间的视觉位 = 原基位 + 跟指位移；落格惯性 =
                                        // 视觉位 − 目标基位（Item 层从这个剩余 delta 滑到 0）。
                                        val targetBase = Offset(target.x * (cellW + gapPx), target.y * (cellH + gapPx))
                                        val visualNow = Offset(
                                            orig.x * (cellW + gapPx) + dragVisual.x,
                                            orig.y * (cellH + gapPx) + dragVisual.y,
                                        )
                                        glideFrom = dropId to (visualNow - targetBase)
                                        commitPageItems(dropPage, dragOrderItems)
                                    }
                                    draggingId = null
                                    dragVisual = Offset.Zero
                                    hoverCell = null
                                    dragOrigCell = null
                                    dragOrderItems = emptyList()
                                    dragPage = null
                                    autoScrollJob?.cancel()
                                    autoScrollJob = null
                                    edgeScrollTarget = null
                                },
                                // 边缘自动滚动（一墨 nz1 的边缘触发同款）：手指停在网格上下沿时
                                // 持续滚动，否则网格比可视区高时够不到下面的行。
                                //
                                // pos 是**项内**坐标，要先换算成「可视区坐标」再判边缘：
                                // 项的原位基线 + 项内偏移 = 网格内容坐标，再减去
                                // scroll.value 才是屏幕上真正的位置。
                                onDragPointer = { itemBaseY, pos ->
                                    val viewport = vScroll.viewportSize
                                    if (viewport > 0) {
                                        val bandPx = with(density) { EdgeScrollBand.toPx() }
                                        val viewportY = itemBaseY + pos.y - vScroll.value
                                        val direction = when {
                                            viewportY < bandPx -> -1
                                            viewportY > viewport - bandPx -> 1
                                            else -> 0
                                        }
                                        val maxScroll = vScroll.maxValue
                                        val stepPx = with(density) { EdgeScrollStep.toPx() }.roundToInt()
                                        if (direction == 0) {
                                            autoScrollJob?.cancel()
                                            autoScrollJob = null
                                            edgeScrollTarget = null
                                            edgeScrollDir = 0
                                        } else if (edgeScrollTarget !== vScroll || direction != edgeScrollDir) {
                                            // 只在方向/页变化时重启：按住不动必须持续滚，
                                            // 每帧重启协程反而会把滚动打断成一顿一顿的。
                                            autoScrollJob?.cancel()
                                            edgeScrollTarget = vScroll
                                            edgeScrollDir = direction
                                            autoScrollJob = scope.launch {
                                                while (true) {
                                                    val before = vScroll.value
                                                    vScroll.scrollTo(
                                                        (before + direction * stepPx)
                                                            .coerceIn(0, maxScroll),
                                                    )
                                                    if (vScroll.value == before) break // 顶到头了
                                                    delay(16L)
                                                }
                                            }
                                        }
                                    }
                                },
                                resizingId = resizingId,
                                resizeSpan = resizeSpan,
                                onResizeStart = { item ->
                                    resizingId = item.id
                                    resizeSpan = item.spanX to item.spanY
                                    resizePage = pageIndex
                                },
                                onResize = ::resizeItemTo,
                                onResizeEnd = { commitResize() },
                            )
                        }
                        // 空页提示。编辑态也要给：否则用户进编辑态只看到一片空网格，
                        // 不知道该点页眉那颗「+」—— 空态在这两种模式下要做的事不一样，
                        // 文案也跟着不一样。
                        if (pageItems.isEmpty()) {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text(
                                    text = stringResource(
                                        if (editMode) {
                                            R.string.grid_panel_empty_items_edit
                                        } else {
                                            R.string.grid_panel_empty_items
                                        },
                                    ),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * 页眉：编辑态显示「编辑中」+ 面板名 + 时间日期；多页时标题下方铺页指示点；
 * 非编辑态只显示面板名（编辑按钮不出现 —— 进编辑入口是**长按空白**，§10①）。
 *
 * 两态标题都不留空 —— 面板名没起时给「未命名」而不是空行，空标题看着像加载失败。
 *
 * 时间日期**不进网格**：它是纯 Compose 自绘的，AppWidget 却要常驻宿主、用户授权、
 * 跨进程存 id，塞进同一个模型会让每格都处理「有没有 appWidgetId」的分支。
 *
 * 编辑态右侧的「+」是**唯一的加项入口**（时间左边），不再往每个空格子塞加号。
 */
@Composable
private fun GridPanelHeader(
    editMode: Boolean,
    panelName: String,
    pageCount: Int,
    activePage: Int,
    onSelectPage: (Int) -> Unit,
    onDoneEdit: () -> Unit,
    onAdd: () -> Unit,
) {
    var now by remember { mutableStateOf(Calendar.getInstance()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000)
            now = Calendar.getInstance()
        }
    }
    val time = remember(now) { SimpleDateFormat("HH:mm", Locale.getDefault()).format(now.time) }
    val date = remember(now) {
        SimpleDateFormat("MM月dd日 EEEE", Locale.getDefault()).format(now.time)
    }
    Row(
        Modifier
            .fillMaxWidth()
            .height(HeaderHeight)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = when {
                    editMode -> stringResource(R.string.grid_panel_edit_mode)
                    panelName.isNotBlank() -> panelName
                    else -> stringResource(R.string.grid_panel_unnamed)
                },
                style = MaterialTheme.typography.labelLarge,
                color = if (editMode) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = date,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
        // 编辑态的「完成」按钮：长按空白进编辑后，这里作为显式出口兜底
        // （编辑态点空白也能退，但按钮给的是可发现性）。
        if (editMode) {
            IconButton(onClick = onDoneEdit, modifier = Modifier.size(40.dp)) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = stringResource(R.string.grid_panel_edit_done),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
            IconButton(onClick = onAdd, modifier = Modifier.size(40.dp)) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = stringResource(R.string.grid_panel_add_item),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
        Text(
            text = time,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
    // 页指示点（简版多页）：可点直达，与 HorizontalPager 的跟手翻页并行。
    // 固定 12dp 高，卡片高度预算（GridPanelRoot 的 cardH）按 pageCount>1 加同值。
    if (pageCount > 1) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(IndicatorHeight)
                .padding(horizontal = 14.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            repeat(pageCount) { index ->
                if (index > 0) {
                    Box(Modifier.width(6.dp))
                }
                val active = index == activePage
                Box(
                    Modifier
                        .size(7.dp)
                        .clip(RoundedCornerShape(50))
                        .background(
                            if (active) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
                            },
                        )
                        .clickable { onSelectPage(index) },
                )
            }
        }
    }
}

@Composable
private fun GridCanvas(
    items: List<GridPanelItem>,
    columns: Int,
    rows: Int,
    cellSize: IntSize,
    gapPx: Float,
    editMode: Boolean,
    draggingId: String?,
    hoverCell: GridCell?,
    dragVisual: Offset,
    /** 松手惯性：(itemId, 视觉残差 px)。项从这个剩余量滑向落格基位。 */
    glideFrom: Pair<String, Offset>?,
    libraryEntries: List<NewActionLibraryEntry>,
    onTapItem: (GridPanelItem, IntOffset) -> Unit,
    onDeleteItem: (GridPanelItem) -> Unit,
    onDragStartItem: (GridPanelItem) -> Unit,
    onDrag: (amount: Offset) -> Unit,
    onDragEnd: () -> Unit,
    /**
     * 拖拽期间的指针位置（项内坐标）。第一个参数是该项在网格内容里的基线 y ——
     * 边缘自动滚动要在**可视区**坐标里判上下沿，必须由项提供这个基线，
     * 只给项内偏移是判不出来的。传 null 则不接。
     */
    onDragPointer: ((itemBaseY: Float, position: Offset) -> Unit)? = null,
    /** 尺寸手柄（编辑态每项）：resizingId 之外显示实时 span，onResize(id 内坐标) → onResizeEnd 提交。 */
    resizingId: String?,
    resizeSpan: Pair<Int, Int>?,
    onResizeStart: (GridPanelItem) -> Unit,
    onResize: (GridPanelItem, itemX: Float, itemY: Float) -> Unit,
    onResizeEnd: () -> Unit,
) {
    val density = LocalDensity.current
    // 跨格项的左上角落在第几格：一维步进，跨格只是坐标乘倍数。
    val stepW = cellSize.width + gapPx
    val stepH = cellSize.height + gapPx
    Box(Modifier.fillMaxWidth()) {
        // 编辑态的空位：只用一层淡灰底，不加加号也不加虚线 ——
        // 加项入口是页眉那颗「+」，每格再放一个入口会让网格看着像菜单。
        // 非编辑态不画：运行态面板要的是干净，空白靠「空面板」文案说明。
        //
        // 这里用**一个 Canvas**而不是每格一个 Box：4×3 就是 12 个 Box，改成 6×8 就是
        // 48 个纯装饰节点；而我们只在**空格**上铺底（被占用的格子不需要底，画了也只
        // 是被项盖住的白费合成）。顺带把网格线一起画上，让列/行的对齐关系可见 ——
        // 跨格小部件的边界应该正好压在网格线上，用户才能确认它对齐了。
        if (editMode) {
            val occupied = HashSet<Int>(items.size * 4)
            for (it in items) {
                val n = it.normalized(columns, rows)
                for (gy in n.y until n.bottom) {
                    for (gx in n.x until n.right) occupied.add(gy * columns + gx)
                }
            }
            val idleColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f)
            val lineColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
            val hairline = with(density) { 1.dp.toPx() }
            val corner = with(density) { 14.dp.toPx() }
            Canvas(
                Modifier
                    .fillMaxWidth()
                    .size(
                        with(density) { (columns * stepW - gapPx).toDp() },
                        with(density) { (rows * stepH - gapPx).toDp() },
                    ),
            ) {
                for (gy in 0 until rows) {
                    for (gx in 0 until columns) {
                        if (gy * columns + gx in occupied) continue
                        drawRoundRect(
                            color = idleColor,
                            size = Size(cellSize.width.toFloat(), cellSize.height.toFloat()),
                            topLeft = Offset(gx * stepW, gy * stepH),
                            cornerRadius = CornerRadius(corner, corner),
                        )
                    }
                }
                // 网格线：只在空格区域内描边，已占用的格交给项自己的边界表达。
                for (gy in 0 until rows) {
                    for (gx in 0 until columns) {
                        if (gy * columns + gx in occupied) continue
                        drawRoundRect(
                            color = lineColor,
                            size = Size(cellSize.width.toFloat(), cellSize.height.toFloat()),
                            topLeft = Offset(gx * stepW + hairline / 2f, gy * stepH + hairline / 2f),
                            cornerRadius = CornerRadius(corner, corner),
                            style = Stroke(width = hairline),
                        )
                    }
                }
            }
        }
        for (raw in items) {
            // key 必须有：remember/Animatable/AndroidView 是按**组合位置**记忆的，
            // 没有它，重排换位后各项会拿到别项的动画状态和宿主视图。
            key(raw.id) {
                // 越界兑底：列数/行数被改小、或数据来自旧版本时，项可能落在网格外。
                // 这类项直接画会被 offset 到可视区之外，表现就是「加了小部件却什么都不见」。
                // 渲染时夹一次即可（数据本身由设置页改列数时重排落盘，见 GridPanelEditScreen）。
                val item = raw.normalized(columns, rows)
                val dragging = item.id == draggingId
                val resizing = item.id == resizingId
                // 实时 span：手柄拖拽期间盖在项上，松手才 commit。
                val spanX = if (resizing) resizeSpan?.first ?: item.spanX else item.spanX
                val spanY = if (resizing) resizeSpan?.second ?: item.spanY else item.spanY
                val itemW = cellSize.width * spanX + gapPx * (spanX - 1)
                val itemH = cellSize.height * spanY + gapPx * (spanY - 1)
                // 项的基位走 Animatable（一墨 settle 手感）：重排让位时其它项滑动到新位；
                // 被拖项自身不动画（跟指走 dragVisual 叠加），松手从 glideFrom 残差滑入落格。
                val glideStart = glideFrom?.takeIf { it.first == item.id }?.second
                val basePos = remember(item.id, glideStart) {
                    Animatable(
                        glideStart ?: Offset(item.x * stepW, item.y * stepH),
                        Offset.VectorConverter,
                    )
                }
                LaunchedEffect(item.x, item.y, dragging) {
                    val target = Offset(item.x * stepW, item.y * stepH)
                    if (!dragging && target != basePos.value) {
                        // 只 animateTo，不要跟一句 snapTo：snapTo 是同步跳变，会把
                        // 正在跑的插值直接拽到终点，整页让位就变成瞬移（一墨那套
                        // cascade 的滑动感全丢）。animateTo 结束本身就停在 target。
                        basePos.animateTo(
                            target,
                            spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow),
                        )
                    }
                }
                // 本项的指针回调 = 外层回调 + 注入本项当前视觉基线 y。基线必须走
                // basePos + 跟指位移：拖拽中项本身在动，用落盘的 item.y 会差一截，
                // 边缘判定会跟着偏。
                val pointerForItem: ((Offset) -> Unit)? = if (onDragPointer == null) {
                    null
                } else {
                    { pos ->
                        onDragPointer(
                            basePos.value.y + if (dragging) dragVisual.y else 0f,
                            pos,
                        )
                    }
                }
                Box(
                    Modifier
                        .offset {
                            IntOffset(
                                (basePos.value.x + if (dragging) dragVisual.x else 0f).roundToInt(),
                                (basePos.value.y + if (dragging) dragVisual.y else 0f).roundToInt(),
                            )
                        }
                        .size(with(density) { itemW.toDp() }, with(density) { itemH.toDp() })
                        .zIndex(if (dragging || resizing) 1f else 0f)
                        .graphicsLayer {
                            if (dragging) {
                                scaleX = 1.06f
                                scaleY = 1.06f
                            }
                        },
                ) {
                    // 拖拽中的中心十字参考线。吸附是「跳格」的，用户只看到卡片突然
                    // 换位，不确定对没对齐；给出格心参考，一眼能看出对齐与否。
                    // 画在内容之下（这是项 Box 的第一个子层），不遮内容、也不会
                    // 抢走点击 —— Canvas 不参与命中测试。
                    if (dragging) {
                        val crossColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f)
                        val hair = with(density) { 1.dp.toPx() }
                        Canvas(Modifier.fillMaxSize()) {
                            val cx = size.width / 2f
                            val cy = size.height / 2f
                            val arm = minOf(size.width, size.height) * 0.18f
                            drawLine(
                                color = crossColor,
                                start = Offset(cx - arm, cy),
                                end = Offset(cx + arm, cy),
                                strokeWidth = hair,
                            )
                            drawLine(
                                color = crossColor,
                                start = Offset(cx, cy - arm),
                                end = Offset(cx, cy + arm),
                                strokeWidth = hair,
                            )
                        }
                    }
                    when (val kind = item.kind) {
                        is GridPanelItem.Kind.Action -> {
                            val action = kind.items.firstOrNull()
                            if (action == null) {
                                // 数据残缺（理论上 saveGridPanelItem 不会写出这种项）
                                Box(
                                    Modifier
                                        .fillMaxSize()
                                        .background(
                                            MaterialTheme.colorScheme.errorContainer,
                                            RoundedCornerShape(14.dp),
                                        ),
                                )
                            } else {
                                GridItemCell(
                                    action = action,
                                    label = actionText(
                                        action,
                                        emptyIfNone = false,
                                        actionLibraryEntries = libraryEntries,
                                    ),
                                    editMode = editMode,
                                    dragging = dragging,
                                    onTap = { center -> onTapItem(item, center) },
                                    onDragStart = { onDragStartItem(item) },
                                    onDrag = onDrag,
                                    onDragEnd = onDragEnd,
                                    onDragPointer = pointerForItem,
                                    modifier = Modifier.fillMaxSize(),
                                )
                            }
                        }

                        is GridPanelItem.Kind.AppWidget -> {
                            // 这一项实际拿到的 dp 尺寸。**必须用含 gap 的真实像素换算**：
                            // 早期版本按 cellDp × span 下发，漏掉了跨格间的 gap
                            // （3 格就少 2×gap ≈ 12dp），provider 按偏小的尺寸画，
                            // 内容被裁掉右/下边缘。实时 span 变化也走这里。
                            val maxWidthDp = (itemW / density.density).roundToInt()
                            val maxHeightDp = (itemH / density.density).roundToInt()
                            // 加载态由宿主视图在 createView 的异步路径上回调，组合侧
                            // 用remember 存下来；key 用 appWidgetId，换绑会重置。
                            var widgetState by remember(kind.appWidgetId) {
                                mutableStateOf(WidgetLoadState.Loading)
                            }
                            val stateListener: (WidgetLoadState) -> Unit = {
                                widgetState = it
                            }
                            AndroidView(
                                factory = { ctx -> PanelAppWidgetView(ctx) },
                                update = { hostView ->
                                    // 监听要每帧重挂：AndroidView 的 update lambda 会随
                                    // 重组换新实例，宿主持旧闭包就收不到状态了。
                                    hostView.setLoadStateListener(stateListener)
                                    hostView.updateWidget(
                                        kind.appWidgetId,
                                        kind.provider,
                                        maxWidthDp,
                                        maxHeightDp,
                                    )
                                },
                                // 故意不给 onRelease：AndroidView 离开组合（翻页/关面板）≠ 删小部件，
                                // 在这里回收 id 会把还配置着的项杀掉（回 bind 不到 → 被
                                // WidgetGone 收掉，真机上就是「翻个页小部件就没了」）。
                                // 真正的回收点只有两处：本页 onDeleteItem、设置页缩格丢弃。
                                modifier = Modifier.fillMaxSize(),
                            )
                            // 底衬按状态分层：Ready 不垫底色（provider 自带背景，垫一层
                            // 会出现双层底 + 圆角对不齐）；Loading / Failed 才需要我们自己
                            // 画一层，好让「在加载」和「加载不出来」不再都是一块空白。
                            if (widgetState != WidgetLoadState.Ready) {
                                val failed = widgetState == WidgetLoadState.Failed
                                Box(
                                    Modifier
                                        .fillMaxSize()
                                        .background(
                                            if (failed) {
                                                MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f)
                                            } else {
                                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                                            },
                                            RoundedCornerShape(14.dp),
                                        )
                                        .clip(RoundedCornerShape(14.dp)),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    if (failed) {
                                        Text(
                                            text = stringResource(R.string.grid_panel_widget_failed),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    } else {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(18.dp),
                                            strokeWidth = 2.dp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            }
                            // 小部件的点击由 provider 的 PendingIntent 自己处理（决策：
                            // 不过 Luno 手势分发链）。但编辑态要能拖动、也不能把点按
                            // 漏给根层当「点空白退出编辑」，所以盖一层编辑手势
                            //（消费点按、长按转拖拽）。
                            if (editMode) {
                                Box(
                                    Modifier
                                        .fillMaxSize()
                                        .editItemGestures(
                                            onDragStart = { onDragStartItem(item) },
                                            onDrag = onDrag,
                                            onDragEnd = onDragEnd,
                                            onDragPosition = pointerForItem,
                                        ),
                                )
                            }
                        }
                    }

                    // 编辑态的操作角标：删除（右上）+ 尺寸手柄（右下），**常显**——
                    // 不做「先点选中再出角标」的二段式，进编辑态一眼看到所有可操作件。
                    // 都是项的**子层**且排在最后 —— 子层先于父项的 pointerInput 拿到事件，
                    // 否则长按判定会被项的拖拽手势先消费掉（手柄拖拽消费后，父项手势在
                    // awaitEachGesture 里见 change.isConsumed 即退出，不会误报 tap）。
                    if (editMode) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = stringResource(R.string.grid_panel_item_menu_delete),
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(2.dp)
                                .size(20.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.75f))
                                .clickable { onDeleteItem(item) },
                        )
                        // 右下角尺寸手柄。命中区 44dp（无障碍下限），视觉件 26dp 半透明
                        //（一墨侧边栏把手 ic_resize_handle 36dp alpha 0.4 的量级）——
                        // 手感大、存在感小，不抢内容。拖动实时改 span（只让进空位，
                        // 碰撞即停），松手落盘；小部件项随新 span 重新协商 options。
                        val handlePad = with(density) { 2.dp.toPx() }
                        val handleHit = with(density) { 44.dp.toPx() }
                        val liveItemW by rememberUpdatedState(itemW)
                        val liveItemH by rememberUpdatedState(itemH)
                        val resizeStartRef by rememberUpdatedState(onResizeStart)
                        val resizeRef by rememberUpdatedState(onResize)
                        val resizeEndRef by rememberUpdatedState(onResizeEnd)
                        Box(
                            Modifier
                                .align(Alignment.BottomEnd)
                                .padding(2.dp)
                                .size(44.dp)
                                .pointerInput(item.id) {
                                    detectDragGestures(
                                        onDragStart = { resizeStartRef(item) },
                                        onDrag = { change, _ ->
                                            change.consume()
                                            // 指针（手柄本地系）→ 项内坐标：手柄贴项右下角
                                            //（外沿 2dp），拖拽起点≈把手中心。实时 span
                                            // 变化会让 itemW/H 增长，走 updated ref 取最新。
                                            resizeRef(
                                                item,
                                                liveItemW - handlePad - handleHit / 2 + change.position.x,
                                                liveItemH - handlePad - handleHit / 2 + change.position.y,
                                            )
                                        },
                                        onDragEnd = { resizeEndRef() },
                                        onDragCancel = { resizeEndRef() },
                                    )
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            Box(
                                Modifier
                                    .size(26.dp)
                                    .clip(RoundedCornerShape(9.dp))
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    imageVector = Icons.Default.GridView,
                                    contentDescription = stringResource(R.string.grid_panel_item_resize),
                                    tint = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.size(14.dp),
                                )
                            }
                        }
                    }
                }
            }
        }

        // 拖拽落位预览：把落点高亮出来，让「放得下」可见。
        // zIndex 必须低于被拖项（1f）：早期版本给 2f，结果这层半透明色块正好糊在
        // 正在被拖的那张卡上，用户既看不清内容也看不清落点。放在普通项（0f）之上、
        // 拖拽项之下，才是对「空位」的高亮。
        if (hoverCell != null) {
            val dragging = items.firstOrNull { it.id == draggingId }
            val spanX = dragging?.spanX ?: 1
            val spanY = dragging?.spanY ?: 1
            val previewW = cellSize.width * spanX + gapPx * (spanX - 1)
            val previewH = cellSize.height * spanY + gapPx * (spanY - 1)
            val stroke = 2.dp
            Box(
                Modifier
                    .offset {
                        IntOffset(
                            (hoverCell.x * stepW).roundToInt(),
                            (hoverCell.y * stepH).roundToInt(),
                        )
                    }
                    .size(
                        with(density) { previewW.toDp() },
                        with(density) { previewH.toDp() },
                    )
                    .zIndex(0.5f)
                    // 主题色而不是硬编码绿色：面板配色可随主题/壁纸变化，写死的绿
                    // 在浅色主题下会突兀，也会和「错误色」语义混淆。
                    .background(
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
                        RoundedCornerShape(14.dp),
                    )
                    .border(
                        stroke,
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.55f),
                        RoundedCornerShape(14.dp),
                    ),
            )
        }
    }
}

/** [editItemGestures] 等待期的结局：抬手 = 点按，超时 = 长按，其余放弃。 */
private enum class ItemGestureOutcome { TAP, GIVE_UP }

/**
 * 编辑态的项手势（动作格与小部件覆盖层共用）：
 * - 短点：**只消费不给语义** —— 角标/手柄常显后点项无事可做，但必须吃掉这次抬手，
 *   否则会漏给根层判成「点空白退出编辑」；
 * - 长按后拖动：拖拽落位（跟指位移回调 [onDrag]，见 GridPanelRoot 的目标格换算）；
 * - 长按前滑动 / 事件被子层（手柄）或父层（翻页、滚动）消费：放弃，谁消费谁处理。
 *
 * 为什么手写而不用 detector 组合：tap 与「长按后拖」的优先级要显式表达
 * （先到长按超时算拖、超时前抬手算点），`detectDragGesturesAfterLongPress` +
 * `detectTapGestures` 两个 pointerInput 会互相抢先，之前版本踩过「拖不动」。
 *
 * 为什么用 `withTimeoutOrNull`（AwaitPointerEventScope 的成员）而不是
 * kotlinx 的 `withTimeout`：后者超时是抛 TimeoutCancellationException，
 * 会把 pointerInput 协程整个杀掉（这个项的手势从此死透），成员版才是
 *「超时返回 null」的手势语义。
 */
@Composable
private fun Modifier.editItemGestures(
    onDragStart: () -> Unit,
    onDrag: (amount: Offset) -> Unit,
    onDragEnd: () -> Unit,
    /**
     * 拖拽期间每帧回报指针的**绝对位置**（项内坐标）。边缘自动滚动要靠它判断
     * 「手指到了网格上下沿」，而 [onDrag] 只给差分量、拿不到绝对位置。
     * 传 null 表示不需要（默认，省掉一次闭包调用）。
     */
    onDragPosition: ((position: Offset) -> Unit)? = null,
): Modifier {
    val view = LocalView.current
    val viewConfiguration = LocalViewConfiguration.current
    val dragStartRef by rememberUpdatedState(onDragStart)
    val dragRef by rememberUpdatedState(onDrag)
    val dragEndRef by rememberUpdatedState(onDragEnd)
    val dragPosRef = rememberUpdatedState(onDragPosition)
    return pointerInput(Unit) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            var upForTap: androidx.compose.ui.input.pointer.PointerInputChange? = null
            // 结局在块内 return；超时（=长按成立）拿到 null。
            val outcome = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                var result = ItemGestureOutcome.GIVE_UP
                while (true) {
                    val event = awaitPointerEvent()
                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                    when {
                        // 手柄等子层已消费 = 这根手指不归我们管。
                        change.isConsumed -> break
                        // 超时前抬手 = 点按（记下 change，出去补消费）。
                        !change.pressed -> {
                            upForTap = change
                            result = ItemGestureOutcome.TAP
                            break
                        }
                        // 长按前就滑出 slop = 放弃（父层翻页/滚动接管）。
                        (change.position - down.position).getDistance() > viewConfiguration.touchSlop -> break
                    }
                }
                result
            }
            when (outcome) {
                ItemGestureOutcome.TAP -> {
                    // 消费抬手，阻断根层的「点空白」判定。
                    upForTap?.consume()
                }

                ItemGestureOutcome.GIVE_UP -> Unit

                // null = 超时：长按成立，转拖拽（保持手指按下，之后位移即落位）。
                null -> {
                    dragStartRef()
                    dragPosRef.value?.invoke(down.position)
                    // position 在手势期内按下时定基：这里逐事件做差分就是真实指移，
                    // 不会被项自身跟指移动回卷。
                    var last = down.position
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (change.isConsumed) {
                            dragEndRef()
                            return@awaitEachGesture
                        }
                        if (!change.pressed) break
                        val delta = change.position - last
                        last = change.position
                        // 位置先报：即使这一帧位移为零（手指按住不动），边缘滚动
                        // 也要继续生效，所以不能挂在 `delta != Zero` 分支里。
                        dragPosRef.value?.invoke(change.position)
                        if (delta != Offset.Zero) {
                            change.consume()
                            dragRef(delta)
                        }
                    }
                    dragEndRef()
                }
            }
        }
    }
}

/**
 * 单个动作格。运行态：点=执行主动作，长按=只触发触感并**消费**事件
 *（事件被消费后根 Box 的长按空白进不了编辑态 —— 按着项长按唤起编辑会误导）；
 * 编辑态：手势全在 [editItemGestures]（点按吞掉、长按拖拽落位）。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun GridItemCell(
    modifier: Modifier = Modifier,
    action: StoredAction,
    label: String,
    editMode: Boolean,
    dragging: Boolean,
    onTap: (IntOffset) -> Unit,
    onDragStart: () -> Unit,
    onDrag: (amount: Offset) -> Unit,
    onDragEnd: () -> Unit,
    /** 已由调用方注入好本项基线 y 的指针回调（见 GridCanvas 的 pointerForItem）。 */
    onDragPointer: ((Offset) -> Unit)? = null,
) {
    val view = LocalView.current
    var center by remember { mutableStateOf(IntOffset.Zero) }
    val interactionModifier = if (editMode) {
        Modifier.editItemGestures(
            onDragStart = onDragStart,
            onDrag = onDrag,
            onDragEnd = onDragEnd,
            onDragPosition = onDragPointer,
        )
    } else {
        // 运行态长按：没有变体语义了，但必须保留 onLongClick 让 combinedClickable
        // 把长按消费掉 —— 否则长按项会漏到根 Box 的长按判定，误触「进编辑态」。
        Modifier.combinedClickable(
            onClick = { onTap(center) },
            onLongClick = {
                view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            },
        )
    }

    Column(
        modifier = modifier
            .onGloballyPositioned { coords ->
                // 屏幕坐标（非窗口坐标）：本窗口原点会被 correctWindowOrigin 按 ROM
                // 实测修正，窗口坐标与屏幕坐标未必同原点，转发的锚点要的是屏幕坐标。
                center = IntOffset(
                    coords.positionOnScreen().x.roundToInt() + coords.size.width / 2,
                    coords.positionOnScreen().y.roundToInt() + coords.size.height / 2,
                )
            }
            .background(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                shape = RoundedCornerShape(14.dp),
            )
            .padding(vertical = 10.dp, horizontal = 4.dp)
            .then(interactionModifier)
            .alpha(if (dragging) 0.9f else 1f),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        GridItemIcon(action = action, iconSize = 36.dp)
        if (label.isNotEmpty()) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

@Composable
private fun GridItemIcon(action: StoredAction, iconSize: Dp) {
    val model = actionIcon(action = action)
    when (model) {
        is ImageVector -> Icon(
            imageVector = model,
            contentDescription = null,
            modifier = Modifier.size(iconSize),
            tint = MaterialTheme.colorScheme.onSurface,
        )

        is TextIcon -> TextIconGlyph(
            text = model.text,
            size = iconSize,
            color = MaterialTheme.colorScheme.onSurface,
        )

        else -> AsyncImage(
            model = model,
            contentDescription = null,
            modifier = Modifier.size(iconSize),
            imageLoader = LocalContext.current.imageLoader,
        )
    }
}
