package hunoia.luno.ui.actionlibrary

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DataObject
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import hunoia.luno.R
import hunoia.luno.ui.theme.LargeShape

/**
 * 支持模板插入的文本框：自己持有光标状态，点右侧按钮在光标处插入 `${token}`。
 */
@Composable
fun TemplateTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    isError: Boolean,
    singleLine: Boolean,
    modifier: Modifier = Modifier,
    shape: Shape = LargeShape,
    allowTemplates: Boolean = true,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    placeholder: String? = null,
) {
    var field by remember { mutableStateOf(TextFieldValue(value, TextRange(value.length))) }
    var showPicker by remember { mutableStateOf(false) }

    LaunchedEffect(value) {
        if (field.text != value) {
            field = TextFieldValue(value, TextRange(value.length))
        }
    }

    OutlinedTextField(
        value = field,
        onValueChange = {
            field = it
            onValueChange(it.text)
        },
        label = { Text(label) },
        placeholder = placeholder?.let { { Text(it) } },
        singleLine = singleLine,
        minLines = if (singleLine) 1 else 3,
        maxLines = if (singleLine) 1 else 10,
        modifier = modifier,
        isError = isError,
        shape = shape,
        keyboardOptions = keyboardOptions,
        trailingIcon = if (allowTemplates) {
            {
                IconButton(onClick = { showPicker = true }) {
                    Icon(
                        imageVector = Icons.Default.DataObject,
                        contentDescription = stringResource(R.string.action_param_insert_template),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        } else null,
    )

    if (showPicker) {
        TemplatePickerSheet(
            onDismissRequest = { showPicker = false },
            onPick = { text ->
                val start = field.selection.min.coerceIn(0, field.text.length)
                val end = field.selection.max.coerceIn(start, field.text.length)
                val updated = field.text.replaceRange(start, end, text)
                field = TextFieldValue(updated, TextRange(start + text.length))
                onValueChange(updated)
                showPicker = false
            },
        )
    }
}
