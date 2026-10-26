package hunoia.luno.gridpanel.internal

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.imageLoader
import hunoia.luno.R
import hunoia.luno.bridge.system.ClipboardContent
import hunoia.luno.bridge.ui.showToast
import hunoia.luno.config.ConfigProvider
import hunoia.luno.config.model.ClipboardHistory
import hunoia.luno.config.model.ClipboardHistoryEntry
import hunoia.luno.domain.clipboard.ClipboardCenter
import hunoia.luno.gridpanel.LocalGridPanelClose
import hunoia.luno.gridpanel.geometry.ItemCorner
import hunoia.luno.gridpanel.ui.gridPanelOnCardColor
import kotlinx.coroutines.launch

/**
 * 剪贴板页面部件：顶部「当前主剪贴项」+ 下面的历史列表（纯文本，最新在最前）。
 *
 * 内外滚动约定：列表纵向滚动归本部件（`LazyColumn` 内嵌在面板的 `verticalScroll`
 * 里，Compose 嵌套滚动会在内层滚到底后把剩余拖动交给面板）；横向滑动不消费，
 * 留给面板翻页。
 * 捕获时机：本部件每次进入组合（= 面板打开）读一次当前剪贴项；剪贴板球弹出时
 * 另有一条捕获（见 FloatBallService）。
 */
@Composable
fun ClipboardPageCell(
    modifier: Modifier = Modifier,
    spanX: Int = 4,
    spanY: Int = 3,
    /** 编辑态=false：条目点按/菜单全禁，事件让给层根拖拽/缩放。 */
    enabled: Boolean = true,
) {
    val closePanel = LocalGridPanelClose.current
    val scope = rememberCoroutineScope()
    val history by ConfigProvider.clipboardHistory.collectAsState(initial = ClipboardHistory())
    var current by remember { mutableStateOf<ClipboardContent?>(null) }

    // 打开部件即抓一次当前主剪贴项（新 ROM 后台监听不可用，这是主要捕获时机）。
    LaunchedEffect(Unit) {
        val content = ClipboardCenter.readCurrent()
        current = content
        ClipboardCenter.record(content)
    }

    fun copyAndClose(text: String) {
        scope.launch {
            if (ClipboardCenter.copy(text)) {
                closePanel()
            } else {
                showToast(R.string.grid_panel_clipboard_copy_failed)
            }
        }
    }

    fun copyCurrentAndClose(content: ClipboardContent) {
        scope.launch {
            val ok = when (content) {
                is ClipboardContent.Text -> ClipboardCenter.copy(content.text)
                is ClipboardContent.Image -> ClipboardCenter.copyImage(content.uri)
            }
            if (ok) closePanel() else showToast(R.string.grid_panel_clipboard_copy_failed)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(ItemCorner))
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        current?.let { content ->
            CurrentClipCard(
                content = content,
                enabled = enabled,
                onCopy = { copyCurrentAndClose(content) },
            )
        }
        if (current == null && history.entries.isEmpty()) {
            EmptyClipboardHint(
                vertical = spanY > spanX,
                modifier = Modifier.fillMaxWidth().weight(1f),
            )
        } else if (history.entries.isNotEmpty()) {
            LazyColumn(
                modifier = Modifier.fillMaxWidth().weight(1f),
                contentPadding = PaddingValues(vertical = 2.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
                // 编辑态禁用内部滚动：否则在部件上拖会滚列表而不拖项。
                userScrollEnabled = enabled,
            ) {
                items(history.entries, key = { it.id }) { entry ->
                    HistoryRow(
                        entry = entry,
                        enabled = enabled,
                        onCopy = { copyAndClose(entry.text) },
                        onDelete = { ClipboardCenter.remove(entry.id) },
                        onClearAll = { ClipboardCenter.clear() },
                    )
                }
            }
        }
    }
}

/** 当前主剪贴项：文本（含敏感项）或图片缩略图；点按写回剪贴板并关面板。 */
@Composable
private fun CurrentClipCard(
    content: ClipboardContent,
    enabled: Boolean,
    onCopy: () -> Unit,
) {
    val context = LocalContext.current
    val onCard = gridPanelOnCardColor
    val base = Modifier
        .fillMaxWidth()
        .clip(RoundedCornerShape(ItemCorner * 0.6f))
        .background(onCard.copy(alpha = 0.12f))
    Row(
        modifier = base
            .then(if (enabled) Modifier.clickable(onClick = onCopy) else Modifier)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = stringResource(R.string.grid_panel_clipboard_current),
            style = MaterialTheme.typography.labelSmall,
            color = onCard.copy(alpha = 0.6f),
        )
        when (content) {
            is ClipboardContent.Text -> Text(
                text = content.text,
                style = MaterialTheme.typography.bodySmall,
                color = onCard,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            is ClipboardContent.Image -> {
                Text(
                    text = stringResource(R.string.grid_panel_clipboard_image),
                    style = MaterialTheme.typography.bodySmall,
                    color = onCard,
                    modifier = Modifier.weight(1f),
                )
                AsyncImage(
                    model = content.uri,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(28.dp)
                        .clip(RoundedCornerShape(ItemCorner * 0.4f)),
                    imageLoader = context.imageLoader,
                )
            }
        }
    }
}

/** 历史行：文本（两行省略）+ 三点菜单（复制 / 删除 / 清空全部）。 */
@Composable
private fun HistoryRow(
    entry: ClipboardHistoryEntry,
    enabled: Boolean,
    onCopy: () -> Unit,
    onDelete: () -> Unit,
    onClearAll: () -> Unit,
) {
    val onCard = gridPanelOnCardColor
    var menuOpen by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(ItemCorner * 0.5f))
            .then(if (enabled) Modifier.clickable(onClick = onCopy) else Modifier)
            .padding(start = 6.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = entry.text,
            style = MaterialTheme.typography.bodySmall,
            color = onCard,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (enabled) {
            Box {
                IconButton(
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
