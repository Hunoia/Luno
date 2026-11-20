package hunoia.luno.gridpanel.internal

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.imageLoader
import hunoia.luno.runtime.surface.ClipFloatDispatch
import hunoia.luno.gridpanel.R
import hunoia.luno.bridge.system.FileOutput
import hunoia.luno.bridge.system.shareText
import hunoia.luno.bridge.ui.showToast
import hunoia.luno.clipboard.ClipboardCenter
import hunoia.luno.config.model.ClipKind
import hunoia.luno.config.model.ClipboardEntry
import hunoia.luno.config.model.ClipboardHistory
import hunoia.luno.gridpanel.LocalGridPanelClose
import hunoia.luno.gridpanel.LocalGridPanelDragGhost
import hunoia.luno.gridpanel.geometry.ItemCorner
import hunoia.luno.gridpanel.ui.gridPanelOnCardColor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import hunoia.luno.ui.component.PressIconButton
import hunoia.luno.ui.component.menu.LunoDropdownMenu
import hunoia.luno.ui.component.menu.LunoDropdownMenuItem
import hunoia.luno.ui.component.overlay.EdgeBarSlot
import hunoia.luno.ui.component.pressClickable
import hunoia.luno.ui.component.pressScale
import hunoia.luno.ui.theme.CardInnerSpacing
import hunoia.luno.ui.theme.ClipboardTabBarMinCellHeight
import hunoia.luno.ui.theme.GridCellSpacing
import hunoia.luno.ui.theme.MotionSelection
import hunoia.luno.ui.theme.SubMinInteractiveSize
import hunoia.luno.ui.theme.containerShape
import hunoia.luno.ui.theme.panelTileContainer

/**
 * 与 `InternalSysMonitorWidget` 同一口径：部件自有几何，不进主题 token（ui-guidelines §2.1）。
 * 低于阈值判定为「紧凑」：三点菜单缩到 20dp 无底片、文本上留白收紧。
 */
private val CompactMaxWidth = 210.dp
private val CompactMaxHeight = 190.dp

/**
 * 多列网格的最小格宽（≈2 个面板格）：宽度够才加一列，文字格不致过窄。
 * 面板卡宽固定（默认 0.72 × 屏宽），整宽部件内容宽恒约 231dp，因此上限 2 列；
 * 若下调到 <96dp，需同步收紧文本排版（两行在 fontScale 2.0 下会裁）。
 */
private val GridMinCell = 112.dp

/**
 * 剪贴板页面部件：历史（文本 + 图片缩略图，最新在最前），多列网格自适应宽度。
 *
 * 底部有「文本 / 图片」两个 tab，一次只显示一种类型（与剪贴板管理页同口径）；
 * 固定项在各自类型内置顶并带固定标识。文本格两行省略 / 图片格方形缩略图。
 *
 * 只读视图 + 最小操作（写回 / 固定 / 分享 / 删除 / 悬浮显示 / 文本拖到输入框）。
 * 管理页是主入口，这里不再有「当前主剪贴项」卡片，也不自己注册剪贴板监听 ——
 * 捕获由 `ClipboardCapture` 在常驻服务里统一做，本部件打开时补一次幂等
 * [ClipboardCenter.refresh]。
 *
 * 内外滚动约定：网格纵向滚动归本部件（`LazyVerticalGrid` 独立接手，不依赖面板滚动）。拖到边界后的剩余**拖动**仍交给面板（可以继续滑面板），但 fling 到边界后
 * 的剩余**惯性**由 [PanelFlingGate] 吃掉，不再带着面板跑；横向滑动不消费，留给翻页。
 */
@Composable
fun ClipboardPageCell(
    modifier: Modifier = Modifier,
    spanX: Int = 4,
    spanY: Int = 3,
    /** 编辑态=false：条目点按/菜单全禁，事件让给层根拖拽/缩放。 */
    enabled: Boolean = true,
) {
    val outerPadding = PaddingValues(WidgetInnerPadding)
    val closePanel = LocalGridPanelClose.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val ghost = LocalGridPanelDragGhost.current
    val history by ClipboardCenter.history.collectAsState(initial = ClipboardHistory())
    // null = 自动：只有一类有内容时直接停在那一类（图片型部件 / 编辑态不画 tab 栏也不会空着）。
    var chosenTab by remember { mutableStateOf<ClipKind?>(null) }
    val textEntries = remember(history.entries) { history.ofKind(ClipKind.TEXT) }
    val imageEntries = remember(history.entries) { history.ofKind(ClipKind.IMAGE) }
    val tab = chosenTab
        // 用户停在的类型被删空、另一类还有内容时回退（管理页删空 / 只新增另一类），
        // 不再卡在「暂无文本 / 暂无图片」。
        ?.takeIf { candidate ->
            when (candidate) {
                ClipKind.TEXT -> textEntries.isNotEmpty() || imageEntries.isEmpty()
                ClipKind.IMAGE -> imageEntries.isNotEmpty() || textEntries.isEmpty()
            }
        }
        ?: if (textEntries.isEmpty() && imageEntries.isNotEmpty()) ClipKind.IMAGE else ClipKind.TEXT
    // 拖拽动作条槽位：文案走 stringResource（配置变化会跟着重组；Context.getString 不会）。
    val dragSlots = ClipDragAction.entries.map { action ->
        EdgeBarSlot(
            icon = action.icon,
            label = stringResource(action.labelRes),
            id = action.name,
        )
    }

    // 打开部件补捕获一次（新 ROM 后台可能不投递监听回调；同内容幂等）。
    LaunchedEffect(Unit) {
        runCatching { ClipboardCenter.refresh() }
    }

    fun copyAndClose(entry: ClipboardEntry) {
        scope.launch {
            if (ClipboardCenter.copyEntry(entry)) {
                closePanel()
            } else {
                showToast(R.string.grid_panel_clipboard_copy_failed)
            }
        }
    }

    fun togglePin(entry: ClipboardEntry) {
        scope.launch {
            runCatching { ClipboardCenter.setPinned(entry.id, !entry.pinned) }
        }
    }

    fun share(entry: ClipboardEntry) {
        scope.launch(Dispatchers.IO) {
            val ok = when (entry.kind) {
                ClipKind.TEXT -> shareText(context, entry.text)
                ClipKind.IMAGE -> {
                    val file = ClipboardCenter.imageFile(entry.imagePath)
                    file != null &&
                        FileOutput.share(context, file, entry.mime ?: "image/*", file.name)
                }
            }
            if (!ok) showToast(R.string.grid_panel_clipboard_share_failed)
        }
    }

    /**
     * 拖拽落点：把文本输入到面板下面那个应用的输入框里。
     * 落点已经在行手势里用 `localToScreen` 换成了屏幕坐标。
     *
     * **先关面板**再粘贴：一是保证「拖了就一定关」，不依赖异步粘贴的结果；
     * 二是收掉面板后目标应用才能重新拿到窗口焦点，粘贴/聚焦都更可靠。
     * 粘贴本身跑在进程级 scope 上，不跟着面板组合一起被取消。
     */
    fun dropToInput(text: String, screenPosition: Offset) {
        ghost?.end()
        dropTextToInput(text, screenPosition, closePanel)
    }

    val imageLabel = stringResource(R.string.grid_panel_clipboard_image)

    // 全空：没有任何可切换的内容，直接空态（不画 tab）。
    if (history.entries.isEmpty()) {
        EmptyClipboardHint(
            message = stringResource(R.string.grid_panel_clipboard_empty),
            vertical = spanY > spanX,
            modifier = modifier.fillMaxSize().padding(outerPadding),
        )
        return
    }

    // 当前 tab 的条目：固定项置顶，其余按新→旧（`ClipboardHistory.ofKind` 一份口径）。
    val ordered = if (tab == ClipKind.TEXT) textEntries else imageEntries
    val gridState = rememberLazyGridState()
    // 切 tab 回顶：新内容从头部开始（固定组在顶）。
    LaunchedEffect(tab) { gridState.scrollToItem(0) }
    // 只拦惯性：网格 fling 到边后剩余速度不再带动面板；直接拖到边界仍会滑面板。
    val flingGate = remember(gridState) {
        PanelFlingGate { gridState.canScrollBackward || gridState.canScrollForward }
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .padding(outerPadding),
    ) {
        // 1 行高的格子放不下 tab 栏（会吃掉整个网格区）：此时只按自动类型显示，
        // 拉高部件后再切换；两者都从同一份 [chosenTab] 派生。
        val showTabs = enabled && maxHeight >= ClipboardTabBarMinCellHeight
        // 紧凑阈值与资源监控同一口径（部件自有几何，不进主题）：低于它收紧三点 / 文本排版。
        val compact = maxWidth < CompactMaxWidth || maxHeight < CompactMaxHeight
        // 列数由内容宽推（多列阈值在部件内，不再让主题层背单点 token）；上限占格，最少 1 列。
        val columns = (maxWidth / GridMinCell).toInt().coerceIn(1, spanX.coerceAtLeast(1))
        Column(modifier = Modifier.fillMaxSize()) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(columns),
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .nestedScroll(flingGate),
                state = gridState,
                verticalArrangement = Arrangement.spacedBy(GridCellSpacing),
                horizontalArrangement = Arrangement.spacedBy(GridCellSpacing),
                // 编辑态禁用内部滚动：否则在部件上拖会滚网格而不拖项。
                userScrollEnabled = enabled,
            ) {
                if (ordered.isEmpty()) {
                    item(key = "empty", span = { GridItemSpan(maxLineSpan) }) {
                        EmptyClipboardHint(
                            message = stringResource(
                                if (tab == ClipKind.TEXT) R.string.grid_panel_clipboard_empty_text
                                else R.string.grid_panel_clipboard_empty_image,
                            ),
                            vertical = spanY > spanX,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        )
                    }
                } else {
                    items(ordered, key = { it.id }) { entry ->
                        HistoryCell(
                            entry = entry,
                            enabled = enabled,
                            // 新条目进入 / 删除 / 固定重排走放置动画（ui-guidelines §3.2 第 6 条）。
                            modifier = Modifier.animateItem(),
                            compact = compact,
                            onCopy = { copyAndClose(entry) },
                            onPin = { togglePin(entry) },
                            onShare = { share(entry) },
                            onFloat = {
                                ClipFloatDispatch.show?.invoke(context, entry.id)
                                closePanel()
                            },
                            onDelete = {
                                scope.launch {
                                    runCatching { ClipboardCenter.remove(entry.id) }
                                }
                            },
                            onDragStart = { windowPos, screenPos ->
                                when {
                                    entry.kind == ClipKind.TEXT && entry.text.isNotBlank() ->
                                        ghost?.begin(entry.text, windowPos)
                                    entry.kind == ClipKind.IMAGE && !entry.imagePath.isNullOrBlank() ->
                                        ghost?.begin(imageLabel, windowPos)
                                }
                                ghost?.moveDrag(screenPos)
                            },
                            onDrag = { windowPos, screenPos ->
                                ghost?.move(windowPos)
                                ghost?.moveDrag(screenPos)
                            },
                            onDragThresholdPassed = {
                                ghost?.hideChrome()
                                ghost?.beginDrag(dragSlots)
                            },
                            onDragEnd = { screenPos ->
                                val action = ghost?.resolveDrag(screenPos)
                                    ?.let { id -> ClipDragAction.entries.firstOrNull { it.name == id } }
                                when (action) {
                                    // 落空（条体范围外松手）= 默认插入到落点；命中「取消」则反悔。
                                    ClipDragAction.CANCEL -> {
                                        ghost.showChrome()
                                        ghost.end()
                                    }
                                    null -> when {
                                        entry.kind == ClipKind.TEXT && entry.text.isNotBlank() ->
                                            dropToInput(entry.text, screenPos)
                                        entry.kind == ClipKind.IMAGE -> entry.imagePath
                                            ?.takeIf { it.isNotBlank() }
                                            ?.let { path ->
                                                ghost?.end()
                                                dropImageToInput(path, entry.mime, screenPos, closePanel)
                                            }
                                    }
                                    ClipDragAction.COPY -> {
                                        ghost.end()
                                        copyAndClose(entry)
                                    }
                                    ClipDragAction.FLOAT -> {
                                        ghost.end()
                                        ClipFloatDispatch.show?.invoke(context, entry.id)
                                        closePanel()
                                    }
                                    ClipDragAction.SHARE -> {
                                        ghost.end()
                                        share(entry)
                                    }
                                }
                            },
                            onDragCancel = {
                                ghost?.showChrome()
                                ghost?.end()
                                ghost?.endDrag()
                            },
                        )
                    }
                }
            }
            if (showTabs) {
                ClipboardTabBar(
                    tab = tab,
                    onTabChange = { chosenTab = it },
                    modifier = Modifier.fillMaxWidth().padding(top = WidgetInnerPadding),
                )
            }
        }
    }
}

/**
 * 多列网格里的历史格：文本（两行省略）或方形图片缩略图，角上叠固定徽标与三点菜单
 * （复制 / 固定 / 悬浮 / 分享 / 删除）。整格 [itemDrag]：短按写回并关面板；长按拖出
 * 插入 / 动作条（见 [dropToInput]）。
 */
@Composable
private fun HistoryCell(
    entry: ClipboardEntry,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    /** 部件整体短/窄（低于 [CompactMaxWidth] / [CompactMaxHeight]）：三点缩到 20dp、文本上留白收紧。 */
    compact: Boolean,
    onCopy: () -> Unit,
    onPin: () -> Unit,
    onShare: () -> Unit,
    onFloat: () -> Unit,
    onDelete: () -> Unit,
    onDragStart: (windowPos: Offset, screenPos: Offset) -> Unit,
    onDrag: (windowPos: Offset, screenPos: Offset) -> Unit,
    onDragEnd: (screenPos: Offset) -> Unit,
    onDragCancel: () -> Unit,
    /** 位移越过 8dp 阈值（一次拖拽成立）：调用方把面板本体淡出。 */
    onDragThresholdPassed: () -> Unit,
) {
    val onCard = gridPanelOnCardColor
    val context = LocalContext.current
    var menuOpen by remember { mutableStateOf(false) }
    val draggable = when (entry.kind) {
        ClipKind.TEXT -> entry.text.isNotBlank()
        ClipKind.IMAGE -> !entry.imagePath.isNullOrBlank()
    }
    // 按压缩放 + 读屏激活共用这一个 interaction source：手势在 itemDrag 里发 press 事件。
    val interactions = remember { MutableInteractionSource() }
    val menuSize = if (compact) 20.dp else SubMinInteractiveSize
    val imageDesc = stringResource(R.string.grid_panel_clipboard_image)
    val pinnedState = stringResource(R.string.grid_panel_clipboard_pinned)
    val copyLabel = stringResource(R.string.grid_panel_clipboard_copy)
    Box(
        modifier = modifier
            // 统一方形：网格不会把同排条目拉到等高，文本 1 行 / 2 行混排会锯齿。
            .fillMaxWidth()
            .aspectRatio(1f)
            // 与外层格子同心：外角 ItemCorner、内边距 WidgetInnerPadding（ui-guidelines §2.1.4）。
            .clip(containerShape(ItemCorner - WidgetInnerPadding))
            .then(
                if (enabled) {
                    Modifier.itemDrag(
                        key = entry.id,
                        draggable = draggable,
                        interactionSource = interactions,
                        handlers = ItemDragHandlers(
                            onTap = onCopy,
                            onDragStart = onDragStart,
                            onDrag = onDrag,
                            onDragEnd = onDragEnd,
                            onDragCancel = onDragCancel,
                            onDragThresholdPassed = onDragThresholdPassed,
                        ),
                    )
                } else {
                    Modifier
                },
            )
            // 与动作格 / 键盘键同一块面板方块色（不是不透明的卡色：面板卡透明度/模糊仍透得过来）。
            .background(panelTileContainer())
            .pressScale(interactions)
            .semantics {
                if (entry.kind == ClipKind.IMAGE) contentDescription = imageDesc
                if (entry.pinned) stateDescription = pinnedState
                onClick(label = copyLabel) { onCopy(); true }
            },
    ) {
        when (entry.kind) {
            ClipKind.TEXT -> Text(
                text = entry.text,
                style = MaterialTheme.typography.bodySmall,
                color = onCard,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .fillMaxWidth()
                    // 上留白让开固定徽标（16dp + 3dp 底片）；右留白让开三点菜单命中区。
                    .padding(
                        start = CardInnerSpacing,
                        end = if (enabled) menuSize + 4.dp else CardInnerSpacing,
                        top = if (compact) 14.dp else 18.dp,
                        bottom = CardInnerSpacing,
                    ),
            )
            ClipKind.IMAGE -> {
                val file = remember(entry.imagePath) { ClipboardCenter.imageFile(entry.imagePath) }
                AsyncImage(
                    model = file,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    // 满幅贴边：方形缩略图直接裁到格子的圆角，不再叠一层看不见的内角裁切。
                    modifier = Modifier.fillMaxSize(),
                    imageLoader = context.imageLoader,
                )
            }
        }
        // 固定徽标（左上）：盖在内容角上，给个底片保证图片上可读。
        if (entry.pinned) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(3.dp)
                    .size(16.dp)
                    // 底片用 surface 系（同删除角标口径），不随格子的面板方块色一起变。
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.75f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Default.PushPin,
                    contentDescription = stringResource(R.string.grid_panel_clipboard_pinned),
                    tint = onCard.copy(alpha = 0.6f),
                    modifier = Modifier.size(10.dp),
                )
            }
        }
        // 三点菜单（右上）：盖在内容角上，给个底片保证图片上可读。
        if (enabled) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(2.dp),
            ) {
                PressIconButton(
                    onClick = { menuOpen = true },
                    // 常规 36dp（ui-guidelines §1.6：密排网格内下调，格子约 113dp 宽仍不冲突）；
                    // 紧凑档缩到 20dp 无底片，让出整格给文字 / 缩略图。
                    modifier = Modifier.size(menuSize),
                ) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = stringResource(R.string.grid_panel_clipboard_more),
                        tint = onCard.copy(alpha = 0.7f),
                        modifier = Modifier.size(if (compact) 14.dp else 16.dp),
                    )
                }
                LunoDropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    LunoDropdownMenuItem(
                        text = stringResource(R.string.grid_panel_clipboard_copy),
                        onClick = { menuOpen = false; onCopy() },
                    )
                    LunoDropdownMenuItem(
                        text = stringResource(
                            if (entry.pinned) R.string.grid_panel_clipboard_unpin
                            else R.string.grid_panel_clipboard_pin,
                        ),
                        onClick = { menuOpen = false; onPin() },
                    )
                    LunoDropdownMenuItem(
                        text = stringResource(R.string.grid_panel_clipboard_float),
                        onClick = { menuOpen = false; onFloat() },
                    )
                    LunoDropdownMenuItem(
                        text = stringResource(R.string.grid_panel_clipboard_share),
                        onClick = { menuOpen = false; onShare() },
                    )
                    LunoDropdownMenuItem(
                        text = stringResource(R.string.grid_panel_clipboard_delete),
                        onClick = { menuOpen = false; onDelete() },
                        isError = true,
                    )
                }
            }
        }
    }
}

/** 底部「文本 / 图片」tab：图标分段，一次只显示一种类型，与剪贴板管理页同口径。 */
@Composable
private fun ClipboardTabBar(
    tab: ClipKind,
    onTabChange: (ClipKind) -> Unit,
    modifier: Modifier = Modifier,
) {
    val onCard = gridPanelOnCardColor
    // 与外层格子同心（§2.1.4）：外角 ItemCorner − WidgetInnerPadding。
    val shape = containerShape(ItemCorner - WidgetInnerPadding)
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(GridCellSpacing),
    ) {
        ClipboardTabItem(
            kind = ClipKind.TEXT,
            selected = tab,
            onTabChange = onTabChange,
            onCard = onCard,
            shape = shape,
            modifier = Modifier.weight(1f),
        )
        ClipboardTabItem(
            kind = ClipKind.IMAGE,
            selected = tab,
            onTabChange = onTabChange,
            onCard = onCard,
            shape = shape,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun ClipboardTabItem(
    kind: ClipKind,
    selected: ClipKind,
    onTabChange: (ClipKind) -> Unit,
    onCard: Color,
    shape: Shape,
    modifier: Modifier = Modifier,
) {
    val isSelected = kind == selected
    val primary = MaterialTheme.colorScheme.primary
    val icon = if (kind == ClipKind.TEXT) Icons.Default.TextFields else Icons.Default.Image
    val desc = stringResource(
        if (kind == ClipKind.TEXT) R.string.grid_panel_clipboard_text
        else R.string.grid_panel_clipboard_image,
    )
    // 选中态用 15% primary 底 + primary 图标（同面板其它选中态口径，SysMonitor 12% 那一档），
    // 不用整块实心 primary——面板里最重的一块会跳出来；颜色过渡走 MotionSelection（§5.1）。
    val bg by animateColorAsState(
        targetValue = if (isSelected) primary.copy(alpha = 0.15f) else Color.Transparent,
        animationSpec = tween(MotionSelection),
        label = "clipboardTabBg",
    )
    Box(
        modifier = modifier
            .clip(shape)
            .background(bg)
            .pressClickable(role = Role.Tab, onClick = { onTabChange(kind) })
            .semantics { this.selected = isSelected },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = desc,
            tint = if (isSelected) primary else onCard.copy(alpha = 0.7f),
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 10.dp)
                .size(18.dp),
        )
    }
}

@Composable
private fun EmptyClipboardHint(
    message: String,
    vertical: Boolean,
    modifier: Modifier = Modifier,
) {
    val onCard = gridPanelOnCardColor
    val badge: @Composable () -> Unit = {
        Icon(
            imageVector = Icons.Default.ContentPaste,
            contentDescription = null,
            tint = onCard,
            modifier = Modifier.size(18.dp),
        )
    }
    val label: @Composable () -> Unit = {
        Text(
            text = message,
            style = MaterialTheme.typography.bodySmall,
            color = onCard,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
    Box(
        modifier = modifier.alpha(0.45f).padding(horizontal = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        // 竖向格只有一格宽，横排放不下文案。
        if (vertical) {
            Column(
                verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterVertically),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                badge()
                label()
            }
        } else {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                badge()
                label()
            }
        }
    }
}
