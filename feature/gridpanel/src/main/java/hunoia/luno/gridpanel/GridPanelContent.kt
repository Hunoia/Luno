package hunoia.luno.gridpanel

import android.os.SystemClock
import hunoia.luno.core.LunoLog as Log
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import hunoia.luno.gridpanel.ui.PanelDockAnchor
import hunoia.luno.gridpanel.ui.PanelDockGap
import hunoia.luno.gridpanel.ui.PanelDockHeight
import hunoia.luno.gridpanel.ui.PanelDockLayer
import hunoia.luno.gridpanel.ui.PanelDockPadding
import hunoia.luno.gridpanel.ui.PanelDockSession
import hunoia.luno.gridpanel.ui.PanelDockTabMaxWidth
import hunoia.luno.gridpanel.ui.PanelDockTabMinWidth
import hunoia.luno.gridpanel.R
import hunoia.luno.ui.component.panel.awaitPanelExitFrame
import hunoia.luno.core.domain.StoredAction
import hunoia.luno.core.domain.gridPanelId
import hunoia.luno.core.domain.withoutVariantKey
import hunoia.luno.bridge.ui.showToast
import hunoia.luno.bridge.ui.systemBarsSafeArea
import hunoia.luno.bridge.ui.windowManager
import hunoia.luno.config.model.coerced
import hunoia.luno.config.model.EditRowExtra
import hunoia.luno.config.model.GridCell
import hunoia.luno.config.model.GridPanel
import hunoia.luno.config.model.GridPanelDefaults
import hunoia.luno.config.model.GridPanelGeometry
import hunoia.luno.config.model.GridPanelItem
import hunoia.luno.config.model.GridPanelItemLayout
import hunoia.luno.config.model.GridPanelOrientation
import hunoia.luno.config.model.withOrientation
import hunoia.luno.gridpanel.core.GridPanelState
import hunoia.luno.gridpanel.geometry.GridMetrics
import hunoia.luno.gridpanel.geometry.ResizeCorner
import hunoia.luno.gridpanel.geometry.GridPadding
import hunoia.luno.gridpanel.geometry.IndicatorHeight
import hunoia.luno.gridpanel.geometry.gapFor
import hunoia.luno.gridpanel.geometry.gridPanelCardHeight
import hunoia.luno.gridpanel.geometry.keepCardHeightFraction
import hunoia.luno.gridpanel.geometry.rememberCardCornerPx
import hunoia.luno.gridpanel.geometry.rememberGridMetrics
import hunoia.luno.gridpanel.geometry.planGridResize
import hunoia.luno.gridpanel.geometry.resolvePanelDockPlacement
import hunoia.luno.gridpanel.geometry.resolvePanelResize
import hunoia.luno.ui.theme.panelSpec
import hunoia.luno.ui.theme.tween
import hunoia.luno.gridpanel.ui.GridItemActions
import hunoia.luno.gridpanel.widget.WidgetGoneListener
import hunoia.luno.gridpanel.widget.addWidgetGoneListener
import hunoia.luno.gridpanel.widget.removeWidgetGoneListener
import hunoia.luno.bridge.ui.hapticLongPress
import hunoia.luno.bridge.ui.hapticMove
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.launch


/** 点空白退出编辑后，这段时间内的项点按不执行（防连点误触）。 */
private const val ExitEditTapGuardMillis = 150L

private const val TAG = "GridPanel"

/**
 * 面板根：网格面板是固定网格 + 跨格项 + 简版多页；单功能面板通过 [singleContent] 注入整页功能。
 * 运行态点项执行、长按空白进编辑；编辑态显示删除角标与尺寸手柄、可拖拽落位。
 * 单功能编辑态只保留面板缩放手柄，不再有换功能入口。
 * 本函数只管生命周期与落盘：几何在 [rememberGridMetrics]，拖拽/缩放在
 * [GridPanelState]，渲染在 GridPanelCard / GridPanelLayer。
 */
@Composable
fun GridPanelRoot(
    /** 面板 id；与 [panel] 分开传：panel 短暂为 null（配置重读）时状态键不能跟着变。 */
    panelId: String,
    panel: GridPanel?,
    anchorX: Int,
    anchorY: Int,
    dispatch: (action: StoredAction, screenX: Int, screenY: Int) -> Boolean,
    /** 跳动作选择页；返回 false 表示没跳起来，调用方负责把面板放回可见。 */
    onPickItem: (GridPanelItemPick) -> Boolean,
    onClose: () -> Unit,
    onCloseAnimated: () -> Unit,
    onRegisterCloseAnimated: ((() -> Unit) -> Unit)?,
    /** 注册返回键处理器：组合先行消费（编辑态退编辑），未消费由浮窗关闭。 */
    onRegisterBackHandler: (((() -> Boolean)) -> Unit)? = null,
    /** 非 null = 单功能面板内容；参数为卡片几何与当前编辑态。 */
    singleContent: (@Composable (GridMetrics, Boolean) -> Unit)? = null,
    /** 单功能面板的几何落盘口：网格走默认的 actionGridPanels 更新。 */
    onCommitGeometry: ((hunoia.luno.config.model.GridPanelGeometry) -> Unit)? = null,
    /**
     * 底栏成员（已解析、已过滤悬空）。**空 = 不显示底栏**：连可用高度都不扣，
     * 面板定位与没有这个功能时完全一致。可见性判定在 [PanelDockCatalog]。
     */
    dockEntries: List<PanelDockEntry> = emptyList(),
    /** 点底栏 tab：由浮层换内容，不重开面板。 */
    onSwitchPanel: (String) -> Unit = {},
) {
    // 进出场用一张 Animatable 管进度（位移/缩放/alpha 在 Card 里派生）：语义可证伪，
    // 不依赖服务进程里 animate*AsState 的收敛。时长直接传 MotionPanel*：Compose 的
    // Recomposer 已把系统动画时长缩放注入 MotionDurationScale，tween 会自动跟随；
    // 生产代码不得再自己读设置乘一遍（会双乘：0.5x 下 140ms → 35ms，退场只剩两帧，
    // 表现就是「缩一下直接消失」）。系统关闭动画时 Compose 在首帧落位（ui-guidelines §5.2）。
    val appearance = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    var closing by remember { mutableStateOf(false) }
    // 家族 spec 在组合期取一次（@Composable），effect 里只做时长换算。
    val panelMotion = panelSpec()
    LaunchedEffect(Unit) {
        appearance.animateTo(1f, panelMotion.tween())
    }
    fun closeWithAnim() {
        if (closing) return
        closing = true
        scope.launch {
            appearance.animateTo(0f, panelMotion.tween())
            // 让 alpha 归零的那一帧真正画出来再拆窗（真机表现是「缩到最后直接关」）。
            awaitPanelExitFrame()
            onCloseAnimated()
        }
    }
    LaunchedEffect(Unit) { onRegisterCloseAnimated?.invoke { closeWithAnim() } }

    // 横竖屏分别取几何与部件：转屏后面板显示另一朝向的独立布局。
    val landscape = LocalConfiguration.current.orientation ==
        android.content.res.Configuration.ORIENTATION_LANDSCAPE

    // ---- 循环翻页状态（必须在 panel == null 分支之前声明：配置重读时 panel 可能
    // 闪一帧 null，状态若声明在其后会被整体丢弃 —— pager 回到初始页、editMode
    // 丢失，表现为「虚拟页加项返回后回到前一页」）。----
    // panel 短暂为 null 时沿用上一次有效页数：否则 pageCount 会闪成 1，
    // 映射效果把 pager 重锚到低位，回来后当前页和循环起点都丢了。
    var lastKnownPageCount by remember(panelId) {
        mutableIntStateOf(panel?.orientation(landscape)?.derivedPageCount ?: 1)
    }
    val pageCount = panel?.orientation(landscape)?.derivedPageCount ?: lastKnownPageCount
    LaunchedEffect(pageCount) {
        if (panel != null) lastKnownPageCount = pageCount
    }
    var editMode by remember(panelId) { mutableStateOf(false) }
    // 循环翻页：pager 页数恒为极大值（运行态与编辑态一致，避免页数切换引发
    // clamp/闪跳）。真实页 = index % 有效页数；编辑态有效页数 +1（末尾虚页：
    // index ≡ 派生页数 即虚页，落项建页）。loopBase 为页数倍数，初始落在真实第 0 页。
    val loopBase = remember(panelId) { 1_000_000 - 1_000_000 % pageCount }
    // 循环映射：真实页 = index % mappingPages（编辑态 +1，末尾虚页 ≡ 派生页数）。
    // mappingPages 滞后于 editMode/pageCount：目标映射生效前先把索引 snap 到新旧映射下
    // 真实页相同的等价点（n 与 n±1 互素，周期 n(n±1) 内必有解），切换后真实页不漂移。
    val effectivePages = pageCount + if (editMode) 1 else 0
    var mappingPages by remember(panelId) { mutableIntStateOf(pageCount) }
    // pager 页数恒为极大值：页数若真的收缩到 1，PagerState 会把高位的循环索引钳回
    // 0，之后再进编辑态右滑（索引减）就滑不动了。单页不滑改用 userScrollEnabled。
    val pagerState = rememberPagerState(initialPage = loopBase) { Int.MAX_VALUE }
    LaunchedEffect(pageCount, editMode) {
        if (panel == null) return@LaunchedEffect
        if (effectivePages != mappingPages) {
            val cur = pagerState.currentPage
            // 页数一次跳 2+（外部整批替换配置）时新旧模数不互素，等价点可能不存在：
            // 直接落真实页，但要落在高位锚点附近 —— 落到 0..n-1 会丢掉循环空间，
            // 之后再往左滑就停在第一页，指示点也可能算出负索引。
            val jump = kotlin.math.abs(effectivePages - mappingPages)
            val real = cur.mod(mappingPages).coerceAtMost(effectivePages - 1) // 退出编辑落在虚页时归末页
            if (jump > 1) {
                val anchor = 1_000_000 - 1_000_000 % effectivePages
                Log.i(TAG, "page mapping jump $mappingPages->$effectivePages, snap to real=$real")
                pagerState.scrollToPage(anchor + real)
            } else {
                val period = mappingPages * effectivePages
                val step = ((cur - real).toDouble() / period).roundToInt()
                pagerState.scrollToPage((real + period * step).coerceAtLeast(0))
            }
            Log.d(
                TAG,
                "page mapping $mappingPages->$effectivePages cur=$cur real=$real " +
                    "-> index=${pagerState.currentPage} edit=$editMode",
            )
            mappingPages = effectivePages
        }
    }
    // 就近等价索引：离当前位置最近的、真实页为 real 的 pager 索引，避免跨百万页滚动。
    fun nearestPageIndexOf(real: Int): Int {
        val cur = pagerState.settledPage
        val base = cur - cur.mod(mappingPages) + real
        return listOf(base - mappingPages, base, base + mappingPages)
            .filter { it >= 0 }
            .minBy { abs(it - cur) }
    }
    // 退出编辑态：只翻 editMode，映射收缩交给上面的 LaunchedEffect 按当前 pageCount
    // 收尾（配置回流前会沿用上次有效页数，避免把新页误判成虚页）。不能在这里按本地
    // pageCount 预 snap：刚在虚页落项时配置回流有延迟，会把「已建的新页」误 snap 回
    // 上一页。新页未建时的代价是收缩帧闪一下空虚页，可接受。
    fun exitEditMode() {
        Log.i(TAG, "exitEditMode: cur=${pagerState.settledPage} mapping=$mappingPages pageCount=$pageCount")
        editMode = false
    }

    if (panel == null) {
        // 执行层已拦空面板；能到这里只有「打开后面板被删」的极端路径。给个落点不静默。
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.32f * appearance.value))
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

    // 横竖屏分别取几何：尺寸拖拽期间用本地预览，松手才落盘。
    val orientation = panel.orientation(landscape)
    val serverGeometry = orientation.geometry
    var localGeometry by remember(panelId, landscape) { mutableStateOf(serverGeometry) }
    LaunchedEffect(serverGeometry) { localGeometry = serverGeometry }
    val geo = localGeometry
    val baseColumns = geo.columnCount.coerceIn(
        GridPanelDefaults.MinColumnCount,
        GridPanelDefaults.MaxColumnCount,
    )
    // 双指调列数：手势期间只改本地预览（列数 + 非破坏性重排），松手才落盘。
    var pinchColumns by remember(panelId, landscape) { mutableStateOf<Int?>(null) }
    val columns = (pinchColumns ?: baseColumns).coerceIn(
        GridPanelDefaults.MinColumnCount,
        GridPanelDefaults.MaxColumnCount,
    )
    // 底栏在打开面板时冻结（位置 / 外观 / 卡片宽度），之后切换面板不重算。
    // 状态必须声明在 cardFraction 之前：冻结后的面板宽度要反过来参与几何计算。
    var dockSession by remember { mutableStateOf<PanelDockSession?>(null) }
    // 底栏一关闭（总开关关 / 成员不足）就丢掉冻结值：重新开启时重新按当前面板定位。
    LaunchedEffect(dockEntries.isEmpty()) {
        if (dockEntries.isEmpty()) dockSession = null
    }
    val activeDockSession = dockSession.takeIf { dockEntries.isNotEmpty() }
    // 有底栏时统一用打开那一刻的面板宽度：切换面板只换内容不换宽度，
    // 否则每切一次卡和底栏都会重新对齐一次。
    val cardFraction = (activeDockSession?.cardWidthFraction ?: geo.cardWidthFraction)
        .coerceIn(
            GridPanelDefaults.MinCardWidthFraction,
            GridPanelDefaults.MaxCardWidthFraction,
        )
    val heightFraction = geo.cardHeightFraction
        .coerceIn(GridPanelDefaults.MinCardHeightFraction, GridPanelDefaults.MaxCardHeightFraction)
    val cardAlpha = panel.cardAlpha.coerceIn(GridPanelDefaults.MinCardAlpha, GridPanelDefaults.MaxCardAlpha)

    // 项列表的本地真身：拖拽要连续多次落位，DataStore 回流有延迟，直接渲染
    // panel.items 会在快速拖动时被旧快照回跳。回流同步只认「与 lastWritten
    // 不同」的变更（自己写出去的回声不覆盖本地）。
    var localItems by remember(panelId, landscape) { mutableStateOf(orientation.items) }
    var lastWritten by remember(panelId, landscape) { mutableStateOf(orientation.items) }
    LaunchedEffect(orientation.items) {
        if (orientation.items != lastWritten) {
            localItems = orientation.items
            lastWritten = orientation.items
        }
    }

    /** 当前朝向面板的落盘单口：读最新配置、只改本朝向、统一失败日志。 */
    fun updatePanel(
        failureTag: String,
        cleanupResources: Boolean = false,
        transform: (GridPanelOrientation) -> GridPanelOrientation,
    ) {
        scope.launch {
            runCatching {
                hunoia.luno.domain.gridpanel.GridPanelCenter.updateActionSettings { settings ->
                    val latest = settings.actionGridPanels.panelById(panel.id)
                        ?: return@updateActionSettings settings
                    settings.copy(
                        actionGridPanels = settings.actionGridPanels.withPanel(
                            latest.withOrientation(landscape, transform),
                        ),
                    )
                }
            }.onFailure { Log.w(TAG, "$failureTag: ${it.message}") }
            if (cleanupResources) PanelResources.cleanup()
        }
    }

    fun commitPageItems(pageIndex: Int, next: List<GridPanelItem>) {
        val rebuilt = localItems.filter { it.pageIndex != pageIndex } + next
        localItems = rebuilt
        lastWritten = rebuilt
        updatePanel("grid panel commitPage failed") { it.copy(items = rebuilt) }
    }

    fun commitAllItems(next: List<GridPanelItem>, cleanupResources: Boolean = false) {
        localItems = next
        lastWritten = next
        updatePanel("grid panel commitAll failed", cleanupResources) { it.copy(items = next) }
    }

    /**
     * 底栏切面板前先把本地改动落盘：换内容会重建本函数的全部状态（editMode / localItems /
     * pager），没落盘的拖拽与删除会静默丢失。无改动时不写盘，避免每切一次都发一次 DataStore。
     */
    fun commitBeforeSwitch() {
        if (localItems != lastWritten) {
            updatePanel("panel dock switch commit failed") { it.copy(items = localItems) }
        }
    }

    val view = LocalView.current
    // 走 LocalConfiguration 而不是 displayMetrics：组合必须订阅配置变化才会在转屏后
    // 重算（只读 metrics 的组合不会重组）。
    val configuration = LocalConfiguration.current
    // 系统栏安全区与悬浮球同源（:core:bridge）：只避让可见状态栏 / 导航栏，左右贴物理边。
    // 按 configuration 记忆：转屏（导航栏换边）后不能沿用首帧的值。
    // 声明在 metrics 之前：底栏预留要当“额外的底部边距”算进可用高度。
    val barSafeArea = remember(configuration) { view.context.windowManager().systemBarsSafeArea() }
    val insetsTop = barSafeArea.top
    val insetsBottom = barSafeArea.bottom
    // 提前取渲染几何常量：metrics 声明在 commitPinchColumns 之后，而保持卡高的反推
    // 需要这些不随列数变化的量（screen/gridPad/indicator 固定，gap 随列数另取）。
    val density = LocalDensity.current
    // 模糊半径按面板配置转 px；0 表示关闭。隐藏 API 不可用时由 UI 层静默降级。
    val blurRadiusPx = with(density) {
        panel.blurRadiusDp
            .coerceIn(GridPanelDefaults.MinBlurRadiusDp, GridPanelDefaults.MaxBlurRadiusDp)
            .dp.toPx().roundToInt()
    }
    val renderScreenW = with(density) { configuration.screenWidthDp.dp.toPx() }
    val renderScreenH = with(density) { configuration.screenHeightDp.dp.toPx() }
    val renderGridPad = with(density) { GridPadding.toPx() }
    val renderIndicator = with(density) { (IndicatorHeight * 2).toPx() }
    val dockGapPx = with(density) { PanelDockGap.toPx() }
    val dockStaticReservePx = with(density) { (PanelDockHeight + PanelDockGap).toPx() }
    // 底栏当「额外的底部边距」：没冻结时用固定预算，冻结后按底栏上沿反推卡高上限，
    // 保证切换后的高面板是向上长，而不是盖住底栏。
    val reservedBottomPx = when {
        dockEntries.isEmpty() -> insetsBottom.toFloat()
        activeDockSession == null -> insetsBottom + dockStaticReservePx
        else -> renderScreenH - activeDockSession.placement.topY + dockGapPx + insetsTop
    }
    // 缩放用的「底边不可用带」与上面的「卡高上限」不是同一个量：
    // resolvePanelResize 已经自己减了 insetsBottom，这里只补底栏占掉的那一段。
    val resizeBottomReservePx = when {
        dockEntries.isEmpty() -> 0f
        activeDockSession == null -> dockStaticReservePx
        else -> (renderScreenH - activeDockSession.placement.topY + dockGapPx - insetsBottom)
            .coerceAtLeast(0f)
    }

    // 手势预览：按目标列数重排一份临时项，拖动过程中卡片能实时「放大/缩小」。
    // expandRows = true 保证缩列只把项往下挤，不静默丢项（面板内没有确认弹窗）。
    val pinchPlan = remember(localItems, pinchColumns, cardFraction, heightFraction, configuration) {
        pinchColumns?.let { target ->
            planGridResize(
                items = localItems,
                columns = target,
                cardWidthFraction = cardFraction,
                cardHeightFraction = heightFraction,
                screenWdp = configuration.screenWidthDp.toFloat(),
                screenHdp = configuration.screenHeightDp.toFloat(),
                expandRows = true,
            )
        }
    }
    val renderItems = pinchPlan?.items ?: localItems
    val renderItemsState = rememberUpdatedState(renderItems)

    fun commitPinchColumns(target: Int) {
        val cols = target.coerceIn(GridPanelDefaults.MinColumnCount, GridPanelDefaults.MaxColumnCount)
        pinchColumns = null
        if (cols == baseColumns) return
        val plan = planGridResize(
            items = localItems,
            columns = cols,
            cardWidthFraction = cardFraction,
            cardHeightFraction = heightFraction,
            screenWdp = configuration.screenWidthDp.toFloat(),
            screenHdp = configuration.screenHeightDp.toFloat(),
            expandRows = true,
        )
        if (plan.dropped.isNotEmpty()) {
            Log.w(TAG, "pinch columns $cols drops ${plan.dropped.size} item(s)")
        }
        // 调列数保持视觉卡高：卡高已连续，列数变化只影响行数派生，不顺带改高度。
        val baseCardH = gridPanelCardHeight(
            columns = baseColumns,
            cardW = renderScreenW * cardFraction,
            cardHeightFraction = geo.cardHeightFraction,
            screenH = renderScreenH,
            gridPad = renderGridPad,
            indicator = renderIndicator,
            gap = with(density) { gapFor(baseColumns).toPx() },
            // 与渲染一致的上限：否则调完列数反推出的比例会被底栏夹一次，视觉卡高就变了。
            maxCardHeightPx = (renderScreenH - reservedBottomPx).coerceAtLeast(0f),
        )
        val newFracH = keepCardHeightFraction(baseCardH, renderScreenH)
        localGeometry = localGeometry.copy(columnCount = cols, cardHeightFraction = newFracH)
        localItems = plan.items
        lastWritten = plan.items
        updatePanel("pinch column commit failed", cleanupResources = plan.dropped.isNotEmpty()) {
            it.copy(
                items = plan.items,
                geometry = it.geometry.copy(
                    columnCount = cols,
                    cardHeightFraction = newFracH,
                ),
            )
        }
        Log.i(TAG, "pinch columns committed: $baseColumns -> $cols, heightFraction $newFracH")
    }

    // 系统栏安全区（声明已上提到 metrics 之前）。

    // 行数按页计算（页内占用各自撑）：一个 metrics 服务整个 pager，若占用取全部页的
    // 最大值，新加页/少内容页会和最满的页一样高。base metrics 只带可见行数（+编辑态
    // 尾行），每页在其上按自身占用上撑。
    val editExtraRows = if (editMode) EditRowExtra else 0
    val metrics = rememberGridMetrics(
        columns, cardFraction, heightFraction,
        0,
        extraRows = editExtraRows,
        reservedBottomPx = reservedBottomPx,
    )
    val metricsState = rememberUpdatedState(metrics)
    // state 在 metricsForPage 之后创建；用 ref 打破循环，拖拽过程中的行数要按
    // state.resolved 计算，否则项被推到当前行数以下时 planReorder 会因越界返回 null。
    val stateRef = remember { arrayOfNulls<GridPanelState>(1) }
    val metricsForPage: (Int) -> GridMetrics = { page ->
        val m = metricsState.value
        val pageItemsNow = stateRef[0]?.renderItems(page)
            ?: renderItemsState.value.filter { it.pageIndex == page }
        val occ = pageItemsNow.maxOfOrNull { it.bottom } ?: 0
        val rows = maxOf(m.rows, occ + if (editMode) EditRowExtra else 0)
        if (rows == m.rows) m else m.copy(rows = rows)
    }
    val placement = geo.placement.coerced()
    val sideMarginPx = with(density) { placement.sideMarginDp.dp.toPx() }
    val topGapPx = with(density) { placement.topGapDp.dp.toPx() }
    val bottomGapPx = with(density) { placement.bottomGapDp.dp.toPx() }
    val cornerPx = rememberCardCornerPx(placement.sideMarginDp)


    // ---- 整块面板缩放：手势期间只改本地预览，松手一次性写当前朝向几何 ----
    var panelResizeCorner by remember(panelId, landscape) {
        mutableStateOf<ResizeCorner?>(null)
    }
    var panelResizePosition by remember(panelId, landscape) {
        mutableStateOf<Offset?>(null)
    }
    var panelResizeStartGeometry by remember(panelId, landscape) {
        mutableStateOf<GridPanelGeometry?>(null)
    }
    var panelResizeStartPosition by remember(panelId, landscape) {
        mutableStateOf(Offset.Zero)
    }
    // 起手尺寸取实际渲染尺寸（高度会被可见行数量化），不能从比例反推：
    // 否则第一次拖动时手柄与手指之间会跳一行。
    var panelResizeStartSize by remember(panelId, landscape) {
        mutableStateOf<Offset?>(null)
    }
    val minWidthFraction = GridPanelDefaults.MinCardWidthFraction

    // 底栏冻结：只在「第一次拿到卡片实际尺寸」与「用户拖缩放手柄期间」重算。
    // 第一次算完之后就钉住 —— 切换面板不重算（那是「底栏不动」的口径），
    // 缩放期间跟随（用户主动拖，底栏必须跟手），松手后就是新的钉住位置。
    //
    // 用 snapshotFlow 收卡片上报，而不是在组合里读锚点：卡片的几何是在它自己的
    // SideEffect 里写的，父层读到的第一帧必然是 0，而「父层的 SideEffect 写在可跳过的
    // if 组里」时重组不会再登记它 —— 表现就是正常态永远冻不住、进编辑态后突然出现。
    val dockAnchor = remember(panel.id) { PanelDockAnchor() }
    // 冻结逻辑包在 lambda 里再 rememberUpdatedState：协程里要读的是「当前」的
    // metrics / 面板 / 间距，而不是启动那一刻的快照（切换面板后会变）。
    val freezeDock = rememberUpdatedState {
        val cardHeight = dockAnchor.cardHeightPx
        val needsFreeze = dockSession == null || panelResizeCorner != null
        if (cardHeight > 0f && needsFreeze) {
            val next = PanelDockSession(
                placement = resolvePanelDockPlacement(
                    cardX = dockAnchor.position.x,
                    cardY = dockAnchor.position.y,
                    cardWidthPx = metrics.cardW,
                    cardHeightPx = cardHeight,
                    gapPx = dockGapPx,
                    tabCount = dockEntries.size,
                    maxTabWidthPx = with(density) { PanelDockTabMaxWidth.toPx() },
                    minTabWidthPx = with(density) { PanelDockTabMinWidth.toPx() },
                    insetPx = with(density) { PanelDockPadding.toPx() },
                ),
                cardWidthFraction = cardFraction,
                cardAlpha = panel.cardAlpha,
                blurRadiusPx = blurRadiusPx,
                panelId = panel.id,
            )
            // 相同值不写回：避免与重组互相触发。
            if (next != dockSession) dockSession = next
        }
    }
    LaunchedEffect(dockEntries.isEmpty()) {
        if (dockEntries.isEmpty()) return@LaunchedEffect
        snapshotFlow { Triple(dockAnchor.position, dockAnchor.cardHeightPx, panelResizeCorner) }
            .collect { freezeDock.value() }
    }

    fun beginPanelResize(corner: ResizeCorner, cardLeft: Float, cardTop: Float) {
        panelResizeStartGeometry = localGeometry
        panelResizeStartPosition = Offset(cardLeft, cardTop)
        panelResizeStartSize = Offset(metrics.cardW, metrics.cardH)
        panelResizePosition = panelResizeStartPosition
        panelResizeCorner = corner
    }

    fun updatePanelResize(total: Offset) {
        val startGeo = panelResizeStartGeometry ?: return
        val corner = panelResizeCorner ?: return
        val startSize = panelResizeStartSize ?: return
        val startPos = panelResizeStartPosition
        val result = resolvePanelResize(
            corner = corner,
            startSize = startSize,
            startPos = startPos,
            total = total,
            screenW = metrics.screenW,
            screenH = metrics.screenH,
            insetsTop = insetsTop,
            insetsBottom = insetsBottom,
            sideMarginPx = sideMarginPx,
            topGapPx = topGapPx,
            bottomGapPx = bottomGapPx,
            columns = metrics.columns,
            gridPad = metrics.gridPad,
            indicator = metrics.indicator,
            gap = metrics.gap,
            minWidthFraction = minWidthFraction,
            reservedBottomPx = resizeBottomReservePx,
        )
        localGeometry = startGeo.copy(
            cardWidthFraction = result.cardWidthFraction,
            cardHeightFraction = result.cardHeightFraction,
        )
        panelResizePosition = Offset(result.left, result.top)
    }

    fun endPanelResize() {
        val corner = panelResizeCorner ?: return
        panelResizeCorner = null
        panelResizeStartGeometry = null
        panelResizeStartSize = null
        val finalGeo = localGeometry
        // 保留预览位置到面板关闭：尺寸落盘回流后不再跳回 anchor 推导位。
        val commit = onCommitGeometry
        if (commit != null) {
            commit(finalGeo)
        } else {
            updatePanel("panel resize commit failed") {
                it.copy(
                    geometry = it.geometry.copy(
                        cardWidthFraction = finalGeo.cardWidthFraction,
                        cardHeightFraction = finalGeo.cardHeightFraction,
                    ),
                )
            }
        }
        Log.i(
            TAG,
            "panel resized corner=$corner size=${finalGeo.cardWidthFraction}x${finalGeo.cardHeightFraction}",
        )
    }

    // provider 被卸载 / 用户在系统里删掉小部件 → 宿主回调 onAppWidgetRemoved。
    // 那时格子会永远空白，所以要把这项收掉。
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

    /**
     * 拖到顶部页指示点松手：把项挪到目标页。
     * 源页保持原布局（拖拽中的重排只是预览，没写盘）；目标页找首个空位，
     * 满了就落到最底下一行（页高会随之撑开）。
     *
     * 页数用 [localItems] 现算，不用异步回流的面板配置：刚建过一页时配置还没
     * 回来，按旧页数把「新建页」点当成刚建的那页，会连续两次落到同一页。
     */
    fun moveItemToPage(item: GridPanelItem, fromPage: Int, dotIndex: Int, isVirtual: Boolean) {
        val localPageCount = (localItems.maxOfOrNull { it.pageIndex } ?: 0) + 1
        val targetPage = if (isVirtual) localPageCount else dotIndex.coerceIn(0, localPageCount - 1)
        if (targetPage == fromPage) return
        val targetItems = localItems.filter { it.pageIndex == targetPage }
        val m = metricsForPage(targetPage)
        // 跨页搬项也走归一化：旧坏 span 不跟着搬，目标页的空位扫描也不被越界项挡住。
        val base = item.normalized(m.columns, m.rows)
        val normalizedTargets = targetItems.map { it.normalized(m.columns, m.rows) }
        val slot = GridPanelItemLayout.firstFreeSlot(
            items = normalizedTargets,
            columns = m.columns,
            rows = m.rows + EditRowExtra,
            spanX = base.spanX,
            spanY = base.spanY,
        ) ?: GridCell(0, normalizedTargets.maxOfOrNull { it.bottom } ?: 0)
        val moved = base.copy(pageIndex = targetPage, x = slot.x, y = slot.y)
        Log.i(TAG, "moveItemToPage id=${item.id} $fromPage -> $targetPage at (${slot.x},${slot.y})")
        commitAllItems(localItems.filterNot { it.id == item.id } + moved)
    }

    fun execute(action: StoredAction, screenX: Int, screenY: Int) {
        // 执行链要求 a11y 容器。没开无障碍时转发失败：别关面板也别装没事。
        if (!dispatch(action, screenX, screenY)) {
            showToast(R.string.toast_accessibility_required)
            return
        }
        // 另一个网格面板：执行链已让 GridPanelOverlay 就地切换 panelIdState，
        // 这里不能再关，否则刚切过去的目标面板会被旧面板的退场动画收掉。
        val targetPanelId = action.gridPanelId()
        if (targetPanelId != null && targetPanelId != panel.id) return
        closeWithAnim()
    }

    val state = remember(panel.id) {
        GridPanelState(
            metricsProvider = { page -> metricsForPage(page) },
            pageItemsProvider = { page -> renderItemsState.value.filter { it.pageIndex == page } },
            onHaptic = { view.hapticMove() },
            commitPage = { page, items -> commitPageItems(page, items) },
            moveItemToPage = { item, fromPage, dotIndex, isVirtual ->
                moveItemToPage(item, fromPage, dotIndex, isVirtual)
            },
            scope = scope,
        )
    }
    // 供 metricsForPage 读取拖拽中的 resolved 布局（仅读，不参与 state 构造）。
    stateRef[0] = state

    // 返回键优先级：拖拽中 → 取消拖拽；缩放中 → 取消缩放；编辑态 → 退编辑；否则交给浮窗。
    // 注册必须放在 state 之后（处理器要能读到当前拖拽/缩放状态），并且以 state 为 key：
    // 面板切换会重建 state，旧闭包不能留着。
    LaunchedEffect(state) {
        onRegisterBackHandler?.invoke {
            when {
                state.draggingId != null -> { state.cancelDrag(); true }
                state.resizingId != null -> { state.cancelResize(); true }
                editMode -> { exitEditMode(); true }
                else -> false
            }
        }
    }

    var suppressItemTapUntil by remember(panel.id) { mutableStateOf(0L) }

    // 拖拽幽灵提供在更外层（见 GridPanelOverlay）：拖拽时这里只负责把卡片本体淡出，
    // 让下面的应用露出来；窗口不摘，手势才接得上。
    val dragGhost = LocalGridPanelDragGhost.current

    // 根 Box：点按 + 长按空白进出编辑。
    // 长按只收**空白**的事件：项的手势层会在触发时消费同一路事件，
    // detectTapGestures 见 consumed 即取消 —— 消费链天然就是判定域。
    // 底栏是唯一例外：它是卡的兄弟、不在卡内，必须显式按矩形排除，否则点胶囊等于点空白。
    val dockBounds = remember { mutableStateOf<Rect?>(null) }
    Box(
        Modifier
            .fillMaxSize()
            .pointerInput(editMode) {
                detectTapGestures(
                    onTap = { offset ->
                        if (dockBounds.value?.contains(offset) == true) return@detectTapGestures
                        // 退场动画期间不再接受任何点击：窗口还在，眨眼间点项会把动作再执行一遍。
                        if (closing) return@detectTapGestures
                        if (editMode) {
                            exitEditMode()
                            suppressItemTapUntil =
                                SystemClock.elapsedRealtime() + ExitEditTapGuardMillis
                        } else {
                            onClose()
                        }
                    },
                    onLongPress = { offset ->
                        if (dockBounds.value?.contains(offset) == true) return@detectTapGestures
                        if (!editMode && !closing) {
                            view.hapticLongPress()
                            editMode = true
                        }
                    },
                )
            },
    ) {
        // 面板打开时的触点随内容树下发：应用抽屉等内部部件启动小窗时用它做落点。
        androidx.compose.runtime.CompositionLocalProvider(
            LocalGridPanelAnchor provides (anchorX to anchorY),
        ) {
        GridPanelCard(
            panelId = panel.id,
            editMode = editMode,
            pageCount = pageCount,
            showIndicator = singleContent == null && (pageCount > 1 || editMode),
            mappingPages = mappingPages,
            placement = placement,
            cornerPx = cornerPx,
            pagerState = pagerState,
            metrics = metrics,
            metricsForPage = metricsForPage,
            anchorX = anchorX,
            anchorY = anchorY,
            insetsTop = insetsTop,
            insetsBottom = insetsBottom,
            appearance = appearance.value,
            hiddenForDrag = dragGhost?.chromeHidden == true,
            cardAlpha = cardAlpha,
            blurRadiusPx = blurRadiusPx,
            state = state,
            itemActions = { pageIndex ->
                GridItemActions(
                    onTap = { item, center ->
                        if (!editMode && !closing &&
                            SystemClock.elapsedRealtime() >= suppressItemTapUntil
                        ) {
                            // 动作在 Kind.Action.items 里（面板项只进不出）；
                            // 小部件项没有可执行动作，点击由 provider 自己处理。
                            // 「无」占位项：什么都不做（也不能关面板，否则等于点一下就没了）。
                            val action = (item.kind as? GridPanelItem.Kind.Action)
                                ?.items
                                ?.firstOrNull()
                            if (action != null && action.typeId != hunoia.luno.action.model.Action.NONE_TYPE_ID) {
                                execute(action.withoutVariantKey(), center.x, center.y)
                            }
                        }
                    },
                    onDelete = { item ->
                        // 删除后由 PanelResources.cleanup() 统一回收 widget id 和图标文件。
                        commitAllItems(localItems.filterNot { it.id == item.id }, cleanupResources = true)
                    },
                    onDragStart = { item -> state.beginDrag(pageIndex, item) },
                    onDrag = { amount -> state.onDragDelta(amount) },
                    onDragEnd = { state.endDrag() },
                    // 边缘滚动由 Card 在页面内接（它有该页的 ScrollState）。
                    onDragPosition = null,
                    onResizeStart = { item -> state.beginResize(pageIndex, item) },
                    onResizeDelta = { delta -> state.onResizeDelta(delta) },
                    onResizeEnd = { state.endResize() },
                    onToggleBackground = { item ->
                        commitAllItems(
                            localItems.map {
                                val k = it.kind as? GridPanelItem.Kind.Internal
                                if (it.id == item.id && k != null) {
                                    it.copy(kind = k.copy(showBackground = !k.showBackground))
                                } else it
                            },
                        )
                    },
                    onReconfigureInternal = { item ->
                        // 内置部件重新配置（习惯部件删后重选）：打开现有选择页，
                        // 选中新部件时落盘原位替换本项（位置/span 不变，见 saveGridPanelInternalWidget）。
                        val started = onPickItem(
                            GridPanelItemPick(
                                gridPanelId = panel.id,
                                pageIndex = item.pageIndex,
                                replaceItemId = item.id,
                            ),
                        )
                        if (!started) showToast(R.string.toast_select_page_failed)
                    },
                    onNudge = { item, dx, dy -> state.nudge(pageIndex, item.id, dx, dy) },
                )
            },
            onSelectPage = { index ->
                // 指示点给的是真实页；循环模式下滚到就近等价索引，不做跨段长距离动画。
                scope.launch { pagerState.animateScrollToPage(nearestPageIndexOf(index)) }
            },
            positionOverride = panelResizePosition,
            panelResizing = panelResizeCorner != null,
            onPanelResizeStart = ::beginPanelResize,
            onPanelResizeDelta = ::updatePanelResize,
            onPanelResizeEnd = ::endPanelResize,
            pinchColumns = pinchColumns,
            onPinchColumns = if (singleContent != null) {
                null
            } else {
                { target, final ->
                    if (final) {
                        commitPinchColumns(target)
                    } else {
                        // 回到起始列数时隐藏 HUD，但手势结束仍按 target 收尾。
                        pinchColumns = target.takeIf { it != baseColumns }
                    }
                }
            },
            singleContent = singleContent?.let { content ->
                { cardMetrics -> content(cardMetrics, editMode) }
            },
            dockReservePx = resizeBottomReservePx,
            dockAnchorPlacement = activeDockSession?.placement,
            dockGapPx = dockGapPx,
            dockAnchor = dockAnchor.takeIf { dockEntries.isNotEmpty() },
            onAdd = {
                // 加项落点交给落盘层决定（首个空位），这里不预选格子。
                // 循环模式下 pager 索引先换算回真实页/虚页。用 currentPage：连续快滑
                // 后立刻点 FAB，settledPage 还停在几页之前（实测 settled=1000000
                // 实际已在第 4 页）；currentPage 随手势过半页即更新。
                val settled = pagerState.currentPage
                val realSettled = settled.mod(mappingPages)
                Log.i(TAG, "onAdd: settled=$settled mapping=$mappingPages -> targetPage=$realSettled")
                val started = onPickItem(
                    GridPanelItemPick(
                        gridPanelId = panel.id,
                        pageIndex = realSettled,
                    ),
                )
                if (!started) showToast(R.string.toast_select_page_failed)
            },
        )
        // 底栏：位置与外观在打开时冻结，切换面板不动（卡反过来以底栏为锚点）。
        val frozenDock = activeDockSession
        if (dockEntries.isNotEmpty() && frozenDock != null) {
            PanelDockLayer(
                entries = dockEntries,
                selectedId = panel.id,
                onSelect = { id ->
                    commitBeforeSwitch()
                    onSwitchPanel(id)
                },
                session = frozenDock,
                anchor = dockAnchor,
                onBounds = { dockBounds.value = it },
            )
        }
        }
    }
}
