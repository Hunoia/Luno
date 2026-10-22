package hunoia.luno.ui.component.screen

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import dev.chrisbanes.haze.HazeState
import hunoia.luno.ui.component.LunoColumn
import hunoia.luno.ui.component.TopBar
import hunoia.luno.ui.theme.ContentBottom
import hunoia.luno.ui.theme.ListSpacing
import hunoia.luno.ui.theme.PageGutter
import hunoia.luno.ui.theme.appHazeSource
import hunoia.luno.ui.theme.rememberAppHazeState

/**
 * 屏幕模板：`Scaffold` / `TopBar` / haze / `nestedScroll` / 内容容器全部私有，
 * 界面只给标题、动作与内容。固定项：`surfaceContainer`、空 `contentWindowInsets`、
 * `exitUntilCollapsedScrollBehavior`、haze 只由 `rememberAppHazeState()` 提供、
 * `nestedScroll` 挂内容容器、只取 Scaffold top padding。[dialogs]/[sheets] 是弹层唯一宿主。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LunoScreen(
    title: String,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    actions: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit = {},
    postfixTitle: (@Composable () -> Unit)? = null,
    floatingContent: (@Composable BoxScope.(hazeState: HazeState) -> Unit)? = null,
    contentSpacing: Dp = ListSpacing,
    contentPadding: Dp = PageGutter,
    bottomContentPadding: Dp = ContentBottom,
    animateContent: Boolean = false,
    dialogs: @Composable () -> Unit = {},
    sheets: @Composable () -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val hazeState = rememberAppHazeState()
    ClearFocusWhenImeHidden()
    Box(modifier = modifier.fillMaxSize()) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            contentWindowInsets = WindowInsets(),
            topBar = {
                TopBar(
                    title = title,
                    onBack = onBack,
                    actions = actions,
                    postfixTitle = postfixTitle,
                    scrollBehavior = scrollBehavior,
                    hazeState = hazeState,
                )
            },
        ) { padding ->
            LunoColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .appHazeSource(hazeState)
                    .nestedScroll(scrollBehavior.nestedScrollConnection)
                    .then(if (animateContent) Modifier.animateContentSize() else Modifier),
                topPadding = padding.calculateTopPadding(),
                verticalArrangement = Arrangement.spacedBy(contentSpacing),
                contentPadding = contentPadding,
                bottomPadding = bottomContentPadding,
                content = { content() },
            )
        }
        floatingContent?.invoke(this, hazeState)
        dialogs()
        sheets()
    }
}

/**
 * 全屏内容模板：骨架同 [LunoScreen]，但内容容器由调用方自己拼，用于内容本身是
 * 滚动容器、内部还嵌 `weight(1f)` 列表的页面（动作选择页）——另两个模板的容器
 * 都带滚动或撑不满。模板负责 Scaffold / TopBar / `nestedScroll`，把 top padding
 * 与 [HazeState] 交给调用方。
 *
 * 注意：模板**不**给内容层挂 `appHazeSource`。调用方要把滚动内容自己标成 source，
 * 悬浮底栏才能作为它的**兄弟**节点画 hazeEffect —— 把 effect 嵌在 source 里会采到
 * 自己，胶囊底看起来像完全透明（主页 HomeShell 也是同样的兄弟结构）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LunoScreenPlain(
    title: String,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    showBackIcon: Boolean = true,
    actions: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit = {},
    postfixTitle: (@Composable () -> Unit)? = null,
    floatingContent: (@Composable () -> Unit)? = null,
    dialogs: @Composable () -> Unit = {},
    sheets: @Composable () -> Unit = {},
    content: @Composable (topPadding: Dp, hazeState: HazeState) -> Unit,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val hazeState = rememberAppHazeState()
    ClearFocusWhenImeHidden()
    Box(modifier = modifier.fillMaxSize()) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            contentWindowInsets = WindowInsets(),
            topBar = {
                TopBar(
                    title = title,
                    onBack = onBack,
                    actions = actions,
                    showBackIcon = showBackIcon,
                    postfixTitle = postfixTitle,
                    scrollBehavior = scrollBehavior,
                    hazeState = hazeState,
                )
            },
        ) { padding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .nestedScroll(scrollBehavior.nestedScrollConnection),
            ) {
                content(padding.calculateTopPadding(), hazeState)
            }
        }
        floatingContent?.invoke()
        dialogs()
        sheets()
    }
}

/**
 * 列表页模板：骨架与 [LunoScreen] 完全相同，只把内容容器换成 LazyColumn。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LunoScreenLazy(
    title: String,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    actions: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit = {},
    postfixTitle: (@Composable () -> Unit)? = null,
    floatingContent: (@Composable () -> Unit)? = null,
    contentSpacing: Dp = ListSpacing,
    contentPadding: Dp = PageGutter,
    bottomContentPadding: Dp = ContentBottom,
    state: LazyListState = rememberLazyListState(),
    dialogs: @Composable () -> Unit = {},
    sheets: @Composable () -> Unit = {},
    content: LazyListScope.() -> Unit,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val hazeState = rememberAppHazeState()
    ClearFocusWhenImeHidden()
    Box(modifier = modifier.fillMaxSize()) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            contentWindowInsets = WindowInsets(),
            topBar = {
                TopBar(
                    title = title,
                    onBack = onBack,
                    actions = actions,
                    postfixTitle = postfixTitle,
                    scrollBehavior = scrollBehavior,
                    hazeState = hazeState,
                )
            },
        ) { padding ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .appHazeSource(hazeState)
                    .nestedScroll(scrollBehavior.nestedScrollConnection),
                state = state,
                verticalArrangement = Arrangement.spacedBy(contentSpacing),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    start = contentPadding,
                    end = contentPadding,
                    top = padding.calculateTopPadding(),
                    bottom = bottomContentPadding,
                ),
                content = { content() },
            )
        }
        floatingContent?.invoke()
        dialogs()
        sheets()
    }
}

/** 键盘收起后自动取消焦点：输入框不再保持高亮/光标，返回键恢复正常导航。 */
@Composable
private fun ClearFocusWhenImeHidden() {
    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
    val imeVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0
    LaunchedEffect(imeVisible) {
        if (!imeVisible) focusManager.clearFocus()
    }
}
