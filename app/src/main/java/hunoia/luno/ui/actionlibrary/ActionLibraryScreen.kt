package hunoia.luno.ui.actionlibrary

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import hunoia.luno.R
import hunoia.luno.config.model.Action
import hunoia.luno.config.model.ActionLibraryEntry
import hunoia.luno.config.model.ActionLibraryType
import hunoia.luno.config.model.OpenAppOrUrlData
import hunoia.luno.config.model.ShellCommandData
import hunoia.luno.core.JsonSerializer
import hunoia.luno.ui.component.AppSearchBar
import hunoia.luno.ui.component.EmptyState
import hunoia.luno.ui.component.TopBar
import hunoia.luno.ui.settings.ActivitySettingsContent
import hunoia.luno.ui.settings.ShellCommandSettingsContent
import hunoia.luno.ui.settings.UrlSettingsContent
import hunoia.luno.ui.theme.ContentPaddingHorizontal
import hunoia.luno.ui.theme.ScrollBottomPadding
import hunoia.luno.ui.theme.Spacing12
import hunoia.luno.ui.theme.Spacing4
import hunoia.luno.ui.theme.Spacing8

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActionLibraryScreen(
    onBack: () -> Unit,
    vm: ActionLibraryVM = viewModel(),
) {
    val uiState by vm.uiState.collectAsState()
    var query by rememberSaveable { mutableStateOf("") }
    var selectedType by rememberSaveable { mutableStateOf<ActionLibraryType?>(null) }
    var sortMode by rememberSaveable { mutableStateOf(ActionLibrarySortMode.CreatedAt) }
    var sortMenuExpanded by remember { mutableStateOf(false) }
    var selectionMode by rememberSaveable { mutableStateOf(false) }
    var selectedIds by rememberSaveable { mutableStateOf(emptySet<String>()) }
    var menuExpanded by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<ActionLibraryEntry?>(null) }
    var deleting by remember { mutableStateOf<ActionLibraryEntry?>(null) }
    var deletingSelected by remember { mutableStateOf(false) }
    val filtered = uiState.entries
        .filter { selectedType == null || it.type == selectedType }
        .filter { it.matchesQuery(query) }
        .sortedWith(actionLibraryComparator(sortMode, uiState.referenceCounts))
    val selectedEntries = uiState.entries.filter { it.id in selectedIds }
    Scaffold(
        topBar = { TopBar(onBack = onBack, title = stringResource(R.string.action_library)) },
        floatingActionButton = {
            FloatingActionButton(onClick = { menuExpanded = true }) {
                Icon(Icons.Default.Add, contentDescription = null)
                DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                    ActionLibraryType.entries.forEach { type ->
                        DropdownMenuItem(
                            text = { Text(stringResource(type.titleRes)) },
                            onClick = {
                                menuExpanded = false
                                editing = ActionLibraryEntry.create(type, defaultName(type, uiState.entries))
                            },
                        )
                    }
                }
            }
        }
    ) { padding ->
        LazyColumn(contentPadding = PaddingValues(bottom = ScrollBottomPadding), modifier = Modifier.padding(padding)) {
            item {
                AppSearchBar(
                    query = query,
                    onQueryChange = { query = it },
                    placeholder = stringResource(R.string.action_library_search_hint),
                    modifier = Modifier.padding(horizontal = ContentPaddingHorizontal * 2, vertical = Spacing8),
                )
            }
            item {
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
                    modifier = Modifier.padding(horizontal = ContentPaddingHorizontal * 2, vertical = Spacing4),
                )
            }
            if (filtered.isEmpty()) {
                item { EmptyState(stringResource(R.string.action_library_empty)) }
            } else {
                filtered.groupBy { it.type }.forEach { (type, entries) ->
                    item(key = "header_${type.name}") {
                        Text(
                            text = stringResource(type.titleRes),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = ContentPaddingHorizontal * 2, vertical = Spacing8),
                        )
                    }
                    items(entries, key = { it.id }) { entry ->
                        ActionLibraryItem(
                            entry = entry,
                            referenceCount = uiState.referenceCounts[entry.id] ?: 0,
                            selectionMode = selectionMode,
                            selected = entry.id in selectedIds,
                            onClick = {
                                if (selectionMode) {
                                    selectedIds = selectedIds.toggle(entry.id)
                                } else {
                                    editing = entry
                                }
                            },
                            onSelectedChange = { selected ->
                                selectedIds = if (selected) selectedIds + entry.id else selectedIds - entry.id
                            },
                            onEdit = { editing = entry },
                            onDuplicate = { vm.duplicate(entry, defaultName(entry.type, uiState.entries)) },
                            onDelete = { deleting = entry },
                        )
                    }
                }
            }
        }
    }
    editing?.let { entry ->
        ActionLibraryEditDialog(
            entry = entry,
            onDismiss = { editing = null },
            onSave = { vm.save(it); editing = null },
        )
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
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing4)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing8),
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
            horizontalArrangement = Arrangement.spacedBy(Spacing8),
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

@Composable
private fun ActionLibraryItem(
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
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing12, vertical = Spacing4),
    ) {
        Row(modifier = Modifier.fillMaxWidth().padding(Spacing12), horizontalArrangement = Arrangement.spacedBy(Spacing12)) {
            if (selectionMode) {
                Checkbox(checked = selected, onCheckedChange = onSelectedChange)
            }
            Icon(entry.type.icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Column(modifier = Modifier.weight(1f)) {
                Text(entry.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(entry.summary(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(stringResource(R.string.action_library_reference_count, referenceCount), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
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
    }
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

@Composable
private fun ActionLibraryEditDialog(entry: ActionLibraryEntry, onDismiss: () -> Unit, onSave: (ActionLibraryEntry) -> Unit) {
    var name by remember(entry.id) { mutableStateOf(entry.name) }
    var draftEntry by remember(entry.id) { mutableStateOf(entry) }
    val action = remember(entry) { entry.toConfigAction() }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing12)
                .heightIn(max = 720.dp),
        ) {
            Column(
                modifier = Modifier.padding(Spacing12),
                verticalArrangement = Arrangement.spacedBy(Spacing12),
            ) {
                Text(stringResource(R.string.action_library_edit), style = MaterialTheme.typography.titleLarge)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(Spacing12),
                ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = {
                        name = it
                        draftEntry = draftEntry.copy(name = it)
                    },
                    label = { Text(stringResource(R.string.action_library_entry_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                when (entry.type) {
                    ActionLibraryType.Shell -> ShellCommandSettingsContent(
                        action = action,
                        onConfirm = {},
                        showConfirmButton = false,
                        onDataChange = { data ->
                        val shell = JsonSerializer.decodeFromString<ShellCommandData>(data)
                        draftEntry = draftEntry.copy(name = name.ifBlank { entry.name }, shellCommand = shell)
                        },
                    )
                    ActionLibraryType.Url -> UrlSettingsContent(
                        action = action,
                        onConfirm = {},
                        showConfirmButton = false,
                        onDataChange = { data ->
                        draftEntry = draftEntry.copy(name = name.ifBlank { entry.name }, openAppOrUrl = JsonSerializer.decodeFromString<OpenAppOrUrlData>(data))
                        },
                    )
                    ActionLibraryType.Activity -> ActivitySettingsContent(
                        action = action,
                        onConfirm = {},
                        onDataChange = { data ->
                        draftEntry = draftEntry.copy(name = name.ifBlank { entry.name }, openAppOrUrl = JsonSerializer.decodeFromString<OpenAppOrUrlData>(data))
                        },
                    )
                }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
                    TextButton(onClick = { onSave(draftEntry.copy(name = name.ifBlank { entry.name })) }) {
                        Text(stringResource(R.string.save))
                    }
                }
            }
        }
    }
}

private val ActionLibraryType.titleRes: Int get() = when (this) {
    ActionLibraryType.Shell -> R.string.action_library_shell
    ActionLibraryType.Url -> R.string.action_library_url
    ActionLibraryType.Activity -> R.string.action_library_activity
}

private val ActionLibraryType.icon: ImageVector get() = when (this) {
    ActionLibraryType.Shell -> Icons.Default.Terminal
    ActionLibraryType.Url -> Icons.AutoMirrored.Filled.OpenInNew
    ActionLibraryType.Activity -> Icons.Default.Settings
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
}

private fun ActionLibraryEntry.toConfigAction(): Action = when (type) {
    ActionLibraryType.Shell -> Action(data = JsonSerializer.encodeToString(shellCommand))
    ActionLibraryType.Url,
    ActionLibraryType.Activity -> Action(data = JsonSerializer.encodeToString(openAppOrUrl))
}

private fun defaultName(type: ActionLibraryType, entries: List<ActionLibraryEntry>): String {
    val count = entries.count { it.type == type } + 1
    val res = when (type) {
        ActionLibraryType.Shell -> R.string.action_library_default_shell
        ActionLibraryType.Url -> R.string.action_library_default_url
        ActionLibraryType.Activity -> R.string.action_library_default_activity
    }
    return hunoia.luno.core.AppContext.get().getString(res, count)
}
