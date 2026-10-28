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
 * 底部弹层的唯一实现：`skipPartiallyExpanded`、容器色、顶角与入场动画在此定死，
 * 调用方只写内容。
 *
 * [containerColor] / [topShape] 供浮层内嵌覆盖；[scrollState] 传 Scroll 时模板负责
 * 与内容滚动的嵌套衔接；内容自带标题栏时不要 [showDragHandle]。
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
