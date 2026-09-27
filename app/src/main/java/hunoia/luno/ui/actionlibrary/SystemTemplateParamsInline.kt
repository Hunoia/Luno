package hunoia.luno.ui.actionlibrary

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import hunoia.luno.R
import hunoia.luno.action.template.SystemFunctionTemplates
import hunoia.luno.action.template.TemplateParam
import hunoia.luno.config.model.ActionLibraryEntry
import hunoia.luno.config.model.ParamType

private data class SelectOption(val value: String, val label: String)

private fun TemplateParam.selectOptions(): List<SelectOption> = options.map { raw ->
    val idx = raw.indexOf(':')
    if (idx >= 0) SelectOption(raw.substring(0, idx), raw.substring(idx + 1)) else SelectOption(raw, raw)
}

@Composable
fun SystemTemplateParamsInline(
    entry: ActionLibraryEntry,
    onConfirm: (ActionLibraryEntry) -> Unit,
) {
    val template = SystemFunctionTemplates.getById(entry.systemTemplate.templateId) ?: return
    if (template.params.isEmpty()) return

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = stringResource(R.string.action_library_template_params),
            style = MaterialTheme.typography.titleSmall,
        )
        template.params.forEach { param ->
            TemplateParamEditor(
                param = param,
                value = entry.systemTemplate.params[param.key] ?: param.defaultValue,
                onValueChange = { value ->
                    onConfirm(
                        entry.updateSystemTemplate(
                            entry.systemTemplate.copy(params = entry.systemTemplate.params + (param.key to value))
                        )
                    )
                },
            )
        }
    }
}

@Composable
private fun TemplateParamEditor(
    param: TemplateParam,
    value: String,
    onValueChange: (String) -> Unit,
) {
    when (param.type) {
        ParamType.SELECT -> TemplateSelectEditor(param, param.selectOptions(), value, onValueChange)
        ParamType.INT -> TemplateValueEditor(param, value, onValueChange, numericOnly = true)
        ParamType.STRING -> TemplateValueEditor(param, value, onValueChange, numericOnly = false)
        ParamType.BOOLEAN -> TemplateBooleanEditor(param, value.toBoolean(), onValueChange)
    }
}

@Composable
private fun TemplateSelectEditor(
    param: TemplateParam,
    options: List<SelectOption>,
    value: String,
    onValueChange: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(4.dp)
    val label = options.firstOrNull { it.value == value }?.label ?: value.ifBlank { param.defaultValue }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape)
            .clickable { expanded = true }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = param.label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Icon(Icons.Default.KeyboardArrowDown, contentDescription = null)
    }
    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
        options.forEach { option ->
            DropdownMenuItem(
                text = { Text(option.label) },
                onClick = {
                    expanded = false
                    onValueChange(option.value)
                },
            )
        }
    }
}

@Composable
private fun TemplateValueEditor(
    param: TemplateParam,
    value: String,
    onValueChange: (String) -> Unit,
    numericOnly: Boolean,
) {
    OutlinedTextField(
        value = value,
        onValueChange = { newText ->
            if (!numericOnly || newText.isEmpty() || newText.all { it.isDigit() || it == '-' }) {
                onValueChange(newText)
            }
        },
        modifier = Modifier.fillMaxWidth(),
        label = { Text(param.label) },
        placeholder = { Text(param.defaultValue) },
        singleLine = true,
        keyboardOptions = if (numericOnly) KeyboardOptions(keyboardType = KeyboardType.Number) else KeyboardOptions(),
    )
}

@Composable
private fun TemplateBooleanEditor(
    param: TemplateParam,
    value: Boolean,
    onValueChange: (String) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = param.label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Switch(
            checked = value,
            onCheckedChange = { onValueChange(it.toString()) },
        )
    }
}
