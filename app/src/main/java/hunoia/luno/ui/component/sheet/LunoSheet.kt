package hunoia.luno.ui.component.sheet

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.material3.MaterialTheme
import hunoia.luno.ui.theme.SheetTopShape

/**
 * 底部弹层的唯一实现（见 docs/ui-spec.md §7）。
 *
 * 全应用弹层此前共用同一套参数（`skipPartiallyExpanded`、`surfaceContainer` 容器、`SheetTopShape`
 * 顶角），差异只在内容，所以这些参数在这里定死：调用方只写内容，避免 16 处各写一遍外观。
 * 入场动画（淡入 + 轻微上滑）也在模板里统一，原先只有 `OptimizedBottomSheet` 有。
 *
 * @param showDragHandle 是否画顶部拖拽条。内容自带标题栏时不需要。
 * @param containerColor 覆盖容器色（仅浮层内嵌场景使用）。
 * @param topShape 覆盖顶角形状。
 * @param scrollState 内容自带滚动容器时传 Scroll，模板负责 sheet 与内容的嵌套滚动衔接。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LunoSheet(
    onDismissRequest: () -> Unit,
    showDragHandle: Boolean = false,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainer,
    topShape: Shape = SheetTopShape,
    scrollState: OptimizedScrollState = OptimizedScrollState.None,
    content: @Composable ColumnScope.() -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        containerColor = containerColor,
        shape = topShape,
        dragHandle = if (showDragHandle) {
            { BottomSheetDefaults.DragHandle() }
        } else {
            null
        },
        content = {
            SheetNestedContent(scrollState = scrollState) {
                AnimatedVisibility(
                    visible = true,
                    enter = fadeIn(spring(stiffness = Spring.StiffnessMedium)) +
                        slideInVertically { it / 8 },
                ) {
                    Column {
                        content()
                    }
                }
            }
        },
    )
}
