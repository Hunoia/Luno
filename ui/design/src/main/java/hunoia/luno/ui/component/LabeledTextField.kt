package hunoia.luno.ui.component

import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import hunoia.luno.ui.theme.LargeShape

/** 带标签的普通文本输入框（动作参数 / 链接规则共用）。 */
@Composable
fun LabeledTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    isError: Boolean,
    singleLine: Boolean,
    modifier: Modifier = Modifier,
    shape: Shape = LargeShape,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    placeholder: String? = null,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        placeholder = placeholder?.let { { Text(it) } },
        singleLine = singleLine,
        minLines = if (singleLine) 1 else 3,
        maxLines = if (singleLine) 1 else 10,
        modifier = modifier,
        isError = isError,
        shape = shape,
        keyboardOptions = keyboardOptions,
    )
}
