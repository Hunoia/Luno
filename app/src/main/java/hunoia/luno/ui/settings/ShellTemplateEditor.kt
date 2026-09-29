package hunoia.luno.ui.settings

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import hunoia.luno.R
import hunoia.luno.action.template.SystemFunctionTemplates
import hunoia.luno.action.template.TemplateParam
import hunoia.luno.config.model.ParamType
import hunoia.luno.config.model.ShellTemplateData

@Composable
fun ShellTemplateEditor(
    template: ShellTemplateData,
    generatedCommand: String,
    onTemplateChange: (ShellTemplateData, String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var query by rememberSaveable { mutableStateOf("") }

    val selectedTpl = SystemFunctionTemplates.getById(template.templateId)
    val filteredTemplates = remember(query) {
        SystemFunctionTemplates.templates.filter { t ->
            query.isBlank() || t.id.contains(query, ignoreCase = true) ||
                context.getString(t.nameResId).contains(query, ignoreCase = true)
        }
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
            placeholder = { Text(stringResource(R.string.search_hint_all)) },
            singleLine = true,
        )
        LazyColumn(
            modifier = Modifier.heightIn(max = 260.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            items(filteredTemplates, key = { it.id }) { tpl ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            val newParams = if (template.templateId != tpl.id) emptyMap() else template.params
                            val newTemplate = ShellTemplateData(tpl.id, newParams)
                            val newCmd = SystemFunctionTemplates.generateCommand(tpl, newParams)
                            onTemplateChange(newTemplate, newCmd)
                        }
                        .padding(vertical = 8.dp, horizontal = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(tpl.nameResId),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Text(
                            text = tpl.category,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (template.templateId == tpl.id) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
        }
        selectedTpl?.params?.filter { it.key in template.params.keys || it.defaultValue.isNotBlank() }?.let { params ->
            if (params.isNotEmpty()) {
                val tpl = selectedTpl
                Text(
                    text = stringResource(R.string.action_library_template_params),
                    style = MaterialTheme.typography.titleSmall,
                )
                params.forEach { param ->
                    TemplateParamEditor(
                        param = param,
                        value = template.params[param.key] ?: param.defaultValue,
                        onValueChange = { value ->
                            val newParams = template.params + (param.key to value)
                            val newTemplate = template.copy(params = newParams)
                            val newCmd = SystemFunctionTemplates.generateCommand(tpl, newParams)
                            onTemplateChange(newTemplate, newCmd)
                        },
                    )
                }
            }
        }
    }
}

private data class SelectOption(val value: String, val label: String)

private fun TemplateParam.selectOptions(): List<SelectOption> = options.map { raw ->
    val idx = raw.indexOf(':')
    if (idx >= 0) SelectOption(raw.substring(0, idx), raw.substring(idx + 1)) else SelectOption(raw, raw)
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
