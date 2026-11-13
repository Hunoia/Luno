package hunoia.luno.gridpanel.internal

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.unit.dp
import hunoia.luno.bridge.ui.hapticLongPress
import hunoia.luno.bridge.ui.showToast
import hunoia.luno.action.controller.ImageInserter
import hunoia.luno.action.controller.TextInserter
import hunoia.luno.core.AppContext
import hunoia.luno.gesture.GestureCommand
import hunoia.luno.gridpanel.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.roundToInt

/** 拖拽落点后等面板窗口真正摘掉的时间（退场动画 + 移除）。 */
internal const val PanelCloseSettleMs = 260L

/**
 * 文本拖出：先关面板再按落点写入，落点已在手势层换成屏幕坐标。
 *
 * 面板窗口还在时会把下面应用窗口顶成非交互窗口，无障碍 `windows` 里看不到目标，
 * 所以必须等退场动画播完、窗口真正摘掉再插入。粘贴跑在进程级 scope 上，不跟组合取消。
 */
internal fun dropTextToInput(
    text: String,
    screenPosition: Offset,
    closePanel: () -> Unit,
) {
    closePanel()
    val insertText = GestureCommand.insertText
    if (insertText == null) {
        showToast(R.string.toast_accessibility_required)
        return
    }
    val scope = AppContext.applicationScope ?: return
    val x = screenPosition.x.roundToInt()
    val y = screenPosition.y.roundToInt()
    scope.launch(Dispatchers.Default) {
        delay(PanelCloseSettleMs)
        when (insertText(text, x, y)) {
            TextInserter.Result.InsertedViaClipboard ->
                showToast(R.string.grid_panel_clipboard_pasted_via_clipboard)
            // 找不到输入框 / 写入失败：静默收口，不弹提示。
            else -> Unit
        }
    }
}

/**
 * 图片拖出：先关面板，再把本地副本写回剪贴板并尝试粘进落点输入框。
 *
 * 图片没有通用插入通道，目标不支持粘贴时静默收口（不误导用户“已发送”）。
 */
internal fun dropImageToInput(
    imagePath: String,
    mime: String?,
    screenPosition: Offset,
    closePanel: () -> Unit,
) {
    closePanel()
    val pasteImage = GestureCommand.pasteImageAt
    if (pasteImage == null) {
        showToast(R.string.toast_accessibility_required)
        return
    }
    val scope = AppContext.applicationScope ?: return
    val x = screenPosition.x.roundToInt()
    val y = screenPosition.y.roundToInt()
    scope.launch(Dispatchers.Default) {
        delay(PanelCloseSettleMs)
        when (pasteImage(imagePath, mime, x, y)) {
            ImageInserter.Result.Pasted,
            ImageInserter.Result.NoTarget,
            ImageInserter.Result.Failed,
            -> Unit
        }
    }
}

/**
 * 面板内条目「短按 / 长按拖出」的统一下拉手势（剪贴板部件使用）。
 *
 * 坐标系口径：幽灵用窗口坐标，落点用屏幕坐标（叠加层/圆角修正下手算窗口原点会偏）。
 */
internal class ItemDragHandlers(
    val onTap: () -> Unit,
    val onDragStart: (windowPos: Offset, screenPos: Offset) -> Unit,
    val onDrag: (windowPos: Offset, screenPos: Offset) -> Unit,
    val onDragEnd: (screenPos: Offset) -> Unit,
    val onDragCancel: () -> Unit,
    /** 位移越过 8dp 阈值（一次拖拽成立）：调用方把面板本体淡出。 */
    val onDragThresholdPassed: () -> Unit,
)

/**
 * 条目的统一手势：短按 [ItemDragHandlers.onTap]；长按拿起后可拖到面板外的输入框。
 *
 * [draggable] = false：长按没有可拖的内容，等同一次点按。
 */
@Composable
internal fun Modifier.itemDrag(
    key: Any,
    draggable: Boolean,
    handlers: ItemDragHandlers,
): Modifier {
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
        .pointerInput(key, draggable) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                // 吃掉 down：面板根的「点空白关窗 / 长按进编辑」不再把这次按压当自己的。
                // 列表滚动不受影响：scrollable 用 requireUnconsumed=false 看后续移动。
                down.consume()
                // 长按前：抬手 = 点按；滑出 slop = 让给列表滚动/面板翻页。
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
                        handlers.onTap()
                    }
                    return@awaitEachGesture
                }
                if (!draggable) {
                    // 没有可拖内容：长按等同一次点按。
                    handlers.onTap()
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
