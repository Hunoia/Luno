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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavDestination.Companion.hasRoute
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
import hunoia.luno.ui.component.sheet.LunoAppPicker
import hunoia.luno.ui.theme.MotionNavTransition
import kotlinx.coroutines.launch

import hunoia.luno.picker.ActionSelect
import hunoia.luno.picker.FloatBallSlot
import hunoia.luno.actions.shortcut.DynamicShortcutsScreen

import hunoia.luno.gesture.settings.GestureButtonSettings

import hunoia.luno.ui.navigation.Home
import hunoia.luno.ui.navigation.LogSettings
import hunoia.luno.ui.navigation.AutoBackupSettings
import hunoia.luno.ui.navigation.AppControl
import hunoia.luno.ui.navigation.Clipboard
import hunoia.luno.ui.navigation.ScreenPickSettings
import hunoia.luno.ui.navigation.NotificationBlocklist
import hunoia.luno.ui.navigation.PrivilegedPermissions
import hunoia.luno.ui.navigation.AccessibilityManager
import hunoia.luno.ui.navigation.PersistentNotificationSettings
import hunoia.luno.ui.navigation.NotificationSettings
import hunoia.luno.ui.navigation.StatusBarIconSettings
import hunoia.luno.ui.navigation.RecentAppsBlocklist
import hunoia.luno.ui.navigation.RecentsGestureSettings
import hunoia.luno.ui.navigation.RecognitionRules
import hunoia.luno.ui.navigation.RecognitionRuleEdit
import hunoia.luno.ui.navigation.SceneRecognizeList
import hunoia.luno.ui.navigation.ActionPanelList
import hunoia.luno.ui.navigation.ActionPanelEdit
import hunoia.luno.ui.navigation.GridPanelEdit
import hunoia.luno.ui.navigation.SinglePanelEdit
import hunoia.luno.ui.navigation.HabitEdit
import hunoia.luno.ui.navigation.HabitDetail
import hunoia.luno.ui.navigation.TodoSettings
import hunoia.luno.ui.navigation.TrendDetail
import hunoia.luno.ui.navigation.TrendEdit
import hunoia.luno.ui.navigation.SearchEngineSettings
import hunoia.luno.ui.navigation.SearchEngineEdit
import hunoia.luno.ui.navigation.SearchSourceSettings
import hunoia.luno.ui.navigation.MiniWindowSettings
import hunoia.luno.ui.navigation.MiniWindowAdjust
import hunoia.luno.ui.navigation.PointerAdjust
import hunoia.luno.ui.navigation.PointerSettings
import hunoia.luno.ui.navigation.DynamicShortcutSettings
import hunoia.luno.ui.navigation.PendingDynamicShortcuts
import hunoia.luno.ui.navigation.AppearanceSettings
import hunoia.luno.ui.navigation.FloatBallEdit
import hunoia.luno.ui.navigation.FloatBallDropZoneAdjust
import hunoia.luno.ui.navigation.FloatBallAlertBlocklist
import hunoia.luno.ui.navigation.FloatBallActionAlert
import hunoia.luno.ui.navigation.FloatBallAlert
import hunoia.luno.ui.navigation.FloatBallAlertPresetLibrary
import hunoia.luno.ui.navigation.FloatBallAlertPresetEditor
import hunoia.luno.ui.navigation.FloatBallAlertLayerEditor
import hunoia.luno.ui.navigation.FloatBallAlertLayerAdd
import hunoia.luno.ui.navigation.FloatBallAlertPaletteManager
import hunoia.luno.ui.navigation.FloatBallAlertPaletteEdit
import hunoia.luno.ui.navigation.FloatBallNotificationCapsule
import hunoia.luno.ui.navigation.FloatBallResultCapsule
import hunoia.luno.ui.navigation.ButtonVisibility
import hunoia.luno.ui.navigation.ButtonVisibilityEdit
import hunoia.luno.floatball.settings.FloatBallEditScreen
import hunoia.luno.floatball.settings.NotificationCapsuleSettingsScreen
import hunoia.luno.floatball.settings.alert.AlertPresetLibraryScreen
import hunoia.luno.floatball.settings.alert.AlertPresetEditorScreen
import hunoia.luno.floatball.settings.alert.AlertLayerEditorScreen
import hunoia.luno.floatball.settings.alert.AlertLayerAddScreen
import hunoia.luno.floatball.settings.alert.ActionAlertSettingsScreen
import hunoia.luno.floatball.settings.alert.AlertSettingsScreen
import hunoia.luno.floatball.settings.alert.AlertPaletteManagerScreen
import hunoia.luno.floatball.settings.alert.AlertPaletteEditScreen
import hunoia.luno.floatball.settings.ResultCapsuleSettingsScreen
import hunoia.luno.gesture.settings.visibility.ButtonVisibilityEditScreen
import hunoia.luno.gesture.settings.visibility.ButtonVisibilityScreen
import hunoia.luno.gesture.settings.SubGestureEditor
import hunoia.luno.gesture.settings.global.GlobalGestureZoneEditor
import hunoia.luno.gesture.settings.global.GlobalGestureZoneEditorScreen
import hunoia.luno.gesture.settings.global.GlobalGestureTriggerEditor
import hunoia.luno.gesture.settings.global.GlobalGestureTriggerEditorScreen

import hunoia.luno.ui.navigation.ActionLibraryEdit

import hunoia.luno.picker.ActionSelectContent
import hunoia.luno.actions.library.ActionLibraryEditScreen
import hunoia.luno.ui.settings.action.LogSettingsScreen
import hunoia.luno.ui.settings.backup.AutoBackupSettingsScreen
import hunoia.luno.appcontrol.AppControlScreen
import hunoia.luno.clipboard.ClipboardScreen
import hunoia.luno.ui.settings.action.ScreenPickSettingsScreen
import hunoia.luno.ui.settings.action.RecentAppsBlocklistScreen
import hunoia.luno.ui.settings.action.RecentsGestureSettingsScreen
import hunoia.luno.ui.settings.accessibility.AccessibilityManagerScreen
import hunoia.luno.ui.settings.notification.PersistentNotificationSettingsScreen
import hunoia.luno.ui.settings.notification.NotificationSettingsScreen
import hunoia.luno.ui.settings.statusbar.StatusBarIconSettingsScreen
import hunoia.luno.ui.settings.permission.PrivilegedPermissionsScreen
import hunoia.luno.ui.settings.action.MiniWindowAdjustScreen
import hunoia.luno.ui.settings.action.DropZoneAdjustScreen
import hunoia.luno.ui.settings.action.PointerAdjustScreen
import hunoia.luno.ui.settings.action.PointerSettingsScreen
import hunoia.luno.ui.settings.action.AppearanceSettingsScreen
import hunoia.luno.ui.settings.action.MiniWindowSettingsScreen
import hunoia.luno.recognition.RecognitionRuleEditScreen
import hunoia.luno.recognition.RecognitionSettingsScreen
import hunoia.luno.ui.settings.action.panel.ActionPanelEditScreen
import hunoia.luno.ui.settings.action.panel.ActionPanelListScreen
import hunoia.luno.ui.settings.action.gridpanel.GridPanelEditScreen
import hunoia.luno.gridpanel.habit.HabitEditScreen
import hunoia.luno.gridpanel.habit.HabitDetailScreen
import hunoia.luno.gridpanel.todo.TodoSettingsScreen
import hunoia.luno.gridpanel.trend.TrendDetailScreen
import hunoia.luno.gridpanel.trend.TrendEditScreen
import hunoia.luno.quickaccess.settings.SearchEngineSettingsScreen
import hunoia.luno.quickaccess.settings.SearchEngineEditScreen
import hunoia.luno.quickaccess.settings.SearchSourceSettingsScreen

import hunoia.luno.gesture.settings.button.GestureButtonSettingsScreen

import hunoia.luno.gesture.settings.subgesture.SubGestureSettingsScreen
import hunoia.luno.domain.action.ActionLibrary
import hunoia.luno.ui.theme.SideGestureTheme
import hunoia.luno.ui.navigation.LocalActionLibraryEntries
import hunoia.luno.ui.navigation.LocalActionPanels
import hunoia.luno.ui.navigation.LocalGridPanels
import hunoia.luno.ui.navigation.LocalPanelRefIcon
import hunoia.luno.ui.navigation.LocalSubGestures
import hunoia.luno.ui.navigation.LocalNavController
import hunoia.luno.ui.navigation.PendingHabitDetail
import hunoia.luno.ui.navigation.PendingIncidentView
import hunoia.luno.ui.navigation.PendingTrendRecord
import hunoia.luno.gridpanel.todo.TodoListRequests
import hunoia.luno.ui.home.HomeScreen
import hunoia.luno.ui.home.MainTab
import hunoia.luno.ui.navigation.PendingHomeTab
import hunoia.luno.domain.tune.TuneConfig
import hunoia.luno.domain.gesture.GestureConfig
import kotlin.reflect.KType
import hunoia.luno.ui.component.row.LocalListStyle


@Composable
fun SideGestureApp(navController: NavHostController = rememberNavController()) {
    SideGestureTheme {
        val context = LocalContext.current
        val durationMs = ANIMATION_DURATION_MS
        val actionLibrarySettings by ActionLibrary.settings
            .collectAsStateWithLifecycle(initialValue = ActionLibrary.latestSettings())
        val actionSettings by TuneConfig.action
            .collectAsStateWithLifecycle(initialValue = TuneConfig.latestAction())
        val subGestureSettings by GestureConfig.subGestures
            .collectAsStateWithLifecycle(initialValue = GestureConfig.latestSubGestures())
        // 设置列表视觉风格：全局开关，LunoGroup 读取后决定分段式 / 大卡分割线。
        val advancedSettings by TuneConfig.advanced
            .collectAsStateWithLifecycle(initialValue = TuneConfig.latestAdvanced())
        // 习惯部件 / 提醒通知「打开详情页」：跨进程 Intent → [PendingHabitDetail]，消费一次就跳详情页。
        val pendingHabitDetailId by PendingHabitDetail.habitId.collectAsStateWithLifecycle()
        LaunchedEffect(pendingHabitDetailId) {
            val habitId = pendingHabitDetailId ?: return@LaunchedEffect
            PendingHabitDetail.consume()
            navController.navigate(HabitDetail(habitId)) { launchSingleTop = true }
        }
        // 趋势部件 / 提醒通知 → [PendingTrendRecord]：有指标进详情（可自动弹快速记录），无指标回主页趋势 tab。
        val pendingTrendOpen by PendingTrendRecord.open.collectAsStateWithLifecycle()
        LaunchedEffect(pendingTrendOpen) {
            val open = pendingTrendOpen ?: return@LaunchedEffect
            PendingTrendRecord.consume()
            val metricId = open.metricId
            if (metricId == null) {
                PendingHomeTab.request(MainTab.Trends)
                navController.popBackStack(Home, inclusive = false)
            } else {
                navController.navigate(TrendDetail(metricId, open.openRecord)) { launchSingleTop = true }
            }
        }
        // 代办面板部件「打开代办列表页」：跨进程 Intent → [TodoListRequests]，回主页代办 tab。
        // consume 由嵌入的代办内容完成（它读 todoId 决定开添加/编辑弹窗）；这里只负责导航。
        val pendingTodoList by TodoListRequests.open.collectAsStateWithLifecycle()
        LaunchedEffect(pendingTodoList) {
            if (pendingTodoList == null) return@LaunchedEffect
            PendingHomeTab.request(MainTab.Todos)
            navController.popBackStack(Home, inclusive = false)
        }
        // 故障通知点击 → 运行日志页；日志页自己消费该请求并展开故障记录。
        // 已经在日志页时不要重复导航：launchSingleTop 仍会重建页面，会把刚展开的
        // 故障 sheet 连同局部状态一起丢掉。
        val pendingIncident by PendingIncidentView.requested.collectAsStateWithLifecycle()
        LaunchedEffect(pendingIncident) {
            if (!pendingIncident) return@LaunchedEffect
            if (navController.currentDestination?.hasRoute(LogSettings::class) == true) {
                return@LaunchedEffect
            }
            navController.navigate(LogSettings) { launchSingleTop = true }
        }
        // 临时默认桌面的 HOME 入口按 Home → 回主界面并跳到动态快捷方式导入页。
        val pendingDynamicShortcuts by PendingDynamicShortcuts.requested.collectAsStateWithLifecycle()
        LaunchedEffect(pendingDynamicShortcuts) {
            if (!pendingDynamicShortcuts) return@LaunchedEffect
            PendingDynamicShortcuts.consume()
            navController.navigate(DynamicShortcutSettings) { launchSingleTop = true }
        }
        CompositionLocalProvider(
            LocalNavController provides navController,
            LocalActionLibraryEntries provides actionLibrarySettings.entries,
            LocalActionPanels provides actionSettings.actionPanels.panels,
            LocalGridPanels provides actionSettings.actionGridPanels.panels +
                hunoia.luno.gridpanel.SinglePanelCatalog.shells(context, actionSettings),
            LocalPanelRefIcon provides { id ->
                hunoia.luno.gridpanel.resolvePanelRefIcon(id, actionSettings.actionGridPanels.panels)
            },
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
                        onNavToGlobalZoneEditor = { zoneId ->
                            navController.navigate(GlobalGestureZoneEditor(zoneId))
                        },
                        onNavToSearchEngines = { navController.navigate(SearchEngineSettings) },
                        onNavToFloatBallSettings = { navController.navigate(FloatBallEdit) },
                        onNavToNotificationCapsuleSettings = {
                            navController.navigate(FloatBallNotificationCapsule)
                        },
                        onNavToAlertPresetLibrary = {
                            navController.navigate(FloatBallAlertPresetLibrary)
                        },
                        onNavToAlertPaletteManager = {
                            navController.navigate(FloatBallAlertPaletteManager)
                        },
                        onNavToActionAlert = {
                            navController.navigate(FloatBallActionAlert)
                        },
                        onNavToButtonVisibility = { navController.navigate(ButtonVisibility) },
                        onNavToActionPanels = { navController.navigate(ActionPanelList) },
                        onNavToMiniWindowSettings = { navController.navigate(MiniWindowSettings) },
                        onNavToPointerSettings = { navController.navigate(PointerSettings) },
                        onNavToDynamicShortcuts = {
                            navController.navigate(DynamicShortcutSettings)
                        },
                        onNavToRecentsGestureSettings = {
                            navController.navigate(RecentsGestureSettings)
                        },
                        onNavToAppearanceSettings = { navController.navigate(AppearanceSettings) },
                        onNavToRecognition = { navController.navigate(RecognitionRules) },
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
                        onNavToNotificationSettings = {
                            navController.navigate(NotificationSettings)
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
                        onNavToAppControl = {
                            navController.navigate(AppControl)
                        },
                        onNavToHabitEdit = { habitId ->
                            navController.navigate(HabitEdit(habitId ?: ""))
                        },
                        onNavToHabitDetail = { habitId ->
                            navController.navigate(HabitDetail(habitId))
                        },
                        onNavToTodoSettings = {
                            navController.navigate(TodoSettings)
                        },
                        onNavToTrendEdit = { metricId ->
                            navController.navigate(TrendEdit(metricId ?: ""))
                        },
                        onNavToTrendDetail = { metricId ->
                            navController.navigate(TrendDetail(metricId))
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
                                    globalGestureTriggerId = target.globalGestureTriggerId,
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
                    ScreenPickSettingsScreen(
                        onBack = { navController.popBackStack() },
                        onNavToRecognition = { navController.navigate(RecognitionRules) },
                    )
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
                myComposable<AppControl> {
                    AppControlScreen(
                        onBack = { navController.popBackStack() },
                        appPicker = { request -> LunoAppPicker(request) },
                    )
                }
                myComposable<RecognitionRules> {
                    RecognitionSettingsScreen(
                        onBack = { navController.popBackStack() },
                        onEditRule = { ruleId ->
                            navController.navigate(RecognitionRuleEdit(ruleId))
                        },
                        onCreateRule = {
                            navController.navigate(RecognitionRuleEdit(""))
                        },
                        onNavToSceneList = { navController.navigate(SceneRecognizeList) },
                        onNavToResultCapsuleSettings = {
                            navController.navigate(FloatBallResultCapsule)
                        },
                    )
                }
                myComposable<RecognitionRuleEdit> {
                    RecognitionRuleEditScreen(
                        ruleId = it.toRoute<RecognitionRuleEdit>().ruleId,
                        prefillPattern = it.toRoute<RecognitionRuleEdit>().pattern,
                        onBack = { navController.popBackStack() },
                        appPicker = { request -> LunoAppPicker(request) },
                        actionPicker = { ctx -> hunoia.luno.domain.recognition.ActionPick.launch(ctx) },
                        subscribePickedAction = { token, onPicked ->
                            // 选择页在 :service 的独立 Activity，结果走进程内事件；token 隔离本次挑选。
                            hunoia.luno.core.Events.SubscribeEvent(
                                hunoia.luno.core.ActionPickedEvent::class,
                            ) { event ->
                                if (event.token == token) onPicked(event.action)
                            }
                        },
                    )
                }
                myComposable<RecentAppsBlocklist> {
                    val scope = rememberCoroutineScope()
                    val actionSettings by TuneConfig.action
                        .collectAsStateWithLifecycle(initialValue = TuneConfig.latestAction())
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
                myComposable<RecentsGestureSettings> {
                    RecentsGestureSettingsScreen(
                        onBack = { navController.popBackStack() },
                        onNavToBlocklist = { navController.navigate(RecentAppsBlocklist) },
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
                myComposable<NotificationSettings> {
                    NotificationSettingsScreen(onBack = { navController.popBackStack() })
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
                myComposable<PointerSettings> {
                    PointerSettingsScreen(
                        onBack = { navController.popBackStack() },
                        onAdjustPosition = { follow -> navController.navigate(PointerAdjust(follow)) },
                    )
                }
                myComposable<DynamicShortcutSettings> {
                    DynamicShortcutsScreen(onBack = { navController.popBackStack() })
                }
                myComposable<AppearanceSettings> {
                    AppearanceSettingsScreen(onBack = { navController.popBackStack() })
                }
                myComposable<PointerAdjust> {
                    PointerAdjustScreen(
                        follow = it.toRoute<PointerAdjust>().follow,
                        onBack = { navController.popBackStack() },
                    )
                }
                myComposable<FloatBallEdit> {
                    FloatBallEditScreen(
                        onBack = { navController.popBackStack() },
                        onEditTapAction = {
                            navController.navigate(ActionSelect(floatBallSlot = FloatBallSlot.TAP))
                        },
                        onEditLongPressAction = {
                            navController.navigate(ActionSelect(floatBallSlot = FloatBallSlot.LONG_PRESS))
                        },
                        onEditDropAction = {
                            navController.navigate(ActionSelect(floatBallSlot = FloatBallSlot.DROP_CENTER))
                        },
                        onAdjustDropZone = {
                            navController.navigate(FloatBallDropZoneAdjust)
                        },
                    )
                }
                myComposable<FloatBallDropZoneAdjust> {
                    DropZoneAdjustScreen(onBack = { navController.popBackStack() })
                }
                myComposable<FloatBallNotificationCapsule> {
                    NotificationCapsuleSettingsScreen(
                        onBack = { navController.popBackStack() },
                        onNavToNotificationBlocklist = { navController.navigate(NotificationBlocklist) },
                        onNavToAlert = { navController.navigate(FloatBallAlert) },
                        onNavToAlertBlocklist = { navController.navigate(FloatBallAlertBlocklist) },
                    )
                }
                myComposable<FloatBallActionAlert> {
                    ActionAlertSettingsScreen(
                        onBack = { navController.popBackStack() },
                    )
                }
                myComposable<FloatBallAlert> {
                    AlertSettingsScreen(
                        onBack = { navController.popBackStack() },
                        onNavToAlertBlocklist = {
                            navController.navigate(FloatBallAlertBlocklist)
                        },
                    )
                }
                myComposable<FloatBallAlertPresetLibrary> {
                    AlertPresetLibraryScreen(
                        onBack = { navController.popBackStack() },
                        onEditPreset = { presetId ->
                            navController.navigate(FloatBallAlertPresetEditor(presetId))
                        },
                    )
                }
                myComposable<FloatBallAlertPresetEditor> { entry ->
                    val route = entry.toRoute<FloatBallAlertPresetEditor>()
                    AlertPresetEditorScreen(
                        presetId = route.presetId,
                        onBack = { navController.popBackStack() },
                        onEditLayer = { layerId ->
                            navController.navigate(
                                FloatBallAlertLayerEditor(route.presetId, layerId),
                            )
                        },
                        onAddLayer = {
                            navController.navigate(FloatBallAlertLayerAdd(route.presetId))
                        },
                        onGone = { navController.popBackStack() },
                    )
                }
                myComposable<FloatBallAlertLayerAdd> { entry ->
                    val route = entry.toRoute<FloatBallAlertLayerAdd>()
                    AlertLayerAddScreen(
                        presetId = route.presetId,
                        onBack = { navController.popBackStack() },
                        onAdded = { layerId ->
                            // 添加页只做一次选择：从返回栈移掉，层编辑器的返回直接回预设编辑器。
                            navController.popBackStack()
                            navController.navigate(
                                FloatBallAlertLayerEditor(route.presetId, layerId),
                            )
                        },
                        onGone = { navController.popBackStack() },
                    )
                }
                myComposable<FloatBallAlertLayerEditor> { entry ->
                    val route = entry.toRoute<FloatBallAlertLayerEditor>()
                    AlertLayerEditorScreen(
                        presetId = route.presetId,
                        layerId = route.layerId,
                        onBack = { navController.popBackStack() },
                        onGone = { navController.popBackStack() },
                    )
                }
                myComposable<FloatBallAlertPaletteManager> {
                    AlertPaletteManagerScreen(
                        onBack = { navController.popBackStack() },
                        onEditPalette = { paletteId ->
                            navController.navigate(FloatBallAlertPaletteEdit(paletteId))
                        },
                    )
                }
                myComposable<FloatBallAlertPaletteEdit> { entry ->
                    val route = entry.toRoute<FloatBallAlertPaletteEdit>()
                    AlertPaletteEditScreen(
                        paletteId = route.paletteId,
                        onBack = { navController.popBackStack() },
                    )
                }
                myComposable<FloatBallAlertBlocklist> {
                    val scope = rememberCoroutineScope()
                    val actionSettings by TuneConfig.action
                        .collectAsStateWithLifecycle(initialValue = TuneConfig.latestAction())
                    RecentAppsBlocklistScreen(
                        onBack = { navController.popBackStack() },
                        title = stringResource(
                            hunoia.luno.floatball.R.string.float_ball_capsule_alert_blocklist,
                        ),
                        current = actionSettings.floatBall.notificationCapsule.alert.appBlocklist,
                        onSave = { ordered ->
                            scope.launch {
                                TuneConfig.updateAction {
                                    val alert = it.floatBall.notificationCapsule.alert
                                    it.copy(
                                        floatBall = it.floatBall.copy(
                                            notificationCapsule = it.floatBall.notificationCapsule.copy(
                                                alert = alert.copy(appBlocklist = ordered),
                                            ),
                                        ),
                                    )
                                }
                                TuneConfig.syncActionSnapshot()
                                navController.popBackStack()
                            }
                        },
                    )
                }
                myComposable<FloatBallResultCapsule> {
                    ResultCapsuleSettingsScreen(
                        onBack = { navController.popBackStack() },
                    )
                }
                myComposable<SceneRecognizeList> {
                    val scope = rememberCoroutineScope()
                    val actionSettings by TuneConfig.action
                        .collectAsStateWithLifecycle(initialValue = TuneConfig.latestAction())
                    RecentAppsBlocklistScreen(
                        onBack = { navController.popBackStack() },
                        title = stringResource(hunoia.luno.link.R.string.recognition_scene_list),
                        current = actionSettings.recognition.scenePackages,
                        onSave = { ordered ->
                            scope.launch {
                                TuneConfig.updateAction {
                                    it.copy(
                                        recognition = it.recognition.copy(scenePackages = ordered),
                                    )
                                }
                                navController.popBackStack()
                            }
                        },
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
                        onPickTriggerAction = { profileId, enter ->
                            navController.navigate(
                                ActionSelect(
                                    visibilityProfileId = profileId,
                                    visibilityEnter = enter,
                                ),
                            )
                        },
                    )
                }
                myComposable<NotificationBlocklist> {
                    val scope = rememberCoroutineScope()
                    val actionSettings by TuneConfig.action
                        .collectAsStateWithLifecycle(initialValue = TuneConfig.latestAction())
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
                        onEditActionPanel = { panelId -> navController.navigate(ActionPanelEdit(panelId)) },
                        onEditSinglePanel = { widgetId -> navController.navigate(SinglePanelEdit(widgetId)) },
                        onEditGridPanel = { panelId -> navController.navigate(GridPanelEdit(panelId)) },
                    )
                }
                myComposable<SinglePanelEdit> {
                    hunoia.luno.ui.settings.action.gridpanel.SinglePanelEditScreen(
                        widgetId = it.toRoute<SinglePanelEdit>().widgetId,
                        onBack = { navController.popBackStack() },
                    )
                }
                myComposable<ActionPanelEdit> {
                    ActionPanelEditScreen(
                        panelId = it.toRoute<ActionPanelEdit>().panelId,
                        onBack = { navController.popBackStack() },
                        onPickItem = { navController.navigate(it) },
                    )
                }
                myComposable<GridPanelEdit> {
                    GridPanelEditScreen(
                        panelId = it.toRoute<GridPanelEdit>().panelId,
                        onBack = { navController.popBackStack() },
                    )
                }
                myComposable<HabitEdit> {
                    HabitEditScreen(
                        habitId = it.toRoute<HabitEdit>().habitId.takeIf { h -> h.isNotBlank() },
                        onBack = { navController.popBackStack() },
                    )
                }
                myComposable<HabitDetail> {
                    HabitDetailScreen(
                        habitId = it.toRoute<HabitDetail>().habitId,
                        onBack = { navController.popBackStack() },
                        onEdit = { habitId -> navController.navigate(HabitEdit(habitId)) },
                    )
                }
                myComposable<TodoSettings> {
                    TodoSettingsScreen(
                        onBack = { navController.popBackStack() },
                    )
                }
                myComposable<TrendDetail> {
                    val route = it.toRoute<TrendDetail>()
                    TrendDetailScreen(
                        metricId = route.metricId,
                        openRecord = route.openRecord,
                        onBack = { navController.popBackStack() },
                        onEditMetric = { metricId -> navController.navigate(TrendEdit(metricId)) },
                    )
                }
                myComposable<TrendEdit> {
                    TrendEditScreen(
                        metricId = it.toRoute<TrendEdit>().metricId.takeIf { id -> id.isNotBlank() },
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
                myComposable<GlobalGestureZoneEditor> { entry ->
                    val route = entry.toRoute<GlobalGestureZoneEditor>()
                    GlobalGestureZoneEditorScreen(
                        zoneId = route.zoneId,
                        onBack = { navController.navigateUp() },
                        onNavToTriggerEditor = { triggerId ->
                            navController.navigate(GlobalGestureTriggerEditor(route.zoneId, triggerId))
                        },
                    )
                }
                myComposable<GlobalGestureTriggerEditor> { entry ->
                    val route = entry.toRoute<GlobalGestureTriggerEditor>()
                    GlobalGestureTriggerEditorScreen(
                        zoneId = route.zoneId,
                        triggerId = route.triggerId,
                        onBack = { navController.navigateUp() },
                        onNavToActionSelect = { target ->
                            navController.navigate(
                                ActionSelect(
                                    gestureButtonId = target.gestureButtonId,
                                    slot = target.slot,
                                    directionLabel = target.directionLabel,
                                    triggerType = target.triggerType,
                                    subGestureId = target.subGestureId,
                                    globalGestureTriggerId = target.globalGestureTriggerId,
                                ),
                            )
                        },
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
                                    globalGestureTriggerId = target.globalGestureTriggerId,
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

private const val ANIMATION_DURATION_MS = MotionNavTransition
