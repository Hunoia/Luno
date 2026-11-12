package hunoia.luno.quickaccess.panel
import hunoia.luno.ui.theme.*

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
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
import androidx.compose.runtime.withFrameNanos
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
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.hypot
import kotlin.math.roundToInt
import hunoia.luno.ui.component.panel.panelAppear
import hunoia.luno.ui.component.panel.panelFadeIn
import hunoia.luno.ui.component.panel.panelFadeOut
import hunoia.luno.ui.theme.ExtraLargeShape

import hunoia.luno.bridge.ui.systemBarsSafeArea
import hunoia.luno.bridge.ui.windowManager
import hunoia.luno.core.domain.AppInfo
import hunoia.luno.config.model.resolveTopLeft
import hunoia.luno.config.model.QuickAppLauncherSettings
import kotlinx.coroutines.flow.collectLatest

/**
 * 面板展开后所需的内容高度：键盘三行 + 键盘行间距 + 候选项 + 间距 + 卡片上下内边距。
 * [QuickAppLauncherAdjustPanel] 用它界定面板高度滑块的行程，这里用它兜底；两边必须同源，
 * 否则面板能拖到把候选项或键盘裁掉的高度。
 */
internal fun requiredContentHeight(
    keyHeight: Dp,
    candidateRows: Int,
    candidateRowHeight: Dp,
): Dp = keyHeight * KeyboardRowCount + KeyboardGap * 2 +
    candidateRowHeight * candidateRows + KeyboardGap + PanelPaddingVertical * 2

/**
 * 常驻入口。ComposeView 在【每次打开】时重新创建（关闭 = 移窗 + 销毁 owner，见
 * `QuickAppLauncherOverlay.closeImmediate`），所以这里每次都是「全新 attach + 首次组合」：
 * 进场动画必须靠「首帧先以 visible=false 组合」来拿到，置位时机会直接决定进不进场。
 */
@Composable
internal fun QuickAppLauncherRoot(
    initialSettings: QuickAppLauncherSettings,
    anchorX: Int,
    anchorY: Int,
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
    // 首次组合时 visible 必须还是 false，下一帧才置位：AnimatedVisibility 与本组合同帧
    // 创建时若已读到 true，会走「初始即目标」路径，enter 根本不播（每次打开都是全新 attach，
    // 所以每次首开都是瞬现，不是偶发）。show() 走的是「组合已存在」分支，不在这里。
    LaunchedEffect(Unit) {
        withFrameNanos { }
        // 这一帧里已经有人请求关闭（返回键 / 点外部）的话不要复活它。
        if (!state.closing) state.setPanelShown(true)
    }
    // 进退场走 AnimatedVisibility 维持组合生命周期（关闭时靠它把退场播完再拆窗）。
    // 几何与 alpha 曲线统一走面板家族（ui-guidelines §5.1）：绕触发点从 PanelAppearScale
    // 展开 + 位移 + alpha 尾段，与网格面板、动作面板同一份实现（以前这里是 0.92 轻缩放 + 纯淡出）。
    // 拆窗时机仍由 closeAnimated→closeImmediate 编排，不在这里管。
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val view = LocalView.current
    val landscape = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
    val layout = state.previewSettings?.layout(landscape)
        ?: state.launcherSettings.layout(landscape)
    val screenWidthPx = with(density) { configuration.screenWidthDp.dp.toPx().roundToInt() }
    val screenHeightPx = with(density) { configuration.screenHeightDp.dp.toPx().roundToInt() }
    val panelWidthPx = (screenWidthPx * layout.effectivePanelWidthFraction()).roundToInt().coerceIn(1, screenWidthPx)
    // 与 Content 内实际渲染同口径：fraction 高度兜不住键盘+候选的最小内容时按最小内容算。
    val requiredHeightPx = with(density) {
        requiredContentHeight(layout.keyHeightDp.dp, layout.candidateRows.coerceIn(1, 3), CandidateMinHeight)
            .toPx().roundToInt()
    }
    val panelHeightPx = (screenHeightPx * layout.effectiveContentHeightFraction())
        .roundToInt().coerceAtLeast(requiredHeightPx).coerceAtMost(screenHeightPx)
    // 系统栏安全区与悬浮球同源（:core:bridge），按 configuration 记忆：转屏后不沿用旧值。
    val barSafeArea = remember(configuration) { view.context.windowManager().systemBarsSafeArea() }
    val target = layout.placement.resolveTopLeft(
        anchorX = anchorX,
        anchorY = anchorY,
        widthPx = panelWidthPx.toFloat(),
        heightPx = panelHeightPx.toFloat(),
        screenWidthPx = screenWidthPx,
        screenHeightPx = screenHeightPx,
        safeTopPx = barSafeArea.top,
        safeBottomPx = barSafeArea.bottom,
        density = density.density,
    )
    val targetPosX = target.x
    val targetPosY = target.y
    // 触发点（屏幕坐标）换算到面板局部 0..1；夹进边界，避免贴边时锚点跑到面板外。
    val triggerOrigin = remember(targetPosX, targetPosY, panelWidthPx, panelHeightPx) {
        TransformOrigin(
            ((anchorX - targetPosX) / panelWidthPx).coerceIn(0f, 1f),
            ((anchorY - targetPosY) / panelHeightPx).coerceIn(0f, 1f),
        )
    }
    // 面板自身的位移方向：卡片中心 → 触发点（与网格面板同口径：进度 0 时向触发点偏移）。
    val appearDir = remember(targetPosX, targetPosY, anchorX, anchorY, panelWidthPx, panelHeightPx) {
        val dx = anchorX - (targetPosX + panelWidthPx / 2f)
        val dy = anchorY - (targetPosY + panelHeightPx / 2f)
        val len = hypot(dx, dy).coerceAtLeast(1f)
        Offset(dx / len, dy / len)
    }
    val appearSlidePx = with(density) { PanelAppearSlide.toPx() }
    val panelMotion = panelSpec()
    AnimatedVisibility(
        visible = state.panelVisible,
        enter = panelFadeIn(panelMotion),
        exit = panelFadeOut(panelMotion),
    ) {
        // 几何与 alpha 同一段时长：进度直接读 AnimatedVisibility 自己的 transition。
        val appear = transition.animateFloat(transitionSpec = { panelMotion.tween() }) { from ->
            if (from == EnterExitState.Visible) 1f else 0f
        }
        // 全屏 scrim：面板外点击只关面板，不传透下层应用。
        // **scrim 不参与缩放**：缩了之后角落的点击会漏给下层应用（它在面板动画前 15% 里最小）。
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onClose,
                ),
        ) {
            // 首帧（重开）初值即目标，不飞入；已可见时再触发换锚点，弹簧平滑挪过去（同网格面板卡片）。
            val posAnimX = remember { Animatable(targetPosX) }
            val posAnimY = remember { Animatable(targetPosY) }
            LaunchedEffect(targetPosX, targetPosY) {
                posAnimX.animateTo(targetPosX, spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow))
                posAnimY.animateTo(targetPosY, spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow))
            }
            Box(
                modifier = Modifier
                    .offset { IntOffset(posAnimX.value.roundToInt(), posAnimY.value.roundToInt()) }
                    .width(with(density) { panelWidthPx.toDp() })
                    .panelAppear(
                        progress = { appear.value },
                        originForSize = { triggerOrigin },
                        slideForSize = { appearDir * appearSlidePx },
                        fade = false,
                    ),
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
            orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE,
        ).effectivePanelWidthFraction()).toDp()
    }
    var gridAtTop by remember { mutableStateOf(true) }
    val gridState = rememberLazyGridState()
    var currentPage by remember { mutableStateOf(Page.App) }
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
                        val landscape = orientation ==
                            android.content.res.Configuration.ORIENTATION_LANDSCAPE
                        val geo = state.launcherSettings.layout(landscape)
                        val contentHeightFraction = geo.effectiveContentHeightFraction()
                        val candidateRows = geo.candidateRows.coerceIn(1, 3)
                        val density = LocalDensity.current
                        val screenHeightPx = with(density) {
                            configuration.screenHeightDp.dp.toPx().roundToInt()
                        }
                        val panelHeightDp = with(density) { (screenHeightPx * contentHeightFraction).toDp() }
                        val keyHeight = geo.keyHeightDp.dp
                        val keyboardHeight = keyHeight * KeyboardRowCount + KeyboardGap * 2
                        val minContentHeight = requiredContentHeight(keyHeight, candidateRows, CandidateMinHeight) -
                            PanelPaddingVertical * 2
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
                                fadeIn(animationSpec = tween(MotionContentIn)) togetherWith fadeOut(animationSpec = tween(MotionContentOut))
                            },
                            label = "pageSwitch"
                        ) { page ->
                            when (page) {
                                Page.App -> {
                                    AnimatedContent(
                                        targetState = state.keyboardExpanded,
                                        transitionSpec = {
                                            fadeIn(animationSpec = tween(MotionContentIn)) togetherWith fadeOut(animationSpec = tween(MotionContentOut)) using SizeTransform(clip = false)
                                        },
                                        label = "keyboardExpand"
                                    ) { expanded ->
                                        if (expanded) {
                                            Column {
                                                CandidateAppRows(
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
                                                        KeyboardKey.Clear,
                                                    ),
                                                    onClear = { state.clearTokens() },
                                                    onAdjust = { currentPage = Page.Settings },
                                                    keyHeight = keyHeight
                                                ) { token -> state.addToken(token) }
                                            }
                                        } else {
                                            Column {
                                                AppGrid(
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
                            }
                        }
                    }
                }
            }
        }
    }
}


