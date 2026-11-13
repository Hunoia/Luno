package hunoia.luno.quickaccess.panel

import hunoia.luno.ui.theme.*

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.collectLatest

/**
 * 面板展开后所需的内容高度：键盘三行 + 键盘行间距 + 候选项 + 间距 + 卡片上下内边距。
 * [QuickLaunchBody] 用它兜底面板高度下限，避免键盘 / 候选项被裁；只此一份。
 */
internal fun requiredContentHeight(
    keyHeight: Dp,
    candidateRows: Int,
    candidateRowHeight: Dp,
): Dp = keyHeight * KeyboardRowCount + KeyboardGap * 2 +
    candidateRowHeight * candidateRows + KeyboardGap + PanelPaddingVertical * 2

/**
 * 快速启动的纯内容：候选行 + 键盘 / 应用网格，加上可选的调整页。
 *
 * 宿主负责窗口、进出场、Card 背景与最终关闭：
 * - 单功能面板（`internal.quickLaunch`）传面板卡片尺寸；
 * - 搜索、键盘、候选、启动语义只在这里维护一份。
 */
@Composable
fun QuickLaunchBody(
    state: QuickLaunchContentState,
    panelWidth: Dp,
    panelHeight: Dp,
    modifier: Modifier = Modifier,
) {
    val view = LocalView.current
    val density = LocalDensity.current
    val configuration = LocalConfiguration.current
    val landscape = configuration.orientation ==
        android.content.res.Configuration.ORIENTATION_LANDSCAPE
    val geo = state.previewSettings?.layout(landscape) ?: state.launcherSettings.layout(landscape)
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
    val contentHeight = (panelHeight - PanelPaddingVertical * 2).coerceAtLeast(1.dp)
    val contentWidth = (panelWidth - PanelPaddingHorizontal * 2).coerceAtLeast(1.dp)
    val candidateRows = geo.candidateRows.coerceIn(1, 3)
    val keyHeight = geo.keyHeightDp.dp
    val keyboardHeight = keyHeight * KeyboardRowCount + KeyboardGap * 2
    val minContentHeight = requiredContentHeight(keyHeight, candidateRows, CandidateMinHeight) -
        PanelPaddingVertical * 2
    // 面板被拉得太矮时先收起键盘只显示应用网格，避免键盘/候选被裁掉。
    val canExpandKeyboard = contentHeight >= minContentHeight
    val effectiveKeyboardExpanded = state.keyboardExpanded && canExpandKeyboard
    val candidateHeight = (contentHeight - keyboardHeight - KeyboardGap).coerceAtLeast(1.dp)
    val iconGridCols = geo.gridColumns.coerceIn(3, 5)
    val iconSizePx = with(density) {
        // 候选项：列宽固定 CandidateColumnWidth，减去 AppItem 的 AppItemPadding×2。
        // iconHeight 往往远大于它 —— AppItem 是 height(iconHeight).fillMaxWidth()
        // 配 ContentScale.Fit，图标实际只渲染 min(宽, 高) 见方，所以按宽算而不是按框高算。
        val candidateDp = minOf(
            CandidateColumnWidth - AppItemPadding * 2,
            candidateHeight / candidateRows - AppItemPadding * 2
        )
        // 网格格子：(内容宽 - 列间距) / 列数 - AppItem 内边距×2
        val gridCellDp = (contentWidth - GridCellSpacing * (iconGridCols - 1)) / iconGridCols -
            AppItemPadding * 2
        maxOf(candidateDp, gridCellDp).roundToPx().coerceAtLeast(1)
    }
    // 预热当前查询命中列表的前几个。放在 LaunchedEffect 而不是 token 的写点上，
    // 是为了快速连打时自动取消上一次预热，只留最终查询的那一批。
    LaunchedEffect(state.tokens, state.appListState, iconSizePx) {
        state.prewarmIcons(state.filteredApps, iconSizePx)
    }
    Column(
        modifier
            .fillMaxSize()
            .padding(horizontal = PanelPaddingHorizontal, vertical = PanelPaddingVertical),
    ) {
        Box(
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
                },
        ) {
            AnimatedContent(
                targetState = currentPage,
                transitionSpec = {
                    fadeIn(animationSpec = tween(MotionContentIn)) togetherWith
                        fadeOut(animationSpec = tween(MotionContentOut))
                },
                label = "pageSwitch"
            ) { page ->
                when (page) {
                    Page.App -> AnimatedContent(
                        targetState = effectiveKeyboardExpanded,
                        transitionSpec = {
                            fadeIn(animationSpec = tween(MotionContentIn)) togetherWith
                                fadeOut(animationSpec = tween(MotionContentOut)) using
                                SizeTransform(clip = false)
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
                                    keyboardExpanded = effectiveKeyboardExpanded,
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

                    Page.Settings -> SettingsPageContent(
                        contentHeight = contentHeight,
                        onUpdateLayout = { state.setPreviewLayout(it) },
                        onNavigateToApp = { currentPage = Page.App; state.keyboardExpanded = true },
                    )
                }
            }
        }
    }
}
