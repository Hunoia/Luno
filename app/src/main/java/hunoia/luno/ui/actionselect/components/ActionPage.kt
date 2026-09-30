package hunoia.luno.ui.actionselect

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import hunoia.luno.ui.theme.PageGutter
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import hunoia.luno.R
import hunoia.luno.action.definition.ActionCategory
import hunoia.luno.action.model.NewActionLibraryEntry
import hunoia.luno.config.model.Action
import hunoia.luno.config.model.SubGesture
import hunoia.luno.quicklaunch.model.AppInfo
import hunoia.luno.quicklaunch.model.LauncherInfo
import hunoia.luno.quicklaunch.model.qualifiedName
import hunoia.luno.ui.actionselect.UiState.SelectedRecord
import hunoia.luno.ui.component.AppSearchBar
import hunoia.luno.ui.component.EmptyState
import hunoia.luno.ui.actionlibrary.matchesQuery
import hunoia.luno.ui.component.displayNameRes
import hunoia.luno.ui.component.segmentedShape
import hunoia.luno.ui.theme.*
import hunoia.luno.ui.theme.ListItemVerticalPadding

private const val TYPE_ACTION_LIBRARY = "action_library"
private const val TYPE_APP = "app"
private const val TYPE_SHORTCUT = "shortcut"

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun ActionPage(
    onSelect: (Action, Boolean) -> Unit,
    onSelectLibraryEntry: (NewActionLibraryEntry, Boolean) -> Unit = { _, _ -> },
    onSelectLongPress: (Any) -> Unit = {},
    onSelectApp: (AppInfo, Boolean) -> Unit,
    onSelectShortcut: (LauncherInfo.ShortcutInfo, Boolean) -> Unit,
    onSetLongPress: (Int) -> Unit = {},
    onCancelLongPress: () -> Unit = {},
    onMoveSelected: (Int, Int) -> Unit = { _, _ -> },
    onAppLongClick: (AppInfo) -> Unit,
    onShortcutClick: (LauncherInfo) -> Unit = {},
    modifier: Modifier = Modifier,
    nestedScroll: NestedScrollConnection? = null,
    subGestures: List<SubGesture> = emptyList(),
    actions: List<Action>,
    actionLibraryEntries: List<NewActionLibraryEntry> = emptyList(),
    appInfos: List<AppInfo>,
    createShortcuts: List<LauncherInfo>,
    launchShortcuts: List<LauncherInfo>,
    selectedRecord: SelectedRecord,
    longPressTargetIndex: Int?,
    permissionState: hunoia.luno.ui.permission.PermissionState,
    contentPadding: PaddingValues = PaddingValues(),
    maxSelectCount: Int = MAX_SELECT_COUNT
) {
    var query by rememberSaveable { mutableStateOf("") }
    var selectedCategory by rememberSaveable { mutableStateOf<ActionCategory?>(null) }
    var selectedType by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val selectingLongPress = longPressTargetIndex != null
    val categoryChips = remember(actions) {
        buildList<Pair<Any?, String>> {
            add(null to context.getString(R.string.all_categories))
            actions
                .map { actionCategory(it) }
                .distinct()
                .forEach { category -> add(category to context.getString(category.displayNameRes)) }
            add(TYPE_ACTION_LIBRARY to context.getString(R.string.action_library))
            add(TYPE_APP to context.getString(R.string.tab_apps))
            add(TYPE_SHORTCUT to context.getString(R.string.tab_shortcuts))
        }
    }
    val filteredActions = remember(actions, query, selectedCategory, selectedType) {
        if (query.isNotBlank()) {
            var result = actions
            if (selectedCategory != null) {
                result = result.filter { action ->
                    val cat = actionCategory(action)
                    cat == selectedCategory
                }
            }
            result = result.filter {
                context.actionTextWithSubGesture(it, subGestures, actionLibraryEntries, emptyIfNone = false)
                    .contains(query, ignoreCase = true)
            }
            if (selectedType == TYPE_ACTION_LIBRARY) emptyList() else result
        } else if (selectedType == TYPE_APP || selectedType == TYPE_SHORTCUT) emptyList()
        else if (selectedType == TYPE_ACTION_LIBRARY) emptyList()
        else {
            var result = actions
            if (selectedCategory != null) {
                result = result.filter { action ->
                    val cat = actionCategory(action)
                    cat == selectedCategory
                }
            }
            result
        }
    }
    val grouped = remember(filteredActions) {
        val map = LinkedHashMap<ActionCategory, MutableList<Action>>()
        filteredActions.forEach { action ->
            val category = actionCategory(action)
            map.getOrPut(category) { mutableListOf() }.add(action)
        }
        map
    }
    val filteredLibraryEntries = remember(actionLibraryEntries, query, selectedType) {
        if (selectedType == TYPE_ACTION_LIBRARY || query.isNotBlank()) {
            actionLibraryEntries.filter { if (query.isNotBlank()) it.matchesQuery(query) else true }
        } else emptyList()
    }.sortedBy { it.createdAt }
    val filteredApps = remember(appInfos, query, selectedType) {
        if (selectedType == TYPE_APP || query.isNotBlank()) {
            appInfos.filter {
                if (query.isBlank()) true
                else it.label.contains(query, ignoreCase = true) || it.packageName.contains(query, ignoreCase = true)
            }
        } else emptyList()
    }
    val filteredCreateShortcuts = remember(createShortcuts, query, selectedType) {
        if (selectedType == TYPE_SHORTCUT || query.isNotBlank()) {
            createShortcuts.filter {
                if (query.isBlank()) true
                else it.label.contains(query, ignoreCase = true) ||
                    it.shortcuts.any { s -> s.label.contains(query, ignoreCase = true) }
            }
        } else emptyList()
    }
    val filteredLaunchShortcuts = remember(launchShortcuts, query, selectedType) {
        if (selectedType == TYPE_SHORTCUT || query.isNotBlank()) {
            launchShortcuts.filter {
                if (query.isBlank()) true
                else it.label.contains(query, ignoreCase = true) ||
                    it.shortcuts.any { s -> s.label.contains(query, ignoreCase = true) }
            }
        } else emptyList()
    }
    LazyColumn(
        modifier = (nestedScroll?.let { modifier.nestedScroll(it) } ?: modifier)
            .fillMaxWidth(),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(SegmentedGap),
    ) {
        item(key = "search") {
            AppSearchBar(
                query = query,
                onQueryChange = { query = it },
                modifier = Modifier.padding(horizontal = PageGutter, vertical = 8.dp),
                placeholder = stringResource(R.string.search_hint_all),
            )
        }
        item(key = "category_chips") {
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = PageGutter, vertical = ListItemVerticalPadding),
                horizontalArrangement = Arrangement.spacedBy(CardInnerSpacing)
            ) {
                items(categoryChips) { (chipKey, label) ->
                    val isSelected = when (chipKey) {
                        null -> selectedType == null && selectedCategory == null
                        is String -> chipKey == selectedType
                        is ActionCategory -> chipKey == selectedCategory
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
                                is ActionCategory -> {
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
        if ((query.isNotEmpty() || selectedType != null || selectedCategory != null) && !hasAnyContent) {
            item {
                EmptyState(message = stringResource(R.string.no_matching_results))
            }
        } else {
            if (grouped.isNotEmpty()) {
                grouped.forEach { (category, categoryActions) ->
                    stickyHeader(key = "cat_${category.name}") {
                        Text(
                            text = stringResource(id = category.displayNameRes),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = PageGutter, top = 8.dp, bottom = 16.dp)
                        )
                    }
                    itemsIndexed(
                        items = categoryActions,
                        key = { _, it -> "${it.value}:${it.data}" }
                    ) { index, item ->
                        ActionItem(
                            modifier = Modifier.animateItem(),
                            action = item,
                            actionLabel = context.actionTextWithSubGesture(item, subGestures, actionLibraryEntries, emptyIfNone = false),
                            selected = selectedRecord.isSelected(item),
                            selectSingle = selectingLongPress,
                            enabled = selectingLongPress || canActionEnabled(selectedRecord, item, maxSelectCount),
                            onSelect = { selected ->
                                if (selectingLongPress) onSelectLongPress(item) else onSelect(item, selected)
                            },
                            shape = segmentedShape(index, categoryActions.size),
                        )
                    }
                }
            }
            if (filteredLibraryEntries.isNotEmpty()) {
                stickyHeader(key = "lib_all") {
                    Text(
                        text = stringResource(R.string.action_library),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.fillMaxWidth().padding(start = PageGutter, top = 8.dp, bottom = 16.dp)
                    )
                }
                itemsIndexed(items = filteredLibraryEntries, key = { _, it -> "lib_${it.id}" }) { index, entry ->
                    val action = entry.toReferenceAction()
                    ActionItem(
                        modifier = Modifier.animateItem(),
                        action = action,
                        actionLabel = entry.name,
                        selected = selectedRecord.isSelected(action),
                        selectSingle = selectingLongPress,
                        enabled = selectingLongPress || canActionEnabled(selectedRecord, action, maxSelectCount),
                        onSelect = { selected ->
                            if (selectingLongPress) onSelectLongPress(entry) else onSelectLibraryEntry(entry, selected)
                        },
                        shape = segmentedShape(index, filteredLibraryEntries.size),
                    )
                }
            }
            if (filteredApps.isNotEmpty()) {
                stickyHeader(key = "apps") {
                    Text(
                        text = stringResource(R.string.tab_apps),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.fillMaxWidth().padding(start = PageGutter, top = 8.dp, bottom = 16.dp)
                    )
                }
                itemsIndexed(items = filteredApps, key = { _, it -> "app_${it.qualifiedName}" }) { index, item ->
                    AppItem(appInfo = item, selected = selectedRecord.isSelected(item), selectSingle = selectingLongPress,
                        enabled = selectingLongPress || canAppInfoEnabled(selectedRecord, item, maxSelectCount),
                        onSelect = { selected ->
                            if (selectingLongPress) onSelectLongPress(item) else onSelectApp(item, selected)
                        },
                        onLongClick = { onAppLongClick(item) },
                        modifier = Modifier.animateItem(),
                        shape = segmentedShape(index, filteredApps.size))
                }
            }
            if (filteredCreateShortcuts.isNotEmpty()) {
                stickyHeader(key = "create_shortcuts") {
                    Text(stringResource(R.string.create_shortcut), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.fillMaxWidth().padding(start = PageGutter, top = 8.dp, bottom = 16.dp))
                }
                items(items = filteredCreateShortcuts, key = { "cs_${it.qualifiedName}" }) { item ->
                    LauncherInfoItem(launcherInfo = item, selectSingle = selectingLongPress,
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
                    Text(stringResource(R.string.launch_shortcut), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.fillMaxWidth().padding(start = PageGutter, top = 8.dp, bottom = 16.dp))
                }
                items(items = filteredLaunchShortcuts, key = { "ls_${it.qualifiedName}" }) { item ->
                    LauncherInfoItem(launcherInfo = item, selectSingle = selectingLongPress,
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
