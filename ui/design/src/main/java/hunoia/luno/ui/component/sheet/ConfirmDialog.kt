package hunoia.luno.ui.component.sheet

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import hunoia.luno.design.R
import hunoia.luno.ui.theme.LargeShape
import hunoia.luno.ui.theme.ListSpacing
import hunoia.luno.ui.theme.SheetListMaxHeight
import hunoia.luno.ui.component.PressTextButton

/**
 * 底部确认弹窗：Dialog（遮罩 + 点外部/返回关闭），本体锚在屏幕底部（澎湃OS 样式），
 * 四角大圆角、四周留边；按钮为澎湃大按钮。
 *
 * 窗口与进出场动画由 [AnchoredDialog] 提供，这里只填内容。
 */
@Composable
fun ConfirmDialog(
    title: String,
    onDismissRequest: () -> Unit,
    onConfirm: () -> Unit,
    text: String? = null,
    confirmLabel: String = stringResource(R.string.confirm),
    dismissLabel: String = stringResource(R.string.cancel),
    destructive: Boolean = false,
) {
    AnchoredDialog(onDismiss = onDismissRequest) { dismiss ->
        ConfirmCardContent(
            title = title,
            text = text,
            confirmLabel = confirmLabel,
            dismissLabel = dismissLabel,
            destructive = destructive,
            onDismiss = dismiss,
            // 先执行动作再退场：动作立即生效，退场由弹层自己收尾（宿主别再同步卸载）。
            onConfirm = {
                onConfirm()
                dismiss()
            },
        )
    }
}

/**
 * 确认弹窗内容（底部锚定卡片 + 取消/确认）：独立 Activity 形态直接铺一屏（部件确认弹窗），
 * 不再套一层 Compose `Dialog`，避免双窗口/双遮罩。
 */
@Composable
fun ConfirmDialogContent(
    title: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    text: String? = null,
    confirmLabel: String = stringResource(R.string.confirm),
    dismissLabel: String = stringResource(R.string.cancel),
    destructive: Boolean = false,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismiss,
            ),
        contentAlignment = Alignment.BottomCenter,
    ) {
        DialogCard {
            ConfirmCardContent(
                title = title,
                text = text,
                confirmLabel = confirmLabel,
                dismissLabel = dismissLabel,
                destructive = destructive,
                onDismiss = onDismiss,
                onConfirm = onConfirm,
            )
        }
    }
}

/** 确认卡片内容：标题 + 可选多行正文（超长内部滚动）+ 取消/确认。 */
@Composable
private fun ColumnScope.ConfirmCardContent(
    title: String,
    text: String?,
    confirmLabel: String,
    dismissLabel: String,
    destructive: Boolean,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    Text(
        text = title,
        style = MaterialTheme.typography.headlineSmall,
        color = MaterialTheme.colorScheme.onSurface,
    )
    text?.let { body ->
        Text(
            text = body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .heightIn(max = SheetListMaxHeight)
                .verticalScroll(rememberScrollState()),
        )
    }
    SheetActionButtons(
        onDismiss = onDismiss,
        onConfirm = onConfirm,
        confirmLabel = confirmLabel,
        dismissLabel = dismissLabel,
        destructive = destructive,
    )
}

/** 单字段输入：IME Done 等同确认；[confirmEnabled] 为 false 时确认禁用。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TextInputDialog(
    title: String,
    label: String,
    initialValue: String,
    onDismissRequest: () -> Unit,
    onConfirm: (String) -> Unit,
    confirmEnabled: Boolean = true,
    isError: Boolean = false,
    supportingText: String = "",
    keyboardOptions: KeyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
) {
    // 按 initialValue 复位：同一调用点切换目标（换引擎/换重命名对象）时不能残留上次的输入。
    // 光标放末尾（编辑预填后直接续写，而不是从最前插入）。
    var textValue by rememberSaveable(initialValue, stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(initialValue, selection = TextRange(initialValue.length)))
    }
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val curOnConfirm by rememberUpdatedState(onConfirm)

    AnchoredDialog(onDismiss = onDismissRequest) { dismiss ->
        fun submit() {
            if (!confirmEnabled) return
            keyboard?.hide()
            curOnConfirm(textValue.text)
            dismiss()
        }

        // 自动聚焦：键盘随之弹出。
        LaunchedEffect(Unit) { focusRequester.requestFocus() }

        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
        OutlinedTextField(
            value = textValue,
            onValueChange = { textValue = it },
            label = label.takeIf { it.isNotEmpty() }?.let { l -> { Text(l) } },
            singleLine = true,
            isError = isError,
            shape = LargeShape,
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focusRequester),
            keyboardOptions = keyboardOptions,
            keyboardActions = KeyboardActions(onDone = { submit() }),
        )
        // supportingText 常驻占位：无错时空字符串，避免错误出现/消失时弹窗高度跳动
        if (supportingText.isNotEmpty()) {
            Text(
                text = supportingText,
                style = MaterialTheme.typography.bodySmall,
                color = if (isError) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(ListSpacing),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Spacer(Modifier.weight(1f))
            PressTextButton(onClick = dismiss) {
                Text(stringResource(R.string.cancel))
            }
            PressTextButton(
                onClick = { submit() },
                enabled = confirmEnabled,
            ) {
                Text(stringResource(R.string.confirm))
            }
        }
    }
}
