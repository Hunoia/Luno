package hunoia.luno.ui.actionlibrary

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.stringResource
import hunoia.luno.ui.component.segmentedShape
import hunoia.luno.ui.theme.CardInnerSpacing
import hunoia.luno.ui.theme.ContentBottom
import hunoia.luno.ui.theme.ListSpacing
import hunoia.luno.ui.theme.PageGutter
import hunoia.luno.ui.theme.SegmentedGap
import hunoia.luno.ui.theme.SheetTopShape
import hunoia.luno.R
import hunoia.luno.action.definitions.ActionDefinition
import hunoia.luno.action.definitions.ActionDefinitions
import hunoia.luno.action.model.Capability
import hunoia.luno.ui.component.displayNameRes
import hunoia.luno.ui.component.SegmentedSettingsRow


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TypePickerSheet(
    onDismiss: () -> Unit,
    onSelectType: (ActionDefinition) -> Unit,
) {
    val byCategory = remember { ActionDefinitions.libraryDefinitions().groupBy { it.category } }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        shape = SheetTopShape,
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(bottom = PageGutter)) {
            LazyColumn(
                modifier = Modifier.fillMaxWidth().padding(horizontal = PageGutter),
                contentPadding = PaddingValues(bottom = ContentBottom),
                verticalArrangement = Arrangement.spacedBy(SegmentedGap),
            ) {
                byCategory.forEach { (category, categoryDefs) ->
                    item(key = "cat_$category") {
                        Text(
                            text = stringResource(category.displayNameRes),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = ListSpacing, bottom = SegmentedGap),
                        )
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
            TextButton(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = PageGutter, vertical = CardInnerSpacing),
                onClick = onDismiss,
            ) {
                Text(stringResource(R.string.cancel))
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
    SegmentedSettingsRow(
        title = definition.name,
        icon = definition.icon,
        subtitle = if (definition.parameters.isNotEmpty()) {
            stringResource(R.string.action_param_count, definition.parameters.size)
        } else {
            ""
        },
        shape = shape,
        onClick = onClick,
        trailingContent = if (definition.capability != Capability.None) {
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
