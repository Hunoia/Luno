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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import dev.chrisbanes.haze.HazeState
import hunoia.luno.ui.component.FloatingTopBar
import hunoia.luno.ui.component.TopBar
import hunoia.luno.ui.component.rememberLunoTopBarScrollBehavior
import hunoia.luno.ui.theme.appHazeSource
import hunoia.luno.ui.theme.rememberAppHazeState

/**
 * 主页外壳：无返回键的标签页页面，[bottomBar] 悬浮在内容之上；骨架、haze 状态、
 * `nestedScroll` 只在这里拼一次。
 *
 * haze 状态由外壳创建并同时交给内容层与底栏：底栏 blur 采样的必须是同一层，
 * 各自建实例会断链，调用方不要另建。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeShell(
    title: String,
    modifier: Modifier = Modifier,
    actions: (@Composable RowScope.() -> Unit)? = null,
    groupActions: Boolean = true,
    dialogs: @Composable () -> Unit = {},
    sheets: @Composable () -> Unit = {},
    bottomBar: @Composable BoxScope.(hazeState: HazeState) -> Unit = {},
    content: @Composable BoxScope.(topPadding: Dp, hazeState: HazeState) -> Unit,
) {
    val scrollBehavior = rememberLunoTopBarScrollBehavior()
    val hazeState = rememberAppHazeState()
    // 顶栏高度（含状态栏让位）：先按「展开高 + 顶栏自己那套 windowInsets 顶 inset」兜底，
    // 量到真实高度后回写，收拢时跟着收。
    val density = LocalDensity.current
    val statusBarInset = with(density) {
        TopAppBarDefaults.windowInsets.getTop(this).toDp()
    }
    val topBarHeight = remember {
        mutableStateOf(TopAppBarDefaults.MediumAppBarExpandedHeight + statusBarInset)
    }
    Box(modifier = modifier.fillMaxSize()) {
        // 顶栏不占 Scaffold 的 topBar 槽（占槽会把内容视口裁在顶栏下沿，滚动内容到不了
        // 顶栏底下，磨砂层底下没有内容可糊）。改 overlay 悬浮：内容从顶栏底下穿过。
        Scaffold(
            modifier = Modifier
                .fillMaxSize()
                .nestedScroll(scrollBehavior.nestedScrollConnection),
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            contentWindowInsets = WindowInsets(),
        ) { _ ->
            // 必须显式开这一层：Scaffold 的 content receiver 是 PaddingValues，没有 BoxScope，
            // 下面底栏的 Modifier.align 会被静默解析到更外层的 Box，底栏就会跑到顶部去。
            Box(modifier = Modifier.fillMaxSize()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .appHazeSource(hazeState),
                ) {
                    content(topBarHeight.value, hazeState)
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
        FloatingTopBar(onHeightChange = { topBarHeight.value = it }) {
            TopBar(
                title = title,
                onBack = null,
                actions = actions,
                groupActions = groupActions,
                showBackIcon = false,
                scrollBehavior = scrollBehavior,
                hazeState = hazeState,
            )
        }
        dialogs()
        sheets()
    }
}
