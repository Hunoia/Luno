package hunoia.luno.ui.component.sheet

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import hunoia.luno.R
import hunoia.luno.ui.theme.CardInnerSpacing
import hunoia.luno.ui.theme.PageGutter

/**
 * 弹层标题栏（见 docs/ui-spec.md §6）：左标题 + 右侧可选操作区。
 *
 * 各 sheet 手写的标题 Row（字号/边距各异）收敛到这里。
 */
@Composable
fun SheetHeader(
    title: String,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = PageGutter, vertical = CardInnerSpacing),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CardInnerSpacing),
    ) {
        Text(
            modifier = Modifier.weight(1f),
            text = title,
            style = MaterialTheme.typography.titleLarge,
        )
        trailing?.invoke()
    }
}

/**
 * 弹层底部操作区（见 docs/ui-spec.md §6）：取消 + 确认。
 *
 * 确认文案可覆盖（删除/重置类语义需要自己的文案）；[confirmEnabled] 为 false 时确认禁用。
 */
@Composable
fun SheetFooterActions(
    onDismissRequest: () -> Unit,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier,
    confirmLabel: String = stringResource(R.string.confirm),
    dismissLabel: String = stringResource(R.string.cancel),
    confirmEnabled: Boolean = true,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = PageGutter, vertical = CardInnerSpacing),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.End,
    ) {
        TextButton(onClick = onDismissRequest) {
            Text(dismissLabel)
        }
        TextButton(onClick = onConfirm, enabled = confirmEnabled) {
            Text(confirmLabel)
        }
    }
}

/** 行作用域内的底部操作区：调用方在 Row 里排布时用这个。 */
@Composable
fun RowScope.SheetFooterActionsRow(
    onDismissRequest: () -> Unit,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier,
    confirmLabel: String = stringResource(R.string.confirm),
    dismissLabel: String = stringResource(R.string.cancel),
    confirmEnabled: Boolean = true,
) {
    TextButton(onClick = onDismissRequest, modifier = modifier) {
        Text(dismissLabel)
    }
    TextButton(onClick = onConfirm, enabled = confirmEnabled) {
        Text(confirmLabel)
    }
}
