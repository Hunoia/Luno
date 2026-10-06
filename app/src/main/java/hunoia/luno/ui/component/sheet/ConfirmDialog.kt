package hunoia.luno.ui.component.sheet

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import hunoia.luno.R
import hunoia.luno.ui.theme.LargeShape

/**
 * 弹窗的唯一实现（见 docs/ui-spec.md §6）。三个变体覆盖全部用途：
 * [ConfirmDialog] 确认/删除/丢弃、[TextInputDialog] 单字段输入、[CustomDialog] 自定义内容。
 *
 * 容器色与弹层统一用 `surface`；confirm 文案可覆盖（原先 `MyAlertDialog` 把"确定/取消"
 * 写死，删除/重置这类语义需要自己的文案）。
 */

/** 确认弹窗。[destructive] 用 error 色标按钮（删除类）。 */
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
    AlertDialog(
        containerColor = MaterialTheme.colorScheme.surface,
        onDismissRequest = onDismissRequest,
        title = { Text(title) },
        text = text?.let { { Text(it) } },
        confirmButton = {
            TextButton(
                onClick = {
                    onDismissRequest()
                    onConfirm()
                },
            ) {
                Text(
                    text = confirmLabel,
                    color = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) { Text(dismissLabel) }
        },
    )
}

/**
 * 单字段输入弹窗（重命名 / 搜索词 / 引擎编辑）。
 * 回车（IME Done）等同确认；[confirmEnabled] 为 false 时确认按钮禁用。
 */
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
    // 按 initialValue 复位：同一调用点切换目标（换引擎/换重命名对象）时不能残留上次的输入
    var text by rememberSaveable(initialValue) { mutableStateOf(initialValue) }
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val curOnConfirm by rememberUpdatedState(onConfirm)

    fun submit() {
        if (!confirmEnabled) return
        keyboard?.hide()
        curOnConfirm(text)
    }

    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    AlertDialog(
        containerColor = MaterialTheme.colorScheme.surface,
        onDismissRequest = onDismissRequest,
        title = { Text(title) },
        text = {
            Column {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = label.takeIf { it.isNotEmpty() }?.let { l -> { Text(l) } },
                    singleLine = true,
                    isError = isError,
                    shape = LargeShape,
                    modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
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
            }
        },
        confirmButton = {
            TextButton(onClick = { submit() }, enabled = confirmEnabled) { Text(stringResource(R.string.confirm)) }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) { Text(stringResource(R.string.cancel)) }
        },
    )
}

/** 自定义内容弹窗（如时间选择器）。 */
@Composable
fun CustomDialog(
    title: String,
    onDismissRequest: () -> Unit,
    confirmLabel: String = stringResource(R.string.confirm),
    dismissLabel: String = stringResource(R.string.cancel),
    onConfirm: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    AlertDialog(
        containerColor = MaterialTheme.colorScheme.surface,
        onDismissRequest = onDismissRequest,
        title = { Text(title) },
        text = { content() },
        confirmButton = {
            if (onConfirm != null) {
                TextButton(onClick = { onConfirm() }) { Text(confirmLabel) }
            }
        },
        dismissButton = { TextButton(onClick = onDismissRequest) { Text(dismissLabel) } },
    )
}
