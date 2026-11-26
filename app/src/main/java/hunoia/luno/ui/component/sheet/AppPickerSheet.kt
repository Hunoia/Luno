package hunoia.luno.ui.component.sheet
import hunoia.luno.ui.component.AppSearchBar
import hunoia.luno.ui.component.feedback.EmptyState
import hunoia.luno.ui.component.feedback.LoadingState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import hunoia.luno.R
import hunoia.luno.bridge.system.gotoAppDetailSettings
import hunoia.luno.core.AppContext
import hunoia.luno.quickaccess.QuickLaunchFacade
import hunoia.luno.core.domain.AppInfo
import hunoia.luno.quickaccess.model.icon
import hunoia.luno.quickaccess.query.AppDrawerSections
import hunoia.luno.quickaccess.query.AlphabeticalSection
import hunoia.luno.quickaccess.query.SystemAppsVisibility
import hunoia.luno.ui.permission.rememberGetInstalledAppsPermissionState
import hunoia.luno.ui.theme.PageGutter
import hunoia.luno.ui.theme.CardInnerSpacing
import hunoia.luno.ui.theme.ListSpacing
import hunoia.luno.ui.theme.SheetListMaxHeight
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import hunoia.luno.ui.component.IndexBar
import hunoia.luno.ui.component.IndexBarEntry
import hunoia.luno.ui.component.IndexBarMetrics
import hunoia.luno.ui.component.IndexBarSide
import hunoia.luno.ui.component.row.LunoSectionTitle
import hunoia.luno.ui.component.rememberIndexBarMetrics
import hunoia.luno.ui.component.row.LunoGroup
import hunoia.luno.ui.component.row.LunoSelectableRow
import hunoia.luno.ui.component.row.selectedSegmentedShape
import hunoia.luno.ui.component.feedback.LunoIconButton
import hunoia.luno.ui.component.PressTextButton
import hunoia.luno.ui.component.SystemAppsMenuButton

/**
 * 应用选择 sheet（单选）：点一行即确认并关闭（[onConfirm] / [onSelectApp]），无底部确认/取消。
 *
 * 列表按拼音字母分节（A–#、节内全拼序），风格跟随最近应用黑名单页：
 * [LunoGroup] 分组 + 吸顶字母头 [LunoSectionTitle]，左右各一条竖向拼音滑杆
 * [IndexBar]（参考应用抽屉部件），贴底限高 [SheetIndexBarMaxHeight]、覆盖在列表上
 * 不改变列表间距。
 *
 * 多选走全屏页 [AppPickerPage]（黑名单同款），由 [LunoAppPicker] 按
 * [AppPickerRequest.confirmOnSelect] 分派；本 sheet 只服务单选。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AppPickerSheet(
    onDismissRequest: () -> Unit,
    selectedPackageNames: List<String>,
    onConfirm: (List<String>) -> Unit,
    /** 单选场景（含分身 userId）：点击行直接把完整 AppInfo 交给调用方。 */
    onSelectApp: ((AppInfo) -> Unit)? = null,
    /** 与 [onSelectApp] 搭配：只高亮这个用户下的那一行，避免主应用/分身同时选中。 */
    selectedUserId: Int? = null,
) {
    val scope = rememberCoroutineScope()
    var apps by remember { mutableStateOf<List<AppInfo>>(emptyList()) }
    val selected = remember(selectedPackageNames) { selectedPackageNames.toSet() }
    var searchQuery by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var loaded by remember { mutableStateOf(false) }
    // 页内内存开关：默认关、不记忆；两类（无界面组件 / 有界面无入口）各自独立，展开重查。
    var systemVisibility by remember { mutableStateOf(SystemAppsVisibility()) }
    val context = LocalContext.current
    val permissionState = rememberGetInstalledAppsPermissionState { granted ->
        if (granted) {
            // 刚授权：清单可能已被缓存成空表，清缓存后再触发一次重读。
            QuickLaunchFacade.invalidateAppListCaches(AppContext.get())
            loaded = false
        }
    }
    suspend fun loadApps() {
        loading = true
        apps = withContext(Dispatchers.IO) {
            QuickLaunchFacade.queryAppsIncludingDisabled(
                AppContext.get(),
                systemVisibility = systemVisibility,
            )
        }
        loading = false
    }
    LaunchedEffect(permissionState.isGranted, loaded) {
        if (!permissionState.isGranted) {
            // 被永久拒绝后再申请不会弹框，留给用户点空态里的按钮去设置。
            if (!permissionState.deniedForever) permissionState.launchPermissionRequest()
        } else if (!loaded) {
            loadApps()
            loaded = true
        }
    }
    val filteredApps = remember(searchQuery, apps) {
        if (searchQuery.isBlank()) apps
        else apps.filter {
            it.label.contains(searchQuery, ignoreCase = true) ||
                it.packageName.contains(searchQuery, ignoreCase = true)
        }
    }
    LunoSheet(
        onDismissRequest = onDismissRequest,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = PageGutter),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = CardInnerSpacing),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(CardInnerSpacing),
            ) {
                Text(
                    modifier = Modifier.weight(1f),
                    text = stringResource(R.string.app_picker_select_title),
                    style = MaterialTheme.typography.titleLarge,
                )
                if (permissionState.isGranted) {
                    SystemAppsMenuButton(
                        showUILess = systemVisibility.showUILess,
                        showNoEntry = systemVisibility.showNoEntry,
                        onShowUILessChange = { enabled ->
                            systemVisibility = systemVisibility.copy(showUILess = enabled)
                            scope.launch { loadApps() }
                        },
                        onShowNoEntryChange = { enabled ->
                            systemVisibility = systemVisibility.copy(showNoEntry = enabled)
                            scope.launch { loadApps() }
                        },
                    )
                    LunoIconButton(
                        icon = Icons.Default.Refresh,
                        contentDescription = stringResource(R.string.refresh),
                        onClick = { scope.launch { loadApps() } },
                    )
                }
            }
            if (!permissionState.isGranted) {
                EmptyState(
                    icon = Icons.Outlined.Lock,
                    message = stringResource(R.string.apps_permission_required),
                    action = {
                        PressTextButton(
                            onClick = {
                                if (permissionState.deniedForever) context.gotoAppDetailSettings()
                                else permissionState.launchPermissionRequest()
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
            } else {
                AppSearchBar(
                    query = searchQuery,
                    onQueryChange = { searchQuery = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = CardInnerSpacing),
                    placeholder = stringResource(R.string.search_app_hint),
                )
                if (loading) {
                    LoadingState(message = stringResource(R.string.loading))
                } else {
                    val sections = remember(filteredApps) {
                        AppDrawerSections.alphabeticalSectionsOf(filteredApps) { it.label }
                    }
                    val listState = rememberLazyListState()
                    val barEntries = remember(sections) {
                        sections.map { IndexBarEntry(it.letter.toString(), it.letter.toString()) }
                    }
                    val indexMetrics = rememberIndexBarMetrics(
                        entries = barEntries,
                        availableHeight = SheetIndexBarMaxHeight,
                        maxWindow = 5,
                    )
                    val jumpTo = { entry: IndexBarEntry ->
                        val sectionIndex = sections.indexOfFirst {
                            it.letter.toString() == entry.key
                        }
                        if (sectionIndex >= 0) listState.requestScrollToItem(sectionIndex * 2)
                    }
                    Box(modifier = Modifier.fillMaxWidth()) {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = SheetListMaxHeight),
                            contentPadding = PaddingValues(bottom = CardInnerSpacing),
                            verticalArrangement = Arrangement.spacedBy(ListSpacing),
                        ) {
                            if (filteredApps.isEmpty()) {
                                item {
                                    EmptyState(
                                        message = stringResource(hunoia.luno.design.R.string.no_matching_results),
                                    )
                                }
                            } else {
                                sections.forEach { section ->
                                    stickyHeader(key = "app_picker_hdr_${section.letter}") {
                                        LunoSectionTitle(section.letter.toString())
                                    }
                                    item(key = "app_picker_body_${section.letter}") {
                                        LunoGroup {
                                            val sectionFlags = section.items.map {
                                                it.packageName in selected &&
                                                    (selectedUserId == null || it.userId == selectedUserId)
                                            }
                                            section.items.forEachIndexed { index, item ->
                                                val highlight = item.packageName in selected &&
                                                    (selectedUserId == null || item.userId == selectedUserId)
                                                LunoSelectableRow(
                                                    title = item.label,
                                                    subtitle = if (item.isClone) {
                                                        "${item.packageName} · ${stringResource(hunoia.luno.actions.R.string.app_clone_label)}"
                                                    } else {
                                                        item.packageName
                                                    },
                                                    iconModel = item.icon,
                                                    selected = highlight,
                                                    onSelectedChange = {
                                                        if (onSelectApp != null) {
                                                            onSelectApp(item)
                                                            onDismissRequest()
                                                        } else {
                                                            onConfirm(listOf(item.packageName))
                                                            onDismissRequest()
                                                        }
                                                    },
                                                    shape = selectedSegmentedShape(
                                                        index,
                                                        section.items.size,
                                                        sectionFlags,
                                                    ),
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        if (sections.isNotEmpty()) {
                            SheetIndexBar(
                                sections = sections,
                                listState = listState,
                                entries = barEntries,
                                metrics = indexMetrics,
                                side = IndexBarSide.Start,
                                onJump = jumpTo,
                                modifier = Modifier.align(Alignment.BottomStart),
                            )
                            SheetIndexBar(
                                sections = sections,
                                listState = listState,
                                entries = barEntries,
                                metrics = indexMetrics,
                                side = IndexBarSide.End,
                                onJump = jumpTo,
                                modifier = Modifier.align(Alignment.BottomEnd),
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * 竖向拼音滑杆：贴底、限高 [SheetIndexBarMaxHeight]，覆盖在列表上（不改列表间距）。
 * 当前字母由列表滚动位置推导，用 [derivedStateOf] 把读取隔离在本子树，避免滚动时
 * 重排整个列表。
 */
@Composable
private fun SheetIndexBar(
    sections: List<AlphabeticalSection<AppInfo>>,
    listState: LazyListState,
    entries: List<IndexBarEntry>,
    metrics: IndexBarMetrics,
    side: IndexBarSide,
    onJump: (IndexBarEntry) -> Unit,
    modifier: Modifier = Modifier,
) {
    // 每节 = 吸顶字母头 + 一张 LunoGroup，共 2 个 item，故节号 = 首可见 item / 2。
    val activeKey by remember(sections, listState) {
        derivedStateOf { sections.getOrNull(listState.firstVisibleItemIndex / 2)?.letter?.toString() }
    }
    Box(modifier = modifier.heightIn(max = SheetIndexBarMaxHeight)) {
        IndexBar(
            entries = entries,
            metrics = metrics,
            activeKey = activeKey,
            side = side,
            onJump = onJump,
        )
    }
}

/** 紧凑型竖向滑杆的限高（贴底覆盖，不放不下就窗口模式）。 */
private val SheetIndexBarMaxHeight = 160.dp
