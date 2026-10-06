package hunoia.luno.ui.component.screen

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.Dp
import dev.chrisbanes.haze.HazeState
import hunoia.luno.ui.component.TopBar
import hunoia.luno.ui.theme.appHazeSource
import hunoia.luno.ui.theme.rememberAppHazeState

/**
 * 主页外壳（见 docs/ui-spec.md §3）：无返回键的标签页页面，底部悬浮底栏。
 *
 * 与 [LunoScreen] 的差别只有两点：没有返回键、[bottomBar] 悬浮在内容之上。
 * 骨架、haze 状态、`nestedScroll` 仍然只在这里拼一次。
 *
 * **haze 状态由外壳创建并同时交给内容层与底栏** —— 底栏的 blur 要采样的是同一层，
 * 各自建实例会断掉这条链，所以这里把它作为参数传出去，不要在调用方另建。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeShell(
    title: String,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {},
    dialogs: @Composable () -> Unit = {},
    sheets: @Composable () -> Unit = {},
    bottomBar: @Composable BoxScope.(hazeState: HazeState) -> Unit = {},
    content: @Composable BoxScope.(topPadding: Dp, hazeState: HazeState) -> Unit,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val hazeState = rememberAppHazeState()
    Box(modifier = modifier.fillMaxSize()) {
        Scaffold(
            modifier = Modifier
                .fillMaxSize()
                .nestedScroll(scrollBehavior.nestedScrollConnection),
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            contentWindowInsets = WindowInsets(),
            topBar = {
                TopBar(
                    title = title,
                    onBack = null,
                    actions = actions,
                    showBackIcon = false,
                    scrollBehavior = scrollBehavior,
                    hazeState = hazeState,
                )
            },
        ) { padding ->
            // 必须显式开这一层：Scaffold 的 content receiver 是 PaddingValues，没有 BoxScope，
            // 下面底栏的 Modifier.align 会被静默解析到更外层的 Box，底栏就会跑到顶部去。
            Box(modifier = Modifier.fillMaxSize()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .appHazeSource(hazeState),
                ) {
                    content(padding.calculateTopPadding(), hazeState)
                }
                // 底栏是 hazeEffect，不录入自己的采样源。
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth(),
                ) {
                    bottomBar(hazeState)
                }
            }
        }
        dialogs()
        sheets()
    }
}
