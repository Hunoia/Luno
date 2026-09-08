package hunoia.luno.ui.settings

import hunoia.luno.ui.theme.*

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import hunoia.luno.R
import hunoia.luno.config.model.ShellCommandData
import hunoia.luno.bridge.feedback.showToast
import hunoia.luno.config.model.Action
import hunoia.luno.core.JsonSerializer
import hunoia.luno.shizuku.ShizukuFacade
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun ShellCommandSettingsContent(
    action: Action,
    onConfirm: (String) -> Unit,
    showConfirmButton: Boolean = true,
    onDataChange: ((String) -> Unit)? = null,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val existingData = remember(action.data) {
        runCatching { JsonSerializer.decodeFromString<ShellCommandData>(action.data) }.getOrNull()
    }
    var command by remember(action.data) { mutableStateOf(existingData?.command.orEmpty()) }
    var showToast by remember(action.data) { mutableStateOf(existingData?.showToast ?: true) }
    var testing by remember { mutableStateOf(false) }
    var testOutput by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = ItemPadding),
        verticalArrangement = Arrangement.spacedBy(ItemPadding)
    ) {
        OutlinedTextField(
            modifier = Modifier.fillMaxWidth(),
            value = command,
            onValueChange = {
                command = it.take(2000)
                onDataChange?.invoke(JsonSerializer.encodeToString(ShellCommandData(command.trim(), showToast)))
            },
            label = { Text(stringResource(R.string.shell_command_label)) },
            placeholder = { Text(stringResource(R.string.shell_command_placeholder)) },
            minLines = 3,
            maxLines = 6,
        )
        Text(
            text = stringResource(R.string.shell_command_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.shell_command_show_toast),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Switch(
                checked = showToast,
                onCheckedChange = {
                    showToast = it
                    onDataChange?.invoke(JsonSerializer.encodeToString(ShellCommandData(command.trim(), showToast)))
                }
            )
        }
        if (testOutput.isNotBlank()) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surfaceVariant
            ) {
                Text(
                    text = testOutput,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 180.dp)
                        .verticalScroll(rememberScrollState())
                        .padding(ItemPadding),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(
                enabled = command.isNotBlank() && !testing,
                onClick = {
                    val testCommand = command.trim()
                    testing = true
                    testOutput = context.getString(R.string.testing)
                    scope.launch {
                        val result = withContext(Dispatchers.IO) {
                            ShizukuFacade.runShellCommand(context.applicationContext, testCommand)
                        }
                        testing = false
                        val output = result.output.ifBlank { context.getString(R.string.shell_command_no_output) }
                        testOutput = if (result.success) {
                            context.getString(
                                R.string.shell_command_test_output,
                                result.exitCode,
                                result.elapsedMs,
                                output
                            )
                        } else {
                            context.getString(
                                R.string.shell_command_test_error_output,
                                result.error ?: "unknown error",
                                result.exitCode,
                                result.elapsedMs,
                                output
                            )
                        }
                        if (result.success) {
                            showToast(result.output.ifBlank { context.getString(R.string.shell_command_no_output) }.take(500))
                        } else {
                            showToast((result.error ?: result.output.ifBlank { "unknown error" }).take(500))
                        }
                    }
                }
            ) {
                Text(stringResource(if (testing) R.string.testing else R.string.test))
            }
            if (showConfirmButton) {
                TextButton(
                    enabled = command.isNotBlank(),
                    onClick = {
                        onConfirm(JsonSerializer.encodeToString(ShellCommandData(command.trim(), showToast)))
                    }
                ) {
                    Icon(imageVector = Icons.Default.Check, contentDescription = null)
                    Text(text = stringResource(id = R.string.confirm))
                }
            }
        }
    }
}
