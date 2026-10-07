package hunoia.luno.ui.actionlibrary
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.stringResource
import hunoia.luno.ui.component.SheetGroupHeader
import hunoia.luno.ui.theme.PageGutter
import hunoia.luno.R
import hunoia.luno.action.definitions.ActionDefinition
import hunoia.luno.action.definitions.ActionDefinitions
import hunoia.luno.action.model.Capability
import hunoia.luno.ui.component.displayNameRes
import hunoia.luno.ui.component.row.LunoSettingsRow
import hunoia.luno.ui.component.row.segmentedShape
import hunoia.luno.ui.component.sheet.LunoSheet
import hunoia.luno.ui.component.sheet.SheetSearchList
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TypePickerSheet(
    onDismissRequest: () -> Unit,
    onSelectType: (ActionDefinition) -> Unit,
) {
    val byCategory = remember { ActionDefinitions.libraryDefinitions().groupBy { it.category } }
    var query by remember { mutableStateOf("") }
    val filtered = remember(query) {
        if (query.isBlank()) byCategory
        else byCategory.mapValues { (_, defs) ->
            defs.filter {
                it.name.contains(query, ignoreCase = true) ||
                    it.typeId.contains(query, ignoreCase = true)
            }
        }.filterValues { it.isNotEmpty() }
    }
    LunoSheet(
        onDismissRequest = onDismissRequest,
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(bottom = PageGutter)) {
            SheetSearchList(
                query = query,
                onQueryChange = { query = it },
            ) {
                filtered.forEach { (category, categoryDefs) ->
                    item(key = "cat_$category") {
                        SheetGroupHeader(category.displayNameRes)
                    }
                    itemsIndexed(items = categoryDefs, key = { _, def -> "def_${def.typeId}" }) { index, def ->
                        TypePickerItem(
                            definition = def,
                            shape = segmentedShape(index, categoryDefs.size),
                            onClick = { onSelectType(def) },
                        )
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
    shape: Shape,
) {
    LunoSettingsRow(
        title = definition.name,
        icon = definition.icon,
        subtitle = if (definition.parameters.isNotEmpty()) {
            stringResource(R.string.action_param_count, definition.parameters.size)
        } else {
            ""
        },
        shape = shape,
        onClick = onClick,
        trailing = if (definition.capability != Capability.None) {
            {
                Text(
                    text = stringResource(
                        R.string.action_requires_capability,
                        stringResource(definition.capability.displayNameRes),
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        } else {
            null
        },
    )
}