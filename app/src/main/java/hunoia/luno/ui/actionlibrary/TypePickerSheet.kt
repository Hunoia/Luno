package hunoia.luno.ui.actionlibrary

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import hunoia.luno.R
import hunoia.luno.action.definitions.ActionDefinition
import hunoia.luno.action.definitions.ActionDefinitions
import hunoia.luno.ui.component.displayNameRes
import hunoia.luno.action.definition.ActionCategory
import hunoia.luno.ui.component.OptimizedBottomSheet

@Composable
fun TypePickerSheet(
    onDismiss: () -> Unit,
    onSelectType: (ActionDefinition) -> Unit,
) {
    val definitions = ActionDefinitions.userDefinitions()
    val byCategory = definitions.groupBy { it.category }

    OptimizedBottomSheet(
        onDismissRequest = onDismiss,
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            Text(
                text = stringResource(R.string.action_type_picker_title),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 12.dp),
            )
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                byCategory.forEach { (category, categoryDefs) ->
                    item(key = "cat_$category") {
                        Text(
                            text = stringResource(category.displayNameRes),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = 12.dp, bottom = 4.dp, start = 4.dp),
                        )
                    }
                    items(items = categoryDefs, key = { it.typeId }) { def ->
                        TypePickerItem(
                            definition = def,
                            onClick = {
                                onDismiss()
                                onSelectType(def)
                            },
                        )
                    }
                }
                item {
                    TextButton(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        onClick = onDismiss,
                    ) {
                        Text(stringResource(R.string.cancel))
                    }
                }
            }
        }
    }
}

@Composable
private fun TypePickerItem(
    definition: ActionDefinition,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth().clickable(onClick = onClick),
        color = MaterialTheme.colorScheme.surfaceBright,
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Text(
                text = definition.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
            )
            if (definition.parameters.isNotEmpty()) {
                Text(
                    text = stringResource(R.string.action_param_count, definition.parameters.size),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (definition.capability != hunoia.luno.action.model.Capability.None) {
                Text(
                    text = stringResource(R.string.action_requires_capability, definition.capability.name),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}
