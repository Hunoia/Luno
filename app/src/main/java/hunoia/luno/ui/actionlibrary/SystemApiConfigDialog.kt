package hunoia.luno.ui.actionlibrary

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import hunoia.luno.R
import hunoia.luno.config.model.ActionLibraryEntry
import hunoia.luno.config.model.SystemApiData

@Composable
fun SystemApiConfigDialog(
    entry: ActionLibraryEntry,
    onDismiss: () -> Unit,
    onConfirm: (ActionLibraryEntry) -> Unit,
) {
    var command by remember { mutableStateOf(entry.systemApi.command) }
    var category by remember { mutableStateOf(entry.systemApi.category) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.custom_system_api)) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                OutlinedTextField(
                    value = category,
                    onValueChange = { category = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.action_category_system)) },
                    placeholder = { Text("audio, display, network, etc.") },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = command,
                    onValueChange = { command = it.take(2000) },
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
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val updated = entry.copy(
                        systemApi = SystemApiData(
                            category = category,
                            command = command
                        )
                    )
                    onConfirm(updated)
                },
                enabled = command.isNotBlank()
            ) {
                Text(stringResource(R.string.confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}