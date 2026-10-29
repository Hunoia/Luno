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

import hunoia.luno.ui.navigation.ActionSelect

import hunoia.luno.ui.navigation.GestureButtonSettings

import hunoia.luno.ui.navigation.Home
import hunoia.luno.ui.navigation.ActionSettings
import hunoia.luno.ui.navigation.ScreenPickSettings
import hunoia.luno.ui.navigation.NotificationBlocklist
import hunoia.luno.ui.navigation.PrivilegedPermissions
import hunoia.luno.ui.navigation.RecentAppsBlocklist
import hunoia.luno.ui.navigation.LinkDispatchRules
import hunoia.luno.ui.navigation.LinkDispatchRuleEdit
import hunoia.luno.ui.navigation.ActionPanelList
import hunoia.luno.ui.navigation.ActionPanelEdit
import hunoia.luno.ui.navigation.GridPanelList
import hunoia.luno.ui.navigation.GridPanelEdit
import hunoia.luno.ui.navigation.SearchEngineSettings
import hunoia.luno.ui.navigation.SearchEngineEdit
import hunoia.luno.ui.navigation.SearchSourceSettings
import hunoia.luno.ui.navigation.MiniWindowAdjust
import hunoia.luno.ui.navigation.FloatBallEdit
import hunoia.luno.ui.settings.floatball.FloatBallEditScreen
import hunoia.luno.ui.navigation.SubGestureEditor
import hunoia.luno.ui.navigation.AutomationEdit
import hunoia.luno.ui.navigation.AutomationHistory

import hunoia.luno.ui.navigation.ActionLibraryEdit

import hunoia.luno.ui.actionselect.ActionSelectContent
import hunoia.luno.ui.actionlibrary.ActionLibraryEditScreen
import hunoia.luno.ui.automation.AutomationEditScreen
import hunoia.luno.ui.automation.AutomationHistoryScreen
import hunoia.luno.ui.settings.action.ActionSettingsScreen
import hunoia.luno.ui.settings.action.ScreenPickSettingsScreen
import hunoia.luno.ui.settings.action.RecentAppsBlocklistScreen
import hunoia.luno.ui.settings.permission.PrivilegedPermissionsScreen
import hunoia.luno.ui.settings.action.MiniWindowAdjustScreen
import hunoia.luno.ui.settings.link.LinkDispatchEditScreen
import hunoia.luno.ui.settings.link.LinkDispatchSettingsScreen
import hunoia.luno.ui.settings.floatball.FloatBallEditScreen
import hunoia.luno.ui.settings.action.panel.ActionPanelEditScreen
import hunoia.luno.ui.settings.action.panel.ActionPanelListScreen
import hunoia.luno.ui.settings.action.gridpanel.GridPanelEditScreen
import hunoia.luno.ui.settings.action.gridpanel.GridPanelListScreen
import hunoia.luno.ui.settings.search.SearchEngineSettingsScreen
import hunoia.luno.ui.settings.search.SearchEngineEditScreen
import hunoia.luno.ui.settings.search.SearchSourceSettingsScreen

import hunoia.luno.ui.settings.gesture.button.GestureButtonSettingsScreen

import hunoia.luno.ui.settings.gesture.subgesture.SubGestureSettingsScreen
import hunoia.luno.domain.action.ActionLibrary
import hunoia.luno.core.domain.ActionLibrarySettings
import hunoia.luno.ui.theme.SideGestureTheme
import hunoia.luno.ui.navigation.LocalActionLibraryEntries
import hunoia.luno.ui.navigation.LocalActionPanels
import hunoia.luno.ui.navigation.LocalGridPanels
import hunoia.luno.ui.navigation.LocalNavController
import hunoia.luno.ui.navigation.PendingLinkRule
import hunoia.luno.ui.home.HomeScreen
import hunoia.luno.domain.tune.TuneConfig
import hunoia.luno.config.model.ActionSettings as ActionTuning
import kotlin.reflect.KType



@Composable
fun SideGestureApp(navController: NavHostController = rememberNavController()) {
    SideGestureTheme {
        val durationMs = ANIMATION_DURATION_MS
        val actionLibrarySettings by ActionLibrary.settings
            .collectAsStateWithLifecycle(initialValue = ActionLibrarySettings())
        val actionSettings by TuneConfig.action
            .collectAsStateWithLifecycle(initialValue = ActionTuning())
        // 识屏卡片「新建分发规则」：跨进程 Intent → [PendingLinkRule]，消费一次就跳编辑页。
        val pendingRulePattern by PendingLinkRule.pattern.collectAsStateWithLifecycle()
        LaunchedEffect(pendingRulePattern) {
            val pattern = pendingRulePattern ?: return@LaunchedEffect
            PendingLinkRule.consume()
            navController.navigate(LinkDispatchRuleEdit("", pattern)) { launchSingleTop = true }
        }
        CompositionLocalProvider(
            LocalNavController provides navController,
            LocalActionLibraryEntries provides actionLibrarySettings.entries,
            LocalActionPanels provides actionSettings.actionPanels.panels,
            LocalGridPanels provides actionSettings.actionGridPanels.panels,
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
                        onNavToActionSettings = {
                            navController.navigate(ActionSettings)
                        },
                        onNavToSearchEngines = { navController.navigate(SearchEngineSettings) },
                        onNavToFloatBallSettings = { navController.navigate(FloatBallEdit) },
                        onNavToAutomationEdit = { ruleId ->
                            navController.navigate(AutomationEdit(ruleId))
                        },
                        onNavToActionLibraryEdit = { entryId, typeId ->
                            navController.navigate(ActionLibraryEdit(entryId, typeId))
                        },
                    )
                }
                myComposable<GestureButtonSettings> {
                    GestureButtonSettingsScreen(
                        onBack = { navController.navigateUp() },
                        onNavToActionSelect = { navController.navigate(it) }
                    )
                }
                myComposable<ActionSelect> {
                    ActionSelectContent(
                        onDismissRequest = { navController.popBackStack() },
                        actionSelect = it.toRoute()
                    )
                }
                myComposable<ActionSettings> {
                    ActionSettingsScreen(
                        onBack = { navController.popBackStack() },
                        onNavToActionPanels = { navController.navigate(ActionPanelList) },
                        onNavToGridPanels = { navController.navigate(GridPanelList) },
                        onNavToRecentAppsBlocklist = { navController.navigate(RecentAppsBlocklist) },
                        onNavToMiniWindowAdjust = { navController.navigate(MiniWindowAdjust) },
                        onNavToLinkDispatch = { navController.navigate(LinkDispatchRules) },
                        onNavToActionSelect = {
                            navController.navigate(ActionSelect(copyDeepLink = true))
                        },
                        onNavToPrivilegedPermissions = {
                            navController.navigate(PrivilegedPermissions)
                        },
                        onNavToScreenPickSettings = {
                            navController.navigate(ScreenPickSettings)
                        },
                    )
                }
                myComposable<ScreenPickSettings> {
                    ScreenPickSettingsScreen(
                        onBack = { navController.popBackStack() },
                    )
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
                    PrivilegedPermissionsScreen(onBack = { navController.popBackStack() })
                }
                myComposable<MiniWindowAdjust> {
                    MiniWindowAdjustScreen(onBack = { navController.popBackStack() })
                }
                myComposable<FloatBallEdit> {
                    FloatBallEditScreen(
                        onBack = { navController.popBackStack() },
                        onNavToActionSelect = { navController.navigate(it) },
                        onNavToNotificationBlocklist = { navController.navigate(NotificationBlocklist) },
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
                    )
                }
                myComposable<SubGestureEditor> {
                    SubGestureSettingsScreen(
                        onBack = { navController.navigateUp() },
                        onNavToActionSelect = { navController.navigate(it) }
                    )
                }
                myComposable<AutomationEdit> {
                    AutomationEditScreen(
                        onBack = { navController.popBackStack() },
                        ruleId = it.toRoute<AutomationEdit>().ruleId,
                        onNavToActionSelect = { navController.navigate(it) },
                        onNavToHistory = { ruleId -> navController.navigate(AutomationHistory(ruleId)) },
                    )
                }
                myComposable<AutomationHistory> {
                    AutomationHistoryScreen(
                        onBack = { navController.popBackStack() },
                        ruleId = it.toRoute<AutomationHistory>().ruleId,
                    )
                }
                myComposable<ActionLibraryEdit> {
                    ActionLibraryEditScreen(
                        onBack = { navController.popBackStack() },
                        entryId = it.toRoute<ActionLibraryEdit>().entryId,
                        typeId = it.toRoute<ActionLibraryEdit>().typeId,
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
