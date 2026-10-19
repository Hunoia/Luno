package hunoia.luno.gridpanel

import android.view.HapticFeedbackConstants
import android.view.WindowInsets
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionOnScreen
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import coil.compose.AsyncImage
import coil.imageLoader
import com.aaron.compose.ktx.toDp
import hunoia.luno.R
import hunoia.luno.action.model.StoredAction
import hunoia.luno.action.model.longPressVariant
import hunoia.luno.action.model.withoutVariantKey
import hunoia.luno.bridge.ui.showToast
import hunoia.luno.config.model.GridPanel
import hunoia.luno.config.model.GridPanelDefaults
import hunoia.luno.domain.tune.TuneConfig
import hunoia.luno.ui.component.TextIcon
import hunoia.luno.ui.component.TextIconGlyph
import hunoia.luno.ui.component.actionIcon
import hunoia.luno.ui.component.actionText
import hunoia.luno.ui.component.row.LunoSettingsRow
import hunoia.luno.ui.navigation.ActionSelect
import hunoia.luno.ui.navigation.LocalActionLibraryEntries
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** 卡片宽占屏宽比例；其余留给 scrim 与锚点偏移。 */
private const val CardWidthFraction = 0.72f
private val CardMargin = 12.dp
private val CardCorner = 20.dp
private val GridSpacing = 6.dp
/** 卡片本体透明度：半透明透出原背景（用户拍板：背景不遮罩）。 */
private const val CardSurfaceAlpha = 0.75f

/**
 * 内容键：跟随内容而非槽位（拖拽换位时 `animateItem` 才有重排动画），
 * 同内容用「出现序号」消歧。必须与项列表在同一快照里一起写入对应状态。
 */
private fun gridItemKeys(items: List<StoredAction>): List<String> {
    val seen = HashMap<Int, Int>()
    return items.map { action ->
        val nth = seen.merge(action.hashCode(), 1, Int::plus) ?: 1
        "${action.hashCode()}_$nth"
    }
}

/**
 * 网格面板（docs/grid-panel.md）：
 * 运行态 —— 点项执行、长按项执行变体（无变体回落主动作，同弧形面板 selectedAction）；
 * 编辑态 —— 长按空白进出、点项弹菜单、点「+」加项、长按拖拽排序。
 *
 * 项执行统一走 [dispatch]（宿主转发进容器 runGestureActions），本组合不私自分发。
 */
@Composable
fun GridPanelRoot(
    panel: GridPanel?,
    anchorX: Int,
    anchorY: Int,
    dispatch: (action: StoredAction, screenX: Int, screenY: Int) -> Boolean,
    onPickItem: (ActionSelect) -> Unit,
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

    var editMode by remember(panel.id) { mutableStateOf(false) }

    // 项列表的本地真身：拖拽要连续多次换位，DataStore 回流有延迟，
    // 直接渲染 panel.items 会在快速拖动时被旧快照回跳。
    // 回流同步只认「与 lastWritten 不同」的变更（自己写出去的回声不覆盖本地）。
    var localItems by remember(panel.id) { mutableStateOf(panel.items) }
    // 键表与项**在同一个快照里一起写**，不能做成 remember(localItems) 的派生值：
    // LazyGrid 的间隔表是 derivedStateOf（快照通知阶段就是新的），而 key lambda 走
    // rememberUpdatedState（要等组合跑完才换）。两者错开一拍时，每加一项就是
    // 「Index N out of bounds for length N」的 IndexOutOfBoundsException。
    var localKeys by remember(panel.id) { mutableStateOf(gridItemKeys(panel.items)) }
    var lastWritten by remember(panel.id) { mutableStateOf(panel.items) }
    LaunchedEffect(panel.items) {
        if (panel.items != lastWritten) {
            localItems = panel.items
            localKeys = gridItemKeys(panel.items)
            lastWritten = panel.items
        }
    }

    // 卡片几何：宽 = 屏宽比例，高 = 宽/宽高比；锚点近屏缘时夹取屏内（系统栏余量在内）。
    val density = LocalDensity.current
    val view = LocalView.current
    // 走 LocalConfiguration 而不是 displayMetrics：组合必须订阅配置变化才会在转屏后
    // 重算（只读 metrics 的组合不会重组，文档 §7 的「转屏重算」靠的就是这次订阅）。
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
    val ratio = panel.aspectRatio.coerceIn(GridPanelDefaults.MinAspectRatio, GridPanelDefaults.MaxAspectRatio)
    val cardW = screenW * CardWidthFraction
    val cardH = cardW / ratio
    val posX = (anchorX - cardW / 4f).coerceIn(marginPx, (screenW - cardW - marginPx).coerceAtLeast(marginPx))
    val posY = (anchorY - cardH / 4f).coerceIn(
        insetsTop + marginPx,
        (screenH - insetsBottom - cardH - marginPx).coerceAtLeast(insetsTop + marginPx),
    )

    // 编辑态拖拽与菜单
    var draggingIndex by remember { mutableIntStateOf(-1) }
    var dragOffset by remember { mutableStateOf(Offset.Zero) }
    var menuIndex by remember { mutableIntStateOf(-1) }
    val gridState = rememberLazyGridState()
    var cellSize by remember { mutableStateOf(IntSize.Zero) }
    val gapPx = with(density) { GridSpacing.toPx() }
    val columns = panel.columnCount.coerceIn(GridPanelDefaults.MinColumnCount, GridPanelDefaults.MaxColumnCount)

    fun commitItems(next: List<StoredAction>) {
        localItems = next
        localKeys = gridItemKeys(next)
        lastWritten = next
        scope.launch {
            TuneConfig.updateAction { settings ->
                val latest = settings.actionGridPanels.panelById(panel.id) ?: return@updateAction settings
                settings.copy(actionGridPanels = settings.actionGridPanels.withPanel(latest.copy(items = next)))
            }
        }
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

    /** 拖拽中的一个增量：把「视觉位置」换算回网格下标，跨格即换位（含滚动补偿）。 */
    fun onItemDrag(index: Int, amount: Offset) {
        if (draggingIndex < 0 || cellSize == IntSize.Zero) return
        dragOffset += amount
        val cellW = cellSize.width + gapPx
        val cellH = cellSize.height + gapPx
        val visibleRowStart = gridState.firstVisibleItemIndex / columns
        val scrollPx = visibleRowStart * cellH + gridState.firstVisibleItemScrollOffset
        val slotLeft = (index % columns) * cellW
        val slotTop = (index / columns) * cellH - scrollPx
        val visual = Offset(slotLeft + dragOffset.x, slotTop + dragOffset.y)
        val col = (visual.x / cellW).toInt().coerceIn(0, columns - 1)
        val row = (visual.y / cellH).toInt().coerceIn(0, (localItems.size - 1) / columns)
        val target = row * columns + col
        if (target in localItems.indices && target != index) {
            commitItems(localItems.toMutableList().apply { add(target, removeAt(index)) })
            draggingIndex = target
            // 槽位变了，反向补偿拖拽偏移，保持视觉跟手。
            dragOffset += Offset(
                slotLeft - (target % columns) * cellW,
                slotTop - ((target / columns) * cellH - scrollPx),
            )
        }
    }

    // 单一手势裁判挂在根 Box：子级（格子）消费掉的触摸不会重复触发（detectTapGestures
    // 遇到已消费事件即取消）。格子空白处与卡片外都能长按进出编辑、点按关闭。
    // 背景不加遮罩：卡片本体半透明，原背景直接透出（用户拍板：背景透明 + 面板半透明）。
    Box(
        Modifier
            .fillMaxSize()
            .pointerInput(editMode) {
                detectTapGestures(
                    onTap = { if (editMode) editMode = false else onClose() },
                    onLongPress = { editMode = !editMode },
                )
            },
    ) {
        Box(
            Modifier
                .offset { IntOffset(posX.roundToInt(), posY.roundToInt()) }
                .size(with(density) { cardW.toDp() }, with(density) { cardH.toDp() })
                .graphicsLayer {
                    alpha = appearance.value
                    scaleX = 0.92f + 0.08f * appearance.value
                    scaleY = scaleX
                }
                .background(
                    MaterialTheme.colorScheme.surface.copy(alpha = CardSurfaceAlpha),
                    RoundedCornerShape(CardCorner),
                )
                .clip(RoundedCornerShape(CardCorner)),
        ) {
            Column(Modifier.fillMaxSize()) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = if (editMode) {
                            stringResource(R.string.grid_panel_edit_mode)
                        } else {
                            panel.name
                        },
                        style = MaterialTheme.typography.labelLarge,
                        color = if (editMode) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (localItems.isEmpty()) {
                    // 空面板：非编辑态给引导文案；**编辑态必须给 + 格**——否则
                    // 第一项永远加不上（面板本体是编辑入口，空态不能没有入口）。
                    Column(Modifier.weight(1f).fillMaxWidth()) {
                        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                            Text(
                                text = stringResource(R.string.grid_panel_empty_items),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                            )
                        }
                        if (editMode) {
                            AddCell(
                                onClick = { onPickItem(ActionSelect(gridPanelId = panel.id)) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                            )
                        }
                    }
                } else {
                    // 内容键：跟随内容而非槽位，拖拽换位时 animateItem 才有重排动画；
                    // 重复项用「出现序号」消歧。键表读的是与 localItems 同步写入的状态，
                    // 不是组合期缓存（见 localKeys 的注释）。
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(columns),
                        state = gridState,
                        modifier = Modifier.weight(1f).padding(horizontal = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(GridSpacing),
                        verticalArrangement = Arrangement.spacedBy(GridSpacing),
                        userScrollEnabled = draggingIndex < 0,
                    ) {
                        items(
                            count = localItems.size,
                            key = { index -> localKeys[index] },
                        ) { index ->
                            val action = localItems[index]
                            var cellCenter by remember { mutableStateOf(IntOffset.Zero) }
                            GridItemCell(
                                modifier = Modifier.animateItem(),
                                action = action,
                                label = actionText(
                                    action,
                                    emptyIfNone = false,
                                    actionLibraryEntries = libraryEntries,
                                ),
                                editMode = editMode,
                                dragging = index == draggingIndex,
                                dragTranslation = if (index == draggingIndex) dragOffset else Offset.Zero,
                                onGeometry = { size, center ->
                                    cellSize = size
                                    cellCenter = center
                                },
                                onTap = {
                                    if (editMode) menuIndex = index
                                    else execute(action.withoutVariantKey(), cellCenter.x, cellCenter.y)
                                },
                                onLongTap = {
                                    if (!editMode) {
                                        val variant = action.longPressVariant()?.withoutVariantKey()
                                            ?: action.withoutVariantKey()
                                        execute(variant, cellCenter.x, cellCenter.y)
                                    }
                                },
                                onDragStart = {
                                    draggingIndex = index
                                    dragOffset = Offset.Zero
                                    view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                                },
                                onDrag = { amount -> onItemDrag(index, amount) },
                                onDragEnd = { draggingIndex = -1 },
                            )
                        }
                        if (editMode) {
                            item(key = "add_cell") {
                                AddCell(
                                    onClick = { onPickItem(ActionSelect(gridPanelId = panel.id)) },
                                    modifier = Modifier.animateItem(),
                                )
                            }
                        }
                    }
                }
                if (editMode) {
                    Text(
                        text = stringResource(R.string.grid_panel_edit_hint),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }

    // 项编辑菜单（编辑态点已有项）
    if (menuIndex in localItems.indices) {
        val item = localItems[menuIndex]
        GridPanelBottomSheet(onDismiss = { menuIndex = -1 }) {
            LunoSettingsRow(
                title = stringResource(R.string.grid_panel_item_menu_change),
                onClick = {
                    val index = menuIndex
                    menuIndex = -1
                    onPickItem(
                        ActionSelect(
                            gridPanelId = panel.id,
                            gridPanelItemIndex = index,
                            gridPanelReplaceItem = true,
                        ),
                    )
                },
            )
            LunoSettingsRow(
                title = stringResource(R.string.grid_panel_item_menu_long_press),
                subtitle = if (item.longPressVariant() != null) {
                    stringResource(R.string.grid_panel_variant_set)
                } else {
                    ""
                },
                onClick = {
                    val index = menuIndex
                    menuIndex = -1
                    onPickItem(ActionSelect(gridPanelId = panel.id, gridPanelItemIndex = index))
                },
            )
            LunoSettingsRow(
                title = stringResource(R.string.grid_panel_item_menu_delete),
                onClick = {
                    val index = menuIndex
                    menuIndex = -1
                    commitItems(localItems.toMutableList().apply { removeAt(index) })
                },
            )
            LunoSettingsRow(
                title = stringResource(R.string.grid_panel_cancel),
                onClick = { menuIndex = -1 },
            )
        }
    }
}

/**
 * 单个网格格。运行态：点=执行主动作，长按=执行变体；编辑态：点=菜单，
 * 长按拖拽=换位。编辑态下 combinedClickable 的 onLongClick 给空 lambda 消费掉
 * 长按，否则根裁判的长按（进出编辑）会跟拖拽抢同一根手指。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun GridItemCell(
    modifier: Modifier = Modifier,
    action: StoredAction,
    label: String,
    editMode: Boolean,
    dragging: Boolean,
    dragTranslation: Offset,
    onGeometry: (IntSize, IntOffset) -> Unit,
    onTap: () -> Unit,
    onLongTap: () -> Unit,
    onDragStart: () -> Unit,
    onDrag: (Offset) -> Unit,
    onDragEnd: () -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer {
                if (dragging) {
                    translationX = dragTranslation.x
                    translationY = dragTranslation.y
                    scaleX = 1.06f
                    scaleY = 1.06f
                }
            }
            .zIndex(if (dragging) 1f else 0f)
            .onGloballyPositioned { coords ->
                onGeometry(
                    IntSize(coords.size.width, coords.size.height),
                    // 屏幕坐标（非窗口坐标）：本窗口原点会被 correctWindowOrigin 按 ROM
                    // 实测修正，窗口坐标与屏幕坐标未必同原点，转发的锚点要的是屏幕坐标。
                    IntOffset(
                        coords.positionOnScreen().x.roundToInt() + coords.size.width / 2,
                        coords.positionOnScreen().y.roundToInt() + coords.size.height / 2,
                    ),
                )
            }
            .combinedClickable(
                onClick = onTap,
                onLongClick = if (editMode) ({ }) else onLongTap,
            )
            .pointerInput(editMode) {
                if (!editMode) return@pointerInput
                detectDragGesturesAfterLongPress(
                    onDragStart = { onDragStart() },
                    onDrag = { change, amount ->
                        change.consume()
                        onDrag(amount)
                    },
                    onDragEnd = { onDragEnd() },
                    onDragCancel = { onDragEnd() },
                )
            }
            .background(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                shape = RoundedCornerShape(14.dp),
            )
            .padding(vertical = 10.dp, horizontal = 4.dp),
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

/** 编辑态尾部的「+」格。 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AddCell(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val addCellDescription = stringResource(R.string.grid_panel_add_item)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick)
            .background(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                shape = RoundedCornerShape(14.dp),
            )
            .padding(vertical = 22.dp),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Default.Add,
            contentDescription = addCellDescription,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun GridPanelBottomSheet(
    onDismiss: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.32f))
            .pointerInput(Unit) { detectTapGestures(onTap = { onDismiss() }) }
    ) {
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceContainer, RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                .padding(top = 8.dp)
        ) {
            Box(
                Modifier.fillMaxWidth().padding(vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    Modifier.size(36.dp, 4.dp).background(Color.Gray.copy(alpha = 0.4f), RoundedCornerShape(2.dp))
                )
            }
            Column(Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
                content()
            }
        }
    }
}
