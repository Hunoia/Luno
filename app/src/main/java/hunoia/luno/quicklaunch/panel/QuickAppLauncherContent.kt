package hunoia.luno.quicklaunch.panel
import hunoia.luno.ui.theme.*

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import hunoia.luno.ui.theme.ExtraLargeShape

import hunoia.luno.bridge.ui.DensityProvider
import hunoia.luno.quicklaunch.model.AppInfo
import hunoia.luno.config.model.QuickAppLauncherSettings
import kotlinx.coroutines.flow.collectLatest

/**
 * 面板展开后所需的内容高度：键盘三行 + 键盘行间距 + 候选项 + 间距 + 卡片上下内边距。
 *
 * [QuickAppLauncherAdjustPanel] 用它界定面板高度滑块的行程，这里用它在下界兜底。
 * 两边必须同源 —— 之前是各写一份字面量（面板里那份是 `kd * 3 + 6.dp * 2 + 40.dp * rows + 6.dp + 10.dp * 2`），
 * 改一边另一边静默失配，表现是面板能拖到把候选项或键盘裁掉的高度。
 */
internal fun requiredContentHeight(
    keyHeight: Dp,
    candidateRows: Int,
    candidateRowHeight: Dp,
): Dp = keyHeight * KeyboardRowCount + KeyboardGap * 2 +
    candidateRowHeight * candidateRows + KeyboardGap + PanelPaddingVertical * 2

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
    onClose: () -> Unit,
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
    // 进退场淡入淡出走 AnimatedVisibility：Content 每次打开都是全新进入组合，
    // animateFloatAsState 在首次组合时初值即目标值、动画会被跳过（重开瞬现的根因）。
    AnimatedVisibility(
        visible = state.panelVisible,
        enter = fadeIn(animationSpec = tween(durationMillis = AnimOverlayFade.toInt())),
        exit = fadeOut(animationSpec = tween(durationMillis = AnimOverlayFade.toInt())),
    ) {
        // 全屏 scrim：面板外的点击落在这里被消费（只关面板，不传透下层应用），
        // 替代之前的 ACTION_OUTSIDE 机制（通知和传透同时发生，传透拦不住）。
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onClose,
                ),
        ) {
            // 面板几何以前在窗口 params 里（applyPanelLayout），窗口全屏化后搬进 Compose。
            // 换算与原来逐项一致：同 fractions、同钳制。
            val configuration = LocalConfiguration.current
            val density = LocalDensity.current
            val landscape = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
            val layout = state.previewSettings?.layout(landscape)
                ?: state.launcherSettings.layout(landscape)
            val screenWidthPx = with(density) { configuration.screenWidthDp.dp.toPx().roundToInt() }
            val screenHeightPx = with(density) { configuration.screenHeightDp.dp.toPx().roundToInt() }
            val panelWidthPx = (screenWidthPx * layout.panelWidthFraction.coerceIn(0.65f, 1f)).roundToInt().coerceIn(1, screenWidthPx)
            val xPx = ((screenWidthPx - panelWidthPx) * layout.panelHorizontalBias.coerceIn(0f, 1f)).roundToInt().coerceIn(0, screenWidthPx - panelWidthPx)
            val estimatedHeightPx = (screenHeightPx * layout.contentHeightFraction.coerceIn(0.35f, 0.85f)).roundToInt().coerceAtMost(screenHeightPx)
            val maxYPx = kotlin.math.max(0, screenHeightPx - estimatedHeightPx)
            val yPx = (screenHeightPx * (1f - layout.panelHeightFraction.coerceIn(0.05f, 0.95f))).roundToInt().coerceIn(0, maxYPx)
            Box(
                modifier = Modifier
                    .offset { IntOffset(xPx, yPx) }
                    .width(with(density) { panelWidthPx.toDp() }),
            ) {
                QuickAppLauncherContent(
                    state = state,
                    onUpdateLayout = { state.setPreviewLayout(it) },
                )
            }
        }
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
    val panelWidthDp = with(density) {
        (screenWidthPx * state.launcherSettings.layout(
            LocalConfiguration.current.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE,
        ).panelWidthFraction).toDp()
    }
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
                    if (totalPull > DragThresholdPx) {
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
                                if (state.keyboardExpanded && totalDrag < -DragThresholdPx) state.toggleKeyboard()
                                if (!state.keyboardExpanded && gridAtTop && totalDrag > DragThresholdPx) state.expandKeyboard()
                            }
                        )
                    }
            ) {
                Card(
                    modifier = Modifier.width(panelWidthDp),
                    shape = ExtraLargeShape,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
                ) {
                    Column(modifier = Modifier.padding(horizontal = PanelPaddingHorizontal, vertical = PanelPaddingVertical)) {
                        val landscape = LocalConfiguration.current.orientation ==
                            android.content.res.Configuration.ORIENTATION_LANDSCAPE
                        val geo = state.launcherSettings.layout(landscape)
                        val contentHeightFraction = geo.contentHeightFraction
                        val candidateRows = geo.candidateRows.coerceIn(1, 3)
                        val density = LocalDensity.current
                        val screenHeightPx = DensityProvider.screenHeightPx
                        val panelHeightDp = with(density) { (screenHeightPx * contentHeightFraction).toDp() }
                        val keyHeight = geo.keyHeightDp.dp
                        val keyboardHeight = keyHeight * KeyboardRowCount + KeyboardGap * 2
                        val minContentHeight = requiredContentHeight(keyHeight, candidateRows, CandidateMinHeight)
                        val contentDp = (panelHeightDp - PanelPaddingVertical * 2)
                            .coerceAtLeast(minContentHeight)
                        val candidateHeight = contentDp - keyboardHeight - KeyboardGap
                        val contentHeight = contentDp
                        val iconGridCols = geo.gridColumns.coerceIn(3, 5)
                        val iconSizePx = with(density) {
                            // 候选项：列宽固定 CandidateColumnWidth，减去 AppItem 的 AppItemPadding×2。
                            // iconHeight 往往远大于它 —— AppItem 是 height(iconHeight).fillMaxWidth()
                            // 配 ContentScale.Fit，图标实际只渲染 min(宽, 高) 见方，所以按宽算而不是按框高算。
                            val candidateDp = minOf(
                                CandidateColumnWidth - AppItemPadding * 2,
                                candidateHeight / candidateRows - AppItemPadding * 2
                            )
                            // 网格格子：(面板宽 - 卡片横向内边距 - 列间距) / 列数 - AppItem 内边距×2
                            val gridCellDp = (panelWidthDp - PanelPaddingHorizontal * 2
                                - GridCellSpacing * (iconGridCols - 1)) / iconGridCols - AppItemPadding * 2
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
                                fadeIn(animationSpec = tween(AnimMedium.toInt())) togetherWith fadeOut(animationSpec = tween(AnimNormal.toInt()))
                            },
                            label = "pageSwitch"
                        ) { page ->
                            when (page) {
                                Page.App -> {
                                    AnimatedContent(
                                        targetState = state.keyboardExpanded,
                                        transitionSpec = {
                                            fadeIn(animationSpec = tween(AnimMedium.toInt())) togetherWith fadeOut(animationSpec = tween(AnimNormal.toInt())) using SizeTransform(clip = false)
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
                                                Spacer(modifier = Modifier.height(KeyboardGap))
                                                KeyboardRow(
                                                    view,
                                                    listOf(
                                                        KeyboardKey.Letter("QW", "qw", longPressDigit = "1"),
                                                        KeyboardKey.Letter("ER", "er", longPressDigit = "2"),
                                                        KeyboardKey.Letter("TY", "ty", longPressDigit = "3"),
                                                        KeyboardKey.Letter("UI", "ui", longPressDigit = "4"),
                                                        KeyboardKey.Letter("OP", "op", longPressDigit = "5"),
                                                    ),
                                                    keyHeight = keyHeight
                                                ) { token -> state.addToken(token) }
                                                Spacer(modifier = Modifier.height(KeyboardGap))
                                                KeyboardRow(
                                                    view,
                                                    listOf(
                                                        KeyboardKey.Letter("AS", "as", longPressDigit = "6"),
                                                        KeyboardKey.Letter("DF", "df", longPressDigit = "7"),
                                                        KeyboardKey.Letter("GH", "gh", longPressDigit = "8"),
                                                        KeyboardKey.Letter("JK", "jk", longPressDigit = "9"),
                                                        KeyboardKey.Letter("L", "l", longPressDigit = "0"),
                                                    ),
                                                    keyHeight = keyHeight
                                                ) { token -> state.addToken(token) }
                                                Spacer(modifier = Modifier.height(KeyboardGap))
                                                KeyboardRow(
                                                    view,
                                                    listOf(
                                                        KeyboardKey.Adjust,
                                                        KeyboardKey.Letter("ZX", "zx"),
                                                        KeyboardKey.Letter("CV", "cv"),
                                                        KeyboardKey.Letter("BN", "bn"),
                                                        KeyboardKey.Letter("M", "m"),
                                                        KeyboardKey.Delete,
                                                    ),
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
                                                    gridColumns = geo.gridColumns,
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


