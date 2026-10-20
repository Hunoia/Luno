package hunoia.luno.gridpanel

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.stringResource
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import hunoia.luno.R
import hunoia.luno.action.model.NewActionLibraryEntry
import hunoia.luno.gridpanel.core.EdgeAutoScroll
import hunoia.luno.gridpanel.core.GridPanelState
import hunoia.luno.gridpanel.geometry.CardCorner
import hunoia.luno.gridpanel.geometry.CardMargin
import hunoia.luno.gridpanel.geometry.GridMetrics
import hunoia.luno.gridpanel.geometry.GridPadding
import hunoia.luno.gridpanel.ui.GridItemActions
import hunoia.luno.gridpanel.ui.GridPageIndicator
import hunoia.luno.gridpanel.ui.GridPanelLayer
import hunoia.luno.gridpanel.ui.gridPanelCardColor
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

/** 拖拽到距可视区上下沿这么多 dp 内开始自动滚动（一墨 nz1 同款）。 */
private val EdgeScrollBand = 56.dp

/** 自动滚动上限步进（dp/帧，约 960dp/s），实际按越界深度在 25%~100% 间取。 */
private val EdgeScrollStep = 16.dp

/**
 * 卡片容器：几何 → 尺寸 → 位置动画 → HorizontalPager。
 *
 * 每页的 [ScrollState] **在这里创建并长期持有**（map + getOrPut）：以前在 pager 的
 * page lambda 里 remember，页面被回收后边缘滚动协程握着的可能是废弃实例，
 * 滚动会「滚一下就不动了」。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun GridPanelCard(
    panelId: String,
    editMode: Boolean,
    pageCount: Int,
    pagerState: PagerState,
    metrics: GridMetrics,
    anchorX: Int,
    anchorY: Int,
    insetsTop: Int,
    insetsBottom: Int,
    appearance: Float,
    cardAlpha: Float,
    state: GridPanelState,
    libraryEntries: List<NewActionLibraryEntry>,
    itemActions: (pageIndex: Int) -> GridItemActions,
    onSelectPage: (Int) -> Unit,
    onAdd: () -> Unit,
) {
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val marginPx = with(density) { CardMargin.toPx() }
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

    // 每页 scroll 状态由 Card 持有：翻页回收 page lambda 时不会丢。
    val pageScrolls = remember(panelId) { mutableMapOf<Int, ScrollState>() }
    // 页数被调小后清掉越界页的滚动状态，避免再调大时复用旧滚动位置。
    LaunchedEffect(pageCount, panelId) {
        pageScrolls.keys.retainAll(0 until pageCount)
    }

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
                RoundedCornerShape(CardCorner),
            )
            .clip(RoundedCornerShape(CardCorner)),
    ) {
        // 顶栏已删：日期/时间/编辑中提示/加项按钮全下线。多页指示点保留在顶部
        //（高度预算 IndicatorHeight 在 metrics 里照旧）；页切换靠点/横滑。
        Column(Modifier.fillMaxSize()) {
            if (pageCount > 1) {
                GridPageIndicator(
                    pageCount = pageCount,
                    activePage = pagerState.currentPage,
                    onSelectPage = onSelectPage,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                )
            }
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = gridPadDp),
            ) {
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize(),
                ) { pageIndex ->
                    val vScroll = remember(pageIndex, panelId) {
                        pageScrolls.getOrPut(pageIndex) { ScrollState(0) }
                    }
                    val edge = remember(pageIndex, panelId, bandPx, stepPx) {
                        EdgeAutoScroll(scope, state, bandPx, stepPx)
                    }
                    DisposableEffect(pageIndex) {
                        onDispose { edge.stop() }
                    }
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
                            onExpand = { item, rows -> baseActionsState.value.onExpand(item, rows) },
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
                            metrics = metrics,
                            editMode = editMode,
                            draggingId = if (dragOnThisPage) state.draggingId else null,
                            hoverCell = if (dragOnThisPage) state.hoverCell else null,
                            dragVisual = if (dragOnThisPage) state.dragVisual else androidx.compose.ui.geometry.Offset.Zero,
                            glideFrom = state.glideFrom,
                            resizingId = state.resizingId,
                            resizeSpanF = state.resizeSpanF,
                            libraryEntries = libraryEntries,
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
        // 编辑态加项入口：右下角悬浮按钮（顶栏删除后加项的唯一入口）。
        if (editMode) {
            FloatingActionButton(
                onClick = onAdd,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp),
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
