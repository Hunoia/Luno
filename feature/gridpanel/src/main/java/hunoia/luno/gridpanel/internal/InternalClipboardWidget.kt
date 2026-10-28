package hunoia.luno.gridpanel.internal

import android.content.ClipboardManager
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
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
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.imageLoader
import hunoia.luno.gridpanel.R
import hunoia.luno.action.controller.TextInserter
import hunoia.luno.bridge.system.ClipboardContent
import hunoia.luno.bridge.ui.hapticLongPress
import hunoia.luno.bridge.ui.showToast
import hunoia.luno.config.model.ClipboardHistory
import hunoia.luno.config.model.ClipboardHistoryEntry
import hunoia.luno.core.AppContext
import hunoia.luno.clipboard.ClipboardCenter
import hunoia.luno.gridpanel.LocalGridPanelClose
import hunoia.luno.gridpanel.LocalGridPanelDragGhost
import hunoia.luno.gridpanel.geometry.ItemCorner
import hunoia.luno.gridpanel.ui.gridPanelOnCardColor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.roundToInt

/** 拖拽落点后等面板窗口真正摘掉的时间（退场动画 + 移除）。 */
private const val PanelCloseSettleMs = 260L

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
    val context = LocalContext.current
    val closePanel = LocalGridPanelClose.current
    val scope = rememberCoroutineScope()
    val ghost = LocalGridPanelDragGhost.current
    val history by ClipboardCenter.history.collectAsState(initial = ClipboardHistory())
    var current by remember { mutableStateOf<ClipboardContent?>(null) }

    // 打开部件即抓一次当前主剪贴项（新 ROM 后台监听不可用，这是主要捕获时机）。
    LaunchedEffect(Unit) {
        val content = ClipboardCenter.readCurrent()
        current = content
        ClipboardCenter.record(content)
    }
    // 面板开着时剪贴板又变了：事件驱动地补一次（不做常驻轮询）。
    // 部分 ROM 只在应用窗口可见时投递这个回调，投不到也不影响打开时的捕获。
    DisposableEffect(Unit) {
        val clipboard = context.getSystemService(ClipboardManager::class.java)
        val listener = ClipboardManager.OnPrimaryClipChangedListener {
            scope.launch {
                val content = ClipboardCenter.readCurrent() ?: return@launch
                current = content
                ClipboardCenter.record(content)
            }
        }
        clipboard?.addPrimaryClipChangedListener(listener)
        onDispose { clipboard?.removePrimaryClipChangedListener(listener) }
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
        closePanel()
        val insertText = hunoia.luno.gesture.GestureCommand.insertText
        if (insertText == null) {
            showToast(R.string.toast_accessibility_required)
            return
        }
        val x = screenPosition.x.roundToInt()
        val y = screenPosition.y.roundToInt()
        val launchScope = AppContext.applicationScope ?: return
        launchScope.launch(Dispatchers.Default) {
            // 等淡出动画播完、窗口真正摘掉：面板窗口还在时它会把下面应用窗口顶成
            // 非交互窗口，无障碍的 `windows` 列表里根本看不到目标应用。
            delay(PanelCloseSettleMs)
            when (insertText(text, x, y)) {
                TextInserter.Result.Inserted -> Unit
                TextInserter.Result.InsertedViaClipboard ->
                    showToast(R.string.grid_panel_clipboard_pasted_via_clipboard)
                // 找不到输入框 / 写入失败：静默收口，不弹提示。
                TextInserter.Result.NoTarget, TextInserter.Result.Failed -> Unit
            }
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
                dragHandlers = (content as? ClipboardContent.Text)?.let { text ->
                    ClipDragHandlers(
                        onCopy = { copyCurrentAndClose(content) },
                        onDragStart = { windowPos, _ -> ghost?.begin(text.text, windowPos) },
                        onDrag = { windowPos, _ -> ghost?.move(windowPos) },
                        onDragEnd = { screenPos -> dropToInput(text.text, screenPos) },
                        onDragCancel = { ghost?.showChrome(); ghost?.end() },
                        onDragThresholdPassed = { ghost?.hideChrome() },
                    )
                },
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
                        onDragStart = { windowPos, screenPos -> ghost?.begin(entry.text, windowPos) },
                        onDrag = { windowPos, _ -> ghost?.move(windowPos) },
                        onDragEnd = { screenPos -> dropToInput(entry.text, screenPos) },
                        onDragCancel = { ghost?.showChrome(); ghost?.end() },
                        onDragThresholdPassed = { ghost?.hideChrome() },
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
    dragHandlers: ClipDragHandlers?,
) {
    val context = LocalContext.current
    val onCard = gridPanelOnCardColor
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(ItemCorner * 0.6f))
            .background(onCard.copy(alpha = 0.12f))
            // 当前主剪贴项也能长按拖进输入框（与历史行同一套手势）。
            .then(
                if (enabled && dragHandlers != null) {
                    Modifier.clipDrag(content, dragHandlers)
                } else if (enabled) {
                    Modifier.clickable(onClick = onCopy)
                } else {
                    Modifier
                },
            )
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

/** 历史行：文本（两行省略）+ 三点菜单（复制 / 删除 / 清空全部）。
 * 手势：短按复制并关面板；长按拿起后可拖到面板外的输入框（见 [dropToInput]）。 */
@Composable
private fun HistoryRow(
    entry: ClipboardHistoryEntry,
    enabled: Boolean,
    onCopy: () -> Unit,
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
    var menuOpen by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(ItemCorner * 0.5f))
            .then(
                if (enabled) {
                    Modifier.clipDrag(
                        key = entry.text,
                        handlers = ClipDragHandlers(
                            onCopy = onCopy,
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

/** 剪贴板条目拖拽手势的回调（历史行与「当前主剪贴项」共用）。 */
private class ClipDragHandlers(
    val onCopy: () -> Unit,
    val onDragStart: (windowPos: Offset, screenPos: Offset) -> Unit,
    val onDrag: (windowPos: Offset, screenPos: Offset) -> Unit,
    val onDragEnd: (screenPos: Offset) -> Unit,
    val onDragCancel: () -> Unit,
    val onDragThresholdPassed: () -> Unit,
)

/**
 * 剪贴板条目的统一手势：短按复制；长按拿起后可拖到面板外的输入框。
 *
 * 两个坐标系的换算都由布局坐标直接给出：幽灵用窗口坐标，落点用屏幕坐标
 * （叠加层/圆角修正下手算窗口原点会偏）。单一实现，历史行与当前项共用。
 */
@Composable
private fun Modifier.clipDrag(key: Any, handlers: ClipDragHandlers): Modifier {
    val view = LocalView.current
    val viewConfiguration = LocalViewConfiguration.current
    val dragSlopPx = with(LocalDensity.current) { 8.dp.toPx() }
    var coords by remember { mutableStateOf<LayoutCoordinates?>(null) }
    var origin by remember { mutableStateOf(Offset.Zero) }
    return this
        .onGloballyPositioned {
            coords = it
            origin = it.positionInWindow()
        }
        .pointerInput(key) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                // 吃掉 down：面板根的「点空白关窗 / 长按进编辑」不再把这次按压当自己的。
                // 列表滚动不受影响：scrollable 用 requireUnconsumed=false 看后续移动。
                down.consume()
                // 长按前：抬手 = 点按复制；滑出 slop = 让给列表滚动/面板翻页。
                var tapUp: PointerInputChange? = null
                val longPressed = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        when {
                            change.isConsumed -> break
                            !change.pressed -> {
                                tapUp = change
                                break
                            }
                            (change.position - down.position).getDistance() >
                                viewConfiguration.touchSlop -> break
                        }
                    }
                } == null
                if (!longPressed) {
                    val up = tapUp
                    if (up != null) {
                        up.consume()
                        handlers.onCopy()
                    }
                    return@awaitEachGesture
                }
                // 长按成立：拿起。之后手指移动就是拖拽，抬手按落点找输入框。
                view.hapticLongPress()
                var moved = 0f
                var lastLocal = down.position
                fun windowOf(local: Offset) = coords?.localToWindow(local) ?: (origin + local)
                fun screenOf(local: Offset) = coords?.localToScreen(local) ?: (origin + local)
                handlers.onDragStart(windowOf(lastLocal), screenOf(lastLocal))
                var crossed = false
                while (true) {
                    val event = awaitPointerEvent()
                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                    if (!change.pressed) {
                        change.consume()
                        break
                    }
                    val delta = change.positionChange()
                    if (delta != Offset.Zero) {
                        moved += delta.getDistance()
                        change.consume()
                        lastLocal = change.position
                        handlers.onDrag(windowOf(lastLocal), screenOf(lastLocal))
                        if (!crossed && moved >= dragSlopPx) {
                            crossed = true
                            // 越过阈值：面板本体淡出，只留幽灵（窗口留着接手势）。
                            handlers.onDragThresholdPassed()
                        }
                    }
                }
                if (moved >= dragSlopPx) {
                    handlers.onDragEnd(screenOf(lastLocal))
                } else {
                    handlers.onDragCancel()
                }
            }
        }
}
