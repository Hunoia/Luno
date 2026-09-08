package hunoia.luno.ui.actionselect

import android.content.Context
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import hunoia.luno.R
import hunoia.luno.action.api.ActionFacade
import hunoia.luno.action.definition.ActionCategory
import hunoia.luno.config.model.Action
import hunoia.luno.config.model.ActionLibraryEntry
import hunoia.luno.config.model.ActionLibraryType
import hunoia.luno.config.model.SubGesture
import hunoia.luno.quicklaunch.model.AppInfo
import hunoia.luno.quicklaunch.model.LauncherInfo
import hunoia.luno.quicklaunch.model.qualifiedName
import hunoia.luno.ui.actionselect.UiState.SelectedRecord
import hunoia.luno.ui.component.AppSearchBar
import hunoia.luno.ui.component.EmptyState
import hunoia.luno.ui.component.displayNameRes
import hunoia.luno.ui.actionlibrary.matchesQuery
import hunoia.luno.ui.actionlibrary.sortIndex
import hunoia.luno.ui.theme.*

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun ActionPage(
    onSettingsClick: (Action) -> Unit,
    onSelect: (Action, Boolean) -> Unit,
    onSelectLibraryEntry: (ActionLibraryEntry, Boolean) -> Unit = { _, _ -> },
    onSelectLongPress: (Any) -> Unit = {},
    onSelectApp: (AppInfo, Boolean) -> Unit,
    onSelectShortcut: (LauncherInfo.ShortcutInfo, Boolean) -> Unit,
    onSetLongPress: (Int) -> Unit = {},
    onClearLongPress: (Int) -> Unit = {},
    onCancelLongPress: () -> Unit = {},
    onMoveSelected: (Int, Int) -> Unit = { _, _ -> },
    onAppLongClick: (AppInfo) -> Unit,
    onShortcutClick: (LauncherInfo) -> Unit = {},
    modifier: Modifier = Modifier,
    subGestures: List<SubGesture> = emptyList(),
    actions: List<Action>,
    actionLibraryEntries: List<ActionLibraryEntry> = emptyList(),
    appInfos: List<AppInfo>,
    createShortcuts: List<LauncherInfo>,
    launchShortcuts: List<LauncherInfo>,
    selectedRecord: SelectedRecord,
    longPressTargetIndex: Int?,
    selectSingle: Boolean,
    snackbarHostState: SnackbarHostState,
    permissionState: hunoia.luno.ui.permission.PermissionState,
    contentPadding: PaddingValues = PaddingValues(),
    maxSelectCount: Int = MAX_SELECT_COUNT
) {
    var query by rememberSaveable { mutableStateOf("") }
    var selectedCategory by rememberSaveable { mutableStateOf<ActionCategory?>(null) }
    var selectedType by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val selectingLongPress = longPressTargetIndex != null
    val categoryChips = remember(selectSingle) {
        buildList {
            add(null to context.getString(R.string.all_categories))
            add(ActionCategory.NAVIGATION to context.getString(ActionCategory.NAVIGATION.displayNameRes))
            add(ActionCategory.SYSTEM to context.getString(ActionCategory.SYSTEM.displayNameRes))
            add(ActionCategory.TOOL to context.getString(ActionCategory.TOOL.displayNameRes))
            add(ActionCategory.SUB_GESTURE to context.getString(ActionCategory.SUB_GESTURE.displayNameRes))
            add("action_library" to context.getString(R.string.action_library))
            add("app" to context.getString(R.string.tab_apps))
            add("shortcut" to context.getString(R.string.tab_shortcuts))
        }
    }
    LaunchedEffect(selectSingle) {
        if (selectSingle) selectedType = null
    }
    val filteredActions = remember(actions, query, selectedCategory, selectedType, selectingLongPress) {
        if (query.isNotBlank()) {
            var result = actions
            if (selectedCategory != null) {
                result = result.filter { action ->
                    val cat = ActionFacade.byId(action.value)?.category ?: ActionCategory.TOOL
                    cat == selectedCategory
                }
            }
            result = result.filter {
                context.actionTextWithSubGesture(it, subGestures, actionLibraryEntries, emptyIfNone = false)
                    .contains(query, ignoreCase = true)
            }
            if (selectedType == "action_library") emptyList() else result
        } else if (selectedType == "app" || selectedType == "shortcut") emptyList()
        else if (selectedType == "action_library") emptyList()
        else {
            var result = actions
            if (selectedType == "sub_gesture") {
                result = result.filter { it.value == ActionFacade.SUB_GESTURE }
            }
            if (selectedCategory != null) {
                result = result.filter { action ->
                    val cat = ActionFacade.byId(action.value)?.category ?: ActionCategory.TOOL
                    cat == selectedCategory
                }
            }
            result
        }
    }
    val grouped = remember(filteredActions) {
        val map = LinkedHashMap<ActionCategory, MutableList<Action>>()
        filteredActions.forEach { action ->
            val category = ActionFacade.byId(action.value)?.category ?: ActionCategory.TOOL
            map.getOrPut(category) { mutableListOf() }.add(action)
        }
        map
    }
    val selectedItems = selectedRecord.list
    val filteredLibraryEntries = remember(actionLibraryEntries, query, selectedType) {
        if (query.isNotBlank()) actionLibraryEntries.filter { it.matchesQuery(query) }
        else if (selectedType != "action_library") emptyList()
        else actionLibraryEntries
    }.sortedWith(compareBy<ActionLibraryEntry> { it.type.sortIndex() }.thenBy { it.createdAt })
    val filteredApps = remember(appInfos, query, selectedType) {
        if (query.isNotBlank()) appInfos.filter { it.label.contains(query, ignoreCase = true) || it.packageName.contains(query, ignoreCase = true) }
        else if (selectedType != "app") emptyList()
        else appInfos
    }
    val filteredCreateShortcuts = remember(createShortcuts, query, selectedType) {
        if (query.isNotBlank()) createShortcuts.filter { it.label.contains(query, ignoreCase = true) || it.shortcuts.any { s -> s.label.contains(query, ignoreCase = true) } }
        else if (selectedType != "shortcut") emptyList()
        else createShortcuts
    }
    val filteredLaunchShortcuts = remember(launchShortcuts, query, selectedType) {
        if (query.isNotBlank()) launchShortcuts.filter { it.label.contains(query, ignoreCase = true) || it.shortcuts.any { s -> s.label.contains(query, ignoreCase = true) } }
        else if (selectedType != "shortcut") emptyList()
        else launchShortcuts
    }
    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = contentPadding
    ) {
        item(key = "search") {
            AppSearchBar(
                query = query,
                onQueryChange = { query = it },
                modifier = Modifier.padding(horizontal = ContentPaddingHorizontal * 2, vertical = 8.dp),
                placeholder = stringResource(R.string.search_hint_all),
            )
        }
        item(key = "category_chips") {
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = ContentPaddingHorizontal * 2, vertical = Spacing4),
                horizontalArrangement = Arrangement.spacedBy(Spacing8)
            ) {
                items(categoryChips) { (chipKey, label) ->
                    val isSelected = when (chipKey) {
                        null -> selectedType == null && selectedCategory == null
                        is String -> chipKey == selectedType
                        is ActionCategory? -> chipKey == selectedCategory
                        else -> false
                    }
                    FilterChip(
                        selected = isSelected,
                        onClick = {
                            when (chipKey) {
                                null -> { selectedType = null; selectedCategory = null }
                                is String -> {
                                    selectedType = if (isSelected) null else chipKey
                                    if (selectedType != null) selectedCategory = null
                                }
                                is ActionCategory? -> {
                                    selectedCategory = if (isSelected) null else chipKey
                                    if (selectedCategory != null) selectedType = null
                                }
                            }
                        },
                        label = { Text(label) },
                        leadingIcon = if (isSelected) {
                            {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    modifier = Modifier.size(FilterChipDefaults.IconSize)
                                )
                            }
                        } else null
                    )
                }
            }
        }
        val hasAnyContent = grouped.isNotEmpty() || filteredApps.isNotEmpty() || filteredLibraryEntries.isNotEmpty() || filteredCreateShortcuts.isNotEmpty() || filteredLaunchShortcuts.isNotEmpty()
        if ((query.isNotEmpty() || selectedCategory != null || selectedType != null) && !hasAnyContent) {
            item {
                EmptyState(message = stringResource(R.string.no_matching_results))
            }
        } else {
            if (grouped.isNotEmpty()) {
                grouped.forEach { (category, categoryActions) ->
                    stickyHeader(key = "cat_${category.name}") {
                        Text(
                            text = stringResource(id = category.displayNameRes),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = ContentPaddingHorizontal * 2, vertical = 8.dp)
                        )
                    }
                    items(
                        items = categoryActions,
                        key = { "${it.value}:${it.data}" }
                    ) { item ->
                        ActionItem(
                            modifier = Modifier.animateItem(),
                            action = item,
                            actionLabel = context.actionTextWithSubGesture(item, subGestures, actionLibraryEntries, emptyIfNone = false),
                            selected = selectedRecord.isSelected(item),
                            selectSingle = selectSingle || selectingLongPress,
                            enabled = selectingLongPress || canActionEnabled(selectedRecord, item, maxSelectCount),
                            snackbarHostState = snackbarHostState,
                            onSelect = { selected ->
                                if (selectingLongPress) onSelectLongPress(item) else onSelect(item, selected)
                            },
                            showSettings = ActionFacade.hasConfig(item.value),
                            onSettingsClick = {
                                onSettingsClick(item)
                            }
                        )
                    }
                }
            }
            if (filteredLibraryEntries.isNotEmpty()) {
                filteredLibraryEntries.groupBy { it.type }.forEach { (type, entries) ->
                    stickyHeader(key = "lib_${type.name}") {
                        Text(
                            text = stringResource(type.titleRes),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = ContentPaddingHorizontal * 2, vertical = 8.dp)
                        )
                    }
                    items(items = entries, key = { "lib_${it.id}" }) { entry ->
                        val action = entry.toReferenceAction()
                        ActionItem(
                            modifier = Modifier.animateItem(),
                            action = action,
                            actionLabel = entry.name,
                            selected = selectedRecord.isSelected(action),
                            selectSingle = selectSingle || selectingLongPress,
                            enabled = selectingLongPress || canActionEnabled(selectedRecord, action, maxSelectCount),
                            snackbarHostState = snackbarHostState,
                            onSelect = { selected ->
                                if (selectingLongPress) onSelectLongPress(entry) else onSelectLibraryEntry(entry, selected)
                            },
                        )
                    }
                }
            }
            if (filteredApps.isNotEmpty()) {
                items(items = filteredApps, key = { "app_${it.qualifiedName}" }) { item ->
                    AppItem(appInfo = item, selected = selectedRecord.isSelected(item), selectSingle = selectSingle || selectingLongPress,
                        enabled = selectingLongPress || canAppInfoEnabled(selectedRecord, item, maxSelectCount),
                        onSelect = { selected ->
                            if (selectingLongPress) onSelectLongPress(item) else onSelectApp(item, selected)
                        },
                        onLongClick = { onAppLongClick(item) },
                        modifier = Modifier.animateItem())
                }
            }
            if (filteredCreateShortcuts.isNotEmpty()) {
                stickyHeader(key = "create_shortcuts") {
                    Text(stringResource(R.string.create_shortcut), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = ContentPaddingHorizontal * 2, vertical = 8.dp))
                }
                items(items = filteredCreateShortcuts, key = { "cs_${it.qualifiedName}" }) { item ->
                    LauncherInfoItem(launcherInfo = item, selectSingle = selectSingle || selectingLongPress,
                        canLauncherInfoEnabled = { selectingLongPress || canLauncherInfoEnabled(selectedRecord, it, maxSelectCount) },
                        canShortcutInfoEnabled = { selectingLongPress || canShortcutInfoEnabled(selectedRecord, it, maxSelectCount) },
                        isShortcutInfoSelected = { selectedRecord.isSelected(it) },
                        onSelect = { s, sel ->
                            if (selectingLongPress) onSelectLongPress(s) else onSelectShortcut(s, sel)
                        }, onClick = { onShortcutClick(item) },
                        modifier = Modifier.animateItem())
                }
            }
            if (filteredLaunchShortcuts.isNotEmpty()) {
                stickyHeader(key = "launch_shortcuts") {
                    Text(stringResource(R.string.launch_shortcut), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = ContentPaddingHorizontal * 2, vertical = 8.dp))
                }
                items(items = filteredLaunchShortcuts, key = { "ls_${it.qualifiedName}" }) { item ->
                    LauncherInfoItem(launcherInfo = item, selectSingle = selectSingle || selectingLongPress,
                        canLauncherInfoEnabled = { selectingLongPress || canLauncherInfoEnabled(selectedRecord, it, maxSelectCount) },
                        canShortcutInfoEnabled = { selectingLongPress || canShortcutInfoEnabled(selectedRecord, it, maxSelectCount) },
                        isShortcutInfoSelected = { selectedRecord.isSelected(it) },
                        onSelect = { s, sel ->
                            if (selectingLongPress) onSelectLongPress(s) else onSelectShortcut(s, sel)
                        }, onClick = {},
                        modifier = Modifier.animateItem())
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
