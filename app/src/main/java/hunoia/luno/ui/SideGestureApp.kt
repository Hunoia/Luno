package hunoia.luno.ui

import androidx.compose.animation.AnimatedContentScope
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavDeepLink
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import hunoia.luno.R
import hunoia.luno.ui.theme.AnimNavTransition
import kotlinx.coroutines.launch

import hunoia.luno.picker.ActionSelect
import hunoia.luno.picker.FloatBallSlot

import hunoia.luno.gesture.settings.GestureButtonSettings

import hunoia.luno.ui.navigation.Home
import hunoia.luno.ui.navigation.LogSettings
import hunoia.luno.ui.navigation.AutoBackupSettings
import hunoia.luno.ui.navigation.Clipboard
import hunoia.luno.ui.navigation.ScreenPickSettings
import hunoia.luno.ui.navigation.NotificationBlocklist
import hunoia.luno.ui.navigation.PrivilegedPermissions
import hunoia.luno.ui.navigation.AccessibilityManager
import hunoia.luno.ui.navigation.PersistentNotificationSettings
import hunoia.luno.ui.navigation.HabitReminderSettings
import hunoia.luno.ui.navigation.StatusBarIconSettings
import hunoia.luno.ui.navigation.RecentAppsBlocklist
import hunoia.luno.ui.navigation.LinkDispatchRules
import hunoia.luno.ui.navigation.LinkDispatchRuleEdit
import hunoia.luno.ui.navigation.ActionPanelList
import hunoia.luno.ui.navigation.ActionPanelEdit
import hunoia.luno.ui.navigation.GridPanelList
import hunoia.luno.ui.navigation.GridPanelEdit
import hunoia.luno.ui.navigation.HabitList
import hunoia.luno.ui.navigation.HabitEdit
import hunoia.luno.ui.navigation.TodoList
import hunoia.luno.ui.navigation.SearchEngineSettings
import hunoia.luno.ui.navigation.SearchEngineEdit
import hunoia.luno.ui.navigation.SearchSourceSettings
import hunoia.luno.ui.navigation.MiniWindowSettings
import hunoia.luno.ui.navigation.MiniWindowAdjust
import hunoia.luno.ui.navigation.FloatBallEdit
import hunoia.luno.ui.navigation.ButtonVisibility
import hunoia.luno.ui.navigation.ButtonVisibilityEdit
import hunoia.luno.floatball.settings.FloatBallEditScreen
import hunoia.luno.gesture.settings.visibility.ButtonVisibilityEditScreen
import hunoia.luno.gesture.settings.visibility.ButtonVisibilityScreen
import hunoia.luno.gesture.settings.SubGestureEditor

import hunoia.luno.ui.navigation.ActionLibraryEdit

import hunoia.luno.picker.ActionSelectContent
import hunoia.luno.actions.library.ActionLibraryEditScreen
import hunoia.luno.ui.settings.action.LogSettingsScreen
import hunoia.luno.ui.settings.backup.AutoBackupSettingsScreen
import hunoia.luno.clipboard.ClipboardScreen
import hunoia.luno.ui.settings.action.ScreenPickSettingsScreen
import hunoia.luno.ui.settings.action.RecentAppsBlocklistScreen
import hunoia.luno.ui.settings.accessibility.AccessibilityManagerScreen
import hunoia.luno.ui.settings.notification.PersistentNotificationSettingsScreen
import hunoia.luno.ui.settings.habit.HabitReminderSettingsScreen
import hunoia.luno.ui.settings.statusbar.StatusBarIconSettingsScreen
import hunoia.luno.ui.settings.permission.PrivilegedPermissionsScreen
import hunoia.luno.ui.settings.action.MiniWindowAdjustScreen
import hunoia.luno.ui.settings.action.MiniWindowSettingsScreen
import hunoia.luno.link.settings.LinkDispatchEditScreen
import hunoia.luno.link.settings.LinkDispatchSettingsScreen
import hunoia.luno.floatball.settings.FloatBallEditScreen
import hunoia.luno.ui.settings.action.panel.ActionPanelEditScreen
import hunoia.luno.ui.settings.action.panel.ActionPanelListScreen
import hunoia.luno.ui.settings.action.gridpanel.GridPanelEditScreen
import hunoia.luno.ui.settings.action.gridpanel.GridPanelListScreen
import hunoia.luno.gridpanel.habit.HabitEditScreen
import hunoia.luno.gridpanel.habit.HabitListScreen
import hunoia.luno.gridpanel.todo.TodoListScreen
import hunoia.luno.quickaccess.settings.SearchEngineSettingsScreen
import hunoia.luno.quickaccess.settings.SearchEngineEditScreen
import hunoia.luno.quickaccess.settings.SearchSourceSettingsScreen

import hunoia.luno.gesture.settings.button.GestureButtonSettingsScreen

import hunoia.luno.gesture.settings.subgesture.SubGestureSettingsScreen
import hunoia.luno.domain.action.ActionLibrary
import hunoia.luno.core.domain.ActionLibrarySettings
import hunoia.luno.ui.theme.SideGestureTheme
import hunoia.luno.ui.navigation.LocalActionLibraryEntries
import hunoia.luno.ui.navigation.LocalActionPanels
import hunoia.luno.ui.navigation.LocalGridPanels
import hunoia.luno.ui.navigation.LocalSubGestures
import hunoia.luno.ui.navigation.LocalNavController
import hunoia.luno.ui.navigation.PendingHabitEdit
import hunoia.luno.ui.navigation.PendingLinkRule
import hunoia.luno.gridpanel.todo.TodoListRequests
import hunoia.luno.ui.home.HomeScreen
import hunoia.luno.domain.tune.TuneConfig
import hunoia.luno.domain.gesture.GestureConfig
import hunoia.luno.config.model.SubGestureSettings
import hunoia.luno.config.model.ActionSettings as ActionTuning
import kotlin.reflect.KType
import hunoia.luno.ui.component.sheet.LunoAppPicker
import hunoia.luno.ui.component.row.LocalListStyle
import hunoia.luno.config.model.AdvancedSettings



@Composable
fun SideGestureApp(navController: NavHostController = rememberNavController()) {
    SideGestureTheme {
        val durationMs = ANIMATION_DURATION_MS
        val actionLibrarySettings by ActionLibrary.settings
            .collectAsStateWithLifecycle(initialValue = ActionLibrarySettings())
        val actionSettings by TuneConfig.action
            .collectAsStateWithLifecycle(initialValue = ActionTuning())
        val subGestureSettings by GestureConfig.subGestures
            .collectAsStateWithLifecycle(initialValue = SubGestureSettings())
        // 设置列表视觉风格：全局开关，LunoGroup 读取后决定分段式 / 大卡分割线。
        val advancedSettings by TuneConfig.advanced
            .collectAsStateWithLifecycle(initialValue = AdvancedSettings())
        // 识屏卡片「新建分发规则」：跨进程 Intent → [PendingLinkRule]，消费一次就跳编辑页。
        val pendingRulePattern by PendingLinkRule.pattern.collectAsStateWithLifecycle()
        LaunchedEffect(pendingRulePattern) {
            val pattern = pendingRulePattern ?: return@LaunchedEffect
            PendingLinkRule.consume()
            navController.navigate(LinkDispatchRuleEdit("", pattern)) { launchSingleTop = true }
        }
        // 习惯面板部件「打开编辑页」：跨进程 Intent → [PendingHabitEdit]，消费一次就跳对应编辑页。
        val pendingHabitEditId by PendingHabitEdit.habitId.collectAsStateWithLifecycle()
        LaunchedEffect(pendingHabitEditId) {
            val habitId = pendingHabitEditId ?: return@LaunchedEffect
            PendingHabitEdit.consume()
            navController.navigate(HabitEdit(habitId)) { launchSingleTop = true }
        }
        // 代办面板部件「打开代办列表页」：跨进程 Intent → [TodoListRequests]，导航到列表页。
        // consume 由列表页完成（它读 todoId 决定开添加/编辑弹窗）；这里只负责导航。
        val pendingTodoList by TodoListRequests.open.collectAsStateWithLifecycle()
        LaunchedEffect(pendingTodoList) {
            if (pendingTodoList == null) return@LaunchedEffect
            navController.navigate(TodoList) { launchSingleTop = true }
        }
        CompositionLocalProvider(
            LocalNavController provides navController,
            LocalActionLibraryEntries provides actionLibrarySettings.entries,
            LocalActionPanels provides actionSettings.actionPanels.panels,
            LocalGridPanels provides actionSettings.actionGridPanels.panels,
            LocalSubGestures provides subGestureSettings.subGestures,
            LocalListStyle provides advancedSettings.listStyle,
        ) {
            Surface(modifier = Modifier.fillMaxSize()) {
                NavHost(
                    modifier = Modifier.fillMaxSize(),
                navController = navController,
                startDestination = Home,
                enterTransition = {
                    fadeIn(animationSpec = tween(durationMs)) +
                        slideInHorizontally(animationSpec = tween(durationMs)) { it / 4 }
                },
                exitTransition = {
                    fadeOut(animationSpec = tween(durationMs)) +
                        slideOutHorizontally(animationSpec = tween(durationMs)) { -it / 4 }
                },
                popEnterTransition = {
                    fadeIn(animationSpec = tween(durationMs)) +
                        slideInHorizontally(animationSpec = tween(durationMs)) { -it / 4 }
                },
                popExitTransition = {
                    fadeOut(animationSpec = tween(durationMs)) +
                        slideOutHorizontally(animationSpec = tween(durationMs)) { it / 4 }
                }
            ) {
                myComposable<Home> {
                    HomeScreen(
                        onNavToGestureButtonSettings = { button ->
                            navController.navigate(GestureButtonSettings(button.id))
                        },
                        onNavToSubGestureEditor = { subGestureId ->
                            navController.navigate(SubGestureEditor(subGestureId))
                        },
                        onNavToSearchEngines = { navController.navigate(SearchEngineSettings) },
                        onNavToFloatBallSettings = { navController.navigate(FloatBallEdit) },
                        onNavToButtonVisibility = { navController.navigate(ButtonVisibility) },
                        onNavToActionPanels = { navController.navigate(ActionPanelList) },
                        onNavToGridPanels = { navController.navigate(GridPanelList) },
                        onNavToMiniWindowSettings = { navController.navigate(MiniWindowSettings) },
                        onNavToLinkDispatch = { navController.navigate(LinkDispatchRules) },
                        onNavToActionSelect = {
                            navController.navigate(ActionSelect(copyDeepLink = true))
                        },
                        onNavToPrivilegedPermissions = {
                            navController.navigate(PrivilegedPermissions)
                        },
                        onNavToAccessibilityManager = {
                            navController.navigate(AccessibilityManager)
                        },
                        onNavToPersistentNotificationSettings = {
                            navController.navigate(PersistentNotificationSettings)
                        },
                        onNavToHabitReminderSettings = {
                            navController.navigate(HabitReminderSettings)
                        },
                        onNavToStatusBarIconSettings = {
                            navController.navigate(StatusBarIconSettings)
                        },
                        onNavToLogSettings = {
                            navController.navigate(LogSettings)
                        },
                        onNavToAutoBackup = {
                            navController.navigate(AutoBackupSettings)
                        },
                        onNavToClipboard = {
                            navController.navigate(Clipboard)
                        },
                        onNavToHabits = {
                            navController.navigate(HabitList)
                        },
                        onNavToTodos = {
                            navController.navigate(TodoList)
                        },
                        onNavToScreenPickSettings = {
                            navController.navigate(ScreenPickSettings)
                        },
                        onNavToActionLibraryEdit = { entryId, typeId ->
                            navController.navigate(ActionLibraryEdit(entryId, typeId))
                        },
                    )
                }
                myComposable<GestureButtonSettings> {
                    GestureButtonSettingsScreen(
                        onBack = { navController.navigateUp() },
                        onNavToActionSelect = { target ->
                            navController.navigate(
                                ActionSelect(
                                    gestureButtonId = target.gestureButtonId,
                                    slot = target.slot,
                                    directionLabel = target.directionLabel,
                                    triggerType = target.triggerType,
                                    subGestureId = target.subGestureId,
                                ),
                            )
                        },
                    )
                }
                myComposable<ActionSelect> {
                    ActionSelectContent(
                        onDismissRequest = { navController.popBackStack() },
                        actionSelect = it.toRoute()
                    )
                }
                myComposable<ScreenPickSettings> {
                    ScreenPickSettingsScreen(onBack = { navController.popBackStack() })
                }
                myComposable<LogSettings> {
                    LogSettingsScreen(onBack = { navController.popBackStack() })
                }
                myComposable<AutoBackupSettings> {
                    AutoBackupSettingsScreen(onBack = { navController.popBackStack() })
                }
                myComposable<Clipboard> {
                    ClipboardScreen(onBack = { navController.popBackStack() })
                }
                myComposable<LinkDispatchRules> {
                    LinkDispatchSettingsScreen(
                        onBack = { navController.popBackStack() },
                        onEditRule = { ruleId ->
                            navController.navigate(LinkDispatchRuleEdit(ruleId))
                        },
                        onCreateRule = {
                            navController.navigate(LinkDispatchRuleEdit(""))
                        },
                    )
                }
                myComposable<LinkDispatchRuleEdit> {
                    LinkDispatchEditScreen(
                        ruleId = it.toRoute<LinkDispatchRuleEdit>().ruleId,
                        prefillPattern = it.toRoute<LinkDispatchRuleEdit>().pattern,
                        onBack = { navController.popBackStack() },
                        appPicker = { request -> LunoAppPicker(request) },
                    )
                }
                myComposable<RecentAppsBlocklist> {
                    val scope = rememberCoroutineScope()
                    val actionSettings by TuneConfig.action
                        .collectAsStateWithLifecycle(initialValue = ActionTuning())
                    RecentAppsBlocklistScreen(
                        onBack = { navController.popBackStack() },
                        current = actionSettings.recentAppsBlocklist,
                        onSave = { ordered ->
                            scope.launch {
                                TuneConfig.updateAction { it.copy(recentAppsBlocklist = ordered) }
                                navController.popBackStack()
                            }
                        },
                    )
                }
                myComposable<PrivilegedPermissions> {
                    PrivilegedPermissionsScreen(
                        onBack = { navController.popBackStack() },
                        onNavToAccessibilityManager = {
                            navController.navigate(AccessibilityManager)
                        },
                    )
                }
                myComposable<AccessibilityManager> {
                    AccessibilityManagerScreen(onBack = { navController.popBackStack() })
                }
                myComposable<PersistentNotificationSettings> {
                    PersistentNotificationSettingsScreen(onBack = { navController.popBackStack() })
                }
                myComposable<HabitReminderSettings> {
                    HabitReminderSettingsScreen(onBack = { navController.popBackStack() })
                }
                myComposable<StatusBarIconSettings> {
                    StatusBarIconSettingsScreen(onBack = { navController.popBackStack() })
                }
                myComposable<MiniWindowSettings> {
                    MiniWindowSettingsScreen(
                        onBack = { navController.popBackStack() },
                        onAdjustBounds = { navController.navigate(MiniWindowAdjust) },
                    )
                }
                myComposable<MiniWindowAdjust> {
                    MiniWindowAdjustScreen(onBack = { navController.popBackStack() })
                }
                myComposable<FloatBallEdit> {
                    FloatBallEditScreen(
                        onBack = { navController.popBackStack() },
                        onEditTapAction = {
                            navController.navigate(ActionSelect(floatBallSlot = FloatBallSlot.TAP))
                        },
                        onNavToNotificationBlocklist = { navController.navigate(NotificationBlocklist) },
                    )
                }
                myComposable<ButtonVisibility> {
                    ButtonVisibilityScreen(
                        onBack = { navController.popBackStack() },
                        onEdit = { profileId ->
                            navController.navigate(ButtonVisibilityEdit(profileId))
                        },
                        onNavToRecentAppsBlocklist = { navController.navigate(RecentAppsBlocklist) },
                    )
                }
                myComposable<ButtonVisibilityEdit> {
                    ButtonVisibilityEditScreen(
                        profileId = it.toRoute<ButtonVisibilityEdit>().profileId,
                        onBack = { navController.popBackStack() },
                        appPicker = { request -> LunoAppPicker(request) },
                    )
                }
                myComposable<NotificationBlocklist> {
                    val scope = rememberCoroutineScope()
                    val actionSettings by TuneConfig.action
                        .collectAsStateWithLifecycle(initialValue = ActionTuning())
                    RecentAppsBlocklistScreen(
                        onBack = { navController.popBackStack() },
                        title = stringResource(R.string.notification_blocklist),
                        current = actionSettings.floatBall.notificationCapsule.blocklist,
                        onSave = { ordered ->
                            scope.launch {
                                TuneConfig.updateAction {
                                    it.copy(
                                        floatBall = it.floatBall.copy(
                                            notificationCapsule = it.floatBall.notificationCapsule.copy(
                                                blocklist = ordered,
                                            ),
                                        ),
                                    )
                                }
                                navController.popBackStack()
                            }
                        },
                    )
                }
                myComposable<ActionPanelList> {
                    ActionPanelListScreen(
                        onBack = { navController.popBackStack() },
                        onEditPanel = { panelId -> navController.navigate(ActionPanelEdit(panelId)) },
                    )
                }
                myComposable<ActionPanelEdit> {
                    ActionPanelEditScreen(
                        panelId = it.toRoute<ActionPanelEdit>().panelId,
                        onBack = { navController.popBackStack() },
                        onPickItem = { navController.navigate(it) },
                    )
                }
                myComposable<GridPanelList> {
                    GridPanelListScreen(
                        onBack = { navController.popBackStack() },
                        onEditPanel = { panelId -> navController.navigate(GridPanelEdit(panelId)) },
                    )
                }
                myComposable<GridPanelEdit> {
                    GridPanelEditScreen(
                        panelId = it.toRoute<GridPanelEdit>().panelId,
                        onBack = { navController.popBackStack() },
                    )
                }
                myComposable<HabitList> {
                    HabitListScreen(
                        onBack = { navController.popBackStack() },
                        onEditHabit = { habitId ->
                            navController.navigate(HabitEdit(habitId ?: ""))
                        },
                    )
                }
                myComposable<HabitEdit> {
                    HabitEditScreen(
                        habitId = it.toRoute<HabitEdit>().habitId.takeIf { h -> h.isNotBlank() },
                        onBack = { navController.popBackStack() },
                    )
                }
                myComposable<TodoList> {
                    TodoListScreen(
                        onBack = { navController.popBackStack() },
                    )
                }
                myComposable<SearchEngineSettings> {
                    SearchEngineSettingsScreen(
                        onBack = { navController.popBackStack() },
                        onNavToSources = { navController.navigate(SearchSourceSettings) },
                        onNavToEdit = { engineId ->
                            navController.navigate(SearchEngineEdit(engineId))
                        },
                    )
                }
                myComposable<SearchSourceSettings> {
                    SearchSourceSettingsScreen(
                        onBack = { navController.popBackStack() },
                    )
                }
                myComposable<SearchEngineEdit> {
                    SearchEngineEditScreen(
                        engineId = it.toRoute<SearchEngineEdit>().engineId,
                        onBack = { navController.popBackStack() },
                        appPicker = { request -> LunoAppPicker(request) },
                    )
                }
                myComposable<SubGestureEditor> {
                    SubGestureSettingsScreen(
                        onBack = { navController.navigateUp() },
                        onNavToActionSelect = { target ->
                            navController.navigate(
                                ActionSelect(
                                    gestureButtonId = target.gestureButtonId,
                                    slot = target.slot,
                                    directionLabel = target.directionLabel,
                                    triggerType = target.triggerType,
                                    subGestureId = target.subGestureId,
                                ),
                            )
                        },
                    )
                }
                myComposable<ActionLibraryEdit> {
                    ActionLibraryEditScreen(
                        onBack = { navController.popBackStack() },
                        entryId = it.toRoute<ActionLibraryEdit>().entryId,
                        typeId = it.toRoute<ActionLibraryEdit>().typeId,
                        appPicker = { request -> LunoAppPicker(request) },
                    )
                }
            }
            }
        }
    }
}

private inline fun <reified T : Any> NavGraphBuilder.myComposable(
    typeMap: Map<KType, @JvmSuppressWildcards NavType<*>> = emptyMap(),
    deepLinks: List<NavDeepLink> = emptyList(),
    noinline enterTransition:
    (AnimatedContentTransitionScope<NavBackStackEntry>.() -> @JvmSuppressWildcards
    EnterTransition?)? =
        null,
    noinline exitTransition:
    (AnimatedContentTransitionScope<NavBackStackEntry>.() -> @JvmSuppressWildcards
    ExitTransition?)? =
        null,
    noinline popEnterTransition:
    (AnimatedContentTransitionScope<NavBackStackEntry>.() -> @JvmSuppressWildcards
    EnterTransition?)? =
        enterTransition,
    noinline popExitTransition:
    (AnimatedContentTransitionScope<NavBackStackEntry>.() -> @JvmSuppressWildcards
    ExitTransition?)? =
        exitTransition,
    noinline sizeTransform:
    (AnimatedContentTransitionScope<NavBackStackEntry>.() -> @JvmSuppressWildcards
    SizeTransform?)? =
        null,
    noinline content: @Composable AnimatedContentScope.(NavBackStackEntry) -> Unit
) {
    composable<T>(
        typeMap = typeMap,
        deepLinks = deepLinks,
        enterTransition = enterTransition,
        exitTransition = exitTransition,
        popEnterTransition = popEnterTransition,
        popExitTransition = popExitTransition,
        sizeTransform = sizeTransform
    ) { navBackStackEntry ->
        Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
            content(navBackStackEntry)
        }
    }
}

private const val ANIMATION_DURATION_MS = AnimNavTransition
