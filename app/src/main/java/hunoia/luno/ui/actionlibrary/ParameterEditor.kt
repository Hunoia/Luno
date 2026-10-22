package hunoia.luno.ui.actionlibrary
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import hunoia.luno.R
import hunoia.luno.action.definitions.ParameterDefinition
import hunoia.luno.config.model.MiniWindowSettings
import hunoia.luno.config.model.toMiniWindowSettings
import hunoia.luno.core.AppContext
import hunoia.luno.ui.theme.ContainerRadius
import hunoia.luno.ui.theme.LargeShape
import hunoia.luno.ui.theme.ElevationSmall
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlin.math.roundToInt
import hunoia.luno.ui.component.CheckDropdownMenuItem
import hunoia.luno.ui.component.row.LunoSettingsRow
import hunoia.luno.ui.component.row.LunoSwitchRow
val ParameterDefinition.isCardRowType: Boolean
    get() = this is ParameterDefinition.Bool ||
        this is ParameterDefinition.Enum ||
        this is ParameterDefinition.AppSelector ||
        this is ParameterDefinition.AppSelectorMulti ||
        this is ParameterDefinition.ButtonSelectorMulti ||
        this is ParameterDefinition.MiniWindow ||
        this is ParameterDefinition.ActivitySelector
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ParameterEditor(
    definition: ParameterDefinition,
    currentParams: Map<String, JsonElement>,
    onParamChange: (String, String) -> Unit,
    onPickApp: (String) -> Unit = {},
    onPickApps: (String) -> Unit = {},
    onPickButtons: (String) -> Unit = {},
    onPickMiniWindow: (String) -> Unit = {},
    onPickActivity: (String, String) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(ContainerRadius),
) {
    val required = definition.required
    val errorColor = MaterialTheme.colorScheme.error
    val hintColor = MaterialTheme.colorScheme.onSurfaceVariant
    val unselected = stringResource(R.string.action_param_unselected)
    when (definition) {
        is ParameterDefinition.Text -> {
            val value = currentParams[definition.key]?.scalarText() ?: (definition.defaultValue ?: "")
            TemplateTextField(
                value = value,
                onValueChange = { onParamChange(definition.key, it) },
                label = definition.label,
                isError = required && value.isBlank(),
                singleLine = true,
                allowTemplates = definition.allowTemplates,
                modifier = modifier.fillMaxWidth(),
            )
        }
        is ParameterDefinition.Number -> {
            val value = currentParams[definition.key]?.scalarText() ?: (definition.defaultValue ?: "")
            // 数字参数也允许模板：整串是 ${...}/表达式时运行时展开，交给 ActionMapper 按字符串解析。
            val hasTemplate = '$' in value
            TemplateTextField(
                value = value,
                onValueChange = { onParamChange(definition.key, it) },
                label = definition.label,
                isError = (required && value.isBlank()) || (!hasTemplate && !inRange(value, definition)),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = modifier.fillMaxWidth(),
            )
        }
        is ParameterDefinition.Bool -> {
            LunoSwitchRow(
                title = definition.label,
                checked = currentParams[definition.key]?.scalarText()?.toBoolean()
                    ?: definition.defaultValue.toBoolean(),
                onCheckedChange = { onParamChange(definition.key, it.toString()) },
                shape = shape,
                modifier = modifier.fillMaxWidth(),
            )
        }
        is ParameterDefinition.Enum -> {
            val selectedValue = currentParams[definition.key]?.scalarText() ?: (definition.defaultValue ?: "")
            val selectedOption = definition.options.find { it.value == selectedValue }
            var expanded by remember { mutableStateOf(false) }
            val missing = required && selectedOption == null
            Box {
                LunoSettingsRow(
                    title = definition.label,
                    subtitle = selectedOption?.label ?: selectedValue.ifBlank { unselected },
                    onClick = { expanded = !expanded },
                    shape = shape,
                    modifier = modifier.fillMaxWidth(),
                    subtitleColor = if (missing) errorColor else hintColor,
                )
                DropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false },
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    shape = LargeShape,
                    tonalElevation = ElevationSmall,
                ) {
                    definition.options.forEach { option ->
                        CheckDropdownMenuItem(
                            label = option.label,
                            selected = option.value == selectedValue,
                            onClick = {
                                onParamChange(definition.key, option.value)
                                expanded = false
                            },
                        )
                    }
                }
            }
        }
        is ParameterDefinition.AppSelector -> {
            val selected = currentParams[definition.key]?.scalarText() ?: ""
            var appLabel by remember(selected) { mutableStateOf<String?>(null) }
            LaunchedEffect(selected) {
                appLabel = if (selected.isBlank()) null else resolveAppLabel(selected)
            }
            val display = when {
                selected.isBlank() -> unselected
                appLabel != null && appLabel != selected -> "$appLabel ($selected)"
                else -> selected
            }
            val missing = required && selected.isBlank()
            LunoSettingsRow(
                title = definition.label,
                subtitle = display,
                onClick = { onPickApp(definition.key) },
                shape = shape,
                modifier = modifier.fillMaxWidth(),
                subtitleColor = if (missing) errorColor else hintColor,
            )
        }
        is ParameterDefinition.AppSelectorMulti -> {
            val selected = currentParams[definition.key]?.jsonArray?.mapNotNull {
                it.jsonPrimitive?.contentOrNull
            } ?: emptyList()
            val multiLabel = stringResource(R.string.action_param_multi_selected, selected.size)
            val display = if (selected.isEmpty()) unselected else multiLabel
            LunoSettingsRow(
                title = definition.label,
                subtitle = display,
                onClick = { onPickApps(definition.key) },
                shape = shape,
                modifier = modifier.fillMaxWidth(),
            )
        }
        is ParameterDefinition.ButtonSelectorMulti -> {
            val selected = currentParams[definition.key]?.jsonArray?.mapNotNull {
                it.jsonPrimitive?.contentOrNull
            } ?: emptyList()
            val multiLabel = stringResource(R.string.action_param_multi_selected, selected.size)
            val display = if (selected.isEmpty()) unselected else multiLabel
            LunoSettingsRow(
                title = definition.label,
                subtitle = display,
                onClick = { onPickButtons(definition.key) },
                shape = shape,
                modifier = modifier.fillMaxWidth(),
            )
        }
        is ParameterDefinition.MiniWindow -> {
            val settings = currentParams[definition.key]?.toMiniWindowSettings() ?: MiniWindowSettings()
            // 竖横屏各一套，摘要跟着当前方向走 —— 运行时也是这么选的。
            val bounds = settings.forOrientation(
                LocalConfiguration.current.orientation ==
                    android.content.res.Configuration.ORIENTATION_LANDSCAPE
            )
            val display = stringResource(
                R.string.mini_window_size_summary,
                bounds.width.times(100).roundToInt(),
                bounds.height.times(100).roundToInt(),
            )
            LunoSettingsRow(
                title = definition.label,
                subtitle = display,
                onClick = { onPickMiniWindow(definition.key) },
                shape = shape,
                modifier = modifier.fillMaxWidth(),
            )
        }
        is ParameterDefinition.ActivitySelector -> {
            val selected = currentParams[definition.key]?.scalarText() ?: ""
            val packageName = currentParams[definition.packageKey]?.scalarText() ?: ""
            val enabled = packageName.isNotBlank()
            val display = selected.ifBlank { unselected }
            val missing = required && selected.isBlank()
            LunoSettingsRow(
                title = definition.label,
                subtitle = display,
                onClick = { if (enabled) onPickActivity(packageName, definition.key) },
                shape = shape,
                modifier = modifier.fillMaxWidth(),
                enabled = enabled,
                subtitleColor = if (missing && enabled) errorColor else hintColor,
            )
        }
        is ParameterDefinition.Path -> {
            val value = currentParams[definition.key]?.scalarText() ?: (definition.defaultValue ?: "")
            OutlinedTextField(
                value = value,
                onValueChange = { onParamChange(definition.key, it) },
                label = { Text(definition.label) },
                singleLine = true,
                modifier = modifier.fillMaxWidth(),
                isError = required && value.isBlank(),
                shape = LargeShape,
            )
        }
        is ParameterDefinition.TextLarge -> {
            val value = currentParams[definition.key]?.scalarText() ?: (definition.defaultValue ?: "")
            TemplateTextField(
                value = value,
                onValueChange = { onParamChange(definition.key, it) },
                label = definition.label,
                isError = required && value.isBlank(),
                singleLine = false,
                allowTemplates = definition.allowTemplates,
                modifier = modifier.fillMaxWidth(),
            )
        }
    }
}
private fun inRange(value: String, definition: ParameterDefinition.Number): Boolean {
    val number = value.toIntOrNull() ?: return false
    return (definition.min == null || number >= definition.min) &&
        (definition.max == null || number <= definition.max)
}
private suspend fun resolveAppLabel(packageName: String): String? = withContext(Dispatchers.IO) {
    runCatching {
        val pm = AppContext.get().packageManager
        pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString()
    }.getOrNull()
}