package hunoia.luno.ui.quicklaunch
import hunoia.luno.ui.theme.*

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import hunoia.luno.R
import hunoia.luno.action.api.PasswordGenerator
import hunoia.luno.config.defaults.ActionSettingsDefaults.PasswordMaxLength
import hunoia.luno.config.defaults.ActionSettingsDefaults.PasswordMinLength
import hunoia.luno.config.ConfigProvider
import hunoia.luno.config.model.ActionSettings
import hunoia.luno.bridge.feedback.showToast
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
fun PasswordGeneratorPanel(
    onClose: () -> Unit,
    onCopyPassword: (String) -> Boolean,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var config by remember { mutableStateOf(ActionSettings.PasswordGenerator()) }
    var password by remember { mutableStateOf("") }
    var visible by remember { mutableStateOf(false) }
    var loaded by remember { mutableStateOf(false) }

    fun regenerate(nextConfig: ActionSettings.PasswordGenerator = config) {
        runCatching { PasswordGenerator.generate(nextConfig) }
            .onSuccess {
                password = it
            }
            .onFailure { showToast(R.string.password_generate_failed) }
    }

    fun saveConfig(nextConfig: ActionSettings.PasswordGenerator) {
        scope.launch {
            ConfigProvider.updateActionSettings { it.copy(passwordGenerator = nextConfig) }
        }
    }

    LaunchedEffect(Unit) {
        val saved = ConfigProvider.getActionSettings().passwordGenerator
        val normalized = PasswordGenerator.normalize(saved)
        config = normalized
        password = PasswordGenerator.generate(normalized)
        loaded = true
        if (normalized != saved) {
            ConfigProvider.updateActionSettings { it.copy(passwordGenerator = normalized) }
        }
    }

    if (loaded) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 560.dp)
                .verticalScroll(rememberScrollState())
                .padding(Spacing20),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
                    OutlinedTextField(
                        modifier = Modifier.fillMaxWidth(),
                        value = password,
                        onValueChange = {},
                        readOnly = true,
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace),
                        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { visible = !visible }) {
                                Icon(
                                    imageVector = if (visible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = null
                                )
                            }
                        }
                    )

                    Text(
                        text = context.getString(
                            R.string.password_entropy_bits,
                            PasswordGenerator.estimatedEntropyBits(password)
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Slider(
                            modifier = Modifier.weight(1f),
                            value = config.length.toFloat(),
                            onValueChange = {
                                config = config.copy(length = it.roundToInt().coerceIn(PasswordMinLength, PasswordMaxLength))
                            },
                            onValueChangeFinished = {
                                val normalized = PasswordGenerator.normalize(config)
                                config = normalized
                                saveConfig(normalized)
                                regenerate(normalized)
                            },
                            valueRange = PasswordMinLength.toFloat()..PasswordMaxLength.toFloat(),
                            steps = PasswordMaxLength - PasswordMinLength - 1,
                            colors = SliderDefaults.colors(
                                thumbColor = MaterialTheme.colorScheme.tertiary,
                                activeTrackColor = MaterialTheme.colorScheme.tertiary,
                            )
                        )
                        Spacer(modifier = Modifier.width(Spacing12))
                        Text(
                            text = config.length.toString(),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    CharacterTypeSwitches(
                        config = config,
                        onConfigChanged = { next ->
                            val normalized = PasswordGenerator.normalize(next)
                            config = normalized
                            saveConfig(normalized)
                            regenerate(normalized)
                        }
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        FilledTonalIconButton(onClick = { regenerate() }) {
                            Icon(imageVector = Icons.Default.Refresh, contentDescription = null)
                        }
                        Spacer(modifier = Modifier.width(Spacing24))
                        FilledTonalIconButton(
                            enabled = password.isNotEmpty(),
                            onClick = {
                                val copied = onCopyPassword(password)
                                showToast(if (copied) R.string.password_copied else R.string.password_copy_failed)
                            }
                        ) {
                            Icon(imageVector = Icons.Default.ContentCopy, contentDescription = null)
                        }
                    }
                }
            }
}

@Composable
private fun CharacterTypeSwitches(
    config: ActionSettings.PasswordGenerator,
    onConfigChanged: (ActionSettings.PasswordGenerator) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        CharacterTypeCheckbox("a", config.lowercase, Modifier.weight(1f)) { enabled ->
            onSwitchChange(config, config.copy(lowercase = enabled), onConfigChanged)
        }
        CharacterTypeCheckbox("A", config.uppercase, Modifier.weight(1f)) { enabled ->
            onSwitchChange(config, config.copy(uppercase = enabled), onConfigChanged)
        }
        CharacterTypeCheckbox("1", config.digits, Modifier.weight(1f)) { enabled ->
            onSwitchChange(config, config.copy(digits = enabled), onConfigChanged)
        }
        CharacterTypeCheckbox("#", config.symbols, Modifier.weight(1f)) { enabled ->
            onSwitchChange(config, config.copy(symbols = enabled), onConfigChanged)
        }
    }
}

private fun onSwitchChange(
    current: ActionSettings.PasswordGenerator,
    next: ActionSettings.PasswordGenerator,
    onConfigChanged: (ActionSettings.PasswordGenerator) -> Unit,
) {
    if (PasswordGenerator.enabledTypeCount(current) == 1 && PasswordGenerator.enabledTypeCount(next) == 0) {
        showToast(R.string.password_at_least_one_type)
        return
    }
    onConfigChanged(next)
}

@Composable
private fun CharacterTypeCheckbox(
    label: String,
    checked: Boolean,
    modifier: Modifier = Modifier,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = modifier.clickable { onCheckedChange(!checked) },
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(checked = checked, onCheckedChange = onCheckedChange)
        Text(text = label, style = MaterialTheme.typography.bodyMedium)
    }
}
