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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import hunoia.luno.ui.theme.ExtraLargeShape

import hunoia.luno.bridge.DensityProvider
import hunoia.luno.quicklaunch.model.AppInfo
import hunoia.luno.config.model.QuickAppLauncherSettings
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.collectLatest

/**
 * 常驻入口。ComposeView 只创建一次，本组合随它一直活着，所以 `remember` 出来的
 * [QuickAppLauncherState]（已加载的应用列表、已建好的搜索索引、Coil 里的图标）跨会话保留。
 *
 * 收起时只渲染一个 1dp 的盒子：GPU 上那块全屏 RGBA_8888 buffer（1200x2670 ≈ 12.2MB）
 * 随窗口缩到 1x1 就释放了，而 state 还在。首开因此不必重查 PackageManager、不必重建索引。
 */
@Composable
internal fun QuickAppLauncherRoot(
    initialSettings: QuickAppLauncherSettings,
    requestEnableDisabledPackage: (String, (Boolean) -> Unit) -> Unit,
    onCloseAnimated: () -> Unit,
    onUpdateLayout: ((QuickAppLauncherSettings) -> Unit)? = null,
    onLaunch: (AppInfo, Boolean) -> Boolean,
    onRegisterCloseAnimated: ((() -> Unit) -> Unit)? = null,
    onStateReady: (QuickAppLauncherState) -> Unit = {},
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val state = remember(coroutineScope) {
        QuickAppLauncherState(
            context = context,
            coroutineScope = coroutineScope,
            initialSettings = initialSettings,
            requestEnableDisabledPackage = requestEnableDisabledPackage,
            onCloseAnimatedRaw = onCloseAnimated,
            onLaunch = onLaunch,
            onRegisterCloseAnimated = onRegisterCloseAnimated,
        ).also { onStateReady(it) }
    }
    if (state.panelVisible) {
        QuickAppLauncherContent(
            state = state,
            onUpdateLayout = onUpdateLayout,
        )
    } else {
        Box(Modifier.size(1.dp))
    }

    // 组合被销毁（旋转 / 服务销毁走 release）时解注册广播回调。
    DisposableEffect(state) {
        onDispose { state.dispose() }
    }

    // 系统语言变了要重扫应用列表。常驻之后列表不会自己更新，而 label 是跟着语言走的。
    // 首次组合会触发一次，靠 loadedLocaleTag 判定为「无需重载」后立即返回。
    val localeTag = LocalConfiguration.current.locales[0].toLanguageTag()
    LaunchedEffect(localeTag) {
        state.reloadAppsIfLocaleChanged()
    }

    // 旋转后重算窗口几何。
    //
    // 放在这里而不是靠 service 的 onConfigurationChanged：manifest 没声明
    // android:configChanges，无障碍服务收不到那个回调（实测旋转后进程存活、
    // 回调未送达），靠它做旋转修正不可靠。组合一定能看到 LocalConfiguration 变化。
    //
    // 首次组合也会触发一次 updateLayout，幂等无害 —— 不过是按同一份 settings
    // 重算一遍同样的宽高。
    val orientation = LocalConfiguration.current.orientation
    LaunchedEffect(orientation) {
        onUpdateLayout?.invoke(state.launcherSettings)
    }
}

@Composable
private fun QuickAppLauncherContent(
    state: QuickAppLauncherState,
    onUpdateLayout: ((QuickAppLauncherSettings) -> Unit)?,
) {
    val context = LocalContext.current
    val view = LocalView.current
    val density = LocalDensity.current
    val configuration = LocalConfiguration.current
    val orientation = configuration.orientation
    val screenWidthPx = remember(orientation) { with(density) { configuration.screenWidthDp.dp.toPx().roundToInt() } }
    val panelWidthDp = with(density) { (screenWidthPx * state.launcherSettings.panelWidthFraction).toDp() }
    val panelAlpha by animateFloatAsState(if (state.panelVisible) 1f else 0f, animationSpec = spring(stiffness = Spring.StiffnessMedium), label = "panelAlpha")
    val panelShiftY by animateFloatAsState(if (state.panelVisible) 0f else 18f, animationSpec = spring(stiffness = Spring.StiffnessMedium), label = "panelShiftY")
    var gridAtTop by remember { mutableStateOf(true) }
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
                    shape = ExtraLargeShape,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
                ) {
                    Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                        val contentHeightFraction = state.launcherSettings.contentHeightFraction
                        val candidateRows = state.launcherSettings.candidateRows.coerceIn(1, 3)
                        val density = LocalDensity.current
                        val screenHeightPx = DensityProvider.screenHeightPx
                        val panelHeightDp = with(density) { (screenHeightPx * contentHeightFraction).toDp() }
                        val keyHeight = state.launcherSettings.keyHeightDp.dp
                        val keyboardHeight = keyHeight * 3 + 6.dp * 2
                        val minCandidateHeight = 40.dp * candidateRows
                        val contentDp = (panelHeightDp - 10.dp * 2).coerceAtLeast(keyboardHeight + 6.dp + minCandidateHeight)
                        val candidateHeight = contentDp - keyboardHeight - 6.dp
                        val contentHeight = contentDp
                        val iconGridCols = state.launcherSettings.gridColumns.coerceIn(3, 5)
                        val iconSizePx = with(density) {
                            // 候选项：列宽固定 64dp（见 CandidateAppRows），减去 AppItem 的 4dp×2 内边距 = 56dp。
                            // iconHeight 往往远大于它 —— AppItem 是 height(iconHeight).fillMaxWidth()
                            // 配 ContentScale.Fit，图标实际只渲染 min(宽, 高) 见方，所以按宽算而不是按框高算。
                            val candidateDp = minOf(56.dp, candidateHeight / candidateRows - 8.dp)
                            // 网格格子：(面板宽 - 卡片横向内边距 12dp×2 - 列间距 4dp×(列数-1)) / 列数 - 8dp
                            val gridCellDp = (panelWidthDp - 24.dp - 4.dp * (iconGridCols - 1)) / iconGridCols - 8.dp
                            maxOf(candidateDp, gridCellDp).roundToPx().coerceAtLeast(1)
                        }
                        // 预热当前查询命中列表的前几个。放在 LaunchedEffect 而不是 token 的写点上，
                        // 是为了快速连打时自动取消上一次预热，只留最终查询的那一批。
                        LaunchedEffect(state.tokens, state.appListState, iconSizePx) {
                            state.prewarmIcons(state.filteredApps, iconSizePx)
                        }
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
                                                    apps = state.filteredApps,
                                                    disabledPkgs = state.appListState.disabledPkgs,
                                                    candidateHeight = candidateHeight,
                                                    iconSizePx = iconSizePx,
                                                    rows = candidateRows,
                                                    onClick = { app, isDisabled ->
                                                        state.launchApp(app, isDisabled, state.launcherSettings.tapOpensMiniWindow)
                                                    },
                                                    onLongPress = { app, isDisabled ->
                                                        state.launchApp(app, isDisabled, !state.launcherSettings.tapOpensMiniWindow)
                                                    },
                                                    onPageMatchClick = { page ->
                                                        currentPage = page
                                                        state.clearTokens()
                                                    }
                                                )
                                                Spacer(modifier = Modifier.height(6.dp))
                                                KeyboardRow(
                                                    view,
                                                    listOf("QW" to "qw", "ER" to "er", "TY" to "ty", "UI" to "ui", "OP" to "op"),
                                                    keyHeight = keyHeight
                                                ) { token -> state.addToken(token) }
                                                Spacer(modifier = Modifier.height(6.dp))
                                                KeyboardRow(
                                                    view,
                                                    listOf("AS" to "as", "DF" to "df", "GH" to "gh", "JK" to "jk", "L" to "l"),
                                                    keyHeight = keyHeight
                                                ) { token -> state.addToken(token) }
                                                Spacer(modifier = Modifier.height(6.dp))
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
                                                    disabledPkgs = state.appListState.disabledPkgs,
                                                    gridState = gridState,
                                                    gridAtTop = gridAtTop,
                                                    keyboardExpanded = state.keyboardExpanded,
                                                    gridHeight = contentHeight,
                                                    iconSizePx = iconSizePx,
                                                    gridColumns = state.launcherSettings.gridColumns,
                                                    onExpandKeyboard = { state.expandKeyboard() },
                                                    onClick = { app, isDisabled ->
                                                        state.launchApp(app, isDisabled, state.launcherSettings.tapOpensMiniWindow)
                                                    },
                                                    onLongPress = { app, isDisabled ->
                                                        state.launchApp(app, isDisabled, !state.launcherSettings.tapOpensMiniWindow)
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


