package hunoia.luno.ui.component

import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import hunoia.luno.ui.theme.LargeShape

/**
 * 带标签的普通文本输入框（动作参数 / 链接规则共用）。
 *
 * 自然高度的表单走 M3 1.5 Expressive 口径：填充底 + tonal 色 + 标签内嵌
 * （`TextFieldDefaults.tonalColors()`），形状仍是 Luno 的容器角色。固定高度的搜索框
 * 不走这里（`AppSearchBar` / `LunoSearchField` 各有自己的胶囊契约，§4.3）。
 */
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
    TextField(
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
        colors = TextFieldDefaults.tonalColors(),
        keyboardOptions = keyboardOptions,
    )
}
