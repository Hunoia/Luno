package hunoia.luno.ui.quicklaunch
import hunoia.luno.ui.theme.*

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp

import hunoia.luno.bridge.DensityProvider
import hunoia.luno.quicklaunch.model.AppInfo
import hunoia.luno.config.model.QuickAppLauncherSettings
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.collectLatest

@Composable
internal fun QuickAppLauncherContent(
    initialSettings: QuickAppLauncherSettings,
    requestEnableFrozenPackage: (String, (Boolean) -> Unit) -> Unit,
    onCloseAnimated: () -> Unit,
    onUpdateLayout: ((QuickAppLauncherSettings) -> Unit)? = null,
    onLaunch: (AppInfo, Boolean) -> Boolean,
    onRegisterCloseAnimated: ((() -> Unit) -> Unit)? = null,
) {
    val context = LocalContext.current
    val view = LocalView.current
    val coroutineScope = rememberCoroutineScope()
    val state = remember(coroutineScope) {
        QuickAppLauncherState(
            context = context,
            coroutineScope = coroutineScope,
            initialSettings = initialSettings,
            requestEnableFrozenPackage = requestEnableFrozenPackage,
            onCloseAnimatedRaw = onCloseAnimated,
            onLaunch = onLaunch,
            onRegisterCloseAnimated = onRegisterCloseAnimated,
        )
    }
    val density = LocalDensity.current
    val configuration = LocalConfiguration.current
    val orientation = configuration.orientation
    val screenWidthPx = remember(orientation) { with(density) { configuration.screenWidthDp.dp.toPx().roundToInt() } }
    val panelWidthDp = with(density) { (screenWidthPx * state.launcherSettings.panelWidthFraction).toDp() }
    val panelAlpha by animateFloatAsState(if (state.panelVisible) 1f else 0f, animationSpec = spring(stiffness = Spring.StiffnessMedium), label = "panelAlpha")
    val panelShiftY by animateFloatAsState(if (state.panelVisible) 0f else 18f, animationSpec = spring(stiffness = Spring.StiffnessMedium), label = "panelShiftY")
    var gridAtTop by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) { onRegisterCloseAnimated?.invoke(state.closeAnimated) }
    LaunchedEffect(Unit) { state.panelVisible = true }
    val gridState = rememberLazyGridState()
    var currentPage by remember { mutableStateOf(Page.App) }
    val pageMatches = remember(state.tokens) {
        if (state.tokens.isEmpty()) emptyList()
        else buildList {
            listOf(
                "密码" to Page.Password,
                "设置" to Page.Settings,
            ).forEach { (name, page) ->
                if (pageMatchesTokens(name, state.tokens)) add(name to page)
            }
        }
    }
    LaunchedEffect(gridState) {
        snapshotFlow { gridState.firstVisibleItemIndex to gridState.firstVisibleItemScrollOffset }
            .collectLatest { (index, offset) -> gridAtTop = index == 0 && offset == 0 }
    }
    val expandFromTopConnection = remember(gridAtTop, state.keyboardExpanded) {
        object : NestedScrollConnection {
            private var totalPull = 0f

            private fun updatePull(delta: Float) {
                if (!state.keyboardExpanded && gridAtTop && delta > 0f) {
                    totalPull += delta
                    if (totalPull > 32f) {
                        state.expandKeyboard()
                        totalPull = 0f
                    }
                } else if (delta <= 0f) {
                    totalPull = 0f
                }
            }

            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                updatePull(available.y)
                return Offset.Zero
            }

            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                updatePull(available.y)
                return Offset.Zero
            }
        }
    }
    Box(modifier = Modifier.width(panelWidthDp)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer {
                    alpha = panelAlpha
                    translationY = panelShiftY
                }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(
                        if (currentPage == Page.App) Modifier.nestedScroll(expandFromTopConnection) else Modifier
                    )
                    .pointerInput(currentPage, state.keyboardExpanded, gridAtTop) {
                        if (currentPage != Page.App) return@pointerInput
                        var totalDrag = 0f
                        detectVerticalDragGestures(
                            onDragStart = { totalDrag = 0f },
                            onVerticalDrag = { change, dragAmount ->
                                change.consume()
                                totalDrag += dragAmount
                                if (state.keyboardExpanded && totalDrag < -32f) state.toggleKeyboard()
                                if (!state.keyboardExpanded && gridAtTop && totalDrag > 32f) state.expandKeyboard()
                            }
                        )
                    }
            ) {
                Card(
                    modifier = Modifier.width(panelWidthDp),
                    shape = MaterialTheme.shapes.extraLarge,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
                ) {
                    Column(modifier = Modifier.padding(horizontal = Spacing12, vertical = Spacing10)) {
                        val contentHeightFraction = state.launcherSettings.contentHeightFraction
                        val candidateRows = state.launcherSettings.candidateRows.coerceIn(1, 3)
                        val chunkedApps = remember(state.filteredApps, candidateRows) { state.filteredApps.chunked(candidateRows) }
                        val density = LocalDensity.current
                        val screenHeightPx = DensityProvider.screenHeightPx
                        val panelHeightDp = with(density) { (screenHeightPx * contentHeightFraction).toDp() }
                        val keyHeight = state.launcherSettings.keyHeightDp.dp
                        val keyboardHeight = keyHeight * 3 + Spacing6 * 2
                        val minCandidateHeight = 40.dp * candidateRows
                        val contentDp = (panelHeightDp - Spacing10 * 2).coerceAtLeast(keyboardHeight + Spacing6 + minCandidateHeight)
                        val candidateHeight = contentDp - keyboardHeight - Spacing6
                        val contentHeight = contentDp
                        AnimatedContent(
                            targetState = currentPage,
                            transitionSpec = {
                                fadeIn(animationSpec = tween(200)) togetherWith fadeOut(animationSpec = tween(150))
                            },
                            label = "pageSwitch"
                        ) { page ->
                            when (page) {
                                Page.App -> {
                                    AnimatedContent(
                                        targetState = state.keyboardExpanded,
                                        transitionSpec = {
                                            fadeIn(animationSpec = tween(200)) togetherWith fadeOut(animationSpec = tween(150)) using SizeTransform(clip = false)
                                        },
                                        label = "keyboardExpand"
                                    ) { expanded ->
                                        if (expanded) {
                                            Column {
                                                CandidateAppRows(
                                                    pageMatches = pageMatches,
                                                    chunkedApps = chunkedApps,
                                                    frozenPkgs = state.appListState.frozenPkgs,
                                                    candidateHeight = candidateHeight,
                                                    rows = candidateRows,
                                                    onClick = { app, isFrozen ->
                                                        state.launchApp(app, isFrozen, state.launcherSettings.tapOpensMiniWindow, null)
                                                    },
                                                    onLongPress = { app, isFrozen ->
                                                        state.launchApp(app, isFrozen, !state.launcherSettings.tapOpensMiniWindow, "longPress")
                                                    },
                                                    onPageMatchClick = { page ->
                                                        currentPage = page
                                                        state.clearTokens()
                                                    }
                                                )
                                                Spacer(modifier = Modifier.height(Spacing6))
                                                KeyboardRow(
                                                    view,
                                                    listOf("QW" to "qw", "ER" to "er", "TY" to "ty", "UI" to "ui", "OP" to "op"),
                                                    keyHeight = keyHeight
                                                ) { token -> state.addToken(token) }
                                                Spacer(modifier = Modifier.height(Spacing6))
                                                KeyboardRow(
                                                    view,
                                                    listOf("AS" to "as", "DF" to "df", "GH" to "gh", "JK" to "jk", "L" to "l"),
                                                    keyHeight = keyHeight
                                                ) { token -> state.addToken(token) }
                                                Spacer(modifier = Modifier.height(Spacing6))
                                                KeyboardRow(
                                                    view,
                                                    listOf("调整" to null, "ZX" to "zx", "CV" to "cv", "BN" to "bn", "M" to "m", "删除" to null),
                                                    onDelete = { state.deleteToken() },
                                                    onClear = { state.clearTokens() },
                                                    onAdjust = { currentPage = Page.Settings },
                                                    keyHeight = keyHeight
                                                ) { token -> state.addToken(token) }
                                            }
                                        } else {
                                            Column {
                                                AppGrid(
                                                    pageMatches = pageMatches,
                                                    apps = state.filteredApps,
                                                    frozenPkgs = state.appListState.frozenPkgs,
                                                    gridState = gridState,
                                                    gridAtTop = gridAtTop,
                                                    keyboardExpanded = state.keyboardExpanded,
                                                    gridHeight = contentHeight,
                                                    gridColumns = state.launcherSettings.gridColumns,
                                                    onExpandKeyboard = { state.expandKeyboard() },
                                                    onClick = { app, isFrozen ->
                                                        state.launchApp(app, isFrozen, state.launcherSettings.tapOpensMiniWindow, null)
                                                    },
                                                    onLongPress = { app, isFrozen ->
                                                        state.launchApp(app, isFrozen, !state.launcherSettings.tapOpensMiniWindow, "longPress_grid")
                                                    },
                                                    onPageMatchClick = { page ->
                                                        currentPage = page
                                                        state.clearTokens()
                                                    }
                                                )
                                        }
                                    }
                                }
                            }
                                Page.Settings -> SettingsPageContent(
                                    contentHeight = contentHeight,
                                    onUpdateLayout = onUpdateLayout,
                                    onNavigateToApp = { currentPage = Page.App; state.keyboardExpanded = true },
                                )
                                Page.Password -> PasswordPageContent(
                                    contentHeight = contentHeight,
                                    context = context,
                                    onNavigateToApp = { currentPage = Page.App; state.keyboardExpanded = true },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}


