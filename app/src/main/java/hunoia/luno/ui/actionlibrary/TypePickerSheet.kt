package hunoia.luno.ui.actionlibrary

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.graphics.Shape
import hunoia.luno.ui.component.segmentedShape
import hunoia.luno.ui.theme.CardShape
import hunoia.luno.ui.theme.CardInnerSpacing

import hunoia.luno.ui.theme.ContentBottom
import hunoia.luno.ui.theme.ListItemVerticalPadding
import hunoia.luno.ui.theme.ListSpacing
import hunoia.luno.ui.theme.PageGutter
import hunoia.luno.ui.theme.SegmentedGap
import hunoia.luno.R
import hunoia.luno.action.definitions.ActionDefinition
import hunoia.luno.action.definitions.ActionDefinitions
import hunoia.luno.ui.component.displayNameRes
import hunoia.luno.ui.component.OptimizedBottomSheet
import hunoia.luno.ui.component.SegmentedSettingsRow


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
        Column(modifier = Modifier.padding(horizontal = PageGutter, vertical = ListItemVerticalPadding)) {
            Text(
                text = stringResource(R.string.action_type_picker_title),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(bottom = ListSpacing),
            )
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(bottom = ContentBottom),
                verticalArrangement = Arrangement.spacedBy(SegmentedGap),
            ) {
                byCategory.forEach { (category, categoryDefs) ->
                    item(key = "cat_$category") {
                        Text(
                            text = stringResource(category.displayNameRes),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = ListSpacing, bottom = SegmentedGap, start = PageGutter),
                        )
                    }
                    items(items = categoryDefs, key = { "def_${it.typeId}" }) { def ->
                        TypePickerItem(
                            definition = def,
                            shape = segmentedShape(categoryDefs.indexOf(def), categoryDefs.size),
                            onClick = {
                                onDismiss()
                                onSelectType(def)
                            },
                        )
                    }
                }
                item {
                    TextButton(
                        modifier = Modifier.fillMaxWidth().padding(vertical = CardInnerSpacing),
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
    shape: Shape = CardShape,
) {
    SegmentedSettingsRow(
        modifier = modifier,
        title = definition.name,
        subtitle = if (definition.parameters.isNotEmpty()) {
            stringResource(R.string.action_param_count, definition.parameters.size)
        } else {
            ""
        },
        shape = shape,
        onClick = onClick,
        trailingContent = if (definition.capability != hunoia.luno.action.model.Capability.None) {
            {
                Text(
                    text = stringResource(R.string.action_requires_capability, definition.capability.name),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        } else {
            null
        },
    )
}
