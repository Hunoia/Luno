package hunoia.luno.ui.component.sheet

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.material3.MaterialTheme
import hunoia.luno.ui.theme.DialogScrimAlpha
import hunoia.luno.ui.theme.PageGutter
import hunoia.luno.ui.theme.SheetTopShape

/**
 * 底部弹层的唯一实现：`skipPartiallyExpanded`、容器色、顶角与入场动画在此定死，
 * 调用方只写内容。
 *
 * [containerColor] / [topShape] 供浮层内嵌覆盖；[scrollState] 传 Scroll 时模板负责
 * 与内容滚动的嵌套衔接；内容自带标题栏时不要 [showDragHandle]。
 *
 * **横向边距由本组件统一提供**（[horizontalPadding]，默认 `PageGutter`）：sheet 内容
 * 与页面同一条 gutter，列表行 / 分组卡片都不再自带左右边距，否则会出现双重缩进或
 * 通栏贴边（ui-guidelines §4.1）。需要通栏内容（全宽网格 / 底栏）时显式传 `0.dp`。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LunoSheet(
    onDismissRequest: () -> Unit,
    showDragHandle: Boolean = false,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainer,
    topShape: Shape = SheetTopShape,
    scrollState: OptimizedScrollState = OptimizedScrollState.None,
    horizontalPadding: Dp = PageGutter,
    content: @Composable ColumnScope.() -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        containerColor = containerColor,
        shape = topShape,
        // 遮罩与 DialogScrim 同一口径（容器自己会把它跟动画一起淡入淡出）。
        scrimColor = MaterialTheme.colorScheme.scrim.copy(alpha = DialogScrimAlpha),
        dragHandle = if (showDragHandle) {
            { BottomSheetDefaults.DragHandle() }
        } else {
            null
        },
        content = {
            SheetNestedContent(scrollState = scrollState) {
                // 内容直接跟随 M3 容器动画（唯一不吃应用档位的例外，§5.1）。
                Column(modifier = Modifier.padding(horizontal = horizontalPadding)) {
                    content()
                }
            }
        },
    )
}
