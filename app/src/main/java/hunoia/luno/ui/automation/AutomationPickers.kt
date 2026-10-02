package hunoia.luno.ui.automation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import hunoia.luno.R
import hunoia.luno.action.definitions.ActionDefinitions
import hunoia.luno.action.model.NewActionLibraryEntry
import hunoia.luno.ui.component.EmptyState
import hunoia.luno.ui.component.displayNameRes
import hunoia.luno.ui.component.SegmentedSettingsRow
import hunoia.luno.ui.theme.ContentBottom
import hunoia.luno.ui.theme.ListSpacing
import hunoia.luno.ui.theme.PageGutter
import hunoia.luno.ui.theme.SegmentedGap
import hunoia.luno.ui.theme.SheetTopShape

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConditionTypePickerSheet(
    onDismissRequest: () -> Unit,
    onPick: (String) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
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
                item(key = "group_level") {
                    TypePickerHeader(stringResource(R.string.condition_level_group))
                }
                items(items = LEVEL_CONDITION_TYPES, key = { "type_${it.type}" }) { spec ->
                    SegmentedSettingsRow(
                        title = stringResource(spec.labelRes),
                        onClick = {
                            onPick(spec.type)
                            onDismissRequest()
                        },
                    )
                }
                item(key = "group_event") {
                    TypePickerHeader(stringResource(R.string.condition_event_group))
                }
                items(items = EVENT_CONDITION_TYPES, key = { "type_${it.type}" }) { spec ->
                    SegmentedSettingsRow(
                        title = stringResource(spec.labelRes),
                        onClick = {
                            onPick(spec.type)
                            onDismissRequest()
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun TypePickerHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = ListSpacing),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EntryPickerSheet(
    entries: List<NewActionLibraryEntry>,
    selectedEntryId: String,
    onDismissRequest: () -> Unit,
    onPick: (String) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val available = entries.filter { it.typeId !in ActionDefinitions.automationExcludedTypeIds() }
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
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
                if (available.isEmpty()) {
                    item { EmptyState(message = stringResource(R.string.automation_entry_empty)) }
                }
                items(items = available.sortedBy { it.createdAt }, key = { it.id }) { entry ->
                    SegmentedSettingsRow(
                        title = entry.name.ifBlank {
                            stringResource(
                                ActionDefinitions.byTypeId(entry.typeId)?.category?.displayNameRes
                                    ?: R.string.automation_entry,
                            )
                        },
                        subtitle = if (entry.name.isBlank()) "" else entry.typeId,
                        onClick = {
                            onPick(entry.id)
                            onDismissRequest()
                        },
                        trailingContent = if (entry.id == selectedEntryId) {
                            {
                                Text(
                                    text = stringResource(R.string.confirm),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                        } else null,
                    )
                }
            }
        }
    }
}
