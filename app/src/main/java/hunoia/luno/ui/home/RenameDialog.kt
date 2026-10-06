package hunoia.luno.ui.home

import hunoia.luno.R
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import hunoia.luno.ui.component.sheet.TextInputDialog

/** 重命名：单字段输入，自动聚焦，回车即确认（见 docs/ui-spec.md §6）。 */
@Composable
fun RenameDialog(
    target: RenameTarget?,
    onDismissRequest: () -> Unit,
    onConfirm: (RenameTarget, String) -> Unit,
) {
    target ?: return
    TextInputDialog(
        title = stringResource(R.string.rename),
        label = "",
        initialValue = "",
        onDismissRequest = onDismissRequest,
        onConfirm = { onConfirm(target, it) },
    )
}
