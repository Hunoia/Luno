package hunoia.luno.ui.actionlibrary

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
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
fun SystemApiConfigInline(
    entry: ActionLibraryEntry,
    onConfirm: (ActionLibraryEntry) -> Unit,
) {
    var command by remember { mutableStateOf(entry.systemApi.command) }
    var category by remember { mutableStateOf(entry.systemApi.category) }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        OutlinedTextField(
            value = category,
            onValueChange = {
                category = it
                val updated = entry.copy(
                    systemApi = SystemApiData(
                        category = category,
                        command = command
                    )
                )
                onConfirm(updated)
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
                val updated = entry.copy(
                    systemApi = SystemApiData(
                        category = category,
                        command = command
                    )
                )
                onConfirm(updated)
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
    }
}