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
import androidx.compose.ui.Modifier
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavDeepLink
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import hunoia.luno.ui.theme.AnimNavTransition

import hunoia.luno.ui.navigation.ActionSelect

import hunoia.luno.ui.navigation.GestureButtonSettings

import hunoia.luno.ui.navigation.Home
import hunoia.luno.ui.navigation.ActionLibrary
import hunoia.luno.ui.navigation.ActionSettings
import hunoia.luno.ui.navigation.SubGestureEditor
import hunoia.luno.ui.navigation.PointerSettings
import hunoia.luno.ui.navigation.FrozenManage
import hunoia.luno.ui.navigation.AppBlacklist

import hunoia.luno.ui.actionselect.ActionSelectContent
import hunoia.luno.ui.actionlibrary.ActionLibraryScreen
import hunoia.luno.ui.settings.action.ActionSettingsScreen

import hunoia.luno.ui.settings.gesture.button.GestureButtonSettingsScreen

import hunoia.luno.ui.settings.gesture.subgesture.SubGestureSettingsScreen
import hunoia.luno.ui.theme.SideGestureTheme
import hunoia.luno.ui.navigation.LocalNavController
import hunoia.luno.ui.home.HomeScreen
import hunoia.luno.ui.home.sheet.PointerSettingsScreen
import hunoia.luno.ui.freeze.FrozenAppManageContent
import hunoia.luno.ui.freeze.FrozenAppBlacklistContent
import kotlin.reflect.KType



@Composable
fun SideGestureApp() {
    SideGestureTheme {
        val navController = rememberNavController()
        val durationMs = ANIMATION_DURATION_MS
        CompositionLocalProvider(
            LocalNavController provides navController
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
                        onNavToPointerSettings = {
                            navController.navigate(PointerSettings)
                        },
                        onNavToFrozenManage = {
                            navController.navigate(FrozenManage)
                        },
                        onNavToAppBlacklist = {
                            navController.navigate(AppBlacklist)
                        },
                        onNavToActionLibrary = {
                            navController.navigate(ActionLibrary)
                        },
                        onNavToActionSettings = {
                            navController.navigate(ActionSettings)
                        }
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
                        onDismiss = { navController.popBackStack() },
                        actionSelect = it.toRoute()
                    )
                }
                myComposable<ActionLibrary> {
                    ActionLibraryScreen(
                        onBack = { navController.popBackStack() }
                    )
                }
                myComposable<ActionSettings> {
                    ActionSettingsScreen(
                        onBack = { navController.popBackStack() }
                    )
                }
                myComposable<SubGestureEditor> {
                    SubGestureSettingsScreen(
                        onBack = { navController.navigateUp() },
                        onNavToActionSelect = { navController.navigate(it) }
                    )
                }
                myComposable<PointerSettings> {
                    PointerSettingsScreen(
                        onBack = { navController.popBackStack() }
                    )
                }
                myComposable<FrozenManage> {
                    FrozenAppManageContent(
                        onDismiss = { navController.popBackStack() }
                    )
                }
                myComposable<AppBlacklist> {
                    FrozenAppBlacklistContent(
                        onDismiss = { navController.popBackStack() }
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
        Surface(color = MaterialTheme.colorScheme.surface) {
            content(navBackStackEntry)
        }
    }
}

private const val ANIMATION_DURATION_MS = AnimNavTransition
