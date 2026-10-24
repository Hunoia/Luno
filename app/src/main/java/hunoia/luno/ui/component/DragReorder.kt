package hunoia.luno.ui.component

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import hunoia.luno.ui.theme.RowIconSize

/**
 * 列表拖拽排序（动作面板编辑页与搜索引擎设置页共用）：
 * 按住手柄只改本地草稿 [draft]（null = 跟随 [current]），松手且草稿与当前不同时回调 [onCommit] 落盘。
 * 半行高阈值换位 + 跟手位移；行高未测量时用 72.dp 兜底。
 */
@Stable
class DragReorderState<T>(
    private val current: () -> List<T>,
    private val onCommit: (List<T>) -> Unit,
) {
    /** 拖动中的本地顺序草稿；null = 跟随 [current]。 */
    var draft by mutableStateOf<List<T>?>(null)

    /** 渲染用列表：草稿优先。 */
    val items: List<T> get() = draft ?: current()

    var draggingIndex by mutableStateOf<Int?>(null)
        private set
    var dragOffset by mutableFloatStateOf(0f)
        private set
    private var dragRowHeight by mutableFloatStateOf(0f)

    /** 行高测量缓存，行渲染经 [dragReorderPlacement] 自动写入。 */
    val itemHeights = mutableStateMapOf<Int, Float>()

    /** 配置更新后草稿退回跟随；拖拽中不动，避免把进行中的拖拽打回。 */
    fun syncWithCurrent() {
        if (draggingIndex == null) draft = null
    }

    /** 松手落盘：草稿与当前不同才回调 [onCommit]。 */
    fun finish() {
        val final = draft
        draggingIndex = null
        dragOffset = 0f
        if (final != null && final != current()) onCommit(final)
    }

    fun cancel() {
        draggingIndex = null
        dragOffset = 0f
        draft = null
    }

    fun onDragStart(index: Int, fallbackRowHeightPx: Float) {
        draggingIndex = index
        dragOffset = 0f
        dragRowHeight = itemHeights[index] ?: fallbackRowHeightPx
    }

    fun onDrag(dragAmount: Offset) {
        var idx = draggingIndex ?: return
        dragOffset += dragAmount.y
        val rowHeight = dragRowHeight.coerceAtLeast(1f)
        while (dragOffset > rowHeight / 2f && idx < items.lastIndex) {
            val list = items.toMutableList()
            list.add(idx + 1, list.removeAt(idx))
            draft = list
            dragOffset -= rowHeight
            idx += 1
        }
        while (dragOffset < -rowHeight / 2f && idx > 0) {
            val list = items.toMutableList()
            list.add(idx - 1, list.removeAt(idx))
            draft = list
            dragOffset += rowHeight
            idx -= 1
        }
        draggingIndex = idx
    }
}

@Composable
fun <T> rememberDragReorderState(
    vararg keys: Any?,
    current: () -> List<T>,
    onCommit: (List<T>) -> Unit,
): DragReorderState<T> = remember(*keys) { DragReorderState(current, onCommit) }

/** 拖拽行的渲染修饰：拖拽行置顶 + 跟手位移 + 行高测量。 */
fun Modifier.dragReorderPlacement(state: DragReorderState<*>, index: Int): Modifier = this
    .zIndex(if (state.draggingIndex == index) 1f else 0f)
    .graphicsLayer { translationY = if (state.draggingIndex == index) state.dragOffset else 0f }
    .onGloballyPositioned { state.itemHeights[index] = it.size.height.toFloat() }

/** 行尾拖动手柄：按住只改本地草稿顺序，松手落盘；点击被吞掉，不触发整行。 */
@Composable
fun DragReorderHandle(
    state: DragReorderState<*>,
    index: Int,
    contentDescription: String,
    vararg keys: Any?,
) {
    val interaction = remember { MutableInteractionSource() }
    val density = LocalDensity.current
    Box(
        modifier = Modifier
            .size(48.dp)
            .pointerInput(*keys) {
                detectDragGestures(
                    onDragStart = { state.onDragStart(index, with(density) { 72.dp.toPx() }) },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        state.onDrag(dragAmount)
                    },
                    onDragEnd = { state.finish() },
                    onDragCancel = { state.cancel() },
                )
            }
            .clickable(
                interactionSource = interaction,
                indication = null,
                onClick = {},
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Default.DragHandle,
            contentDescription = contentDescription,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(RowIconSize),
        )
    }
}
