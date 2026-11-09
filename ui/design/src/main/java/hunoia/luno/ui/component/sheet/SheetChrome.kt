package hunoia.luno.ui.component.sheet

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import hunoia.luno.design.R
import hunoia.luno.ui.theme.CardInnerSpacing
import hunoia.luno.ui.theme.LargeShape
import hunoia.luno.ui.theme.MinInteractiveSize
import hunoia.luno.ui.theme.PageGutter

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

/** 澎湃样式大按钮：中性灰调实心，用于取消键。 */
@Composable
private fun SheetActionButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Button(
        onClick = onClick,
        modifier = modifier.heightIn(min = MinInteractiveSize),
        shape = LargeShape,
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        ),
    ) {
        Text(text)
    }
}

/**
 * Sheet 底部单动作按钮（与 [SheetActionButtons] 的确认键同款澎湃实心）：
 * 只有一个确认类动作、没有取消对的 sheet 用（如取色器的「确认」）。
 */
@Composable
fun SheetSingleActionButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    destructive: Boolean = false,
    enabled: Boolean = true,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.heightIn(min = MinInteractiveSize),
        shape = LargeShape,
        colors = if (destructive) {
            ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.error,
                contentColor = MaterialTheme.colorScheme.onError,
            )
        } else {
            ButtonDefaults.buttonColors()
        },
    ) {
        Text(text)
    }
}

/**
 * 澎湃样式的确认/取消按钮行：两枚等宽大圆角按钮并排（取消在左、确认在右），
 * [destructive] 时确认键红色实心。
 */
@Composable
fun SheetActionButtons(
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier,
    confirmLabel: String = stringResource(R.string.confirm),
    dismissLabel: String = stringResource(R.string.cancel),
    destructive: Boolean = false,
    confirmEnabled: Boolean = true,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(CardInnerSpacing),
    ) {
        SheetActionButton(text = dismissLabel, onClick = onDismiss, modifier = Modifier.weight(1f))
        SheetSingleActionButton(
            text = confirmLabel,
            onClick = onConfirm,
            modifier = Modifier.weight(1f),
            destructive = destructive,
            enabled = confirmEnabled,
        )
    }
}

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

