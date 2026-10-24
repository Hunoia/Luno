package hunoia.luno.gridpanel

import android.os.SystemClock
import android.util.Log
import android.view.HapticFeedbackConstants
import android.view.WindowInsets
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import hunoia.luno.R
import hunoia.luno.action.model.StoredAction
import hunoia.luno.action.model.withoutVariantKey
import hunoia.luno.bridge.ui.showToast
import hunoia.luno.config.model.EditRowExtra
import hunoia.luno.config.model.GridPanel
import hunoia.luno.config.model.GridPanelDefaults
import hunoia.luno.config.model.GridPanelItem
import hunoia.luno.config.model.derivedPageCount
import hunoia.luno.domain.tune.TuneConfig
import hunoia.luno.gridpanel.core.GridPanelState
import hunoia.luno.gridpanel.geometry.GridMetrics
import hunoia.luno.gridpanel.geometry.rememberCardCornerPx
import hunoia.luno.gridpanel.geometry.rememberGridMetrics
import hunoia.luno.gridpanel.ui.GridItemActions
import hunoia.luno.gridpanel.widget.WidgetGoneListener
import hunoia.luno.gridpanel.widget.addWidgetGoneListener
import hunoia.luno.gridpanel.widget.removeWidgetGoneListener
import hunoia.luno.ui.navigation.ActionSelect
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.launch


/** 点空白退出编辑后，这段时间内的项点按不执行（防连点误触）。 */
private const val ExitEditTapGuardMillis = 150L

private const val TAG = "GridPanel"

/**
 * 网格面板：固定网格 + 跨格项 + 简版多页。
 * 运行态点项执行、长按空白进编辑；编辑态显示删除角标与尺寸手柄、可拖拽落位。
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
    onPickItem: (ActionSelect) -> Boolean,
    onClose: () -> Unit,
    onCloseAnimated: () -> Unit,
    onRegisterCloseAnimated: ((() -> Unit) -> Unit)?,
    /** 注册返回键处理器：组合先行消费（编辑态退编辑），未消费由浮窗关闭。 */
    onRegisterBackHandler: (((() -> Boolean)) -> Unit)? = null,
) {
    // 进出场用一张 Animatable 管 alpha/scale：语义可证伪，不依赖服务进程里
    // animate*AsState 的收敛。
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

    // ---- 循环翻页状态（必须在 panel == null 分支之前声明：配置重读时 panel 可能
    // 闪一帧 null，状态若声明在其后会被整体丢弃 —— pager 回到初始页、editMode
    // 丢失，表现为「虚拟页加项返回后回到前一页」）。----
    // panel 短暂为 null 时沿用上一次有效页数：否则 pageCount 会闪成 1，
    // 映射效果把 pager 重锚到低位，回来后当前页和循环起点都丢了。
    var lastKnownPageCount by remember(panelId) {
        mutableIntStateOf(panel?.derivedPageCount ?: 1)
    }
    val pageCount = panel?.derivedPageCount ?: lastKnownPageCount
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
    LaunchedEffect(Unit) {
        onRegisterBackHandler?.invoke {
            if (editMode) {
                exitEditMode()
                true
            } else {
                false
            }
        }
    }

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

    // 横竖屏分别取几何。
    val landscape = LocalConfiguration.current.orientation ==
        android.content.res.Configuration.ORIENTATION_LANDSCAPE
    val geo = panel.geometry(landscape)
    val columns = geo.columnCount.coerceIn(
        GridPanelDefaults.MinColumnCount,
        GridPanelDefaults.MaxColumnCount,
    )
    val cardFraction = geo.cardWidthFraction
        .coerceIn(
            if (landscape) GridPanelDefaults.MinCardWidthFractionLandscape else GridPanelDefaults.MinCardWidthFraction,
            GridPanelDefaults.MaxCardWidthFraction,
        )
    val heightFraction = geo.cardHeightFraction
        .coerceIn(GridPanelDefaults.MinCardHeightFraction, GridPanelDefaults.MaxCardHeightFraction)
    val cardAlpha = panel.cardAlpha.coerceIn(GridPanelDefaults.MinCardAlpha, GridPanelDefaults.MaxCardAlpha)

    // 项列表的本地真身：拖拽要连续多次落位，DataStore 回流有延迟，直接渲染
    // panel.items 会在快速拖动时被旧快照回跳。回流同步只认「与 lastWritten
    // 不同」的变更（自己写出去的回声不覆盖本地）。
    var localItems by remember(panelId) { mutableStateOf(panel.items) }
    var lastWritten by remember(panelId) { mutableStateOf(panel.items) }
    LaunchedEffect(panel.items) {
        if (panel.items != lastWritten) {
            localItems = panel.items
            lastWritten = panel.items
        }
    }

    fun commitPageItems(pageIndex: Int, next: List<GridPanelItem>) {
        val rebuilt = localItems.filter { it.pageIndex != pageIndex } + next
        localItems = rebuilt
        lastWritten = rebuilt
        scope.launch {
            runCatching {
                TuneConfig.updateAction { settings ->
                    val latest = settings.actionGridPanels.panelById(panel.id) ?: return@updateAction settings
                    settings.copy(
                        actionGridPanels = settings.actionGridPanels.withPanel(
                            latest.copy(items = rebuilt),
                        ),
                    )
                }
            }.onFailure { Log.w(TAG, "grid panel commitPage failed: ${it.message}") }
        }
    }

    fun commitAllItems(next: List<GridPanelItem>, cleanupResources: Boolean = false) {
        localItems = next
        lastWritten = next
        scope.launch {
            runCatching {
                TuneConfig.updateAction { settings ->
                    val latest = settings.actionGridPanels.panelById(panel.id) ?: return@updateAction settings
                    settings.copy(
                        actionGridPanels = settings.actionGridPanels.withPanel(
                            latest.copy(items = next),
                        ),
                    )
                }
            }.onFailure { Log.w(TAG, "grid panel commitAll failed: ${it.message}") }
            if (cleanupResources) hunoia.luno.domain.gridpanel.PanelResources.cleanup()
        }
    }

    val view = LocalView.current
    // 走 LocalConfiguration 而不是 displayMetrics：组合必须订阅配置变化才会在转屏后
    // 重算（只读 metrics 的组合不会重组）。
    val configuration = LocalConfiguration.current
    // 系统栏余量按 configuration 取：转屏（导航栏换边）后不能沿用首帧的值。
    val insetsTop = remember(configuration) {
        runCatching { view.rootWindowInsets.getInsets(WindowInsets.Type.statusBars()).top }
            .getOrDefault(0)
    }
    val insetsBottom = remember(configuration) {
        runCatching { view.rootWindowInsets.getInsets(WindowInsets.Type.navigationBars()).bottom }
            .getOrDefault(0)
    }

    // 行数按页计算（页内占用各自撑）：一个 metrics 服务整个 pager，若占用取全部页的
    // 最大值，新加页/少内容页会和最满的页一样高。base metrics 只带可见行数（+编辑态
    // 尾行），每页在其上按自身占用上撑。
    val editExtraRows = if (editMode) EditRowExtra else 0
    val metrics = rememberGridMetrics(
        columns, cardFraction, heightFraction,
        0,
        extraRows = editExtraRows,
    )
    val metricsState = rememberUpdatedState(metrics)
    val metricsForPage: (Int) -> GridMetrics = { page ->
        val m = metricsState.value
        val occ = localItems.filter { it.pageIndex == page }.maxOfOrNull { it.bottom } ?: 0
        val rows = maxOf(m.rows, occ + if (editMode) EditRowExtra else 0)
        if (rows == m.rows) m else m.copy(rows = rows)
    }
    val clampedMarginDp = geo.screenMarginDp.coerceIn(
        GridPanelDefaults.MinScreenMarginDp, GridPanelDefaults.MaxScreenMarginDp,
    )
    val screenMarginPx = with(LocalDensity.current) { clampedMarginDp.dp.toPx() }
    val cornerPx = rememberCardCornerPx(clampedMarginDp)

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

    fun execute(action: StoredAction, screenX: Int, screenY: Int) {
        // 执行链要求 a11y 容器。没开无障碍时转发失败：别关面板也别装没事。
        if (!dispatch(action, screenX, screenY)) {
            showToast(R.string.toast_accessibility_required)
            return
        }
        closeWithAnim()
    }

    val state = remember(panel.id) {
        GridPanelState(
            metricsProvider = { page -> metricsForPage(page) },
            pageItemsProvider = { page -> localItems.filter { it.pageIndex == page } },
            onHaptic = { view.performHapticFeedback(HapticFeedbackConstants.TEXT_HANDLE_MOVE) },
            commitPage = { page, items -> commitPageItems(page, items) },
            scope = scope,
        )
    }

    var suppressItemTapUntil by remember(panel.id) { mutableStateOf(0L) }

    // 根 Box：点按 + 长按空白进出编辑。
    // 长按只收**空白**的事件：项的手势层会在触发时消费同一路事件，
    // detectTapGestures 见 consumed 即取消 —— 消费链天然就是判定域。
    Box(
        Modifier
            .fillMaxSize()
            .pointerInput(editMode) {
                detectTapGestures(
                    onTap = {
                        if (editMode) {
                            exitEditMode()
                            suppressItemTapUntil =
                                SystemClock.elapsedRealtime() + ExitEditTapGuardMillis
                        } else {
                            onClose()
                        }
                    },
                    onLongPress = {
                        if (!editMode) {
                            view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                            editMode = true
                        }
                    },
                )
            },
    ) {
        GridPanelCard(
            panelId = panel.id,
            editMode = editMode,
            pageCount = pageCount,
            showIndicator = pageCount > 1 || editMode,
            mappingPages = mappingPages,
            screenMarginPx = screenMarginPx,
            cornerPx = cornerPx,
            pagerState = pagerState,
            metrics = metrics,
            metricsForPage = metricsForPage,
            anchorX = anchorX,
            anchorY = anchorY,
            insetsTop = insetsTop,
            insetsBottom = insetsBottom,
            appearance = appearance.value,
            cardAlpha = cardAlpha,
            state = state,
            itemActions = { pageIndex ->
                GridItemActions(
                    onTap = { item, center ->
                        if (!editMode &&
                            SystemClock.elapsedRealtime() >= suppressItemTapUntil
                        ) {
                            // 动作在 Kind.Action.items 里（面板项只进不出）；
                            // 小部件项没有可执行动作，点击由 provider 自己处理。
                            val action = (item.kind as? GridPanelItem.Kind.Action)
                                ?.items
                                ?.firstOrNull()
                            if (action != null) {
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
                )
            },
            onSelectPage = { index ->
                // 指示点给的是真实页；循环模式下滚到就近等价索引，不做跨段长距离动画。
                scope.launch { pagerState.animateScrollToPage(nearestPageIndexOf(index)) }
            },
            onAdd = {
                // 加项落点交给落盘层决定（首个空位），这里不预选格子。
                // 循环模式下 pager 索引先换算回真实页/虚页。用 currentPage：连续快滑
                // 后立刻点 FAB，settledPage 还停在几页之前（实测 settled=1000000
                // 实际已在第 4 页）；currentPage 随手势过半页即更新。
                val settled = pagerState.currentPage
                val realSettled = settled.mod(mappingPages)
                Log.i(TAG, "onAdd: settled=$settled mapping=$mappingPages -> targetPage=$realSettled")
                val started = onPickItem(
                    ActionSelect(
                        gridPanelId = panel.id,
                        gridPanelPageIndex = realSettled,
                    ),
                )
                if (!started) showToast(R.string.toast_select_page_failed)
            },
        )
    }
}
