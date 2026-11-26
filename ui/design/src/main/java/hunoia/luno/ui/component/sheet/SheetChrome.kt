package hunoia.luno.ui.component.sheet

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonGroup
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import hunoia.luno.design.R
import hunoia.luno.ui.theme.CardInnerSpacing
import hunoia.luno.ui.theme.MenuShape
import hunoia.luno.ui.theme.MinInteractiveSize
import hunoia.luno.ui.theme.PageGutter
import hunoia.luno.ui.component.PressButton
import hunoia.luno.ui.component.PressTextButton
import hunoia.luno.ui.component.menu.LunoDropdownMenuItem
import hunoia.luno.ui.theme.raisedSurfaceColor

/** 弹层标题行：左右边距由 [LunoSheet] 统一提供，这里只留纵向呼吸空间。 */
@Composable
fun SheetHeader(
    title: String,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = CardInnerSpacing),
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
    interactionSource: MutableInteractionSource? = null,
) {
    PressButton(
        onClick = onClick,
        modifier = modifier.heightIn(min = MinInteractiveSize),
        shape = MenuShape,
        interactionSource = interactionSource,
        colors = ButtonDefaults.buttonColors(
            containerColor = raisedSurfaceColor(),
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
internal fun SheetSingleActionButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    destructive: Boolean = false,
    enabled: Boolean = true,
    interactionSource: MutableInteractionSource? = null,
) {
    PressButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.heightIn(min = MinInteractiveSize),
        shape = MenuShape,
        interactionSource = interactionSource,
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
    val dismissSource = remember { MutableInteractionSource() }
    val confirmSource = remember { MutableInteractionSource() }
    // M3 1.5 ButtonGroup：两枚按钮等宽连接，按下时被按的一枚变宽、另一枚压缩
    // （ui-guidelines §4.4）。视觉仍用 Luno 的容器角色，不切到 M3 默认圆角。
    ButtonGroup(
        overflowIndicator = { ButtonGroupDefaults.OverflowIndicator(it) },
        modifier = modifier.fillMaxWidth(),
        expandedRatio = 1f,
        horizontalArrangement = Arrangement.spacedBy(CardInnerSpacing),
    ) {
        customItem(
            buttonGroupContent = {
                SheetActionButton(
                    text = dismissLabel,
                    onClick = onDismiss,
                    modifier = Modifier
                        .weight(1f)
                        .animateWidth(dismissSource),
                    interactionSource = dismissSource,
                )
            },
            menuContent = {
                LunoDropdownMenuItem(text = dismissLabel, onClick = onDismiss)
            },
        )
        customItem(
            buttonGroupContent = {
                SheetSingleActionButton(
                    text = confirmLabel,
                    onClick = onConfirm,
                    modifier = Modifier
                        .weight(1f)
                        .animateWidth(confirmSource),
                    destructive = destructive,
                    enabled = confirmEnabled,
                    interactionSource = confirmSource,
                )
            },
            menuContent = {
                LunoDropdownMenuItem(
                    text = confirmLabel,
                    onClick = onConfirm,
                    enabled = confirmEnabled,
                    isError = destructive,
                )
            },
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
        PressTextButton(onClick = onDismissRequest) {
            Text(dismissLabel)
        }
        PressTextButton(onClick = onConfirm, enabled = confirmEnabled) {
            Text(confirmLabel)
        }
    }
}

