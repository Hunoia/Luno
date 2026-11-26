package hunoia.luno.ui.component.input

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import hunoia.luno.ui.theme.LargeShape

/**
 * 弹窗 / 浮层里唯一的输入框：M3 填充式（tonal 容器）+ 大圆角。
 *
 * 三种输入形态各管一段，不要互相替代：
 * - 页面内短文本：[hunoia.luno.ui.component.row.LunoInputRow]（标签左、值右对齐、无外框）
 * - 页面内长文本：[hunoia.luno.ui.component.row.LunoCardInput]（`singleLine = false`）
 * - 弹窗 / 浮层：这里（填充色块 + 浮动标签，键盘与 IME 交给 M3）
 *
 * [TextFieldValue] 版本保留光标、选区与输入法组合态；弹窗里需要回填光标位置时用它。
 */
@Composable
fun LunoDialogField(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    modifier: Modifier = Modifier,
    label: String = "",
    placeholder: String = "",
    isError: Boolean = false,
    singleLine: Boolean = true,
    minLines: Int = 1,
    focusRequester: FocusRequester? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    trailing: (@Composable () -> Unit)? = null,
) {
    TextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier
            .fillMaxWidth()
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier),
        label = label.takeIf { it.isNotEmpty() }?.let { text -> { Text(text) } },
        placeholder = placeholder.takeIf { it.isNotEmpty() }?.let { text -> { Text(text) } },
        singleLine = singleLine,
        minLines = minLines,
        isError = isError,
        shape = LargeShape,
        colors = TextFieldDefaults.tonalColors(),
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        trailingIcon = trailing,
    )
}

/** 纯文本版本（不需要光标 / 组合态时用）。 */
@Composable
fun LunoDialogField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String = "",
    placeholder: String = "",
    isError: Boolean = false,
    singleLine: Boolean = true,
    minLines: Int = 1,
    focusRequester: FocusRequester? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    trailing: (@Composable () -> Unit)? = null,
) {
    LunoDialogField(
        value = TextFieldValue(value),
        onValueChange = { onValueChange(it.text) },
        modifier = modifier,
        label = label,
        placeholder = placeholder,
        isError = isError,
        singleLine = singleLine,
        minLines = minLines,
        focusRequester = focusRequester,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        trailing = trailing,
    )
}
