package hunoia.luno.ui.actionlibrary

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import hunoia.luno.R
import hunoia.luno.action.definitions.ActionDefinitions
import hunoia.luno.action.model.NewActionLibraryEntry
import hunoia.luno.action.model.matchesQuery
import hunoia.luno.action.definition.ActionCategory
import hunoia.luno.ui.component.AppSearchBar
import hunoia.luno.ui.component.EmptyState
import hunoia.luno.ui.component.displayNameRes
import hunoia.luno.ui.component.segmentedShape
import hunoia.luno.ui.theme.PageGutter
import hunoia.luno.ui.theme.SegmentedGap
import hunoia.luno.ui.theme.RowIconSize
import hunoia.luno.ui.theme.FloatingContentBottom
import hunoia.luno.ui.component.SegmentedSettingsRow


@Composable
fun NewActionLibraryScreen(
    listState: LazyListState = rememberLazyListState(),
    vm: NewActionLibraryVM = viewModel(),
    onNavToEdit: (String, String?) -> Unit = { _, _ -> },
    contentPadding: PaddingValues = PaddingValues(),
) {
    val uiState by vm.uiState.collectAsState()
    var query by rememberSaveable { mutableStateOf("") }
    var pendingDeleteEntry by remember { mutableStateOf<NewActionLibraryEntry?>(null) }
    var resetCounter by remember { mutableStateOf(0) }

    val filtered = remember(uiState.entries, query) {
        uiState.entries
            .filter { it.matchesQuery(query) }
            .sortedBy { ActionDefinitions.categoryOrder(ActionDefinitions.byTypeId(it.typeId)?.category ?: ActionCategory.INTERNAL) }
    }
    val grouped = remember(filtered) { filtered.groupBy { ActionDefinitions.byTypeId(it.typeId)?.category ?: ActionCategory.INTERNAL } }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize().padding(contentPadding)) {
            AppSearchBar(
                query = query,
                onQueryChange = { query = it },
                placeholder = stringResource(R.string.search_hint_all),
                modifier = Modifier.padding(start = 12.dp, top = 12.dp, end = 12.dp, bottom = 8.dp),
            )
            LazyColumn(
                modifier = Modifier.weight(1f),
                state = listState,
                contentPadding = PaddingValues(start = PageGutter, end = PageGutter, bottom = FloatingContentBottom),
                verticalArrangement = Arrangement.spacedBy(SegmentedGap),
            ) {
                if (filtered.isEmpty()) {
                    item { EmptyState(stringResource(R.string.action_library_empty)) }
                } else {
                    grouped.forEach { (category, entries) ->
                        stickyHeader(key = "header_${category}") {
                            Text(
                                text = stringResource(category.displayNameRes),
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier
                                    .animateItem()
                                    .fillMaxWidth()
                                    .padding(start = PageGutter, top = 8.dp, bottom = 16.dp),
                            )
                        }
                        itemsIndexed(entries, key = { _, entry -> entry.id }) { index, entry ->
                            ActionLibrarySwipeRow(
                                modifier = Modifier.animateItem(),
                                shape = segmentedShape(index, entries.size),
                                entry = entry,
                                referenceCount = uiState.referenceCounts[entry.id] ?: 0,
                                onNavToEdit = {
                                    onNavToEdit(entry.id, null)
                                },
                                onDismiss = { pendingDeleteEntry = entry },
                                resetKey = resetCounter,
                            )
                        }
                    }
                }
            }
        }

        pendingDeleteEntry?.let { entry ->
            val refCount = uiState.referenceCounts[entry.id] ?: 0
            AlertDialog(
                onDismissRequest = {
                    pendingDeleteEntry = null
                    resetCounter++
                },
                title = { Text(stringResource(R.string.action_library_delete_title)) },
                text = {
                    Text(stringResource(
                        if (refCount > 0) R.string.action_library_delete_desc
                        else R.string.action_library_delete_unused_desc,
                        refCount,
                    ))
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            vm.remove(entry)
                            pendingDeleteEntry = null
                        },
                    ) {
                        Text(stringResource(R.string.confirm))
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = {
                            pendingDeleteEntry = null
                            resetCounter++
                        },
                    ) {
                        Text(stringResource(R.string.cancel))
                    }
                },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ActionLibrarySwipeRow(
    modifier: Modifier = Modifier,
    shape: Shape,
    entry: NewActionLibraryEntry,
    referenceCount: Int,
    onNavToEdit: () -> Unit,
    onDismiss: () -> Unit,
    resetKey: Int = 0,
) {
    key(entry.id, resetKey) {
        val state = rememberSwipeToDismissBoxState()

        SwipeToDismissBox(
            modifier = modifier.clip(shape),
            state = state,
            enableDismissFromEndToStart = true,
            enableDismissFromStartToEnd = false,
            gesturesEnabled = true,
            onDismiss = { _ -> onDismiss() },
            backgroundContent = {
                val progress = state.progress
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            if (progress > 0f) MaterialTheme.colorScheme.errorContainer
                            else MaterialTheme.colorScheme.surface,
                        ),
                    contentAlignment = Alignment.CenterEnd,
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = stringResource(R.string.delete),
                        modifier = Modifier
                            .padding(end = 24.dp)
                            .graphicsLayer { alpha = progress.coerceIn(0f, 1f) },
                        tint = MaterialTheme.colorScheme.onErrorContainer,
                    )
                }
            },
            content = {
                ActionLibraryItem(
                    shape = shape,
                    entry = entry,
                    referenceCount = referenceCount,
                    onClick = onNavToEdit,
                )
            },
        )
    }
}

@Composable
private fun ActionLibraryItem(
    shape: Shape,
    entry: NewActionLibraryEntry,
    referenceCount: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val def = ActionDefinitions.byTypeId(entry.typeId)
    SegmentedSettingsRow(
        modifier = modifier,
        title = entry.name.ifBlank { def?.name ?: entry.typeId },
        subtitle = if (entry.name.isBlank()) "" else (def?.name ?: entry.typeId),
        icon = def?.icon ?: Icons.Default.Build,
        shape = shape,
        onClick = onClick,
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.action_library_reference_count, referenceCount),
                    style = MaterialTheme.typography.labelSmall,
                )
                Icon(
                    modifier = Modifier.size(RowIconSize).padding(start = 4.dp),
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                )
            }
        },
    )
}
