package hunoia.luno.quickaccess.panel

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import hunoia.luno.quickaccess.R
import hunoia.luno.ui.component.row.LunoGroup
import hunoia.luno.ui.component.row.LunoSelectableRow
import hunoia.luno.ui.component.row.groupItemClip
import hunoia.luno.ui.component.row.groupItemSpacing
import hunoia.luno.ui.component.row.segmentedShape
import hunoia.luno.ui.component.sheet.AnchoredDialog
import hunoia.luno.ui.theme.containerShape

/**
 * 图文双模式「每次询问」的选择器：点引擎时问一次走文字搜索还是分享图片。
 *
 * 用 [AnchoredDialog] 而不是下拉菜单：两个选项语义不同（一个开网页、一个拉起选图），
 * 需要能读完整描述，菜单行放不下。选中即执行，不设确认按钮。
 */
@Composable
internal fun ContentModeDialog(
    onDismiss: () -> Unit,
    onTextSearch: () -> Unit,
    onShareImage: () -> Unit,
) {
    AnchoredDialog(onDismiss = { onDismiss() }) { dismiss ->
        Text(
            text = stringResource(R.string.search_engine_content_ask_title),
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
        LunoGroup {
            Column(
                modifier = Modifier.fillMaxWidth().groupItemClip(
                    containerShape(
                        topStart = hunoia.luno.ui.theme.ContainerRadius,
                        topEnd = hunoia.luno.ui.theme.ContainerRadius,
                        bottomStart = hunoia.luno.ui.theme.ContainerRadius,
                        bottomEnd = hunoia.luno.ui.theme.ContainerRadius,
                    ),
                ),
                verticalArrangement = Arrangement.spacedBy(groupItemSpacing()),
            ) {
                LunoSelectableRow(
                    title = stringResource(R.string.search_engine_content_text),
                    subtitle = stringResource(R.string.search_engine_content_text_desc),
                    selected = false,
                    onSelectedChange = {
                        dismiss()
                        onTextSearch()
                    },
                    shape = segmentedShape(0, 2),
                )
                LunoSelectableRow(
                    title = stringResource(R.string.search_engine_content_image),
                    subtitle = stringResource(R.string.search_engine_content_image_desc),
                    selected = false,
                    onSelectedChange = {
                        dismiss()
                        onShareImage()
                    },
                    shape = segmentedShape(1, 2),
                )
            }
        }
    }
}
