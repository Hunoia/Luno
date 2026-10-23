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
import hunoia.luno.config.model.DynamicRowExtra
import hunoia.luno.config.model.EditRowExtra
import hunoia.luno.config.model.GridPanel
import hunoia.luno.config.model.GridPanelDefaults
import hunoia.luno.config.model.GridPanelItem
import hunoia.luno.config.model.derivedPageCount
import hunoia.luno.domain.tune.TuneConfig
import hunoia.luno.gridpanel.core.GridPanelState
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
    val pageCount = panel.derivedPageCount
    val cardFraction = geo.cardWidthFraction
        .coerceIn(
            if (landscape) GridPanelDefaults.MinCardWidthFractionLandscape else GridPanelDefaults.MinCardWidthFraction,
            GridPanelDefaults.MaxCardWidthFraction,
        )
    val heightFraction = geo.cardHeightFraction
        .coerceIn(GridPanelDefaults.MinCardHeightFraction, GridPanelDefaults.MaxCardHeightFraction)
    val cardAlpha = panel.cardAlpha.coerceIn(GridPanelDefaults.MinCardAlpha, GridPanelDefaults.MaxCardAlpha)

    var editMode by remember(panel.id) { mutableStateOf(false) }
    // 循环翻页：pager 页数恒为极大值（运行态与编辑态一致，避免页数切换引发
    // clamp/闪跳）。真实页 = index % 有效页数；编辑态有效页数 +1（末尾虚页：
    // index ≡ 派生页数 即虚页，落项建页）。loopBase 为页数倍数，初始落在真实第 0 页。
    val loopBase = remember(panel.id) { 1_000_000 - 1_000_000 % pageCount }
    val pagerState = rememberPagerState(initialPage = loopBase) { Int.MAX_VALUE }
    // 循环映射：真实页 = index % mappingPages（编辑态 +1，末尾虚页 ≡ 派生页数）。
    // mappingPages 滞后于 editMode/pageCount：目标映射生效前先把索引 snap 到新旧映射下
    // 真实页相同的等价点（n 与 n±1 互素，周期 n(n±1) 内必有解），切换后真实页不漂移。
    val effectivePages = pageCount + if (editMode) 1 else 0
    var mappingPages by remember { mutableIntStateOf(pageCount) }
    LaunchedEffect(pageCount, editMode) {
        if (effectivePages != mappingPages) {
            val cur = pagerState.currentPage
            val real = cur.mod(mappingPages).coerceAtMost(effectivePages - 1) // 退出编辑落在虚页时归末页
            val period = mappingPages * effectivePages
            val step = ((cur - real).toDouble() / period).roundToInt()
            pagerState.scrollToPage((real + period * step).coerceAtLeast(0))
            mappingPages = effectivePages
        }
    }
    // 就近等价索引：离当前位置最近的、真实页为 real 的 pager 索引，避免跨百万页滚动。
    fun nearestPageIndexOf(real: Int): Int {
        val cur = pagerState.currentPage
        val base = cur - cur.mod(mappingPages) + real
        return listOf(base - mappingPages, base, base + mappingPages).minBy { abs(it - cur) }
    }
    // 退出编辑态：若停在虚页（旧映射下 ≡ pageCount），直接翻 editMode 会闪一帧空白
    //（editMode 先生效而重锚定下一帧才跑，中间帧仍映射到空虚页）。先 snap 到真实页 =
    // 末页在新旧映射下的 CRT 等价点（snap 期间编辑 UI 还在、内容已是末页），再把
    // mappingPages 与 editMode 同帧写回。实页退出内容一致，交给 LaunchedEffect 收尾。
    fun exitEditMode() {
        val cur = pagerState.currentPage
        if (cur.mod(pageCount + 1) != pageCount) {
            editMode = false
            return
        }
        scope.launch {
            val real = pageCount - 1
            val period = (pageCount + 1) * pageCount
            val step = ((cur - real).toDouble() / period).roundToInt()
            pagerState.scrollToPage((real + period * step).coerceAtLeast(0))
            mappingPages = pageCount
            editMode = false
        }
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

    // 行数不设限：实际行数 = max(可见行数，占用行数) + 额外空行。编辑态末尾 +2 行给
    // 拖拽留落点（避免底部项被加项 FAB 遮挡）；运行态不追加，卡片底边贴齐最后一行。
    // 可见行数由卡片高度反推（在 rememberGridMetrics 内统一算）；占用取全部页的最大值。
    val occupiedRows = localItems.maxOfOrNull { it.bottom } ?: 0
    val editExtraRows = if (editMode) EditRowExtra else 0
    val metrics = rememberGridMetrics(
        columns, cardFraction, heightFraction,
        occupiedRows,
        extraRows = editExtraRows,
    )
    val clampedMarginDp = geo.screenMarginDp.coerceIn(
        GridPanelDefaults.MinScreenMarginDp, GridPanelDefaults.MaxScreenMarginDp,
    )
    val screenMarginPx = with(LocalDensity.current) { clampedMarginDp.dp.toPx() }
    val cornerPx = rememberCardCornerPx(clampedMarginDp)
    val metricsState = rememberUpdatedState(metrics)

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
            metricsProvider = { metricsState.value },
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
                // 循环模式下 settledPage 是 pager 索引，先换算回真实页/虚页。
                val settled = pagerState.settledPage
                val realSettled = settled.mod(mappingPages)
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
