package hunoia.luno.ui.actionlibrary

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import hunoia.luno.R
import hunoia.luno.core.AppContext
import hunoia.luno.config.model.ActionLibraryEntry
import hunoia.luno.config.model.ActionLibraryType
import hunoia.luno.ui.component.AppSearchBar
import hunoia.luno.ui.component.segmentedShape
import hunoia.luno.ui.component.EmptyState
import hunoia.luno.ui.navigation.ActionLibraryEdit
import hunoia.luno.ui.navigation.NEW_ACTION_LIBRARY_ENTRY_ID
import hunoia.luno.ui.theme.ContainerRadius
import hunoia.luno.ui.theme.PageGutter
import hunoia.luno.ui.theme.SegmentedGap
import hunoia.luno.ui.settings.ActivitySettingsContent
import hunoia.luno.ui.settings.ShellCommandSettingsContent
import hunoia.luno.ui.settings.UrlSettingsContent

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ActionLibraryScreen(
    listState: LazyListState = rememberLazyListState(),
    vm: ActionLibraryVM = viewModel(),
    onNavToEdit: (ActionLibraryEdit) -> Unit = {},
    contentPadding: PaddingValues = PaddingValues(),
) {
    val uiState by vm.uiState.collectAsState()
    var query by rememberSaveable { mutableStateOf("") }
    var selectedType by rememberSaveable { mutableStateOf<ActionLibraryType?>(null) }
    var sortMode by rememberSaveable { mutableStateOf(ActionLibrarySortMode.CreatedAt) }
    var sortMenuExpanded by remember { mutableStateOf(false) }
    var selectionMode by rememberSaveable { mutableStateOf(false) }
    var selectedIds by rememberSaveable { mutableStateOf(emptySet<String>()) }
    var menuExpanded by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<ActionLibraryEntry?>(null) }
    var deletingSelected by remember { mutableStateOf(false) }
    val filtered = remember(uiState.entries, uiState.referenceCounts, selectedType, query, sortMode) {
        uiState.entries
            .filter { selectedType == null || it.type == selectedType }
            .filter { it.matchesQuery(query) }
            .sortedWith(actionLibraryComparator(sortMode, uiState.referenceCounts))
    }
    val grouped = remember(filtered) { filtered.groupBy { it.type } }
    val selectedEntries = remember(uiState.entries, selectedIds) {
        uiState.entries.filter { it.id in selectedIds }
    }
    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize().padding(contentPadding)) {
            AppSearchBar(
                query = query,
                onQueryChange = { query = it },
                placeholder = stringResource(R.string.search_hint_all),
                modifier = Modifier.padding(start = 12.dp, top = 12.dp, end = 12.dp, bottom = 8.dp),
            )
            ActionLibraryControls(
                selectedType = selectedType,
                onSelectedTypeChange = { selectedType = it },
                sortMode = sortMode,
                onSortModeChange = { sortMode = it },
                sortMenuExpanded = sortMenuExpanded,
                onSortMenuExpandedChange = { sortMenuExpanded = it },
                selectionMode = selectionMode,
                selectedCount = selectedIds.size,
                totalCount = filtered.size,
                onSelectionModeChange = { enabled ->
                    selectionMode = enabled
                    if (!enabled) selectedIds = emptySet()
                },
                onSelectAll = { selectedIds = filtered.map { it.id }.toSet() },
                onDeleteSelected = { deletingSelected = selectedIds.isNotEmpty() },
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
            )
            LazyColumn(
                modifier = Modifier.weight(1f),
                state = listState,
                contentPadding = PaddingValues(start = PageGutter, end = PageGutter, bottom = 120.dp),
                verticalArrangement = Arrangement.spacedBy(SegmentedGap),
            ) {
                if (filtered.isEmpty()) {
                    item { EmptyState(stringResource(R.string.action_library_empty)) }
                } else {
                    grouped.forEach { (type, entries) ->
                        stickyHeader(key = "header_${type.name}") {
                            Text(
                                text = stringResource(type.titleRes),
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(MaterialTheme.colorScheme.surfaceContainer)
                                    .padding(start = 16.dp, top = 8.dp, bottom = 16.dp),
                            )
                        }
                        itemsIndexed(entries, key = { _, entry -> entry.id }) { index, entry ->
                            ActionLibraryItem(
                                shape = segmentedShape(index, entries.size),
                                entry = entry,
                                referenceCount = uiState.referenceCounts[entry.id] ?: 0,
                                selectionMode = selectionMode,
                                selected = entry.id in selectedIds,
                                onClick = {
                                    if (selectionMode) {
                                        selectedIds = selectedIds.toggle(entry.id)
                                    } else {
                                        onNavToEdit(ActionLibraryEdit(entry.id, entry.type))
                                    }
                                },
                                onSelectedChange = { selected ->
                                    selectedIds = if (selected) selectedIds + entry.id else selectedIds - entry.id
                                },
                                onEdit = { onNavToEdit(ActionLibraryEdit(entry.id, entry.type)) },
                                onDuplicate = { vm.duplicate(entry, defaultActionLibraryName(entry.type, uiState.entries)) },
                                onDelete = { deleting = entry },
                            )
                        }
                    }
                }
            }
        }

        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
                .size(52.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                .clickable { menuExpanded = true },
            contentAlignment = Alignment.Center,
        ) {
            DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                ActionLibraryType.entries.forEach { type ->
                    DropdownMenuItem(
                        text = { Text(stringResource(type.titleRes)) },
                        onClick = {
                            menuExpanded = false
                            onNavToEdit(ActionLibraryEdit(NEW_ACTION_LIBRARY_ENTRY_ID, type))
                        },
                    )
                }
            }
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = stringResource(R.string.action_library_add),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    deleting?.let { entry ->
        val count = uiState.referenceCounts[entry.id] ?: 0
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text(stringResource(R.string.action_library_delete_title)) },
            text = {
                Text(
                    if (count == 0) stringResource(R.string.action_library_delete_unused_desc)
                    else stringResource(R.string.action_library_delete_desc, count)
                )
            },
            confirmButton = { TextButton(onClick = { vm.remove(entry); deleting = null }) { Text(stringResource(R.string.delete)) } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }
    if (deletingSelected) {
        val referenceCount = selectedEntries.sumOf { uiState.referenceCounts[it.id] ?: 0 }
        AlertDialog(
            onDismissRequest = { deletingSelected = false },
            title = { Text(stringResource(R.string.action_library_delete_selected_title)) },
            text = { Text(stringResource(R.string.action_library_delete_selected_desc, selectedEntries.size, referenceCount)) },
            confirmButton = {
                TextButton(onClick = {
                    vm.removeAll(selectedEntries)
                    selectedIds = emptySet()
                    selectionMode = false
                    deletingSelected = false
                }) { Text(stringResource(R.string.delete)) }
            },
            dismissButton = { TextButton(onClick = { deletingSelected = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

@Composable
private fun ActionLibraryControls(
    selectedType: ActionLibraryType?,
    onSelectedTypeChange: (ActionLibraryType?) -> Unit,
    sortMode: ActionLibrarySortMode,
    onSortModeChange: (ActionLibrarySortMode) -> Unit,
    sortMenuExpanded: Boolean,
    onSortMenuExpandedChange: (Boolean) -> Unit,
    selectionMode: Boolean,
    selectedCount: Int,
    totalCount: Int,
    onSelectionModeChange: (Boolean) -> Unit,
    onSelectAll: () -> Unit,
    onDeleteSelected: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FilterChip(
                selected = selectedType == null,
                onClick = { onSelectedTypeChange(null) },
                label = { Text(stringResource(R.string.all_categories)) },
            )
            ActionLibraryType.entries.forEach { type ->
                FilterChip(
                    selected = selectedType == type,
                    onClick = { onSelectedTypeChange(type) },
                    label = { Text(stringResource(type.titleRes)) },
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = { onSortMenuExpandedChange(true) }) {
                Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = null)
                Text(stringResource(sortMode.titleRes))
                DropdownMenu(expanded = sortMenuExpanded, onDismissRequest = { onSortMenuExpandedChange(false) }) {
                    ActionLibrarySortMode.entries.forEach { mode ->
                        DropdownMenuItem(
                            text = { Text(stringResource(mode.titleRes)) },
                            onClick = {
                                onSortModeChange(mode)
                                onSortMenuExpandedChange(false)
                            },
                        )
                    }
                }
            }
            TextButton(onClick = { onSelectionModeChange(!selectionMode) }) {
                Text(stringResource(if (selectionMode) R.string.cancel else R.string.action_library_batch_select))
            }
            if (selectionMode) {
                Text(
                    text = stringResource(R.string.action_library_selected_count, selectedCount),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                TextButton(enabled = totalCount > 0, onClick = onSelectAll) {
                    Text(stringResource(R.string.action_library_select_all))
                }
                TextButton(enabled = selectedCount > 0, onClick = onDeleteSelected) {
                    Text(stringResource(R.string.delete))
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ActionLibraryItem(
    shape: Shape,
    entry: ActionLibraryEntry,
    referenceCount: Int,
    selectionMode: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
    onSelectedChange: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val colorScheme = MaterialTheme.colorScheme
    val colors = if (selected) {
        ListItemDefaults.colors(
            containerColor = colorScheme.primaryContainer,
            contentColor = colorScheme.onPrimaryContainer,
            leadingContentColor = colorScheme.onPrimaryContainer,
            trailingContentColor = colorScheme.onPrimaryContainer,
        )
    } else {
        ListItemDefaults.colors(
            containerColor = colorScheme.surfaceBright,
            contentColor = colorScheme.onSurface,
            leadingContentColor = colorScheme.onSurfaceVariant,
            trailingContentColor = colorScheme.onSurfaceVariant,
        )
    }
    ListItem(
        onClick = onClick,
        content = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(entry.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(entry.summary(), style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        },
        leadingContent = {
            if (selectionMode) {
                Checkbox(checked = selected, onCheckedChange = onSelectedChange)
            } else {
                Icon(
                    entry.type.icon,
                    contentDescription = null,
                    tint = colorScheme.primary,
                    modifier = Modifier.size(24.dp),
                )
            }
        },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.action_library_reference_count, referenceCount),
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.contentColor,
                )
                if (!selectionMode) {
                    IconButton(onClick = { menuExpanded = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.more))
                        DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.action_library_menu_edit)) },
                                leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                                onClick = { menuExpanded = false; onEdit() },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.action_library_menu_duplicate)) },
                                leadingIcon = { Icon(Icons.Default.ContentCopy, contentDescription = null) },
                                onClick = { menuExpanded = false; onDuplicate() },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.delete)) },
                                leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) },
                                onClick = { menuExpanded = false; onDelete() },
                            )
                        }
                    }
                }
            }
        },
        shapes = ListItemDefaults.shapes(
            shape = shape,
            selectedShape = shape,
            pressedShape = RoundedCornerShape(ContainerRadius),
            focusedShape = shape,
            hoveredShape = shape,
            draggedShape = shape,
        ),
        colors = colors,
        contentPadding = PaddingValues(16.dp, 12.dp, 16.dp, 12.dp),
    )
}

private enum class ActionLibrarySortMode(val titleRes: Int) {
    CreatedAt(R.string.action_library_sort_created),
    Name(R.string.action_library_sort_name),
    ReferenceCount(R.string.action_library_sort_reference),
}

private fun actionLibraryComparator(
    sortMode: ActionLibrarySortMode,
    referenceCounts: Map<String, Int>,
): Comparator<ActionLibraryEntry> {
    val inner = when (sortMode) {
        ActionLibrarySortMode.CreatedAt -> compareBy<ActionLibraryEntry> { it.createdAt }
        ActionLibrarySortMode.Name -> compareBy(String.CASE_INSENSITIVE_ORDER) { it.name }
        ActionLibrarySortMode.ReferenceCount -> compareByDescending<ActionLibraryEntry> { referenceCounts[it.id] ?: 0 }
            .thenBy(String.CASE_INSENSITIVE_ORDER) { it.name }
    }
    return compareBy<ActionLibraryEntry> { it.type.sortIndex() }.then(inner)
}

private fun Set<String>.toggle(id: String): Set<String> = if (id in this) this - id else this + id

internal val ActionLibraryType.titleRes: Int get() = when (this) {
    ActionLibraryType.Shell -> R.string.action_library_shell
    ActionLibraryType.Url -> R.string.action_library_url
    ActionLibraryType.Activity -> R.string.action_library_activity
    ActionLibraryType.SystemTemplate -> R.string.action_library_system_function
    ActionLibraryType.SystemApi -> R.string.action_library_custom_system_api
}

private val ActionLibraryType.icon: ImageVector get() = when (this) {
    ActionLibraryType.Shell -> Icons.Default.Terminal
    ActionLibraryType.Url -> Icons.AutoMirrored.Filled.OpenInNew
    ActionLibraryType.Activity -> Icons.Default.Android
    ActionLibraryType.SystemTemplate -> Icons.Default.Build
    ActionLibraryType.SystemApi -> Icons.Default.Code
}

@Composable
private fun ActionLibraryEntry.summary(): String = when (type) {
    ActionLibraryType.Shell -> listOf(
        shellCommand.command.lineSequence().firstOrNull().orEmpty().ifBlank { "Shell" },
        stringResource(if (shellCommand.showToast) R.string.action_library_shell_toast_on else R.string.action_library_shell_toast_off),
    ).joinToString(" · ")
    ActionLibraryType.Url -> buildList {
        add(openAppOrUrl.url.ifBlank { "URL" })
        if (openAppOrUrl.miniWindow) add(stringResource(R.string.open_url_mini_window))
        val enabledParameters = openAppOrUrl.queryParameters.count { it.enabled && it.name.isNotBlank() }
        if (enabledParameters > 0) add(stringResource(R.string.action_library_url_parameter_count, enabledParameters))
    }.joinToString(" · ")
    ActionLibraryType.Activity -> listOf(openAppOrUrl.packageName, openAppOrUrl.activityClassName)
        .filter { it.isNotBlank() }
        .joinToString("/")
        .ifBlank { stringResource(R.string.action_library_activity_empty) }
    ActionLibraryType.SystemTemplate -> systemTemplate.templateId.ifBlank { "Template" }
    ActionLibraryType.SystemApi -> listOf(
        systemApi.command.lineSequence().firstOrNull().orEmpty().ifBlank { "API" },
        stringResource(if (systemApi.showToast) R.string.action_library_shell_toast_on else R.string.action_library_shell_toast_off),
    ).joinToString(" · ")
}

internal fun defaultActionLibraryName(type: ActionLibraryType, entries: List<ActionLibraryEntry>): String {
    val count = entries.count { it.type == type } + 1
    val res = when (type) {
        ActionLibraryType.Shell -> R.string.action_library_default_shell
        ActionLibraryType.Url -> R.string.action_library_default_url
        ActionLibraryType.Activity -> R.string.action_library_default_activity
        ActionLibraryType.SystemTemplate -> R.string.action_library_default_system_function
        ActionLibraryType.SystemApi -> R.string.action_library_default_custom_api
    }
    return AppContext.get().getString(res, count)
}
