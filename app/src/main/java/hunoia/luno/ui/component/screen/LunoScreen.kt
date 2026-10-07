package hunoia.luno.ui.component.screen

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.WindowInsets
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
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
 * 屏幕模板（见 docs/ui-spec.md §3）。
 *
 * `Scaffold` / `TopBar` / haze 状态 / `nestedScroll` / 内容容器的组装**全部是模板私有**，
 * 界面只提供标题、动作与内容 —— 这样"一个界面一套骨架"从物理上不再可能。
 *
 * 固定不变的部分：容器色 `surfaceContainer`、`contentWindowInsets = WindowInsets()`、
 * `exitUntilCollapsedScrollBehavior`、haze 状态只由 `rememberAppHazeState()` 提供、
 * `nestedScroll` 挂在内容容器上、只取 Scaffold 的 top padding。
 *
 * [dialogs] / [sheets] 是弹层宿主，**所有弹层都必须放这里**，位置统一（以前有三种挂法）。
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
 * 全屏内容模板：骨架与 [LunoScreen] 相同，但**内容容器由调用方自己拼**。
 *
 * 用于内容本身就是滚动容器、且内部还有 `weight(1f)` 嵌套列表的页面（动作选择页）——
 * [LunoScreen] / [LunoScreenLazy] 的内容容器都带滚动或撑不满，不能套在它们外面。
 *
 * 模板仍然负责 Scaffold / TopBar / haze 状态 / `nestedScroll`，并把 top padding 与 [HazeState]
 * 作为参数交给调用方，由它决定给列表 `contentPadding` 还是自己留白、以及给 in-flow 底栏做 Haze 底。
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
                    .appHazeSource(hazeState)
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
 * 列表页模板：骨架与 [LunoScreen] 完全相同，只是内容容器换成 LazyColumn。
 * 两者的差异只有这一个，不允许再各自拼 Scaffold。
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
