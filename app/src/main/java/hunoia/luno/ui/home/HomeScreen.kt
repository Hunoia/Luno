package hunoia.luno.ui.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aaron.compose.component.UDFComponent
import hunoia.luno.R
import hunoia.luno.ui.theme.BottomBarPadding
import hunoia.luno.ui.theme.ElevationSmall
import hunoia.luno.ui.theme.LargeShape
import hunoia.luno.ui.theme.FloatingContentBottom
import hunoia.luno.bridge.system.gotoAccessibilitySettings
import hunoia.luno.config.model.GestureButton
import hunoia.luno.config.model.SubGesture
import hunoia.luno.ui.facade.LocalFacades
import hunoia.luno.facade.missingHomePermissions
import hunoia.luno.ui.component.sheet.ColorPickerBottomSheet
import hunoia.luno.ui.component.sheet.ColorSelection
import hunoia.luno.ui.component.sheet.toColorSpec
import hunoia.luno.ui.component.sheet.toInitialSelection
import hunoia.luno.actions.library.ActionLibraryScreen
import hunoia.luno.ui.component.DefaultHomeDockSide
import hunoia.luno.ui.component.HomeDock
import hunoia.luno.ui.component.HomeDockSide
import hunoia.luno.ui.component.HomeDockTab
import hunoia.luno.ui.component.FloatingBottomBarDefaults
import hunoia.luno.bridge.ui.showToast
import hunoia.luno.gridpanel.habit.HabitListContent
import hunoia.luno.gridpanel.todo.TodoListContent
import hunoia.luno.gridpanel.trend.TrendListContent
import hunoia.luno.ui.navigation.PendingHomeTab
import hunoia.luno.ui.theme.PageGutter
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddChart
import androidx.compose.material.icons.filled.AddTask
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material.icons.twotone.CheckCircle
import androidx.compose.material.icons.twotone.Checklist
import androidx.compose.material.icons.twotone.ShowChart
import hunoia.luno.action.definitions.ActionDefinitions
import hunoia.luno.domain.action.ActionCatalog
import hunoia.luno.ui.navigation.ACTION_LIBRARY_ENTRY_ID
import hunoia.luno.domain.system.PermissionStateUseCase
import kotlinx.coroutines.launch
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.twotone.LibraryBooks
import androidx.compose.material.icons.twotone.Home
import androidx.compose.material.icons.twotone.Settings
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import hunoia.luno.ui.theme.ListSpacing
import hunoia.luno.ui.component.screen.HomeShell
import hunoia.luno.ui.settings.action.ActionSettingsTab

enum class MainTab(
    val icon: ImageVector,
    /** 属于哪颗导航球：左主导航 / 中记录导航。 */
    val group: HomeDockSide,
) {
    Home(Icons.TwoTone.Home, HomeDockSide.NAV),
    Habits(Icons.TwoTone.CheckCircle, HomeDockSide.SECONDARY),
    Todos(Icons.TwoTone.Checklist, HomeDockSide.SECONDARY),
    Trends(Icons.TwoTone.ShowChart, HomeDockSide.SECONDARY),
    ActionLibrary(Icons.AutoMirrored.TwoTone.LibraryBooks, HomeDockSide.NAV),
    Settings(Icons.TwoTone.Settings, HomeDockSide.NAV),
}

/** tab 的无障碍文案；标题栏标题另有映射（Home 用 home_title 即应用名）。 */
val MainTab.contentDescriptionRes: Int
    get() = when (this) {
        MainTab.Home -> R.string.home_tab
        MainTab.Habits -> hunoia.luno.gridpanel.R.string.habit_list_title
        MainTab.Todos -> hunoia.luno.gridpanel.R.string.todo_list_title
        MainTab.Trends -> hunoia.luno.gridpanel.R.string.trend_list_title
        MainTab.ActionLibrary -> hunoia.luno.actions.R.string.action_library
        MainTab.Settings -> R.string.settings
    }

/** 顶栏标题：每个页面用自己的标题，主页用应用名。 */
val MainTab.titleRes: Int
    get() = when (this) {
        MainTab.Home -> R.string.home_title
        else -> contentDescriptionRes
    }

/**
 * 导航球收起时显示哪一页，也就是点它之后会切到哪一页：
 * - 当前页就在该球组内 → 当前页（点球只展开胶囊）；
 * - 左球在组外 → 固定回主页；
 * - 中球在组外 → 回到组内上次停留的记录页。
 */
fun collapsedBallTab(
    current: MainTab,
    group: HomeDockSide,
    lastRecordTab: MainTab,
): MainTab = when {
    current.group == group -> current
    group == HomeDockSide.NAV -> MainTab.Home
    lastRecordTab.group == HomeDockSide.SECONDARY -> lastRecordTab
    else -> MainTab.Habits
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onNavToGestureButtonSettings: (GestureButton) -> Unit,
    onNavToSubGestureEditor: (String) -> Unit,
    onNavToGlobalZoneEditor: (String) -> Unit = {},
    onNavToSearchEngines: () -> Unit = {},
    onNavToFloatBallSettings: () -> Unit = {},
    onNavToNotificationCapsuleSettings: () -> Unit = {},
    onNavToSuggestionCapsuleSettings: () -> Unit = {},
    onNavToButtonVisibility: () -> Unit = {},
    onNavToActionPanels: () -> Unit = {},
    onNavToMiniWindowSettings: () -> Unit = {},
    onNavToPointerSettings: () -> Unit = {},
    onNavToAppearanceSettings: () -> Unit = {},
    onNavToLinkDispatch: () -> Unit = {},
    onNavToActionSelect: () -> Unit = {},
    onNavToPrivilegedPermissions: () -> Unit = {},
    onNavToAccessibilityManager: () -> Unit = {},
    onNavToPersistentNotificationSettings: () -> Unit = {},
    onNavToNotificationSettings: () -> Unit = {},
    onNavToStatusBarIconSettings: () -> Unit = {},
    onNavToLogSettings: () -> Unit = {},
    onNavToAutoBackup: () -> Unit = {},
    onNavToClipboard: () -> Unit = {},
    onNavToAppControl: () -> Unit = {},
    onNavToHabitEdit: (habitId: String?) -> Unit = {},
    onNavToTrendEdit: (metricId: String?) -> Unit = {},
    onNavToTrendDetail: (metricId: String) -> Unit = {},
    onNavToScreenPickSettings: () -> Unit = {},
    onNavToActionLibraryEdit: (String, String?) -> Unit = { _, _ -> },
    vmFactory: (hunoia.luno.facade.Facades) -> HomeVM = { HomeVM(it) },
) {
    val facades = LocalFacades.current
    val vm: HomeVM = viewModel { vmFactory(facades) }
    val homeListState = rememberLazyListState()
    val libraryListState = rememberLazyListState()
    val habitListState = rememberLazyListState()
    val todoListState = rememberLazyListState()
    val trendListState = rememberLazyListState()
    val navigationWindowInsets = WindowInsets.navigationBars.only(
        WindowInsetsSides.Bottom,
    )
    var mainTab by rememberSaveable { mutableStateOf(MainTab.Home) }
    // 三球停靠栏：会话内记住展开的一侧（转屏保持），杀进程回默认导航球。
    var dockSide by rememberSaveable(
        stateSaver = Saver(
            save = { it.name },
            restore = { HomeDockSide.valueOf(it) },
        ),
    ) { mutableStateOf(DefaultHomeDockSide) }
    // 中球收起时显示「组内上次停留的记录页」；离开记录组后仍然记住。
    var lastRecordTab by rememberSaveable(
        stateSaver = Saver(
            save = { it.name },
            restore = { MainTab.valueOf(it) },
        ),
    ) { mutableStateOf(MainTab.Habits) }
    // 功能球在待办页触发新建：递增一次，嵌入的待办内容消费后归零。
    var todoAddRequest by rememberSaveable { mutableIntStateOf(0) }
    var expandedGestureSection by rememberSaveable(
        stateSaver = Saver(
            save = { it?.name ?: "" },
            restore = { name -> if (name.isEmpty()) null else GesturePanelSection.valueOf(name) },
        )
    ) { mutableStateOf<GesturePanelSection?>(GesturePanelSection.TouchButton) }
    var colorPickerTarget by remember { mutableStateOf<Any?>(null) }
    val context = LocalContext.current

    // 选页统一入口：tab 决定哪颗球展开；记录页顺手更新中球的记忆。
    fun selectTab(tab: MainTab) {
        mainTab = tab
        dockSide = tab.group
        if (tab.group == HomeDockSide.SECONDARY) lastRecordTab = tab
    }

    // 桌面部件 / 动作面板 / 提醒通知要求「打开主页并停在某个 tab」。
    val pendingHomeTab by PendingHomeTab.requested.collectAsStateWithLifecycle()
    LaunchedEffect(pendingHomeTab) {
        val tab = pendingHomeTab ?: return@LaunchedEffect
        PendingHomeTab.consume()
        selectTab(tab)
    }

    DisposableEffect(Unit) {
        onDispose { vm.collapseAll() }
    }

    UDFComponent(
        component = vm.udfComponent,
        onEvent = { event ->
            when (event) {
                is UiEvent.ScrollToBottom -> {
                    val totalItems = homeListState.layoutInfo.totalItemsCount
                    if (totalItems > 0) {
                        homeListState.animateScrollToItem(
                            index = totalItems - 1,
                            scrollOffset = Int.MAX_VALUE,
                        )
                    }
                }
            }
        }
    ) { uiState ->
        val lifecycleOwner = LocalLifecycleOwner.current
        LaunchedEffect(key1 = lifecycleOwner) {
            // 服务连接就绪/断开时主动刷新状态卡：冷启动时 bind 是异步的，
            // 首次读取会误报「暂不可用」，不能等下次 ON_RESUME 才纠正。
            // repeatOnLifecycle 是挂起阻塞的，必须并行 launch 才不会饿死订阅。
            launch {
                PermissionStateUseCase.dispatchReadyFlow.collect { vm.updatePermissionState() }
            }
            lifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                vm.updatePermissionState()
                vm.refreshShizukuStatus()
                vm.onHomeResumed {
                    showToast(R.string.gesture_switch_retry_failed)
                }
            }
        }

        HomeShell(
            title = stringResource(mainTab.titleRes),
            sheets = {
                if (colorPickerTarget != null) {
                val scheme = MaterialTheme.colorScheme
                ColorPickerBottomSheet(
                    onDismissRequest = { colorPickerTarget = null },
                    onCopyColor = { hex ->
                        hunoia.luno.clipboard.ClipboardCenter.writeTextImmediate(hex, "Luno color")
                    },
                    onColorSelected = { selection ->
                        val colorSpec = selection.toColorSpec(scheme)
                        when (val target = colorPickerTarget) {
                            is GestureButton -> vm.updateGestureButtonColor(target, colorSpec)
                            is SubGesture -> vm.updateSubGestureColor(target, colorSpec)
                        }
                        colorPickerTarget = null
                    },
                    initialSelection = when (val target = colorPickerTarget) {
                        is GestureButton -> target.colorSpec.toInitialSelection(
                            scheme = scheme,
                            primaryFallback = true,
                        )
                        is SubGesture -> target.colorSpec.toInitialSelection(
                            scheme = scheme,
                            primaryFallback = true,
                        )
                        else -> ColorSelection.Custom(MaterialTheme.colorScheme.primary)
                    },
                )
                }
            },
            dialogs = {
                RenameDialog(
                    target = uiState.renameDialogTarget,
                    onDismissRequest = { vm.hideRenameDialog() },
                    onConfirm = { target, name -> vm.doRename(target, name) },
                )
            },
            bottomBar = { hazeState ->
                var showLibraryTypeMenu by remember { mutableStateOf(false) }
                // 动作库页的功能球弹动作类型菜单；离开该页时顺手关掉，避免回来时菜单还挂着。
                LaunchedEffect(mainTab) {
                    if (mainTab != MainTab.ActionLibrary) showLibraryTypeMenu = false
                }
                val navTabs = MainTab.entries.filter { it.group == HomeDockSide.NAV }
                val recordTabs = MainTab.entries.filter { it.group == HomeDockSide.SECONDARY }
                // 收起球显示哪一页，点它就是切到哪一页。
                val navBallTab = collapsedBallTab(mainTab, HomeDockSide.NAV, lastRecordTab)
                val recordBallTab = collapsedBallTab(mainTab, HomeDockSide.SECONDARY, lastRecordTab)
                val recordSelectedIndex = recordTabs.indexOf(mainTab).takeIf { it >= 0 }
                    ?: recordTabs.indexOf(lastRecordTab).coerceAtLeast(0)

                // 功能球跟随当前页：动作库弹类型菜单、记录页新建、图标用各自的「添加」；
                // 主页 / 设置没有可添加内容，整颗球隐藏。
                val functionBall = when (mainTab) {
                    MainTab.ActionLibrary -> DockFunctionBall(
                        icon = Icons.Default.Add,
                        label = stringResource(hunoia.luno.actions.R.string.action_library_add),
                        onClick = { showLibraryTypeMenu = true },
                    )

                    MainTab.Habits -> DockFunctionBall(
                        icon = Icons.Default.AddTask,
                        label = stringResource(hunoia.luno.gridpanel.R.string.habit_add),
                        onClick = { onNavToHabitEdit(null) },
                    )

                    MainTab.Todos -> DockFunctionBall(
                        icon = Icons.Default.PlaylistAdd,
                        label = stringResource(hunoia.luno.gridpanel.R.string.todo_add),
                        onClick = { todoAddRequest++ },
                    )

                    MainTab.Trends -> DockFunctionBall(
                        icon = Icons.Default.AddChart,
                        label = stringResource(hunoia.luno.gridpanel.R.string.trend_add),
                        onClick = { onNavToTrendEdit(null) },
                    )

                    MainTab.Home, MainTab.Settings -> null
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .padding(bottom = BottomBarPadding)
                        // 横屏刘海/挖孔：横向安全区也要让开，否则球会压到挖孔里。
                        .windowInsetsPadding(
                            WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal),
                        )
                        .windowInsetsPadding(navigationWindowInsets),
                ) {
                    HomeDock(
                        navTabs = navTabs.map { tab ->
                            HomeDockTab(
                                id = tab.name,
                                icon = tab.icon,
                                label = stringResource(tab.contentDescriptionRes),
                            )
                        },
                        navSelectedIndex = navTabs.indexOf(mainTab).coerceAtLeast(0),
                        onNavSelected = { index -> selectTab(navTabs[index]) },
                        navBallIcon = navBallTab.icon,
                        navBallLabel = stringResource(
                            R.string.home_dock_nav_ball,
                            stringResource(navBallTab.contentDescriptionRes),
                        ),
                        navBallClickLabel = stringResource(R.string.home_dock_nav_ball_expand),
                        onNavBallClick = { selectTab(navBallTab) },
                        secondaryTabs = recordTabs.map { tab ->
                            HomeDockTab(
                                id = tab.name,
                                icon = tab.icon,
                                label = stringResource(tab.contentDescriptionRes),
                            )
                        },
                        secondarySelectedIndex = recordSelectedIndex,
                        onSecondarySelected = { index -> selectTab(recordTabs[index]) },
                        secondaryBallIcon = recordBallTab.icon,
                        secondaryBallLabel = stringResource(
                            R.string.home_dock_record_ball,
                            stringResource(recordBallTab.contentDescriptionRes),
                        ),
                        secondaryBallClickLabel = stringResource(R.string.home_dock_record_ball_expand),
                        onSecondaryBallClick = { selectTab(recordBallTab) },
                        functionBallIcon = functionBall?.icon ?: Icons.Default.Add,
                        functionBallLabel = functionBall?.label.orEmpty(),
                        onFunctionBallClick = functionBall?.onClick ?: {},
                        showFunctionBall = functionBall != null,
                        expandedSide = dockSide,
                        hazeState = hazeState,
                        functionMenu = {
                            if (mainTab == MainTab.ActionLibrary) {
                                LibraryTypeMenu(
                                    expanded = showLibraryTypeMenu,
                                    onDismiss = { showLibraryTypeMenu = false },
                                    onSelect = { typeId ->
                                        showLibraryTypeMenu = false
                                        onNavToActionLibraryEdit(ACTION_LIBRARY_ENTRY_ID, typeId)
                                    },
                                )
                            }
                        },
                        colors = FloatingBottomBarDefaults.colors(
                            indicatorColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            activeContentColor = MaterialTheme.colorScheme.primary,
                        ),
                    )
                }
            },
        ) { topPadding, _ ->
            // 方向感知的 tab 切换动画：向右切（序号变大）内容左移，向左切反之。
            AnimatedContent(
                targetState = mainTab,
                transitionSpec = {
                    val forward = targetState.ordinal > initialState.ordinal
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
                label = "homeTabs",
            ) { tab ->
                when (tab) {
                        MainTab.Home -> LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            state = homeListState,
                            contentPadding = PaddingValues(
                                start = PageGutter,
                                top = topPadding,
                                end = PageGutter,
                                bottom = FloatingContentBottom,
                            ),
                            verticalArrangement = Arrangement.spacedBy(ListSpacing),
                        ) {
                            item(key = "runtime_status") {
                                HomeRuntimeStatusCard(
                                    runtimeStatus = uiState.runtimeStatus,
                                    isGestureSwitchEnabled = uiState.isGestureSwitchEnabled,
                                    onGestureSwitchEnabledChange = { enabled ->
                                        vm.onGestureSwitchChange(enabled) { context.gotoAccessibilitySettings() }
                                    },
                                )
                            }

                            item(key = "permission_status") {
                                // 权限状态卡：一行摘要 + 一键授权；无缺失时不组合（不占空间）。
                                val missing = uiState.privilegedPermissionStatus.missingHomePermissions()
                                if (missing.isNotEmpty()) {
                                    HomePermissionStatusCard(
                                        missingCount = missing.size,
                                        shizukuReady = uiState.shizukuStatus.isReady,
                                        isGranting = uiState.isGrantingPermissions,
                                        onOpen = onNavToPrivilegedPermissions,
                                        onGrant = {
                                            // Shizuku 不可用时跳授权页：修 Shizuku 的入口在那里。
                                            if (uiState.shizukuStatus.isReady) {
                                                vm.grantMissingHomePermissions()
                                            } else {
                                                onNavToPrivilegedPermissions()
                                            }
                                        },
                                    )
                                }
                            }

                            item(key = "settings_group") {
                                HomeSettingsGroup(
                                    onClipboardClick = onNavToClipboard,
                                    onAppControlClick = onNavToAppControl,
                                )
                            }

                            item(key = "gesture_panel") {
                                GesturePanel(
                                    gestureButtons = uiState.gestureButtons,
                                    subGestures = uiState.subGestures,
                                    globalZones = uiState.globalZones,
                                    expandedSection = expandedGestureSection,
                                    onExpandedChange = { expandedGestureSection = it },
                                    onGestureButtonClick = onNavToGestureButtonSettings,
                                    onSubGestureClick = onNavToSubGestureEditor,
                                    onGlobalZoneClick = onNavToGlobalZoneEditor,
                                    onGestureCheckedChange = { button, enabled -> vm.onGestureButtonEnabledChange(button, enabled) },
                                    onSubCheckedChange = { gesture, enabled -> vm.onSubGestureEnabledChange(gesture, enabled) },
                                    onGlobalZoneCheckedChange = { zone, enabled ->
                                        vm.onGlobalZoneEnabledChange(zone, enabled)
                                    },
                                    onGlobalZoneRename = { zone ->
                                        vm.showRenameDialog(RenameTarget.GlobalZone(zone = zone))
                                    },
                                    onAddGesture = {
                                        // 点「+」添加后展开当前列，让新触钮立即可见。
                                        expandedGestureSection = GesturePanelSection.TouchButton
                                        vm.addGestureButton()
                                    },
                                    onAddSub = {
                                        val id = java.util.UUID.randomUUID().toString()
                                        // 点「+」添加后展开当前列，让新子手势立即可见。
                                        expandedGestureSection = GesturePanelSection.SubGesture
                                        vm.addSubGesture(id)
                                    },
                                    onAddGlobalZone = {
                                        // 点「+」添加后展开当前列，让新识别区立即可见。
                                        expandedGestureSection = GesturePanelSection.GlobalZone
                                        vm.addGlobalZone()
                                    },
                                    onMarkColorClick = { target ->
                                        colorPickerTarget = target
                                    },
                                    onGestureButtonRename = { button ->
                                        vm.showRenameDialog(RenameTarget.GestureButton(button = button))
                                    },
                                    onSubGestureRename = { gesture ->
                                        vm.showRenameDialog(RenameTarget.SubGesture(gesture = gesture))
                                    },
                                )
                            }

                item(key = "data_transfer") {
                    DataTransferGroup(
                        onBackupSettingsClick = onNavToAutoBackup,
                        onActionSelectClick = onNavToActionSelect,
                        onLogSettingsClick = onNavToLogSettings,
                    )
                }
                        }

                        MainTab.Habits -> HabitListContent(
                            topPadding = topPadding,
                            onEditHabit = onNavToHabitEdit,
                            state = habitListState,
                        )

                        MainTab.Todos -> TodoListContent(
                            topPadding = topPadding,
                            addRequest = todoAddRequest,
                            onAddRequestHandled = { todoAddRequest = 0 },
                            state = todoListState,
                        )

                        MainTab.Trends -> TrendListContent(
                            topPadding = topPadding,
                            onOpenMetric = onNavToTrendDetail,
                            state = trendListState,
                        )

                        MainTab.ActionLibrary -> ActionLibraryScreen(
                            listState = libraryListState,
                            onNavToEdit = onNavToActionLibraryEdit,
                            contentPadding = PaddingValues(top = topPadding),
                        )

                        MainTab.Settings -> ActionSettingsTab(
                            topPadding = topPadding,
                            onNavToActionPanels = onNavToActionPanels,
                            onNavToSearchEngines = onNavToSearchEngines,
                            onNavToFloatBallSettings = onNavToFloatBallSettings,
                            onNavToNotificationCapsuleSettings = onNavToNotificationCapsuleSettings,
                            onNavToSuggestionCapsuleSettings = onNavToSuggestionCapsuleSettings,
                            onNavToLinkDispatch = onNavToLinkDispatch,
                            onNavToButtonVisibility = onNavToButtonVisibility,
                            onNavToMiniWindowSettings = onNavToMiniWindowSettings,
                            onNavToPointerSettings = onNavToPointerSettings,
                            onNavToScreenPickSettings = onNavToScreenPickSettings,
                            onNavToPrivilegedPermissions = onNavToPrivilegedPermissions,
                            onNavToAccessibilityManager = onNavToAccessibilityManager,
                            onNavToPersistentNotificationSettings = onNavToPersistentNotificationSettings,
                            onNavToNotificationSettings = onNavToNotificationSettings,
                            onNavToStatusBarIconSettings = onNavToStatusBarIconSettings,
                            onNavToAppearanceSettings = onNavToAppearanceSettings,
                        )
                    }
                }
        }
    }
    }

/** 底栏功能球：当前页的「添加」动作；没有可添加内容的页面为 null（整颗球隐藏）。 */
private class DockFunctionBall(
    val icon: ImageVector,
    val label: String,
    val onClick: () -> Unit,
)

/** 动作库 + 号的类型菜单：DropdownMenu（同规则页优先级菜单风格），锚定 FAB。 */
@Composable
private fun LibraryTypeMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit,
) {
    val grouped = remember(expanded) {
        ActionCatalog.libraryDefinitions()
            .sortedBy { ActionDefinitions.categoryOrder(it.category) }
    }
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = LargeShape,
        tonalElevation = ElevationSmall,
        shadowElevation = 0.dp,
    ) {
        grouped.forEach { def ->
            DropdownMenuItem(
                text = { Text(def.name) },
                leadingIcon = {
                    Icon(def.icon, contentDescription = null)
                },
                onClick = { onSelect(def.typeId) },
            )
        }
    }
}
