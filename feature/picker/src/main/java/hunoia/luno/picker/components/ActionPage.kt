package hunoia.luno.picker
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import android.appwidget.AppWidgetProviderInfo
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.LibraryBooks
import androidx.compose.material.icons.filled.AllInclusive
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import hunoia.luno.picker.R
import hunoia.luno.action.definitions.ActionCategory
import hunoia.luno.action.definitions.ActionDefinitions
import hunoia.luno.action.definitions.ActionGroupSpec
import hunoia.luno.action.definitions.ActionVariant
import hunoia.luno.core.domain.ActionLibraryEntry
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
import hunoia.luno.quickaccess.query.AppDrawerSections
import hunoia.luno.quickaccess.query.SearchText
import hunoia.luno.quickaccess.query.warmSearchIndex
import hunoia.luno.picker.UiState.SelectedRecord
import hunoia.luno.picker.components.ActionRowSettingsSheet
import hunoia.luno.picker.components.RowSettingsActionTypeIds
import hunoia.luno.picker.components.rowSettingsHasSettings
import hunoia.luno.picker.components.rowSettingsSubtitle
import hunoia.luno.actions.library.actionLibraryParamSummary
import hunoia.luno.ui.component.feedback.EmptyState
import hunoia.luno.actions.library.displayNameRes
import hunoia.luno.ui.component.PressTextButton
import hunoia.luno.ui.component.TextIcon
import hunoia.luno.ui.component.actionIcon
import hunoia.luno.ui.theme.*
import hunoia.luno.ui.component.row.LunoSettingsRow
import hunoia.luno.ui.component.row.LunoGroup
import hunoia.luno.ui.component.row.LunoGroupItem
import hunoia.luno.ui.component.row.GroupItemCorners
import hunoia.luno.ui.component.row.groupItemSpacing
import hunoia.luno.ui.component.row.segmentedShape
import hunoia.luno.ui.component.row.selectedSegmentedShape
import hunoia.luno.ui.component.row.selectedSegmentedCorners
import hunoia.luno.ui.component.SheetGroupHeader
import hunoia.luno.ui.component.IndexBarEntry
import hunoia.luno.ui.component.LetterDial
import dev.chrisbanes.haze.HazeState
import hunoia.luno.ui.component.PressIconButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
/** 动作库与规则合成一颗 chip：两者都是「已保存的动作来源」，分屏会多一层切换。 */
private const val TYPE_LIBRARY_AND_RULE = "action_library"
/** 应用与快捷方式合成一颗 chip：两者都是「从别的 app 取入口」，合并后底栏少一格。 */
const val TYPE_APP = "app"
/** 旧版本单独保存的快捷方式筛选值，恢复时并到合并后的 chip。 */
const val TYPE_SHORTCUT_LEGACY = "shortcut"
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
    /** 与内容层同一份 Haze 状态，给悬浮底栏做模糊底；由 [LunoPageRaw] 内容下发。 */
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
    /** 当前 chip（归一化前的原始值）；由宿主提升，顶栏才能按 tab 显示系统应用开关。 */
    selectedType: String?,
    onSelectedTypeChange: (String?) -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    // 旧版本「快捷方式」是独立 chip；合并后归一化，避免恢复出任何 chip 都不匹配的空列表。
    val normalizedType = if (selectedType == TYPE_SHORTCUT_LEGACY) TYPE_APP else selectedType
    // 应用区按字母分节，每个应用行各自可展开；不再有顶层「应用 / 快捷方式」两个折叠组。
    var settingsAction by remember { mutableStateOf<StoredAction?>(null) }
    /**
     * 当前展开的行：预设变体组与应用的快捷方式行**全局互斥**，同一时间最多展开一个。
     * 值带前缀（`g:` 组 / `a:` 应用），两类 key 来源不同也不会撞。
     * 进页时只自动展开包含当前选中项的那一个；搜索态则全部展开（不读这里）。
     */
    var expandedKey by rememberSaveable { mutableStateOf("") }
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
            // 只取翻转值：列表滚动/停止状态用于让字母拨盘自动收起。
            val listScrollInProgress by remember(listState) {
                derivedStateOf { listState.isScrollInProgress }
            }
            // 搜索词变化不换类型、不触发切换动画，但仍要回到列表顶部
            LaunchedEffect(query) {
                if (listState.firstVisibleItemIndex > 0) listState.scrollToItem(0)
            }
            val searchableActions = remember(actions, selectedRecord.action) {
                // 按 typeId 去重：行尾设置可能把改过的具体动作追加进列表，
                // 同一张变体表只展开一次，避免搜索结果出现重复行。
                val seenVariantTypes = mutableSetOf<String>()
                actions.flatMap { action ->
                    val definition = ActionDefinitions.byTypeId(action.typeId)
                    if (definition != null && definition.variants.isNotEmpty()) {
                        if (!seenVariantTypes.add(action.typeId)) {
                            emptyList()
                        } else {
                            definition.variants.map { variant ->
                                variant.concreteAction(action.typeId, actions, selectedRecord.action)
                            }
                        }
                    } else {
                        listOf(action)
                    }
                }
            }
            val searchText = remember(query) { SearchText.compile(query) }
            val filteredActions = remember(actions, searchableActions, query, type, subGestures, actionLibraryEntries) {
                when {
                    // 小部件这一屏不列动作：小部件不是 StoredAction，混进来会让选中类型不可证伪。
                    type == TYPE_WIDGET -> emptyList()
                    type != null && query.isBlank() -> emptyList()
                    type == TYPE_LIBRARY_AND_RULE -> emptyList()
                    query.isBlank() -> actions
                    // 搜索按具体预设展开：搜「Wi-Fi」「媒体音量增加」也能命中组内子行。
                    // 匹配语义与快速搜索统一（拼音首字母 / 全拼 / 模糊音）。
                    else -> searchableActions.filter { action ->
                        searchText?.matches(
                            context.actionTextWithSubGesture(
                                action,
                                subGestures,
                                actionLibraryEntries,
                                actionPanels,
                                gridPanels,
                                emptyIfNone = false,
                            ),
                        ) == true
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
            // 搜索态直接平铺具体动作；非搜索态按组收起。
            val pickerNodes = remember(grouped, query, selectedRecord.action) {
                if (query.isNotBlank()) {
                    emptyMap()
                } else {
                    grouped.mapValues { (_, categoryActions) ->
                        buildPickerNodes(categoryActions, selectedRecord.action)
                    }
                }
            }
            val selectedGroupKeys = remember(pickerNodes, selectedRecord) {
                pickerNodes.values.flatten()
                    .filterIsInstance<PickerNode.Group>()
                    .filter { group -> group.children.any { selectedRecord.isSelected(it) } }
                    .map { it.group.key }
                    .toSet()
            }
            LaunchedEffect(selectedGroupKeys) {
                selectedGroupKeys.firstOrNull()?.let { expandedKey = "g:$it" }
            }
            val filteredLibraryEntries = remember(actionLibraryEntries, query, type) {
                if (type == TYPE_WIDGET) {
                    emptyList()
                } else if (type == TYPE_LIBRARY_AND_RULE || query.isNotBlank()) {
                    when {
                        query.isBlank() -> actionLibraryEntries
                        searchText == null -> emptyList()
                        else -> actionLibraryEntries.filter { entry ->
                            searchText.matches(
                                entry.name,
                                ActionDefinitions.byTypeId(entry.typeId)?.name ?: entry.typeId,
                                entry.typeId,
                            )
                        }
                    }
                } else {
                    emptyList()
                }
            }.sortedBy { it.createdAt }
            // 「动作库」chip 内按动作类型分组；混合搜索时仍收在单个「动作库」标题下。
            val groupedLibraryEntries = remember(filteredLibraryEntries, type) {
                if (type != TYPE_LIBRARY_AND_RULE) {
                    emptyMap()
                } else {
                    filteredLibraryEntries
                        .groupBy { ActionDefinitions.byTypeId(it.typeId)?.category ?: ActionCategory.SYSTEM }
                        .toList()
                        .sortedBy { (category, _) -> ActionDefinitions.categoryOrder(category) }
                        .toMap(LinkedHashMap())
                }
            }
            // 应用与快捷方式按包名合并：同一应用只出现一次，快捷方式挂在应用行下。
            val appShortcutEntries = remember(appInfos, createShortcuts, launchShortcuts) {
                buildAppShortcutEntries(appInfos, createShortcuts, launchShortcuts)
            }
            val filteredAppEntries = remember(appShortcutEntries, query, type) {
                when {
                    type == TYPE_WIDGET -> emptyList()
                    type == TYPE_APP || query.isNotBlank() -> when {
                        query.isBlank() -> appShortcutEntries
                        searchText == null -> emptyList()
                        else -> appShortcutEntries
                            .filter { it.matchesSearch(searchText) }
                            .map { it.filterChildren(searchText) }
                    }
                    else -> emptyList()
                }
            }
            // 选择页的应用区按拼音字母分节（A–#、节内全拼序）；不用抽屉的「常用置顶」：
            // 这里找应用靠名字，滑杆要能稳定落到某个字母。
            val appSections = remember(filteredAppEntries) {
                AppDrawerSections.alphabeticalSectionsOf(filteredAppEntries) { it.app.label }
            }
            val indexBarEntries = remember(appSections) {
                appSections.map { IndexBarEntry(key = it.letter.toString(), label = it.letter.toString()) }
            }
            // 部件与其他源同口径：有搜索词时跨 tab 参与（此前只有部件 tab 内能搜到）；
            // 空查询仍只在自己的 chip 里列出，避免「全部」tab 被部件卡淹没。
            val filteredWidgets = remember(widgetGroups, query, type, showWidgetChip) {
                when {
                    !showWidgetChip -> emptyList()
                    type == TYPE_WIDGET || query.isNotBlank() -> widgetGroups.searchBy(query)
                    else -> emptyList()
                }
            }
            // 内置部件名称走 stringResource（配置变化能重组），搜索与预热共用同一份。
            val internalWidgetLabels = if (showWidgetChip) {
                InternalWidgetDefs.associate { def -> def.id to stringResource(def.labelRes) }
            } else {
                emptyMap()
            }
            // 内置部件跟随小部件 chip 同屏展示；搜索按名字过滤。
            val filteredInternalWidgets = remember(type, query, showWidgetChip, internalWidgetLabels) {
                when {
                    !showWidgetChip -> emptyList()
                    type == TYPE_WIDGET || query.isNotBlank() -> when {
                        query.isBlank() -> InternalWidgetDefs
                        searchText == null -> emptyList()
                        else -> InternalWidgetDefs.filter { def ->
                            searchText.matches(internalWidgetLabels[def.id].orEmpty())
                        }
                    }
                    else -> emptyList()
                }
            }
            // 首键前把拼音索引预热到后台：列表过滤仍同步跑，但不再承担建索引成本。
            LaunchedEffect(appShortcutEntries, searchableActions, actionLibraryEntries, widgetGroups) {
                withContext(Dispatchers.Default) {
                    appShortcutEntries.forEach { entry ->
                        warmSearchIndex(entry.app.label)
                        warmSearchIndex(entry.app.packageName)
                        fun warmLauncher(launcher: LauncherInfo) {
                            warmSearchIndex(launcher.label)
                            warmSearchIndex(launcher.packageName)
                            launcher.shortcuts.forEach { warmSearchIndex(it.label) }
                        }
                        entry.createLaunchers.forEach(::warmLauncher)
                        entry.launchLaunchers.forEach(::warmLauncher)
                    }
                    searchableActions.forEach { action ->
                        warmSearchIndex(
                            context.actionTextWithSubGesture(
                                action,
                                subGestures,
                                actionLibraryEntries,
                                actionPanels,
                                gridPanels,
                                emptyIfNone = false,
                            ),
                        )
                    }
                    actionLibraryEntries.forEach { entry ->
                        warmSearchIndex(entry.name)
                        warmSearchIndex(ActionDefinitions.byTypeId(entry.typeId)?.name ?: entry.typeId)
                    }
                    widgetGroups.forEach { group ->
                        warmSearchIndex(group.appLabel)
                        group.widgetLabels.forEach { warmSearchIndex(it) }
                        group.providers.forEach { warmSearchIndex(it.provider?.packageName.orEmpty()) }
                    }
                    InternalWidgetDefs.forEach { def -> warmSearchIndex(internalWidgetLabels[def.id].orEmpty()) }
                }
            }
            val appsPermissionRequired = !permissionState.isGranted &&
                (type == null || type == TYPE_APP || query.isNotBlank())
            // 应用区的起始 item 下标：滑杆跳转只认这套「前面有几颗 item」的同一份计数，
            // 不在 LazyColumn 里另存映射。每个分类 = 标题 + 一张组卡；每个字母节 = 标题 + 一张组卡。
            val appsStartIndex =
                (if (appsPermissionRequired) 1 else 0) +
                    (if (filteredInternalWidgets.isNotEmpty()) 2 else 0) +
                    filteredWidgets.size * 2 +
                    grouped.entries.sumOf { (category, _) -> if (category == ActionCategory.NONE) 1 else 2 } +
                    if (groupedLibraryEntries.isNotEmpty()) {
                        groupedLibraryEntries.size * 2
                    } else if (filteredLibraryEntries.isNotEmpty()) {
                        2
                    } else {
                        0
                    }
            val selectedAppKey = remember(filteredAppEntries, selectedRecord) {
                filteredAppEntries.firstOrNull { entry ->
                    entry.shortcuts.any { selectedRecord.isSelected(it) }
                }?.rowKey
            }
            // 进页自动展开「已选快捷方式」所在应用并滚到其字母节；之后用户再选别的行不抢滚动。
            var didInitialAppJump by rememberSaveable { mutableStateOf(false) }
            LaunchedEffect(appSections, selectedAppKey) {
                if (selectedAppKey == null) return@LaunchedEffect
                // 与预设变体组互斥：进页 / 选中快捷方式时只留这一个应用展开。
                expandedKey = "a:$selectedAppKey"
                if (didInitialAppJump) return@LaunchedEffect
                val sectionIndex = appSections.indexOfFirst { section ->
                    section.items.any { it.rowKey == selectedAppKey }
                }
                if (sectionIndex >= 0) {
                    listState.requestScrollToItem(appsStartIndex + sectionIndex * 2)
                    didInitialAppJump = true
                }
            }
            // 搜索态 ↔ 非搜索态整表切换时直接换一颗 LazyColumn：旧结果行不会带着
            // animateItem 退场动画和新分类全集同帧叠在一起。普通筛选（type chip）
            // 不建新列表，仍走同一颗 LazyColumn。
            Box(Modifier.fillMaxSize()) {
                val bottomInset = with(density) { bottomBarHeightPx.toDp() }
                Box(Modifier.fillMaxSize()) {
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
                        bottom = bottomInset + ContentBottom,
                    ),
                    // 组与组之间用 ListSpacing；组内分段圆角仍由 LunoGroup 的 contentSpacing 管。
                    verticalArrangement = Arrangement.spacedBy(ListSpacing),
                ) {
                    // 缺「应用列表」权限时，应用 / 快捷方式清单恒为空且无异常可捕获：
                    // 只要这一屏本应列出它们（全部 / 应用 chip / 搜索），就必须给出授权去向，
                    // 否则看起来就是「没有应用」。动作库不受影响，仍正常展示。
                    val appsPermissionRequired = !permissionState.isGranted &&
                        (type == null || type == TYPE_APP || query.isNotBlank())
                    val hasAnyContent = appsPermissionRequired ||
                        grouped.isNotEmpty() || appSections.isNotEmpty() || filteredLibraryEntries.isNotEmpty() ||
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
                        if (grouped.isNotEmpty()) {
                            grouped.forEach { (category, categoryActions) ->
                                if (category != ActionCategory.NONE) {
                                    stickyHeader(key = "cat_${category.name}") {
                                        SheetGroupHeader(stringResource(id = category.displayNameRes))
                                    }
                                }
                                item(key = "cat_body_${category.name}") {
                                    LunoGroup {
                                        if (query.isNotBlank()) {
                                            val flags = categoryActions.map { selectedRecord.isSelected(it) }
                                            categoryActions.forEachIndexed { index, item ->
                                                PickerActionRow(
                                                    item = item,
                                                    shape = selectedSegmentedShape(index, categoryActions.size, flags),
                                                    selected = selectedRecord.isSelected(item),
                                                    context = context,
                                                    subGestures = subGestures,
                                                    actionLibraryEntries = actionLibraryEntries,
                                                    actionPanels = actionPanels,
                                                    gridPanels = gridPanels,
                                                    onSelect = { onSelect(item) },
                                                    onGear = { settingsAction = item },
                                                )
                                            }
                                        } else {
                                            val nodes = pickerNodes[category].orEmpty()
                                            // 分类内累计行号：大卡分割线靠 shape 自带的位置判断上方是否还有行。
                                            var rowIndex = 0
                                            nodes.filterIsInstance<PickerNode.Group>().forEach { node ->
                                                val expanded = expandedKey == "g:${node.group.key}"
                                                val childFlags = node.children.map { selectedRecord.isSelected(it) }
                                                // 组行与子项共用同一分段块：行数 = 1（组行）+ 展开后的子项数。
                                                val rowCount = if (expanded) node.children.size + 1 else 1
                                                val blockFlags = listOf(childFlags.any { it }) + childFlags
                                                val selectedChild = node.children.firstOrNull { selectedRecord.isSelected(it) }
                                                val groupRowIndex = rowIndex
                                                // 展开时底边缝合，收起时回到容器角：动画插值，不做形状硬切。
                                                val bottomRadius by animateDpAsState(
                                                    targetValue = if (expanded) ConnectionRadius else ContainerRadius,
                                                    animationSpec = tween(MotionSubtle),
                                                    label = "pickerGroupBottomRadius",
                                                )
                                                PickerGroupRow(
                                                    title = stringResource(node.group.labelRes),
                                                    icon = actionIcon(selectedChild ?: node.children.first()),
                                                    subtitle = if (selectedChild != null) {
                                                        stringResource(
                                                            R.string.action_group_selected,
                                                            context.actionTextWithSubGesture(
                                                                selectedChild,
                                                                subGestures,
                                                                actionLibraryEntries,
                                                                actionPanels,
                                                                gridPanels,
                                                                emptyIfNone = false,
                                                            ),
                                                        )
                                                    } else {
                                                        stringResource(R.string.action_group_item_count, node.children.size)
                                                    },
                                                    expanded = expanded,
                                                    shape = GroupItemCorners(
                                                        topStart = ContainerRadius,
                                                        topEnd = ContainerRadius,
                                                        bottomStart = bottomRadius,
                                                        bottomEnd = bottomRadius,
                                                    ).shape(index = groupRowIndex),
                                                    onToggle = {
                                                        expandedKey = if (expanded) {
                                                            ""
                                                        } else {
                                                            "g:${node.group.key}"
                                                        }
                                                    },
                                                )
                                                AnimatedVisibility(
                                                    visible = expanded,
                                                    enter = expandVertically() + fadeIn(),
                                                    exit = shrinkVertically() + fadeOut(),
                                                ) {
                                                    Column(
                                                        verticalArrangement = Arrangement.spacedBy(groupItemSpacing()),
                                                    ) {
                                                        node.children.forEachIndexed { index, item ->
                                                            PickerActionRow(
                                                                item = item,
                                                                shape = selectedSegmentedCorners(
                                                                    index = index + 1,
                                                                    count = rowCount,
                                                                    flags = blockFlags,
                                                                    mergedTop = index == 0,
                                                                ).shape(index = groupRowIndex + index + 1),
                                                                selected = selectedRecord.isSelected(item),
                                                                context = context,
                                                                subGestures = subGestures,
                                                                actionLibraryEntries = actionLibraryEntries,
                                                                actionPanels = actionPanels,
                                                                gridPanels = gridPanels,
                                                                onSelect = { onSelect(item) },
                                                                onGear = { settingsAction = item },
                                                            )
                                                        }
                                                    }
                                                }
                                                rowIndex += rowCount
                                            }
                                            val leaves = nodes.filterIsInstance<PickerNode.Leaf>().map { it.action }
                                            if (leaves.isNotEmpty()) {
                                                val flags = leaves.map { selectedRecord.isSelected(it) }
                                                val leavesStart = rowIndex
                                                leaves.forEachIndexed { index, item ->
                                                    PickerActionRow(
                                                        item = item,
                                                        shape = selectedSegmentedCorners(
                                                            index = index,
                                                            count = leaves.size,
                                                            flags = flags,
                                                        ).shape(index = leavesStart + index),
                                                        selected = selectedRecord.isSelected(item),
                                                        context = context,
                                                        subGestures = subGestures,
                                                        actionLibraryEntries = actionLibraryEntries,
                                                        actionPanels = actionPanels,
                                                        gridPanels = gridPanels,
                                                        onSelect = { onSelect(item) },
                                                        onGear = { settingsAction = item },
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        if (groupedLibraryEntries.isNotEmpty()) {
                            groupedLibraryEntries.forEach { (category, entries) ->
                                stickyHeader(key = "lib_cat_${category.name}") {
                                    SheetGroupHeader(stringResource(id = category.displayNameRes))
                                }
                                item(key = "lib_body_${category.name}") {
                                    LunoGroup {
                                        val selectionFlags = entries.map { selectedRecord.isSelected(it.toStoredAction()) }
                                        entries.forEachIndexed { index, entry ->
                                            val action = entry.toStoredAction()
                                            ActionItem(
                                                action = action,
                                                actionLabel = entry.name,
                                                selected = selectedRecord.isSelected(action),
                                                subtitle = actionLibraryParamSummary(entry, context),
                                                onSelect = { onSelectLibraryEntry(entry) },
                                                shape = selectedSegmentedShape(index, entries.size, selectionFlags),
                                            )
                                        }
                                    }
                                }
                            }
                        } else if (filteredLibraryEntries.isNotEmpty()) {
                            stickyHeader(key = "lib_all") {
                                SheetGroupHeader(stringResource(hunoia.luno.actions.R.string.action_library))
                            }
                            item(key = "lib_body_all") {
                                LunoGroup {
                                    val selectionFlags = filteredLibraryEntries.map { selectedRecord.isSelected(it.toStoredAction()) }
                                    filteredLibraryEntries.forEachIndexed { index, entry ->
                                        val action = entry.toStoredAction()
                                        ActionItem(
                                            action = action,
                                            actionLabel = entry.name,
                                            selected = selectedRecord.isSelected(action),
                                            subtitle = actionLibraryParamSummary(entry, context),
                                            onSelect = { onSelectLibraryEntry(entry) },
                                            shape = selectedSegmentedShape(index, filteredLibraryEntries.size, selectionFlags),
                                        )
                                    }
                                }
                            }
                        }
                        // 部件排在动作/动作库之后、字母应用区之前：全局搜索不会一上来就被部件卡占满。
                        if (filteredInternalWidgets.isNotEmpty()) {
                            stickyHeader(key = "widget_internal") {
                                SheetGroupHeader(stringResource(R.string.grid_panel_internal_widgets))
                            }
                            item(key = "widget_internal_row") {
                                LunoGroup {
                                    LunoGroupItem(shape = segmentedShape(0, 1)) {
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
                            }
                        }
                        if (filteredWidgets.isNotEmpty()) {
                            // 每 app 分区头 + 区内横向滚动的预览卡。
                            filteredWidgets.forEach { group ->
                                stickyHeader(key = "widget_${group.appLabel}") {
                                    WidgetGroupHeaderRow(group)
                                }
                                item(key = "widget_row_${group.appLabel}") {
                                    LunoGroup {
                                        LunoGroupItem(shape = segmentedShape(0, 1)) {
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
                            }
                        }
                        appSections.forEach { section ->
                            stickyHeader(key = "app_header_${section.letter}") {
                                SheetGroupHeader(section.letter.toString())
                            }
                            item(key = "app_body_${section.letter}") {
                                LunoGroup {
                                    var rowIndex = 0
                                    section.items.forEach { entry ->
                                        val expanded = entry.hasChildren &&
                                            (query.isNotBlank() || expandedKey == "a:${entry.rowKey}")
                                        AppShortcutBlock(
                                            index = rowIndex,
                                            entry = entry,
                                            expanded = expanded,
                                            // 搜索态全部展开，箭头只会是假控件。
                                            showToggle = query.isBlank() && entry.hasChildren,
                                            selectedRecord = selectedRecord,
                                            onSelectApp = onSelectApp,
                                            onSelectShortcut = onSelectShortcut,
                                            onAppLongClick = onAppLongClick,
                                            onShortcutClick = onShortcutClick,
                                            onToggleExpanded = {
                                                expandedKey = if (expanded) {
                                                    ""
                                                } else {
                                                    "a:${entry.rowKey}"
                                                }
                                            },
                                        )
                                        rowIndex += if (expanded) {
                                            1 + entry.createLaunchers.size + entry.shortcuts.size
                                        } else {
                                            1
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                }
                if (appSections.isNotEmpty()) {
                    LetterDial(
                        entries = indexBarEntries,
                        activeKey = appSections
                            .getOrNull((listState.firstVisibleItemIndex - appsStartIndex).coerceAtLeast(0) / 2)
                            ?.letter?.toString(),
                        onJump = { entry ->
                            val sectionIndex = appSections.indexOfFirst { it.letter.toString() == entry.key }
                            if (sectionIndex >= 0) {
                                listState.requestScrollToItem(appsStartIndex + sectionIndex * 2)
                            }
                        },
                        scrollInProgress = listScrollInProgress,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .padding(bottom = bottomInset + BottomBarPadding),
                    )
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
            onSelectedTypeChange = onSelectedTypeChange,
            hazeState = hazeState,
            onHeightChange = { bottomBarHeightPx = it },
        )
    }
}
/** 选择页的一行动作：组子项与单动作共用同一套渲染与行尾齿轮。 */
@Composable
private fun PickerActionRow(
    item: StoredAction,
    shape: Shape,
    selected: Boolean,
    context: android.content.Context,
    subGestures: List<SubGesture>,
    actionLibraryEntries: List<ActionLibraryEntry>,
    actionPanels: List<ActionPanel>,
    gridPanels: List<GridPanel>,
    onSelect: () -> Unit,
    onGear: () -> Unit,
) {
    val hasRowSettings = item.typeId in RowSettingsActionTypeIds && rowSettingsHasSettings(item)
    ActionItem(
        action = item,
        actionLabel = context.actionTextWithSubGesture(
            item,
            subGestures,
            actionLibraryEntries,
            actionPanels,
            gridPanels,
            emptyIfNone = false,
        ),
        selected = selected,
        onSelect = { onSelect() },
        subtitle = if (item.typeId in RowSettingsActionTypeIds) rowSettingsSubtitle(item, context) else "",
        shape = shape,
        trailing = if (hasRowSettings) {{
            PressIconButton(onClick = onGear) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }} else null,
    )
}

/**
 * 可展开组行：与动作行同规格（同高、同圆角、同图标列），但不可选中，
 * 整行点按只展开/收起；收起时副标题显示已选子项或子项数量。
 */
@Composable
private fun PickerGroupRow(
    title: String,
    icon: Any?,
    subtitle: String,
    expanded: Boolean,
    shape: Shape,
    onToggle: () -> Unit,
) {
    val rotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = tween(MotionSubtle),
        label = "pickerGroupArrow",
    )
    LunoSettingsRow(
        title = title,
        subtitle = subtitle,
        icon = icon as? ImageVector,
        textIcon = icon as? TextIcon,
        iconModel = icon?.takeIf { it !is ImageVector && it !is TextIcon },
        onClick = onToggle,
        shape = shape,
        stateDescription = stringResource(
            if (expanded) {
                hunoia.luno.design.R.string.group_state_expanded
            } else {
                hunoia.luno.design.R.string.group_state_collapsed
            },
        ),
        trailing = {
            Icon(
                modifier = Modifier
                    .size(RowIconSize)
                    .graphicsLayer { rotationZ = rotation },
                imageVector = Icons.Default.ExpandMore,
                contentDescription = null,
            )
        },
    )
}

/** 带预设变体的动作在选择页展开成具体子行；无变体原样返回。 */
private fun ActionVariant.concreteAction(
    typeId: String,
    pool: List<StoredAction>,
    selected: StoredAction?,
): StoredAction {
    // 变体只定义「身份」；时长 / 具体触钮等可编辑参数要优先沿用已选或已改过的那一条，
    // 否则每次重建列表都会把行尾设置里改过的内容丢掉。
    val sameVariant: (StoredAction) -> Boolean = { candidate ->
        candidate.typeId == typeId && candidate.params.isNotEmpty() && matches(candidate.params)
    }
    return selected?.takeIf(sameVariant)
        ?: pool.firstOrNull(sameVariant)
        ?: StoredAction.of(typeId, *params.map { it.key to it.value }.toTypedArray())
}

private sealed interface PickerNode {
    data class Group(val group: ActionGroupSpec, val children: List<StoredAction>) : PickerNode
    data class Leaf(val action: StoredAction) : PickerNode
}

/** 一个分类内的展示节点：组按 order 排在前面，其余单动作保持原顺序。 */
private fun buildPickerNodes(
    actions: List<StoredAction>,
    selected: StoredAction?,
): List<PickerNode> {
    val groups = LinkedHashMap<ActionGroupSpec, MutableList<StoredAction>>()
    val leaves = mutableListOf<StoredAction>()
    // 同一 typeId 的变体表只展开一次；行尾设置追加的具体动作不再重复建组。
    val seenVariantTypes = mutableSetOf<String>()
    actions.forEach { action ->
        val definition = ActionDefinitions.byTypeId(action.typeId)
        if (definition != null && definition.variants.isNotEmpty()) {
            if (!seenVariantTypes.add(action.typeId)) return@forEach
            definition.variants.forEach { variant ->
                val concrete = variant.concreteAction(action.typeId, actions, selected)
                val group = variant.group
                if (group != null) {
                    groups.getOrPut(group) { mutableListOf() }.add(concrete)
                } else {
                    leaves.add(concrete)
                }
            }
        } else {
            val group = definition?.group
            if (group != null) {
                groups.getOrPut(group) { mutableListOf() }.add(action)
            } else {
                leaves.add(action)
            }
        }
    }
    return groups.entries.sortedBy { it.key.order }.map { PickerNode.Group(it.key, it.value) } +
        leaves.map { PickerNode.Leaf(it) }
}

/**
 * 应用行 + 快捷方式子行：应用本体是选中项，快捷方式挂在它下面。
 * 收起时副标题给出「已选哪个快捷方式 / 一共几个」，展开后逐条列出创建器与已有快捷方式。
 */
@Composable
private fun AppShortcutBlock(
    index: Int,
    entry: AppShortcutEntry,
    expanded: Boolean,
    showToggle: Boolean,
    selectedRecord: SelectedRecord,
    onSelectApp: (AppInfo) -> Unit,
    onSelectShortcut: (LauncherInfo.ShortcutInfo) -> Unit,
    onAppLongClick: (AppInfo) -> Unit,
    onShortcutClick: (LauncherInfo) -> Unit,
    onToggleExpanded: () -> Unit,
) {
    val shortcuts = entry.shortcuts
    val selectedShortcut = shortcuts.firstOrNull { selectedRecord.isSelected(it) }
    val blockIndex = index
    val childFlags = entry.createLaunchers.map { false } + shortcuts.map { selectedRecord.isSelected(it) }
    val blockFlags = listOf(selectedRecord.isSelected(entry.app)) + childFlags
    val rowCount = if (expanded) childFlags.size + 1 else 1
    val bottomRadius by animateDpAsState(
        targetValue = if (expanded) ConnectionRadius else ContainerRadius,
        animationSpec = tween(MotionSubtle),
        label = "appShortcutBottomRadius",
    )
    val subtitle = when {
        selectedShortcut != null -> stringResource(
            R.string.action_group_selected,
            selectedShortcut.label,
        )
        shortcuts.isNotEmpty() -> stringResource(R.string.shortcut_count, shortcuts.size)
        entry.createLaunchers.isNotEmpty() -> stringResource(R.string.create_shortcut)
        else -> ""
    }
    AppShortcutRow(
        appInfo = entry.app,
        selected = selectedRecord.isSelected(entry.app),
        expanded = expanded,
        hasChildren = showToggle,
        subtitle = subtitle,
        onSelect = { onSelectApp(entry.app) },
        onToggleExpanded = onToggleExpanded,
        onLongClick = { onAppLongClick(entry.app) },
        shape = GroupItemCorners(
            topStart = ContainerRadius,
            topEnd = ContainerRadius,
            bottomStart = bottomRadius,
            bottomEnd = bottomRadius,
        ).shape(index = index),
    )
    AnimatedVisibility(
        visible = expanded,
        enter = expandVertically() + fadeIn(),
        exit = shrinkVertically() + fadeOut(),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(groupItemSpacing())) {
            entry.createLaunchers.forEachIndexed { index, launcher ->
                val childIndex = index + 1
                CreateShortcutChildRow(
                    launcherInfo = launcher,
                    onClick = { onShortcutClick(launcher) },
                    shape = selectedSegmentedCorners(
                        index = childIndex,
                        count = rowCount,
                        flags = blockFlags,
                        mergedTop = childIndex == 1,
                    ).shape(index = blockIndex + childIndex),
                )
            }
            shortcuts.forEachIndexed { child, shortcut ->
                val childIndex = entry.createLaunchers.size + child + 1
                ShortcutChildRow(
                    shortcutInfo = shortcut,
                    selected = selectedRecord.isSelected(shortcut),
                    onSelect = { onSelectShortcut(shortcut) },
                    shape = selectedSegmentedCorners(
                        index = childIndex,
                        count = rowCount,
                        flags = blockFlags,
                        mergedTop = childIndex == 1,
                    ).shape(index = blockIndex + childIndex),
                )
            }
        }
    }
}
