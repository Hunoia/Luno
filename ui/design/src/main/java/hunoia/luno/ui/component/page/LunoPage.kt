package hunoia.luno.ui.component.page

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.ime
import dev.chrisbanes.haze.HazeState
import hunoia.luno.ui.component.FloatingTopBar
import hunoia.luno.ui.component.TopBar
import hunoia.luno.ui.component.rememberLunoTopBarScrollBehavior
import hunoia.luno.ui.theme.rememberAppHazeState

/**
 * 唯一页面骨架：顶栏 / 可选悬浮底栏 / haze / 弹层槽 / 一级 tab 宿主只在这里拼一次。
 *
 * 内容层不手写 `Scaffold`、`contentPadding`、`imePadding` 或底栏高度：
 * 用 [LunoPageList] / [LunoPageColumn] / [LunoPageRaw]，它们从 [LunoPageEnv] 读实测值。
 *
 * [bottomBarInitialHeight] 只用于首帧兜底，避免底栏量到之前内容先按 0 留白再跳一下；
 * 实测值到达后直接覆盖，不做二次动画（IME 已逐帧，见 ui-guidelines §3.2 第 9 条）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LunoPage(
    title: String,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    showBackIcon: Boolean = true,
    actions: (@Composable RowScope.() -> Unit)? = null,
    groupActions: Boolean = true,
    postfixTitle: (@Composable () -> Unit)? = null,
    bottomBar: (@Composable BoxScope.(HazeState) -> Unit)? = null,
    bottomBarInitialHeight: Dp = 0.dp,
    floatingContent: (@Composable BoxScope.(HazeState) -> Unit)? = null,
    dialogs: @Composable () -> Unit = {},
    sheets: @Composable () -> Unit = {},
    content: @Composable LunoPageScope.() -> Unit,
) {
    val scrollBehavior = rememberLunoTopBarScrollBehavior()
    val hazeState = rememberAppHazeState()
    ClearFocusWhenImeHidden()
    // 顶栏高度（含它自己的状态栏让位）：先按「展开高 + 顶栏自己那套 windowInsets 顶 inset」兜底，
    // 量到真实高度后回写；顶栏收拢时实时回传，内容顶部让位跟着收。
    val density = LocalDensity.current
    val statusBarInset = with(density) {
        TopAppBarDefaults.windowInsets.getTop(this).toDp()
    }
    val topBarHeight = remember {
        mutableStateOf(TopAppBarDefaults.MediumAppBarExpandedHeight + statusBarInset)
    }
    var measuredBottomBarHeight by remember { mutableStateOf(bottomBarInitialHeight) }
    val effectiveBottomBarHeight = if (bottomBar == null) 0.dp else measuredBottomBarHeight
    val env = remember(
        topBarHeight.value,
        effectiveBottomBarHeight,
        hazeState,
        scrollBehavior,
        bottomBar != null,
    ) {
        LunoPageEnv(
            topPadding = topBarHeight.value,
            bottomPadding = effectiveBottomBarHeight,
            hazeState = hazeState,
            nestedScrollConnection = scrollBehavior.nestedScrollConnection,
            hasBottomBar = bottomBar != null,
        )
    }

    Box(modifier = modifier.fillMaxSize()) {
        // 顶栏不占 Scaffold 的 topBar 槽：占槽会把内容视口裁在顶栏下沿，滚动内容到不了
        // 顶栏底下，磨砂层底下没有内容可糊。改成 overlay 悬浮后内容从顶栏底下穿过。
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            contentWindowInsets = WindowInsets(),
        ) { _ ->
            Box(modifier = Modifier.fillMaxSize()) {
                CompositionLocalProvider(LocalLunoPageEnv provides env) {
                    val scope = remember { LunoPageScope() }
                    scope.content()
                }
                val bar = bottomBar
                if (bar != null) {
                    Box(
                        modifier = Modifier
                            .align(androidx.compose.ui.Alignment.BottomCenter)
                            .fillMaxWidth()
                            .onSizeChanged { size ->
                                measuredBottomBarHeight = with(density) { size.height.toDp() }
                            },
                    ) {
                        bar(hazeState)
                    }
                }
            }
        }
        FloatingTopBar(onHeightChange = { topBarHeight.value = it }) {
            TopBar(
                title = title,
                onBack = onBack,
                actions = actions,
                groupActions = groupActions,
                showBackIcon = showBackIcon,
                postfixTitle = postfixTitle,
                scrollBehavior = scrollBehavior,
                hazeState = hazeState,
            )
        }
        floatingContent?.invoke(this, hazeState)
        dialogs()
        sheets()
    }
}

/**
 * 页面内容槽：页面只选择内容形态，不再自己拼容器。
 *
 * - [list]：长页面 / 列表页面，默认。
 * - [column]：一屏能读完的短页面。
 * - [raw]：内容自己持有滚动与 `weight(1f)`（动作选择页、黑名单页）。
 */
class LunoPageScope internal constructor() {
    @Composable
    fun list(
        modifier: Modifier = Modifier,
        state: LazyListState = rememberLazyListState(),
        contentSpacing: Dp = hunoia.luno.ui.theme.ListSpacing,
        contentPadding: Dp = hunoia.luno.ui.theme.PageGutter,
        bottomContentPadding: Dp = hunoia.luno.ui.theme.ContentBottom,
        content: @Composable LunoPageListScope.() -> Unit,
    ) {
        LunoPageList(
            modifier = modifier,
            state = state,
            contentSpacing = contentSpacing,
            contentPadding = contentPadding,
            bottomContentPadding = bottomContentPadding,
            content = content,
        )
    }

    @Composable
    fun column(
        modifier: Modifier = Modifier,
        contentSpacing: Dp = hunoia.luno.ui.theme.ListSpacing,
        contentPadding: Dp = hunoia.luno.ui.theme.PageGutter,
        bottomContentPadding: Dp = hunoia.luno.ui.theme.ContentBottom,
        content: @Composable LunoPageColumnScope.() -> Unit,
    ) {
        LunoPageColumn(
            modifier = modifier,
            contentSpacing = contentSpacing,
            contentPadding = contentPadding,
            bottomContentPadding = bottomContentPadding,
            content = content,
        )
    }

    @Composable
    fun raw(
        modifier: Modifier = Modifier,
        content: @Composable BoxScope.(LunoPageEnv) -> Unit,
    ) {
        LunoPageRaw(modifier = modifier, content = content)
    }
}

/** 键盘收起后自动取消焦点：输入框不再保持高亮 / 光标，返回键恢复正常导航。 */
@Composable
private fun ClearFocusWhenImeHidden() {
    val focusManager = LocalFocusManager.current
    val density = LocalDensity.current
    val imeInsets = WindowInsets.ime
    // 派生状态：IME inset 在键盘动画期间逐帧变化，直接读会让整个屏幕逐帧重组，
    // 只在「可见/不可见」翻转时触发。
    val imeVisible by remember(density, imeInsets) {
        derivedStateOf { imeInsets.getBottom(density) > 0 }
    }
    LaunchedEffect(imeVisible) {
        if (!imeVisible) focusManager.clearFocus()
    }
}