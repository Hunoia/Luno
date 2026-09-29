package hunoia.luno.ui.actionlibrary

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.unit.dp
import hunoia.luno.action.definitions.EnumOption
import hunoia.luno.action.definitions.ParameterDefinition

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ParameterEditor(
    definition: ParameterDefinition,
    currentParams: Map<String, String>,
    onParamChange: (String, String) -> Unit,
    onPickApp: (String) -> Unit = {},
    onPickActivity: (String, String) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier,
) {
    val required = definition.required
    when (definition) {
        is ParameterDefinition.Text -> {
            OutlinedTextField(
                value = currentParams[definition.key] ?: (definition.defaultValue ?: ""),
                onValueChange = { onParamChange(definition.key, it) },
                label = { Text(definition.label) },
                singleLine = true,
                modifier = modifier.fillMaxWidth(),
                isError = required && (currentParams[definition.key] ?: "").isBlank(),
            )
        }
        is ParameterDefinition.Number -> {
            OutlinedTextField(
                value = currentParams[definition.key] ?: (definition.defaultValue ?: ""),
                onValueChange = { onParamChange(definition.key, it) },
                label = { Text(definition.label) },
                singleLine = true,
                modifier = modifier.fillMaxWidth(),
                isError = required && (currentParams[definition.key] ?: "").isBlank(),
            )
        }
        is ParameterDefinition.Bool -> {
            Row(
                modifier = modifier.fillMaxWidth().padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(definition.label)
                Switch(
                    checked = currentParams[definition.key]?.toBoolean() ?: definition.defaultValue.toBoolean(),
                    onCheckedChange = { onParamChange(definition.key, it.toString()) },
                )
            }
        }
        is ParameterDefinition.Enum -> {
            val selectedValue = currentParams[definition.key] ?: (definition.defaultValue ?: "")
            val selectedOption = definition.options.find { it.value == selectedValue }
            var expanded by remember { mutableStateOf(false) }
            OutlinedTextField(
                value = selectedOption?.label ?: "",
                onValueChange = {},
                readOnly = true,
                label = { Text(definition.label) },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                modifier = modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded },
                isError = required && selectedValue.isBlank(),
            )
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier.fillMaxWidth(),
            ) {
                definition.options.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(option.label) },
                        onClick = {
                            onParamChange(definition.key, option.value)
                            expanded = false
                        },
                    )
                }
            }
        }
        is ParameterDefinition.AppSelector -> {
            val selected = currentParams[definition.key] ?: ""
            OutlinedTextField(
                value = selected,
                onValueChange = {},
                readOnly = true,
                label = { Text(definition.label) },
                modifier = modifier.fillMaxWidth().clickable { onPickApp(definition.key) },
                isError = required && selected.isBlank(),
            )
        }
        is ParameterDefinition.ActivitySelector -> {
            val selected = currentParams["activityClassName"] ?: ""
            OutlinedTextField(
                value = selected,
                onValueChange = {},
                readOnly = true,
                label = { Text(definition.label) },
                modifier = modifier.fillMaxWidth().clickable {
                    val pkg = currentParams["packageName"] ?: ""
                    if (pkg.isNotBlank()) onPickActivity(pkg, definition.key)
                },
                isError = required && selected.isBlank(),
            )
        }
        is ParameterDefinition.Path -> {
            OutlinedTextField(
                value = currentParams[definition.key] ?: (definition.defaultValue ?: ""),
                onValueChange = { onParamChange(definition.key, it) },
                label = { Text(definition.label) },
                singleLine = true,
                modifier = modifier.fillMaxWidth(),
                isError = required && (currentParams[definition.key] ?: "").isBlank(),
            )
        }
        is ParameterDefinition.TextLarge -> {
            OutlinedTextField(
                value = currentParams[definition.key] ?: (definition.defaultValue ?: ""),
                onValueChange = { onParamChange(definition.key, it) },
                label = { Text(definition.label) },
                minLines = 3,
                maxLines = 10,
                modifier = modifier.fillMaxWidth(),
                isError = required && (currentParams[definition.key] ?: "").isBlank(),
            )
        }
    }
}
