package hunoia.luno.picker
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import android.appwidget.AppWidgetProviderInfo
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.LibraryBooks
import androidx.compose.material.icons.filled.AllInclusive
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import hunoia.luno.picker.R
import hunoia.luno.action.definitions.ActionCategory
import hunoia.luno.action.definitions.ActionDefinitions
import hunoia.luno.core.domain.ActionLibraryEntry
import hunoia.luno.core.domain.matchesQuery
import hunoia.luno.core.domain.StoredAction
import hunoia.luno.gridpanel.internal.InternalWidgetDefs
import hunoia.luno.gridpanel.widget.WidgetProviderGroup
import hunoia.luno.gridpanel.widget.WidgetSpan
import hunoia.luno.gridpanel.widget.searchBy
import hunoia.luno.config.model.ActionPanel
import hunoia.luno.config.model.GridPanel
import hunoia.luno.config.model.SubGesture
import hunoia.luno.core.domain.AppInfo
import hunoia.luno.bridge.launcher.LauncherInfo
import hunoia.luno.bridge.system.gotoAppDetailSettings
import hunoia.luno.quickaccess.model.identity
import hunoia.luno.quickaccess.model.qualifiedName
import hunoia.luno.picker.UiState.SelectedRecord
import hunoia.luno.picker.components.ActionRowSettingsSheet
import hunoia.luno.picker.components.RowSettingsActionTypeIds
import hunoia.luno.picker.components.rowSettingsSubtitle
import hunoia.luno.actions.library.actionLibraryParamSummary
import hunoia.luno.ui.component.feedback.EmptyState
import hunoia.luno.actions.library.displayNameRes
import hunoia.luno.ui.component.PressTextButton
import hunoia.luno.ui.theme.*
import hunoia.luno.ui.component.row.selectedSegmentedShape
import hunoia.luno.ui.component.SheetGroupHeader
import dev.chrisbanes.haze.HazeState
import hunoia.luno.ui.component.PressIconButton
/** 动作库与规则合成一颗 chip：两者都是「已保存的动作来源」，分屏会多一层切换。 */
private const val TYPE_LIBRARY_AND_RULE = "action_library"
/** 应用与快捷方式合成一颗 chip：两者都是「从别的 app 取入口」，合并后底栏少一格。 */
private const val TYPE_APP = "app"
/** 旧版本单独保存的快捷方式筛选值，恢复时并到合并后的 chip。 */
private const val TYPE_SHORTCUT_LEGACY = "shortcut"
private const val TYPE_WIDGET = "widget"
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun ActionPage(
    onSelect: (StoredAction) -> Unit,
    onSelectLibraryEntry: (ActionLibraryEntry) -> Unit = { },
    onSelectApp: (AppInfo) -> Unit,
    onSelectShortcut: (LauncherInfo.ShortcutInfo) -> Unit,
    onAppLongClick: (AppInfo) -> Unit,
    onShortcutClick: (LauncherInfo) -> Unit = {},
    modifier: Modifier = Modifier,
    subGestures: List<SubGesture> = emptyList(),
    actions: List<StoredAction>,
    actionLibraryEntries: List<ActionLibraryEntry> = emptyList(),
    actionPanels: List<ActionPanel> = emptyList(),
    gridPanels: List<GridPanel> = emptyList(),
    appInfos: List<AppInfo>,
    createShortcuts: List<LauncherInfo>,
    launchShortcuts: List<LauncherInfo>,
    selectedRecord: SelectedRecord,
    permissionState: hunoia.luno.ui.permission.PermissionState,
    /** 与内容层同一份 Haze 状态，给悬浮底栏做模糊底；由 [LunoScreenPlain] 模板下发。 */
    hazeState: HazeState,
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
    // 旧版本「快捷方式」是独立 chip；合并后归一化，避免恢复出任何 chip 都不匹配的空列表。
    val normalizedType = if (selectedType == TYPE_SHORTCUT_LEGACY) TYPE_APP else selectedType
    // 合并后的三组（应用 / 新建快捷方式 / 快捷方式）可收起；搜索时强制展开且不显示箭头，
    // 避免搜索结果被收起状态藏住。
    var appsCollapsed by rememberSaveable { mutableStateOf(false) }
    var createShortcutsCollapsed by rememberSaveable { mutableStateOf(false) }
    var launchShortcutsCollapsed by rememberSaveable { mutableStateOf(false) }
    var settingsAction by remember { mutableStateOf<StoredAction?>(null) }
    settingsAction?.let { editing ->
        ActionRowSettingsSheet(
            action = editing,
            onDismiss = { settingsAction = null },
            onConfirm = { next ->
                onUpdateAction(editing, next)
            },
        )
    }
    val context = LocalContext.current
    val allCategoriesLabel = stringResource(R.string.all_categories)
    val libraryAndRuleLabel = stringResource(R.string.action_library_and_rules)
    val appsAndShortcutsLabel = stringResource(R.string.apps_and_shortcuts)
    val widgetChipLabel = stringResource(R.string.grid_panel_add_widget)
    val typeChips = remember(
        showWidgetChip,
        allCategoriesLabel,
        libraryAndRuleLabel,
        appsAndShortcutsLabel,
        widgetChipLabel,
    ) {
        buildList {
            add(ActionTypeChip(null, allCategoriesLabel, Icons.Default.AllInclusive))
            add(ActionTypeChip(TYPE_LIBRARY_AND_RULE, libraryAndRuleLabel, Icons.AutoMirrored.Filled.LibraryBooks))
            add(ActionTypeChip(TYPE_APP, appsAndShortcutsLabel, Icons.Default.Apps))
            if (showWidgetChip) {
                add(ActionTypeChip(TYPE_WIDGET, widgetChipLabel, Icons.Default.Widgets))
            }
        }
    }
    var bottomBarHeightPx by remember { mutableIntStateOf(0) }
    val density = LocalDensity.current
    fun chipIndexOf(type: String?): Int =
        typeChips.indexOfFirst { it.key == type }.coerceAtLeast(0)
    Box(modifier = modifier.fillMaxSize()) {
        // 方向感知的类型切换动画（同主页 tab）：序号变大向左滑入，反之向右。
        AnimatedContent(
            // 页面容器做唯一的 hazeSource：切换动画期间只允许一个源节点，
            // 底栏作为兄弟节点采样它（effect 不能与 source 嵌套）。
            modifier = Modifier
                .fillMaxSize()
                .appHazeSource(hazeState),
            targetState = normalizedType,
            transitionSpec = {
                val forward = chipIndexOf(targetState) > chipIndexOf(initialState)
                val enter = slideInHorizontally(
                    animationSpec = tween(hunoia.luno.ui.theme.MotionPageEnter, easing = FastOutSlowInEasing),
                ) { full -> if (forward) full / 6 else -full / 6 } +
                    fadeIn(animationSpec = tween(hunoia.luno.ui.theme.MotionPageEnter))
                val exit = slideOutHorizontally(
                    animationSpec = tween(hunoia.luno.ui.theme.MotionPageExit, easing = FastOutSlowInEasing),
                ) { full -> if (forward) -full / 6 else full / 6 } +
                    fadeOut(animationSpec = tween(hunoia.luno.ui.theme.MotionSwapOut))
                enter togetherWith exit
            },
            contentKey = { it },
            label = "actionType",
        ) { type ->
            val listState = rememberLazyListState()
            // 搜索词变化不换类型、不触发切换动画，但仍要回到列表顶部
            LaunchedEffect(query) {
                if (listState.firstVisibleItemIndex > 0) listState.scrollToItem(0)
            }
            val filteredActions = remember(actions, query, type, subGestures, actionLibraryEntries) {
                when {
                    // 小部件这一屏不列动作：小部件不是 StoredAction，混进来会让选中类型不可证伪。
                    type == TYPE_WIDGET -> emptyList()
                    type != null && query.isBlank() -> emptyList()
                    type == TYPE_LIBRARY_AND_RULE -> emptyList()
                    query.isBlank() -> actions
                    else -> actions.filter {
                        context.actionTextWithSubGesture(it, subGestures, actionLibraryEntries, actionPanels, gridPanels, emptyIfNone = false)
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
            val filteredLibraryEntries = remember(actionLibraryEntries, query, type) {
                if (type == TYPE_WIDGET) emptyList() else if (type == TYPE_LIBRARY_AND_RULE || query.isNotBlank()) {
                    actionLibraryEntries.filter { if (query.isNotBlank()) it.matchesQuery(query) else true }
                } else emptyList()
            }.sortedBy { it.createdAt }
            // 「动作库」chip 内按动作类型分组；混合搜索时仍收在单个「动作库」标题下。
            val groupedLibraryEntries = remember(filteredLibraryEntries, type) {
                if (type != TYPE_LIBRARY_AND_RULE) {
                    emptyMap()
                } else {
                    filteredLibraryEntries
                        .groupBy { ActionDefinitions.byTypeId(it.typeId)?.category ?: ActionCategory.ADVANCED }
                        .toList()
                        .sortedBy { (category, _) -> ActionDefinitions.categoryOrder(category) }
                        .toMap(LinkedHashMap())
                }
            }
            val filteredApps = remember(appInfos, query, type) {
                if (type == TYPE_WIDGET) emptyList() else if (type == TYPE_APP || query.isNotBlank()) {
                    appInfos.filter {
                        if (query.isBlank()) true
                        else it.label.contains(query, ignoreCase = true) || it.packageName.contains(query, ignoreCase = true)
                    }
                } else emptyList()
            }
            val filteredCreateShortcuts = remember(createShortcuts, query, type) {
                if (type == TYPE_WIDGET) emptyList() else if (type == TYPE_APP || query.isNotBlank()) {
                    createShortcuts.filter {
                        if (query.isBlank()) true
                        else it.label.contains(query, ignoreCase = true) ||
                            it.shortcuts.any { s -> s.label.contains(query, ignoreCase = true) }
                    }
                } else emptyList()
            }
            val filteredLaunchShortcuts = remember(launchShortcuts, query, type) {
                if (type == TYPE_WIDGET) emptyList() else if (type == TYPE_APP || query.isNotBlank()) {
                    launchShortcuts.filter {
                        if (query.isBlank()) true
                        else it.label.contains(query, ignoreCase = true) ||
                            it.shortcuts.any { s -> s.label.contains(query, ignoreCase = true) }
                    }
                } else emptyList()
            }
                val filteredWidgets = remember(widgetGroups, query, type) {
                    if (type != TYPE_WIDGET) emptyList()
                    else widgetGroups.searchBy(query)
                }
            // 内置部件跟随小部件 chip 同屏展示；搜索按名字过滤。
            val filteredInternalWidgets = remember(type, query, context) {
                if (type != TYPE_WIDGET) emptyList()
                else InternalWidgetDefs.filter { context.getString(it.labelRes).contains(query, ignoreCase = true) || query.isBlank() }
            }
            val appSelectionFlags = filteredApps.map { selectedRecord.isSelected(it) }
            // 搜索态 ↔ 非搜索态整表切换时直接换一颗 LazyColumn：旧结果行不会带着
            // animateItem 退场动画和新分类全集同帧叠在一起。普通筛选（type chip）
            // 不建新列表，仍走同一颗 LazyColumn。
            key(query.isBlank()) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = contentPadding.calculateStartPadding(LocalLayoutDirection.current),
                        end = contentPadding.calculateEndPadding(LocalLayoutDirection.current),
                        top = contentPadding.calculateTopPadding(),
                        // 底栏悬浮在列表之上：尾部留出「底栏实测高 + 页底留白」，否则最后几行永远
                        // 滚不到玻璃上方；底栏高度含导航栏/IME inset，键盘弹起时这里自动跟着让位。
                        bottom = with(density) { bottomBarHeightPx.toDp() } + ContentBottom,
                    ),
                    verticalArrangement = Arrangement.spacedBy(SegmentedGap),
                ) {
                    // 缺「应用列表」权限时，应用 / 快捷方式清单恒为空且无异常可捕获：
                    // 只要这一屏本应列出它们（全部 / 应用 chip / 搜索），就必须给出授权去向，
                    // 否则看起来就是「没有应用」。动作库不受影响，仍正常展示。
                    val appsPermissionRequired = !permissionState.isGranted &&
                        (type == null || type == TYPE_APP || query.isNotBlank())
                    val hasAnyContent = appsPermissionRequired ||
                        grouped.isNotEmpty() || filteredApps.isNotEmpty() || filteredLibraryEntries.isNotEmpty() ||
                        filteredCreateShortcuts.isNotEmpty() || filteredLaunchShortcuts.isNotEmpty() ||
                        filteredWidgets.isNotEmpty() || filteredInternalWidgets.isNotEmpty()
                    if ((query.isNotEmpty() || type != null) && !hasAnyContent) {
                        item {
                            EmptyState(
                                message = stringResource(
                                    if (type == TYPE_WIDGET) R.string.grid_panel_no_widgets
                                    else hunoia.luno.design.R.string.no_matching_results
                                )
                            )
                        }
                    } else {
                        if (appsPermissionRequired) {
                            item(key = "apps_permission") {
                                EmptyState(
                                    icon = Icons.Outlined.Lock,
                                    message = stringResource(R.string.apps_permission_required),
                                    action = {
                                        PressTextButton(
                                            onClick = {
                                                // 系统对话框才是正路；只有在被永久拒绝、再申请
                                                // 也不会弹框时才退回应用详情页。
                                                if (permissionState.deniedForever) {
                                                    context.gotoAppDetailSettings()
                                                } else {
                                                    permissionState.launchPermissionRequest()
                                                }
                                            },
                                        ) {
                                            Text(
                                                stringResource(
                                                    if (permissionState.deniedForever) R.string.apps_permission_settings
                                                    else R.string.request_get_apps_permission,
                                                ),
                                            )
                                        }
                                    },
                                )
                            }
                        }
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
                                // 「无」单独置顶，不显示组标题。
                                if (category != ActionCategory.NONE) {
                                    stickyHeader(key = "cat_${category.name}") {
                                        SheetGroupHeader(stringResource(id = category.displayNameRes))
                                    }
                                }
                                itemsIndexed(
                                    items = categoryActions,
                                    key = { _, it -> "${it.typeId}:${it.params}" }
                                ) { index, item ->
                                    ActionItem(
                                        modifier = Modifier.animateItem(fadeInSpec = null, fadeOutSpec = null),
                                        action = item,
                                        actionLabel = context.actionTextWithSubGesture(
                                            item,
                                            subGestures,
                                            actionLibraryEntries,
                                            actionPanels,
                                            gridPanels,
                                            emptyIfNone = false,
                                        ),
                                        selected = selectedRecord.isSelected(item),
                                        onSelect = { onSelect(item) },
                                        subtitle = if (item.typeId in RowSettingsActionTypeIds) rowSettingsSubtitle(item, context) else "",
                                        shape = selectedSegmentedShape(index, categoryActions.size, categoryFlags),
                                        trailing = if (item.typeId in RowSettingsActionTypeIds) {{
                                            PressIconButton(onClick = { settingsAction = item }) {
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
                        if (groupedLibraryEntries.isNotEmpty()) {
                            groupedLibraryEntries.forEach { (category, entries) ->
                                stickyHeader(key = "lib_cat_${category.name}") {
                                    SheetGroupHeader(stringResource(id = category.displayNameRes))
                                }
                                val selectionFlags = entries.map { selectedRecord.isSelected(it.toStoredAction()) }
                                itemsIndexed(items = entries, key = { _, it -> "lib_${it.id}" }) { index, entry ->
                                    val action = entry.toStoredAction()
                                    ActionItem(
                                        modifier = Modifier.animateItem(fadeInSpec = null, fadeOutSpec = null),
                                        action = action,
                                        actionLabel = entry.name,
                                        selected = selectedRecord.isSelected(action),
                                        subtitle = actionLibraryParamSummary(entry, context),
                                        onSelect = { onSelectLibraryEntry(entry) },
                                        shape = selectedSegmentedShape(index, entries.size, selectionFlags),
                                    )
                                }
                            }
                        } else if (filteredLibraryEntries.isNotEmpty()) {
                            stickyHeader(key = "lib_all") {
                                SheetGroupHeader(stringResource(hunoia.luno.actions.R.string.action_library))
                            }
                            val selectionFlags = filteredLibraryEntries.map { selectedRecord.isSelected(it.toStoredAction()) }
                            itemsIndexed(items = filteredLibraryEntries, key = { _, it -> "lib_${it.id}" }) { index, entry ->
                                val action = entry.toStoredAction()
                                ActionItem(
                                    modifier = Modifier.animateItem(fadeInSpec = null, fadeOutSpec = null),
                                    action = action,
                                    actionLabel = entry.name,
                                    selected = selectedRecord.isSelected(action),
                                    subtitle = actionLibraryParamSummary(entry, context),
                                    onSelect = { onSelectLibraryEntry(entry) },
                                    shape = selectedSegmentedShape(index, filteredLibraryEntries.size, selectionFlags),
                                )
                            }
                        }
                        if (filteredApps.isNotEmpty()) {
                            val appsExpanded = query.isNotBlank() || !appsCollapsed
                            stickyHeader(key = "apps") {
                                if (query.isBlank()) {
                                    SheetGroupHeader(
                                        title = stringResource(R.string.tab_apps),
                                        expanded = appsExpanded,
                                        onToggleExpanded = { appsCollapsed = !appsCollapsed },
                                    )
                                } else {
                                    // 搜索态：结果必须全展开，分组头不显示收起箭头。
                                    SheetGroupHeader(stringResource(R.string.tab_apps))
                                }
                            }
                            if (appsExpanded) {
                                itemsIndexed(items = filteredApps, key = { _, it -> "app_${it.identity}" }) { index, item ->
                                    AppItem(appInfo = item, selected = selectedRecord.isSelected(item),
                                        onSelect = { onSelectApp(item) },
                                        onLongClick = { onAppLongClick(item) },
                                        modifier = Modifier.animateItem(fadeInSpec = null, fadeOutSpec = null),
                                        shape = selectedSegmentedShape(index, filteredApps.size, appSelectionFlags))
                                }
                            }
                        }
                        if (filteredCreateShortcuts.isNotEmpty()) {
                            val createShortcutsExpanded = query.isNotBlank() || !createShortcutsCollapsed
                            stickyHeader(key = "create_shortcuts") {
                                if (query.isBlank()) {
                                    SheetGroupHeader(
                                        title = stringResource(R.string.create_shortcut),
                                        expanded = createShortcutsExpanded,
                                        onToggleExpanded = { createShortcutsCollapsed = !createShortcutsCollapsed },
                                    )
                                } else {
                                    SheetGroupHeader(stringResource(R.string.create_shortcut))
                                }
                            }
                            if (createShortcutsExpanded) {
                                items(items = filteredCreateShortcuts, key = { "cs_${it.qualifiedName}" }) { item ->
                                    LauncherInfoItem(launcherInfo = item,
                                        isShortcutInfoSelected = { selectedRecord.isSelected(it) },
                                        onSelect = { s, _ -> onSelectShortcut(s) }, onClick = { onShortcutClick(item) },
                                        modifier = Modifier.animateItem(fadeInSpec = null, fadeOutSpec = null))
                                }
                            }
                        }
                        if (filteredLaunchShortcuts.isNotEmpty()) {
                            val launchShortcutsExpanded = query.isNotBlank() || !launchShortcutsCollapsed
                            stickyHeader(key = "launch_shortcuts") {
                                if (query.isBlank()) {
                                    SheetGroupHeader(
                                        title = stringResource(R.string.launch_shortcut),
                                        expanded = launchShortcutsExpanded,
                                        onToggleExpanded = { launchShortcutsCollapsed = !launchShortcutsCollapsed },
                                    )
                                } else {
                                    SheetGroupHeader(stringResource(R.string.launch_shortcut))
                                }
                            }
                            if (launchShortcutsExpanded) {
                                items(items = filteredLaunchShortcuts, key = { "ls_${it.qualifiedName}" }) { item ->
                                    LauncherInfoItem(launcherInfo = item,
                                        isShortcutInfoSelected = { selectedRecord.isSelected(it) },
                                        onSelect = { s, _ -> onSelectShortcut(s) }, onClick = {},
                                        modifier = Modifier.animateItem(fadeInSpec = null, fadeOutSpec = null))
                                }
                            }
                        }
                }
            }
            }
        }
        ActionSelectBottomBar(
            modifier = Modifier.align(Alignment.BottomCenter),
            query = query,
            onQueryChange = { query = it },
            typeChips = typeChips,
            selectedType = normalizedType,
            onSelectedTypeChange = { selectedType = it },
            hazeState = hazeState,
            onHeightChange = { bottomBarHeightPx = it },
        )
    }
}