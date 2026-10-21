package hunoia.luno.ui.actionselect
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import android.appwidget.AppWidgetProviderInfo
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import hunoia.luno.R
import hunoia.luno.action.definitions.ActionCategory
import hunoia.luno.action.definitions.ActionDefinitions
import hunoia.luno.action.model.NewActionLibraryEntry
import hunoia.luno.action.model.matchesQuery
import hunoia.luno.action.model.StoredAction
import hunoia.luno.gridpanel.internal.InternalWidgetDefs
import hunoia.luno.gridpanel.widget.WidgetProviderGroup
import hunoia.luno.gridpanel.widget.WidgetSpan
import hunoia.luno.gridpanel.widget.searchBy
import hunoia.luno.config.model.ActionPanel
import hunoia.luno.config.model.AutomationRule
import hunoia.luno.config.model.GridPanel
import hunoia.luno.config.model.SubGesture
import hunoia.luno.domain.action.ActionCatalog
import hunoia.luno.quicklaunch.model.AppInfo
import hunoia.luno.quicklaunch.model.LauncherInfo
import hunoia.luno.quicklaunch.model.qualifiedName
import hunoia.luno.ui.actionselect.UiState.SelectedRecord
import hunoia.luno.ui.actionselect.components.ActionRowSettingsDialog
import hunoia.luno.ui.actionselect.components.RowSettingsActionTypeIds
import hunoia.luno.ui.actionselect.components.rowSettingsSubtitle
import hunoia.luno.ui.component.AppSearchBar
import hunoia.luno.ui.component.feedback.EmptyState
import hunoia.luno.ui.component.displayNameRes
import hunoia.luno.ui.theme.*
import hunoia.luno.ui.theme.ListItemVerticalPadding
import hunoia.luno.ui.component.row.selectedSegmentedShape
import hunoia.luno.ui.component.SheetGroupHeader
import hunoia.luno.ui.automation.conditionSummary
import hunoia.luno.ui.theme.CardInnerSpacing
private const val TYPE_ACTION_LIBRARY = "action_library"
private const val TYPE_APP = "app"
private const val TYPE_SHORTCUT = "shortcut"
private const val TYPE_RULE = "rule"
private const val TYPE_WIDGET = "widget"
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun ActionPage(
    onSelect: (StoredAction) -> Unit,
    onSelectLibraryEntry: (NewActionLibraryEntry) -> Unit = { },
    onSelectApp: (AppInfo) -> Unit,
    onSelectShortcut: (LauncherInfo.ShortcutInfo) -> Unit,
    onAppLongClick: (AppInfo) -> Unit,
    onShortcutClick: (LauncherInfo) -> Unit = {},
    modifier: Modifier = Modifier,
    subGestures: List<SubGesture> = emptyList(),
    actions: List<StoredAction>,
    actionLibraryEntries: List<NewActionLibraryEntry> = emptyList(),
    rules: List<AutomationRule> = emptyList(),
    actionPanels: List<ActionPanel> = emptyList(),
    gridPanels: List<GridPanel> = emptyList(),
    appInfos: List<AppInfo>,
    createShortcuts: List<LauncherInfo>,
    launchShortcuts: List<LauncherInfo>,
    selectedRecord: SelectedRecord,
    permissionState: hunoia.luno.ui.permission.PermissionState,
    contentPadding: PaddingValues = PaddingValues(),
    /**
     * 桌面小部件：只有配网格面板时才有意义；true 时列表顶部多一颗「小部件」chip。
     * 清单为空时 chip 仍然保留 —— 入口凭空消失会让人以为功能不存在。
     */
    showWidgetChip: Boolean = false,
    widgetGroups: List<WidgetProviderGroup> = emptyList(),
    /** provider 声明的尺寸算出的占格数，按面板列/行封顶。 */
    widgetSpanOf: (AppWidgetProviderInfo) -> WidgetSpan = { WidgetSpan(1, 1) },
    onSelectWidget: (AppWidgetProviderInfo) -> Unit = { },
    /** 内置小部件选定（id 见 InternalWidgetIds），直接落盘不经过 Activity 结果。 */
    onSelectInternalWidget: (String) -> Unit = { },
    /** 行尾设置弹窗确认：把带参的动作写回列表（及当前选中项），不触发选择/返回。 */
    onUpdateAction: (old: StoredAction, new: StoredAction) -> Unit = { _, _ -> },
) {
    var query by rememberSaveable { mutableStateOf("") }
    var selectedType by rememberSaveable { mutableStateOf<String?>(null) }
    var settingsAction by remember { mutableStateOf<StoredAction?>(null) }
    settingsAction?.let { editing ->
        ActionRowSettingsDialog(
            action = editing,
            onDismiss = { settingsAction = null },
            onConfirm = { next ->
                onUpdateAction(editing, next)
            },
        )
    }
    val context = LocalContext.current
    val typeChips = remember(context, showWidgetChip) {
        buildList {
            add(null to context.getString(R.string.all_categories))
            add(TYPE_ACTION_LIBRARY to context.getString(R.string.action_library))
            add(TYPE_APP to context.getString(R.string.tab_apps))
            add(TYPE_SHORTCUT to context.getString(R.string.tab_shortcuts))
            add(TYPE_RULE to context.getString(R.string.action_rule_group))
            if (showWidgetChip) add(TYPE_WIDGET to context.getString(R.string.grid_panel_add_widget))
        }
    }
    val filteredActions = remember(actions, query, selectedType, subGestures, actionLibraryEntries, rules) {
        when {
            // 小部件这一屏不列动作：小部件不是 StoredAction，混进来会让选中类型不可证伪。
            selectedType == TYPE_WIDGET -> emptyList()
            selectedType != null && query.isBlank() -> emptyList()
            selectedType == TYPE_ACTION_LIBRARY -> emptyList()
            selectedType == TYPE_RULE -> emptyList()
            query.isBlank() -> actions
            else -> actions.filter {
                context.actionTextWithSubGesture(it, subGestures, actionLibraryEntries, rules, actionPanels, gridPanels, emptyIfNone = false)
                    .contains(query, ignoreCase = true)
            }
        }
    }
    val grouped = remember(filteredActions) {
        val buckets = LinkedHashMap<ActionCategory, MutableList<StoredAction>>()
        filteredActions.forEach { action ->
            buckets.getOrPut(actionCategory(action)) { mutableListOf() }.add(action)
        }
        // 按定义表的声明顺序排分组。原先直接用 LinkedHashMap 的插入序,而
        // assembleDataTransform 会把已选中的动作搬到列表最前面 —— 于是选中一个「无障碍」动作,
        // 整个无障碍分组就跳到最上面。无选中项时插入序本来就等于声明序,所以这里只在有选中时生效。
        LinkedHashMap<ActionCategory, MutableList<StoredAction>>(buckets.size).apply {
            buckets.keys.sortedBy { ActionDefinitions.categoryOrder(it) }.forEach { category ->
                this[category] = buckets.getValue(category)
            }
        }
    }
    val filteredLibraryEntries = remember(actionLibraryEntries, query, selectedType) {
        if (selectedType == TYPE_WIDGET) emptyList() else if (selectedType == TYPE_ACTION_LIBRARY || query.isNotBlank()) {
            actionLibraryEntries.filter { if (query.isNotBlank()) it.matchesQuery(query) else true }
        } else emptyList()
    }.sortedBy { it.createdAt }
    // 规则即动作：专属分组（定义表直选不开，ruleId 必填，只能引用已存规则）。
    val filteredRules = remember(rules, query, selectedType) {
        if (selectedType == TYPE_WIDGET) emptyList() else if (selectedType == TYPE_RULE || query.isNotBlank()) {
            rules.filter {
                if (query.isBlank()) true
                else it.name.contains(query, ignoreCase = true)
            }
        } else emptyList()
    }
    val filteredApps = remember(appInfos, query, selectedType) {
        if (selectedType == TYPE_WIDGET) emptyList() else if (selectedType == TYPE_APP || query.isNotBlank()) {
            appInfos.filter {
                if (query.isBlank()) true
                else it.label.contains(query, ignoreCase = true) || it.packageName.contains(query, ignoreCase = true)
            }
        } else emptyList()
    }
    val filteredCreateShortcuts = remember(createShortcuts, query, selectedType) {
        if (selectedType == TYPE_WIDGET) emptyList() else if (selectedType == TYPE_SHORTCUT || query.isNotBlank()) {
            createShortcuts.filter {
                if (query.isBlank()) true
                else it.label.contains(query, ignoreCase = true) ||
                    it.shortcuts.any { s -> s.label.contains(query, ignoreCase = true) }
            }
        } else emptyList()
    }
    val filteredLaunchShortcuts = remember(launchShortcuts, query, selectedType) {
        if (selectedType == TYPE_WIDGET) emptyList() else if (selectedType == TYPE_SHORTCUT || query.isNotBlank()) {
            launchShortcuts.filter {
                if (query.isBlank()) true
                else it.label.contains(query, ignoreCase = true) ||
                    it.shortcuts.any { s -> s.label.contains(query, ignoreCase = true) }
            }
        } else emptyList()
    }
        val filteredWidgets = remember(widgetGroups, query, selectedType) {
            if (selectedType != TYPE_WIDGET) emptyList()
            else widgetGroups.searchBy(query, context.packageManager)
        }
    // 内置部件跟随小部件 chip 同屏展示；搜索按名字过滤。
    val filteredInternalWidgets = remember(selectedType, query, context) {
        if (selectedType != TYPE_WIDGET) emptyList()
        else InternalWidgetDefs.filter { context.getString(it.labelRes).contains(query, ignoreCase = true) || query.isBlank() }
    }
    val librarySelectionFlags = filteredLibraryEntries.map { selectedRecord.isSelected(it.toStoredAction()) }
    val appSelectionFlags = filteredApps.map { selectedRecord.isSelected(it) }
    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(SegmentedGap),
    ) {
        item(key = "search") {
            AppSearchBar(
                query = query,
                onQueryChange = { query = it },
                // 左右由列表 contentPadding 提供（行组件统一贴边）
                modifier = Modifier.padding(vertical = CardInnerSpacing),
                placeholder = stringResource(R.string.search_hint_all),
            )
        }
        item(key = "type_chips") {
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = ListItemVerticalPadding),  // 左右由列表 contentPadding 提供
                horizontalArrangement = Arrangement.spacedBy(CardInnerSpacing)
            ) {
                items(typeChips) { (chipKey, label) ->
                    val isSelected = if (chipKey == null) selectedType == null else chipKey == selectedType
                    FilterChip(
                        selected = isSelected,
                        onClick = {
                            selectedType = if (isSelected) null else chipKey
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
        val hasAnyContent = grouped.isNotEmpty() || filteredApps.isNotEmpty() || filteredLibraryEntries.isNotEmpty() || filteredCreateShortcuts.isNotEmpty() || filteredLaunchShortcuts.isNotEmpty() || filteredRules.isNotEmpty() || filteredWidgets.isNotEmpty() || filteredInternalWidgets.isNotEmpty()
        if ((query.isNotEmpty() || selectedType != null) && !hasAnyContent) {
            item {
                EmptyState(
                    message = stringResource(
                        if (selectedType == TYPE_WIDGET) R.string.grid_panel_no_widgets
                        else R.string.no_matching_results
                    )
                )
            }
        } else {
            if (filteredInternalWidgets.isNotEmpty()) {
                // 内置部件置顶：没有 provider 卡那么多东西，直接一行卡片。
                stickyHeader(key = "widget_internal") {
                    SheetGroupHeader(stringResource(R.string.grid_panel_internal_widgets))
                }
                item(key = "widget_internal_row") {
                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp),
                    ) {
                        items(
                            items = filteredInternalWidgets,
                            key = { def -> "internal_${def.id}" },
                        ) { def ->
                            InternalWidgetCard(def = def, onSelect = { onSelectInternalWidget(def.id) })
                        }
                    }
                }
            }
            if (filteredWidgets.isNotEmpty()) {
                // 每 app 分区头 + 区内横向滚动的预览卡。
                filteredWidgets.forEach { group ->
                    stickyHeader(key = "widget_${group.appLabel}") {
                        WidgetGroupHeaderRow(group)
                    }
                    item(key = "widget_row_${group.appLabel}") {
                        LazyRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp),
                        ) {
                            items(
                                items = group.providers,
                                key = { info -> "widget_${info.provider?.flattenToString()}_${info.widgetCategory}" },
                            ) { info ->
                                WidgetPreviewCard(
                                    info = info,
                                    span = widgetSpanOf(info),
                                    onSelect = { onSelectWidget(info) },
                                )
                            }
                        }
                    }
                }
            }
            if (grouped.isNotEmpty()) {
                grouped.forEach { (category, categoryActions) ->
                    val categoryFlags = categoryActions.map { selectedRecord.isSelected(it) }
                    stickyHeader(key = "cat_${category.name}") {
                        SheetGroupHeader(stringResource(id = category.displayNameRes))
                    }
                    itemsIndexed(
                        items = categoryActions,
                        key = { _, it -> "${it.typeId}:${it.params}" }
                    ) { index, item ->
                        ActionItem(
                            modifier = Modifier.animateItem(),
                            action = item,
                            actionLabel = context.actionTextWithSubGesture(
                                item,
                                subGestures,
                                actionLibraryEntries,
                                rules,
                                actionPanels,
                                gridPanels,
                                emptyIfNone = false,
                            ),
                            selected = selectedRecord.isSelected(item),
                            onSelect = { onSelect(item) },
                            subtitle = if (item.typeId in RowSettingsActionTypeIds) rowSettingsSubtitle(item) else "",
                            shape = selectedSegmentedShape(index, categoryActions.size, categoryFlags),
                            trailing = if (item.typeId in RowSettingsActionTypeIds) {{
                                IconButton(onClick = { settingsAction = item }) {
                                    Icon(
                                        imageVector = Icons.Default.Settings,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }} else null,
                        )
                    }
                }
            }
            if (filteredLibraryEntries.isNotEmpty()) {
                stickyHeader(key = "lib_all") {
                    SheetGroupHeader(stringResource(R.string.action_library))
                }
                itemsIndexed(items = filteredLibraryEntries, key = { _, it -> "lib_${it.id}" }) { index, entry ->
                    val action = entry.toStoredAction()
                    ActionItem(
                        modifier = Modifier.animateItem(),
                        action = action,
                        actionLabel = entry.name,
                        selected = selectedRecord.isSelected(action),
                        onSelect = { onSelectLibraryEntry(entry) },
                        shape = selectedSegmentedShape(index, filteredLibraryEntries.size, librarySelectionFlags),
                    )
                }
            }
            if (filteredRules.isNotEmpty()) {
                val ruleFlags = filteredRules.map {
                    selectedRecord.isSelected(ActionCatalog.ruleStoredAction(it.id))
                }
                stickyHeader(key = "rules_all") {
                    SheetGroupHeader(stringResource(R.string.action_rule_group))
                }
                itemsIndexed(items = filteredRules, key = { _, it -> "rule_${it.id}" }) { index, rule ->
                    val action = ActionCatalog.ruleStoredAction(rule.id)
                    ActionItem(
                        modifier = Modifier.animateItem(),
                        action = action,
                        actionLabel = rule.name.ifBlank { context.actionTextWithSubGesture(action, subGestures, actionLibraryEntries, rules, actionPanels, gridPanels, emptyIfNone = false) },
                        subtitle = conditionSummary(context, rule.condition),
                        selected = selectedRecord.isSelected(action),
                        onSelect = { onSelect(action) },
                        shape = selectedSegmentedShape(index, filteredRules.size, ruleFlags),
                    )
                }
            }
            if (filteredApps.isNotEmpty()) {
                stickyHeader(key = "apps") {
                    SheetGroupHeader(stringResource(R.string.tab_apps))
                }
                itemsIndexed(items = filteredApps, key = { _, it -> "app_${it.qualifiedName}" }) { index, item ->
                    AppItem(appInfo = item, selected = selectedRecord.isSelected(item),
                        onSelect = { onSelectApp(item) },
                        onLongClick = { onAppLongClick(item) },
                        modifier = Modifier.animateItem(),
                        shape = selectedSegmentedShape(index, filteredApps.size, appSelectionFlags))
                }
            }
            if (filteredCreateShortcuts.isNotEmpty()) {
                stickyHeader(key = "create_shortcuts") {
                    SheetGroupHeader(stringResource(R.string.create_shortcut))
                }
                items(items = filteredCreateShortcuts, key = { "cs_${it.qualifiedName}" }) { item ->
                    LauncherInfoItem(launcherInfo = item,
                        isShortcutInfoSelected = { selectedRecord.isSelected(it) },
                        onSelect = { s, _ -> onSelectShortcut(s) }, onClick = { onShortcutClick(item) },
                        modifier = Modifier.animateItem())
                }
            }
            if (filteredLaunchShortcuts.isNotEmpty()) {
                stickyHeader(key = "launch_shortcuts") {
                    SheetGroupHeader(stringResource(R.string.launch_shortcut))
                }
                items(items = filteredLaunchShortcuts, key = { "ls_${it.qualifiedName}" }) { item ->
                    LauncherInfoItem(launcherInfo = item,
                        isShortcutInfoSelected = { selectedRecord.isSelected(it) },
                        onSelect = { s, _ -> onSelectShortcut(s) }, onClick = {},
                        modifier = Modifier.animateItem())
                }
            }
        }
    }
}