package hunoia.luno.ui.actionlibrary

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import hunoia.luno.R
import hunoia.luno.action.template.SystemFunctionTemplates
import hunoia.luno.config.model.ActionLibraryEntry
import hunoia.luno.config.model.SystemTemplateData

@Composable
fun SystemTemplatePickerInline(
    entry: ActionLibraryEntry,
    onConfirm: (ActionLibraryEntry) -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    val selectedTemplateId = entry.systemTemplate.templateId
    val context = androidx.compose.ui.platform.LocalContext.current
    val filteredTemplates = remember(query) {
        SystemFunctionTemplates.templates.filter { template ->
            query.isBlank() || template.id.contains(query, ignoreCase = true) ||
                context.getString(template.nameResId).contains(query, ignoreCase = true)
        }
    }

    Column {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            placeholder = { Text(stringResource(R.string.search_hint_all)) },
            singleLine = true,
        )
        LazyColumn(
            modifier = Modifier.heightIn(max = 300.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            items(filteredTemplates, key = { it.id }) { template ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            val updated = entry.copy(
                                systemTemplate = SystemTemplateData(template.id)
                            )
                            onConfirm(updated)
                        }
                        .padding(vertical = 8.dp, horizontal = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(template.nameResId),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Text(
                            text = template.category,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (selectedTemplateId == template.id) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
        }
    }
}
