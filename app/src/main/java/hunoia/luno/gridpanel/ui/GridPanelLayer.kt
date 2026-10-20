package hunoia.luno.gridpanel.ui

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionOnScreen
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import hunoia.luno.R
import hunoia.luno.action.model.NewActionLibraryEntry
import hunoia.luno.config.model.GridCell
import hunoia.luno.config.model.GridPanelItem
import hunoia.luno.gridpanel.geometry.GridMetrics
import hunoia.luno.gridpanel.geometry.ItemCorner
import hunoia.luno.gridpanel.gesture.gridLayerGestures
import hunoia.luno.gridpanel.internal.InternalWidgetIds
import hunoia.luno.gridpanel.internal.MediaControlCell
import hunoia.luno.gridpanel.internal.VolumeSliderCell
import hunoia.luno.gridpanel.widget.PanelAppWidgetView
import hunoia.luno.gridpanel.widget.WidgetLoadState
import hunoia.luno.gridpanel.ui.gridPanelOnCardColor
import kotlin.math.roundToInt

/** 手柄命中区上限；小格按边长比例再缩小，避免盖住半个项。 */
private val ResizeHandleHit = 32.dp

/** 项的屏幕中心（点按执行时用）。用普通引用而不是 State：滚动时 onGloballyPositioned
 *  每帧都会触发，写 State 会让整个 Layer 每帧重组（滚动卡顿的来源）。 */
private class ItemCenterHolder {
    var value: IntOffset = IntOffset.Zero
}

/**
 * 项的交互回调集合。参数合并成一个对象，是为了让 [GridPanelLayer] 的签名
 * 从 30+ 收到 12 个 —— 以前每加一种手势就往 GridCanvas 上挂两个参数，
 * 最后一个调用点根本看不出哪个回调对应哪根手指。
 */
@Immutable
class GridItemActions(
    val onTap: (GridPanelItem, IntOffset) -> Unit,
    val onDelete: (GridPanelItem) -> Unit,
    val onDragStart: (GridPanelItem) -> Unit,
    val onDrag: (Offset) -> Unit,
    val onDragEnd: () -> Unit,
    val onDragPosition: ((GridPanelItem, contentY: Float) -> Unit)? = null,
    val onResizeStart: (GridPanelItem) -> Unit,
    val onResizeDelta: (Offset) -> Unit,
    val onResizeEnd: () -> Unit,
    /**
     * 内置小部件请求改变自身纵向 span（媒体控制的会话行数）。
     * 只扩不缩等语义由调用方决定；默认空实现 = 部件类型不动态变高。
     */
    val onExpand: (GridPanelItem, rows: Int) -> Unit = { _, _ -> },
)

/**
 * 网格内容层（原 GridCanvas）：空格/网格线、项循环、落位预览、空页提示。
 * 滚动容器在 [GridPanelCard]，这里只负责内容坐标。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun GridPanelLayer(
    items: List<GridPanelItem>,
    metrics: GridMetrics,
    editMode: Boolean,
    draggingId: String?,
    hoverCell: GridCell?,
    dragVisual: Offset,
    glideFrom: Pair<String, Offset>?,
    resizingId: String?,
    resizeSpanF: Offset?,
    libraryEntries: List<NewActionLibraryEntry>,
    actions: GridItemActions,
    /** 小部件内部滚不动时的竖直位移中继（正值 = 向下滚）。 */
    onWidgetScroll: ((dy: Float) -> Unit)? = null,
    /** 小部件抬手速度中继（正值 = 向下滚），用于面板惯性。 */
    onWidgetFling: ((velocity: Float) -> Unit)? = null,
) {
    val density = LocalDensity.current
    val view = LocalView.current
    val hitPx = with(density) { ResizeHandleHit.toPx() }
    val contentH = with(density) { metrics.contentH.toDp() }

    // 编辑态手势统一在层根（静止节点）上命中：项在拖拽中自身会移动，把
    // pointerInput 挂在项内部会让 positionChange() 被节点位移抵掉（半速跟手）。
    val layerGestures = if (editMode) {
        Modifier.gridLayerGestures(
            metrics = metrics,
            handleHitPx = hitPx,
            onTap = { item -> actions.onTap(item, IntOffset.Zero) },
            onDragStart = actions.onDragStart,
            onDrag = actions.onDrag,
            onDragEnd = actions.onDragEnd,
            onDragPosition = { item, contentY -> actions.onDragPosition?.invoke(item, contentY) },
            onResizeStart = actions.onResizeStart,
            onResizeDelta = actions.onResizeDelta,
            onResizeEnd = actions.onResizeEnd,
            itemsProvider = { items },
        )
    } else {
        Modifier
    }

    Box(Modifier.fillMaxWidth().height(contentH).then(layerGestures)) {
        if (editMode) {
            GridBackground(items = items, metrics = metrics)
        }
        if (items.isEmpty()) {
            Box(
                Modifier.fillMaxWidth().height(contentH),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(
                        if (editMode) {
                            R.string.grid_panel_empty_items_edit
                        } else {
                            R.string.grid_panel_empty_items
                        },
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = gridPanelOnCardColor,
                    textAlign = TextAlign.Center,
                )
            }
        }

        for (raw in items) {
            // key 必须有：remember/Animatable/AndroidView 是按组合位置记忆的，
            // 没有它，重排换位后各项会拿到别项的动画状态和宿主视图。
            key(raw.id) {
                // 越界兜底：列数/行数被改小、或数据来自旧版本时，项可能落在网格外。
                val item = raw.normalized(metrics.columns, metrics.rows)
                val dragging = item.id == draggingId
                val resizing = item.id == resizingId
                val spanX = if (resizing) resizeSpanF?.x ?: item.spanX.toFloat() else item.spanX.toFloat()
                val spanY = if (resizing) resizeSpanF?.y ?: item.spanY.toFloat() else item.spanY.toFloat()
                val itemW = metrics.itemW(spanX)
                val itemH = metrics.itemH(spanY)
                val sizeDp = with(density) { itemW.toDp() }
                val heightDp = with(density) { itemH.toDp() }

                val target = Offset(item.x * metrics.stepW, item.y * metrics.stepH)
                val glide = glideFrom?.takeIf { it.first == item.id }?.second
                val placement = rememberItemPlacement(
                    itemId = item.id,
                    target = target,
                    dragging = dragging,
                    glide = glide,
                )
                // 被拖项跟指：基位保持拖起时的格子，位移走 dragVisual。
                val visual = placement + if (dragging) dragVisual else Offset.Zero

                val itemCenter = remember(item.id) { ItemCenterHolder() }

                Box(
                    Modifier
                        .offset {
                            IntOffset(visual.x.roundToInt(), visual.y.roundToInt())
                        }
                        .size(sizeDp, heightDp)
                        .zIndex(if (dragging || resizing) 1f else 0f)
                        .onGloballyPositioned { coords ->
                            itemCenter.value = IntOffset(
                                coords.positionOnScreen().x.roundToInt() + coords.size.width / 2,
                                coords.positionOnScreen().y.roundToInt() + coords.size.height / 2,
                            )
                        }
                        .graphicsLayer {
                            if (dragging) {
                                scaleX = 1.06f
                                scaleY = 1.06f
                            }
                        },
                ) {
                    if (dragging) {
                        DragCrosshair(
                            color = gridPanelOnCardColor.copy(alpha = 0.25f),
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                    when (val kind = item.kind) {
                        is GridPanelItem.Kind.Action -> {
                            val action = kind.items.firstOrNull()
                            if (action == null) {
                                BrokenItemCell(Modifier.fillMaxSize())
                            } else {
                                // 运行态沿用 combinedClickable（与旧实现一致）：它和
                                // 外层 verticalScroll / pager 的协作是框架验证过的。
                                // 编辑态的拖拽/缩放/点按由层根 gridLayerGestures 统一
                                // 接管，项内不再挂任何手势。
                                val contentGesture = if (editMode) {
                                    Modifier
                                } else {
                                    Modifier.combinedClickable(
                                        onClick = { actions.onTap(item, itemCenter.value) },
                                        onLongClick = {
                                            view.performHapticFeedback(
                                                HapticFeedbackConstants.LONG_PRESS,
                                            )
                                        },
                                    )
                                }
                                ActionItemCell(
                                    action = action,
                                    dragging = dragging,
                                    // 图标尺寸由 ActionItemCell 内按「应用图标占满 /
                                    // 普通动作 72% 留边」区分，这里只传格子短边。
                                    cellSize = with(density) {
                                        minOf(itemW, itemH).toDp()
                                    },
                                    modifier = Modifier.fillMaxSize().then(contentGesture),
                                )
                            }
                        }

                        is GridPanelItem.Kind.Internal -> when (kind.widgetId) {
                            // 自绘部件自带点击目标，不走 ActionItemCell 那套整格 combinedClickable ——
                            // 按钮/滑条就是内容本身；编辑态交给层根手势，这里不动。
                            InternalWidgetIds.MEDIA -> MediaControlCell(
                                modifier = Modifier.fillMaxSize(),
                                onDesiredRows = { rows -> actions.onExpand(item, rows) },
                            )
                            InternalWidgetIds.VOLUME -> VolumeSliderCell(Modifier.fillMaxSize())
                            else -> BrokenItemCell(Modifier.fillMaxSize())
                        }

                        is GridPanelItem.Kind.AppWidget -> {
                            // 用含 gap 的真实像素换算 dp：按 cellDp × span 会漏掉跨格
                            // gap（3 格少 2×gap ≈ 12dp），provider 按偏小尺寸画会裁内容。
                            //
                            // resize 期间 options 按 spanF 的**整数格**实时下发：跨过
                            // 整格边界才重新 createView（provider 按新尺寸重排，即「实时
                            // 重排」）；连续跟手的帧只走 Compose 尺寸 → onMeasure 的 View
                            // 层重排，不重建 provider。松手 commit 后再协商一次。
                            val optionSpanX =
                                (if (resizing) resizeSpanF?.x ?: spanX else spanX)
                                    .roundToInt().coerceAtLeast(1).toFloat()
                            val optionSpanY =
                                (if (resizing) resizeSpanF?.y ?: spanY else spanY)
                                    .roundToInt().coerceAtLeast(1).toFloat()
                            val maxWidthDp =
                                (metrics.itemW(optionSpanX) / density.density).roundToInt()
                            val maxHeightDp =
                                (metrics.itemH(optionSpanY) / density.density).roundToInt()
                            var widgetState by remember(kind.appWidgetId) {
                                mutableStateOf(WidgetLoadState.Loading)
                            }
                            val stateListener: (WidgetLoadState) -> Unit = { widgetState = it }
                            AndroidView(
                                factory = { ctx -> PanelAppWidgetView(ctx) },
                                update = { hostView ->
                                    // 监听每帧重挂：update lambda 会随重组换新实例。
                                    hostView.setLoadStateListener(stateListener)
                                    hostView.onVerticalScroll = onWidgetScroll
                                    hostView.onFling = onWidgetFling
                                    hostView.updateWidget(
                                        kind.appWidgetId,
                                        kind.provider,
                                        maxWidthDp,
                                        maxHeightDp,
                                    )
                                },
                                // 故意不给 onRelease：翻页/关面板 ≠ 删小部件。
                                // 回收点只有 onDeleteItem 与设置页丢弃。
                                modifier = Modifier
                                    .size(sizeDp, heightDp)
                                    .clip(RoundedCornerShape(ItemCorner)),
                            )
                            WidgetStateOverlay(
                                state = widgetState,
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                    }

                    // 编辑态操作角标：删除（右上）+ 尺寸手柄（右下），常显。
                    // 子层排在最后 —— 子层先拿事件，点角标不会误起整项拖拽。
                    if (editMode) {
                        ItemDeleteBadge(
                            onDelete = { actions.onDelete(item) },
                            modifier = Modifier.align(Alignment.TopEnd),
                        )
                        // 所有项都带手柄（不再按尺寸门槛隐藏）。
                        // 小格上把手同步缩小：视觉件 min(20dp, 30% 边长)，
                        // 命中区在 gridLayerGestures 里取 min(32dp, 40% 边长)。
                        ItemResizeHandle(
                            modifier = Modifier.align(Alignment.BottomEnd),
                            handleSize = with(density) {
                                minOf(20.dp.toPx(), minOf(itemW, itemH) * 0.3f).toDp()
                            },
                        )
                    }
                }
            }
        }

        // 落位预览：zIndex 低于被拖项（1f），高亮的是「空位」而不是糊住被拖的卡。
        if (hoverCell != null) {
            val dragged = items.firstOrNull { it.id == draggingId }
            val spanX = dragged?.spanX ?: 1
            val spanY = dragged?.spanY ?: 1
            val previewW = metrics.itemW(spanX.toFloat())
            val previewH = metrics.itemH(spanY.toFloat())
            Box(
                Modifier
                    .offset {
                        IntOffset(
                            (hoverCell.x * metrics.stepW).roundToInt(),
                            (hoverCell.y * metrics.stepH).roundToInt(),
                        )
                    }
                    .size(
                        with(density) { previewW.toDp() },
                        with(density) { previewH.toDp() },
                    )
                    .zIndex(0.5f)
                    .background(
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
                        RoundedCornerShape(ItemCorner),
                    )
                    .border(
                        2.dp,
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.55f),
                        RoundedCornerShape(ItemCorner),
                    ),
            )
        }
    }
}

/**
 * 编辑态的空位与网格线。用**一个 Canvas** 而不是每格一个 Box：
 * 6×8 就是 48 个纯装饰节点，而我们只在空格上铺底（被占用的格画了也被盖住）。
 */
@Composable
private fun GridBackground(items: List<GridPanelItem>, metrics: GridMetrics) {
    val density = LocalDensity.current
    val occupied = HashSet<Int>(items.size * 4)
    for (it in items) {
        val n = it.normalized(metrics.columns, metrics.rows)
        for (gy in n.y until n.bottom) {
            for (gx in n.x until n.right) occupied.add(gy * metrics.columns + gx)
        }
    }
    val idleColor = gridPanelOnCardColor.copy(alpha = 0.06f)
    val lineColor = gridPanelOnCardColor.copy(alpha = 0.08f)
    val hairline = with(density) { 1.dp.toPx() }
    val corner = with(density) { ItemCorner.toPx() }
    Canvas(
        Modifier
            .fillMaxWidth()
            .size(
                with(density) { metrics.contentW.toDp() },
                with(density) { metrics.contentH.toDp() },
            ),
    ) {
        for (gy in 0 until metrics.rows) {
            for (gx in 0 until metrics.columns) {
                if (gy * metrics.columns + gx in occupied) continue
                drawRoundRect(
                    color = idleColor,
                    size = Size(metrics.cellW, metrics.cellH),
                    topLeft = Offset(gx * metrics.stepW, gy * metrics.stepH),
                    cornerRadius = CornerRadius(corner, corner),
                )
            }
        }
        for (gy in 0 until metrics.rows) {
            for (gx in 0 until metrics.columns) {
                if (gy * metrics.columns + gx in occupied) continue
                drawRoundRect(
                    color = lineColor,
                    size = Size(metrics.cellW, metrics.cellH),
                    topLeft = Offset(
                        gx * metrics.stepW + hairline / 2f,
                        gy * metrics.stepH + hairline / 2f,
                    ),
                    cornerRadius = CornerRadius(corner, corner),
                    style = Stroke(width = hairline),
                )
            }
        }
    }
}
