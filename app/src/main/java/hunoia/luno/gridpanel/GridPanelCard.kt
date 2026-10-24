package hunoia.luno.gridpanel

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import hunoia.luno.R
import hunoia.luno.gridpanel.core.EdgeAutoScroll
import hunoia.luno.gridpanel.core.GridPanelState
import hunoia.luno.gridpanel.geometry.GridMetrics
import hunoia.luno.gridpanel.geometry.IndicatorHeight
import hunoia.luno.gridpanel.geometry.GridPadding
import hunoia.luno.gridpanel.ui.GridItemActions
import hunoia.luno.gridpanel.ui.GridPageIndicator
import hunoia.luno.gridpanel.ui.GridPanelLayer
import hunoia.luno.gridpanel.ui.gridPanelCardColor
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch


/** 拖拽到距可视区上下沿这么多 dp 内开始自动滚动。 */
private val EdgeScrollBand = 56.dp

/** 自动滚动上限步进（dp/帧，约 960dp/s），实际按越界深度在 25%~100% 间取。 */
private val EdgeScrollStep = 16.dp

/**
 * 卡片容器：几何 → 尺寸 → 位置动画 → HorizontalPager。
 * 每页的 [ScrollState] 在这里创建并长期持有（map + getOrPut）：放进 pager 的
 * page lambda 里 remember，页面被回收后边缘滚动协程可能握着废弃实例。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun GridPanelCard(
    panelId: String,
    editMode: Boolean,
    /** 派生页数（不含编辑态虚页）。 */
    pageCount: Int,
    /** 是否显示页指示点（派生页数 > 1，或编辑态有虚页）。 */
    showIndicator: Boolean,
    /** 当前生效的循环映射页数（编辑态 +1，见 Content）：真实页 = pagerIndex % mappingPages。 */
    mappingPages: Int,
    /** 卡片离屏边距（px）。 */
    screenMarginPx: Float,
    /** 卡片圆角（px）= 屏幕圆角 − 离屏边距。 */
    cornerPx: Float,
    pagerState: PagerState,
    /** 卡片级几何（定位/卡面尺寸用，不含页差异）。 */
    metrics: GridMetrics,
    /** 按页几何：行数 = max(可见行数，本页占用) + 编辑态尾行，各行页不再同高。 */
    metricsForPage: (Int) -> GridMetrics,
    anchorX: Int,
    anchorY: Int,
    insetsTop: Int,
    insetsBottom: Int,
    appearance: Float,
    cardAlpha: Float,
    state: GridPanelState,
    itemActions: (pageIndex: Int) -> GridItemActions,
    onSelectPage: (Int) -> Unit,
    onAdd: () -> Unit,
) {
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val marginPx = screenMarginPx
    val gridPadDp = GridPadding
    val bandPx = with(density) { EdgeScrollBand.toPx() }
    val stepPx = with(density) { EdgeScrollStep.toPx() }
    val targetPosX = (anchorX - metrics.cardW / 4f).coerceIn(
        marginPx,
        (metrics.screenW - metrics.cardW - marginPx).coerceAtLeast(marginPx),
    )
    val targetPosY = (anchorY - metrics.cardH / 4f).coerceIn(
        insetsTop + marginPx,
        (metrics.screenH - insetsBottom - metrics.cardH - marginPx)
            .coerceAtLeast(insetsTop + marginPx),
    )
    // 转屏 / 改网格尺寸时卡片要挪位置：首帧直接对齐（别从 0 飞过来），之后平滑过渡。
    val posAnimX = remember { Animatable(targetPosX) }
    val posAnimY = remember { Animatable(targetPosY) }
    LaunchedEffect(targetPosX, targetPosY) {
        if (appearance < 0.99f) {
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

    val pageScrolls = remember(panelId) { mutableMapOf<Int, ScrollState>() }
    // 页数被调小后清掉越界页的滚动状态，避免再调大时复用旧滚动位置。
    LaunchedEffect(pageCount, panelId) {
        pageScrolls.keys.retainAll(0 until pageCount)
    }

    // 循环：pager 页数恒为极大值（见 Content）；真实页 = index % mappingPages，
    // 编辑态末尾虚页 ≡ 派生页数。单页取模归 0。
    fun realPage(index: Int): Int = index.mod(mappingPages)

    Box(
        Modifier
            .offset { IntOffset(posAnimX.value.roundToInt(), posAnimY.value.roundToInt()) }
            .size(
                with(density) { metrics.cardW.toDp() },
                with(density) { metrics.cardH.toDp() },
            )
            .graphicsLayer {
                alpha = appearance
                scaleX = 0.92f + 0.08f * appearance
                scaleY = scaleX
            }
            .background(
                gridPanelCardColor.copy(alpha = cardAlpha),
                RoundedCornerShape(cornerPx),
            )
            .clip(RoundedCornerShape(cornerPx)),
    ) {
        // 多页指示点固定在顶部，高度预算 IndicatorHeight 在 metrics 里；页切换靠点/横滑。
        Column(Modifier.fillMaxSize()) {
            // 顶部预留区恒存在（高度 = IndicatorHeight），单页运行态为空白，
            // 与底部等高预留对称：进编辑态指示点出现时卡面不跳动。
            if (showIndicator) {
                GridPageIndicator(
                    pageCount = pageCount,
                    activePage = realPage(pagerState.currentPage),
                    onSelectPage = onSelectPage,
                    virtualLast = editMode,
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                Spacer(
                    Modifier
                        .fillMaxWidth()
                        .height(IndicatorHeight),
                )
            }
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = gridPadDp)
                    // 底部与顶部预留块等高（IndicatorHeight）：进编辑态卡高不跳变，
                    // 卡面上下对称。
                    .padding(bottom = IndicatorHeight),
            ) {
                HorizontalPager(
                    state = pagerState,
                    // 单映射（单页运行态）禁手势滑动：页数仍恒为极大值以保住循环索引，
                    // 只是不让用户滑（见 GridPanelContent 的注释）。
                    userScrollEnabled = mappingPages > 1,
                    modifier = Modifier.fillMaxSize(),
                ) { pagerIndex ->
                    // 循环模式下 pager 索引 ≠ 真实页；滚动状态等按真实页共享，
                    // 同一页的多个循环副本共用同一个 ScrollState。
                    val pageIndex = realPage(pagerIndex)
                    val vScroll = remember(pageIndex, panelId) {
                        pageScrolls.getOrPut(pageIndex) { ScrollState(0) }
                    }
                    val edge = remember(pageIndex, panelId, bandPx, stepPx) {
                        EdgeAutoScroll(scope, state, bandPx, stepPx)
                    }
                    DisposableEffect(pageIndex) {
                        onDispose { edge.stop() }
                    }
                    val pageMetrics = metricsForPage(pageIndex)
                    LaunchedEffect(editMode, pageIndex) {
                        if (!editMode) edge.stop()
                    }
                    // baseActions 每次重组都是新实例（lambda 不稳定），不能直接当
                    // remember 的 key —— 否则等于白记。这里用 rememberUpdatedState
                    // 存最新值，actions 本体只按页建一次，闭包里读到的永远是新的。
                    val baseActionsState = rememberUpdatedState(itemActions(pageIndex))
                    val actions = remember(pageIndex) {
                        GridItemActions(
                            onTap = { item, center ->
                                baseActionsState.value.onTap(item, center)
                            },
                            onDelete = { item -> baseActionsState.value.onDelete(item) },
                            onDragStart = { item ->
                                edge.stop()
                                baseActionsState.value.onDragStart(item)
                            },
                            onDrag = { amount -> baseActionsState.value.onDrag(amount) },
                            onDragEnd = {
                                edge.stop()
                                baseActionsState.value.onDragEnd()
                            },
                            onDragPosition = { item, contentY ->
                                // contentY 已是网格内容坐标（层根 = 滚动内容），
                                // 减去已滚量才是可视区坐标。
                                edge.update(vScroll, contentY - vScroll.value, pageIndex)
                                baseActionsState.value.onDragPosition?.invoke(item, contentY)
                            },
                            onResizeStart = { item ->
                                baseActionsState.value.onResizeStart(item)
                            },
                            onResizeDelta = { delta ->
                                baseActionsState.value.onResizeDelta(delta)
                            },
                            onResizeEnd = { baseActionsState.value.onResizeEnd() },
                            onToggleBackground = { item -> baseActionsState.value.onToggleBackground(item) },
                        )
                    }
                    // pager 会同时组合相邻页：拖拽视觉/落点预览只属于正在拖的那一页，
                    // 否则翻到隔壁也能看到半透明的落位框。
                    val dragOnThisPage = state.dragPage == pageIndex
                    Box(
                        Modifier
                            .fillMaxSize()
                            .verticalScroll(vScroll),
                    ) {
                        GridPanelLayer(
                            items = state.renderItems(pageIndex),
                            metrics = pageMetrics,
                            editMode = editMode,
                            draggingId = if (dragOnThisPage) state.draggingId else null,
                            hoverCell = if (dragOnThisPage) state.hoverCell else null,
                            dragVisual = if (dragOnThisPage) state.dragVisual else androidx.compose.ui.geometry.Offset.Zero,
                            glideFrom = state.glideFrom,
                            resizingId = state.resizingId,
                            resizeSpanF = state.resizeSpanF,
                            actions = actions,
                            onWidgetScroll = { dy -> vScroll.dispatchRawDelta(dy) },
                            onWidgetFling = { velocity ->
                                scope.launch {
                                    var v = velocity
                                    while (abs(v) > 60f) {
                                        val consumed = vScroll.dispatchRawDelta(v * 0.016f)
                                        if (consumed == 0f) break
                                        v *= 0.93f
                                        delay(16L)
                                    }
                                }
                            },
                        )
                    }
                }
            }
        }
        // 编辑态加项入口：右下角悬浮按钮。
        if (editMode) {
            FloatingActionButton(
                onClick = onAdd,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp),
                // 加项是全卡唯一主操作，用 primary：内容色是固定浅灰，FAB 再用内容色
                // 会变成全卡最亮的大白钮；深色方案下 primary 亮、onPrimary 暗，黑卡上看得见。
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = stringResource(R.string.grid_panel_add_item),
                )
            }
        }
    }
}
