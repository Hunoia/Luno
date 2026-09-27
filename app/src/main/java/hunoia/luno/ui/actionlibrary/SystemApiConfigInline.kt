package hunoia.luno.ui.actionlibrary

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import hunoia.luno.bridge.feedback.showToast
import hunoia.luno.config.model.ActionLibraryEntry
import hunoia.luno.config.model.SystemApiData
import hunoia.luno.shizuku.ShizukuFacade
import hunoia.luno.ui.settings.TestOutputBox
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun SystemApiConfigInline(
    entry: ActionLibraryEntry,
    onConfirm: (ActionLibraryEntry) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var category by remember { mutableStateOf(entry.systemApi.category) }
    var command by remember { mutableStateOf(entry.systemApi.command) }
    var showToastEnabled by remember { mutableStateOf(entry.systemApi.showToast) }
    var testing by remember { mutableStateOf(false) }
    var testOutput by remember { mutableStateOf("") }

    val testingLabel = stringResource(R.string.testing)
    val noOutputLabel = stringResource(R.string.shell_command_no_output)
    val shellSuccessFormat = stringResource(R.string.shell_command_test_output)
    val shellErrorFormat = stringResource(R.string.shell_command_test_error_output)

    fun update(category: String, command: String, showToast: Boolean) {
        onConfirm(entry.updateSystemApi(SystemApiData(category, command, showToast)))
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        OutlinedTextField(
            value = category,
            onValueChange = {
                category = it
                update(category, command, showToastEnabled)
            },
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.action_category_system)) },
            placeholder = { Text("audio, display, network, etc.") },
            singleLine = true,
        )
        OutlinedTextField(
            value = command,
            onValueChange = {
                command = it.take(2000)
                update(category, command, showToastEnabled)
            },
            modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp),
            label = { Text(stringResource(R.string.shell_command_label)) },
            placeholder = { Text(stringResource(R.string.template_command_placeholder)) },
            minLines = 3,
            maxLines = 6,
        )
        Text(
            text = stringResource(R.string.shell_command_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.shell_command_show_toast),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Switch(
                checked = showToastEnabled,
                onCheckedChange = {
                    showToastEnabled = it
                    update(category, command, showToastEnabled)
                },
            )
        }
        if (testOutput.isNotBlank()) {
            TestOutputBox(testOutput)
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(
                enabled = command.isNotBlank() && !testing,
                onClick = {
                    val testCommand = command.trim()
                    testing = true
                    testOutput = testingLabel
                    scope.launch {
                        val result = withContext(Dispatchers.IO) {
                            ShizukuFacade.runShellCommand(context.applicationContext, testCommand)
                        }
                        testing = false
                        val output = result.output.ifBlank { noOutputLabel }
                        testOutput = if (result.success) {
                            String.format(shellSuccessFormat, result.exitCode, result.elapsedMs, output)
                        } else {
                            String.format(
                                shellErrorFormat,
                                result.error ?: "unknown error",
                                result.exitCode,
                                result.elapsedMs,
                                output,
                            )
                        }
                        if (result.success) {
                            showToast(result.output.ifBlank { noOutputLabel }.take(500))
                        } else {
                            showToast((result.error ?: result.output.ifBlank { "unknown error" }).take(500))
                        }
                    }
                },
            ) {
                Text(stringResource(if (testing) R.string.testing else R.string.test))
            }
        }
    }
}
