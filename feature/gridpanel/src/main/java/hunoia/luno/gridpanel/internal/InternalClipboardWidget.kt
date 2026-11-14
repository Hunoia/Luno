package hunoia.luno.gridpanel.internal

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.imageLoader
import hunoia.luno.runtime.surface.ClipFloatDispatch
import hunoia.luno.gridpanel.R
import hunoia.luno.bridge.ui.showToast
import hunoia.luno.clipboard.ClipboardCenter
import hunoia.luno.config.model.ClipKind
import hunoia.luno.config.model.ClipboardEntry
import hunoia.luno.config.model.ClipboardHistory
import hunoia.luno.gridpanel.LocalGridPanelClose
import hunoia.luno.gridpanel.LocalGridPanelDragGhost
import hunoia.luno.gridpanel.geometry.ItemCorner
import hunoia.luno.gridpanel.ui.gridPanelOnCardColor
import kotlinx.coroutines.launch
import hunoia.luno.ui.theme.containerShape
import hunoia.luno.ui.component.PressIconButton

/**
 * 剪贴板页面部件：历史列表（文本 + 图片缩略图，最新在最前）。
 *
 * 只读视图 + 最小操作（写回 / 删除 / 清空 / 文本拖到输入框）。管理页是主入口，
 * 这里不再有「当前主剪贴项」卡片，也不自己注册剪贴板监听 —— 捕获由
 * `ClipboardCapture` 在常驻服务里统一做，本部件打开时补一次幂等 [ClipboardCenter.refresh]。
 *
 * 内外滚动约定：列表纵向滚动归本部件（`LazyColumn` 内嵌在面板的 `verticalScroll`
 * 里，Compose 嵌套滚动会在内层滚到底后把剩余拖动交给面板）；横向滑动不消费，
 * 留给面板翻页。
 */
@Composable
fun ClipboardPageCell(
    modifier: Modifier = Modifier,
    spanX: Int = 4,
    spanY: Int = 3,
    /** 编辑态=false：条目点按/菜单全禁，事件让给层根拖拽/缩放。 */
    enabled: Boolean = true,
    /** 网格项用与动作项同档的 4dp 内边距；单功能面板整页保留原有的 8/6dp。 */
    gridItem: Boolean = false,
) {
    val outerPadding = if (gridItem) {
        PaddingValues(WidgetInnerPadding)
    } else {
        PaddingValues(horizontal = 8.dp, vertical = 6.dp)
    }
    val closePanel = LocalGridPanelClose.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val ghost = LocalGridPanelDragGhost.current
    val history by ClipboardCenter.history.collectAsState(initial = ClipboardHistory())

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

    if (history.entries.isEmpty()) {
        EmptyClipboardHint(
            vertical = spanY > spanX,
            modifier = modifier.fillMaxSize().padding(outerPadding),
        )
        return
    }
    val imageLabel = stringResource(R.string.grid_panel_clipboard_image)
    // 固定项永远排在前面：并入剪贴板后它就是「随手可取」的那一组。
    val ordered = history.pinned + history.unpinned
    LazyColumn(
        modifier = modifier.fillMaxSize().padding(outerPadding),
        contentPadding = PaddingValues(vertical = 2.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
        // 编辑态禁用内部滚动：否则在部件上拖会滚列表而不拖项。
        userScrollEnabled = enabled,
    ) {
        items(ordered, key = { it.id }) { entry ->
            HistoryRow(
                entry = entry,
                enabled = enabled,
                onCopy = { copyAndClose(entry) },
                onFloat = {
                    ClipFloatDispatch.show?.invoke(context, entry.id)
                    closePanel()
                },
                onDelete = {
                    scope.launch {
                        runCatching { ClipboardCenter.remove(entry.id) }
                    }
                },
                onClearAll = {
                    scope.launch {
                        runCatching { ClipboardCenter.clear() }
                    }
                },
                onDragStart = { windowPos, _ ->
                    when {
                        entry.kind == ClipKind.TEXT && entry.text.isNotBlank() ->
                            ghost?.begin(entry.text, windowPos)
                        entry.kind == ClipKind.IMAGE && !entry.imagePath.isNullOrBlank() ->
                            ghost?.begin(imageLabel, windowPos)
                    }
                },
                onDrag = { windowPos, _ -> ghost?.move(windowPos) },
                onDragEnd = { screenPos ->
                    when {
                        entry.kind == ClipKind.TEXT && entry.text.isNotBlank() ->
                            dropToInput(entry.text, screenPos)
                        entry.kind == ClipKind.IMAGE -> entry.imagePath
                            ?.takeIf { it.isNotBlank() }
                            ?.let { path ->
                                ghost?.end()
                                dropImageToInput(path, entry.mime, screenPos, closePanel)
                            }
                    }
                },
                onDragCancel = { ghost?.showChrome(); ghost?.end() },
                onDragThresholdPassed = { ghost?.hideChrome() },
            )
        }
    }
}

/** 历史行：文本（两行省略）或图片缩略图 + 三点菜单（复制 / 悬浮 / 删除 / 清空全部）。
 * 手势：短按写回并关面板；文本长按拖出插入，图片长按拖出粘贴（见 [dropToInput]）。 */
@Composable
private fun HistoryRow(
    entry: ClipboardEntry,
    enabled: Boolean,
    onCopy: () -> Unit,
    onFloat: () -> Unit,
    onDelete: () -> Unit,
    onClearAll: () -> Unit,
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
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(containerShape(ItemCorner * 0.5f))
            .then(
                if (enabled) {
                    Modifier.itemDrag(
                        key = entry.id,
                        draggable = draggable,
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
            .padding(start = 6.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        when (entry.kind) {
            ClipKind.TEXT -> Text(
                text = entry.text,
                style = MaterialTheme.typography.bodySmall,
                color = onCard,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            ClipKind.IMAGE -> {
                Text(
                    text = stringResource(R.string.grid_panel_clipboard_image),
                    style = MaterialTheme.typography.bodySmall,
                    color = onCard,
                    modifier = Modifier.weight(1f),
                )
                val file = remember(entry.imagePath) { ClipboardCenter.imageFile(entry.imagePath) }
                AsyncImage(
                    model = file,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(28.dp)
                        .clip(containerShape(ItemCorner * 0.4f)),
                    imageLoader = context.imageLoader,
                )
            }
        }
        if (enabled) {
            Box {
                PressIconButton(
                    onClick = { menuOpen = true },
                    modifier = Modifier.size(28.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = stringResource(R.string.grid_panel_clipboard_more),
                        tint = onCard.copy(alpha = 0.7f),
                        modifier = Modifier.size(16.dp),
                    )
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.grid_panel_clipboard_copy)) },
                        onClick = { menuOpen = false; onCopy() },
                    )
                    if (!entry.sensitive) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.grid_panel_clipboard_float)) },
                            onClick = { menuOpen = false; onFloat() },
                        )
                    }
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.grid_panel_clipboard_delete)) },
                        onClick = { menuOpen = false; onDelete() },
                    )
                    // 清空入口挂在条目菜单里：部件不设标题栏，不做常驻按钮占高度。
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.grid_panel_clipboard_clear)) },
                        onClick = { menuOpen = false; onClearAll() },
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyClipboardHint(vertical: Boolean, modifier: Modifier = Modifier) {
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
            text = stringResource(R.string.grid_panel_clipboard_empty),
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

