package hunoia.luno.ui.actionlibrary

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import hunoia.luno.action.definitions.ParameterDefinition
import hunoia.luno.core.AppContext
import hunoia.luno.ui.component.SegmentedSwitchRow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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
            SegmentedSwitchRow(
                title = definition.label,
                checked = currentParams[definition.key]?.toBoolean() ?: definition.defaultValue.toBoolean(),
                onCheckedChange = { onParamChange(definition.key, it.toString()) },
                modifier = modifier,
            )
        }
        is ParameterDefinition.Enum -> {
            val selectedValue = currentParams[definition.key] ?: (definition.defaultValue ?: "")
            val selectedOption = definition.options.find { it.value == selectedValue }
            var expanded by remember { mutableStateOf(false) }
            val interactionSource = remember { MutableInteractionSource() }
            rememberTapHandler(interactionSource, Unit) { expanded = !expanded }
            OutlinedTextField(
                value = selectedOption?.label ?: "",
                onValueChange = {},
                readOnly = true,
                label = { Text(definition.label) },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                interactionSource = interactionSource,
                modifier = modifier.fillMaxWidth(),
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
            var appLabel by remember(selected) { mutableStateOf<String?>(null) }
            LaunchedEffect(selected) {
                appLabel = if (selected.isBlank()) null else resolveAppLabel(selected)
            }
            val display = when {
                selected.isBlank() -> ""
                appLabel != null && appLabel != selected -> "$appLabel ($selected)"
                else -> selected
            }
            val interactionSource = remember { MutableInteractionSource() }
            rememberTapHandler(interactionSource, Unit) { onPickApp(definition.key) }
            OutlinedTextField(
                value = display,
                onValueChange = {},
                readOnly = true,
                label = { Text(definition.label) },
                interactionSource = interactionSource,
                modifier = modifier.fillMaxWidth(),
                isError = required && selected.isBlank(),
            )
        }
        is ParameterDefinition.ActivitySelector -> {
            val selected = currentParams["activityClassName"] ?: ""
            val pkg = currentParams["packageName"] ?: ""
            val interactionSource = remember { MutableInteractionSource() }
            rememberTapHandler(interactionSource, pkg) {
                if (pkg.isNotBlank()) onPickActivity(pkg, definition.key)
            }
            OutlinedTextField(
                value = selected,
                onValueChange = {},
                readOnly = true,
                label = { Text(definition.label) },
                interactionSource = interactionSource,
                modifier = modifier.fillMaxWidth(),
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

/**
 * OutlinedTextField consumes its own taps internally, so [Modifier.clickable] applied to it never
 * fires. The field instead emits [PressInteraction.Release] on its [MutableInteractionSource]
 * whenever it is tapped, which we observe here.
 */
@Composable
private fun rememberTapHandler(
    interactionSource: MutableInteractionSource,
    keys: Any,
    onRelease: () -> Unit,
) {
    LaunchedEffect(interactionSource, keys) {
        interactionSource.interactions.collect { interaction ->
            if (interaction is PressInteraction.Release) onRelease()
        }
    }
}

private suspend fun resolveAppLabel(packageName: String): String? = withContext(Dispatchers.IO) {
    runCatching {
        val pm = AppContext.get().packageManager
        pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString()
    }.getOrNull()
}
