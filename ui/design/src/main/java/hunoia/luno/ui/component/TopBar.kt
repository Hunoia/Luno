package hunoia.luno.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import dev.chrisbanes.haze.HazeState
import hunoia.luno.design.R
import hunoia.luno.ui.theme.CapsuleShape
import hunoia.luno.ui.theme.TopBarBlurRadius
import hunoia.luno.ui.theme.TopBarHazeAlpha
import hunoia.luno.ui.theme.PageGutter
import hunoia.luno.ui.theme.TopBarTitlePadding
import hunoia.luno.ui.theme.pageSurfaceColor
import hunoia.luno.ui.theme.raisedSurfaceColor
import hunoia.luno.ui.theme.appHazeEffect
import hunoia.luno.ui.theme.hazeSurfaceColor
import hunoia.luno.ui.theme.topBarMask

/**
 * 悬浮顶栏宿主：把顶栏画在内容**之上**，并量出它（含自身状态栏让位）的完整高度
 * 回报给调用方，作为内容层自己的 top padding。
 *
 * 内容层必须铺满整屏、**不**被顶栏占位裁掉视口：否则滚动内容会在顶栏下沿直接消失，
 * 磨砂顶栏底下也没有内容可糊，看起来像「顶栏被内容夹没」。这样内容从顶栏底下穿过，
 * 磨砂层才有东西可糊；顶栏收拢时高度实时回传，内容顶部让位跟着收，不空出一段
 * （ui-guidelines §4.4 顶栏条目）。
 */
@Composable
internal fun FloatingTopBar(
    onHeightChange: (Dp) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val density = LocalDensity.current
    Box(
        modifier = modifier
            .fillMaxWidth()
            .onSizeChanged { onHeightChange(with(density) { it.height.toDp() }) },
    ) {
        content()
    }
}

/**
 * 顶栏收拢状态：`fraction` 0=完全展开（112dp）、1=完全收拢（64dp）。
 *
 * 不用 M3 单行 `TopAppBar` 的滚动行为——单行没有收拢区间，`exitUntilCollapsedScrollBehavior`
 * 在它上面退化成「整根顶栏随内容上滑滚出」，这就是「顶栏全部隐藏」的根因。这里由
 * [rememberLunoTopBarScrollBehavior] 挂 `nestedScrollConnection`，内容上滑把 fraction 推大、
 * 下滑推小（跟手），顶栏高度与磨砂背景 alpha 都读它。
 */
class LunoTopBarScrollBehavior(
    internal val state: LunoTopBarState,
    val nestedScrollConnection: NestedScrollConnection,
) {
    /** 0..1，收拢进度（实时读 state，滚动时组合读到此值会重组）。 */
    val collapsedFraction: Float get() = state.fraction
}

class LunoTopBarState {
    var fraction by mutableFloatStateOf(0f)
        internal set
}

/**
 * 顶栏滚动行为：内容上滑收拢（fraction→1）、下滑展开（fraction→0），跟手、不消费滚动
 * （顶栏是装饰性 overlay，内容照常滚，不占滚动量）。收拢区间 = 展开高 − 收起高。
 */
@Composable
fun rememberLunoTopBarScrollBehavior(): LunoTopBarScrollBehavior {
    val state = remember { LunoTopBarState() }
    val density = LocalDensity.current
    val collapseRangePx = with(density) {
        (TopAppBarDefaults.MediumAppBarExpandedHeight - TopAppBarDefaults.MediumAppBarCollapsedHeight)
            .toPx()
    }
    val connection = remember(state, collapseRangePx) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                // 只跟手指拖动；程序化滚动（SideEffect/scrollTo）不推顶栏。
                if (source != NestedScrollSource.Drag) return Offset.Zero
                val delta = available.y
                if (delta == 0f) return Offset.Zero
                val target = (state.fraction - delta / collapseRangePx).coerceIn(0f, 1f)
                if (target != state.fraction) state.fraction = target
                return Offset.Zero
            }
        }
    }
    return remember(state, connection) {
        LunoTopBarScrollBehavior(state, connection)
    }
}

/**
 * 顶栏动作单胶囊组（FlClash 顶栏动作组同款）：多个 icon-only 动作共享一个
 * `surfaceContainerHigh` 胶囊底，不逐按钮画圆底。恒可见、不随滚动淡出；收拢标题时
 * 动作组照常停在右上。返回键也用本组（单按钮胶囊，与动作组同款底）。
 *
 * 何时不用：动作里含文字按钮（如「保存」）时，文字按钮留在组外（页面传
 * `groupActions = false` 或自行只把图标包进本组）。
 */
@Composable
fun TopBarActionGroup(
    modifier: Modifier = Modifier,
    /** 胶囊底：顶栏随收拢在 `surfaceContainer`(顶栏色) ↔ `surfaceBright`(列表色) 间过渡。 */
    color: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
    content: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier = modifier
            .clip(CapsuleShape)
            .background(color),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

/**
 * 单行收拢顶栏：112dp ↔ 64dp，标题/返回/动作组**常驻不隐藏**，只缩高度（FlClash 顶栏同款）。
 * 磨砂背景 alpha = 0.7 × 收拢进度——**内容没上滑（fraction=0）时顶栏透明**，上滑后才
 * 淡入磨砂（问题 3）。返回键与动作组都包进 `surfaceContainerHigh` 单胶囊底（问题 2）。
 *
 * 收拢动画全部由 [scrollBehavior] 驱动（跟手）；不用 M3 单行 `TopAppBar`（它会在滚动时把
 * 整根顶栏滚出，见 [LunoTopBarScrollBehavior]）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TopBar(
    modifier: Modifier = Modifier,
    title: String = "",
    onBack: (() -> Unit)? = null,
    actions: (@Composable RowScope.() -> Unit)? = null,
    /** 把 [actions] 包进 [TopBarActionGroup] 单胶囊组；含文字按钮的页面传 false。 */
    groupActions: Boolean = true,
    showBackIcon: Boolean = true,
    onTitleClick: (() -> Unit)? = null,
    titleStyle: TextStyle = MaterialTheme.typography.titleLarge,
    postfixTitle: (@Composable () -> Unit)? = null,
    titleContent: (@Composable () -> Unit)? = null,
    scrollBehavior: LunoTopBarScrollBehavior,
    hazeState: HazeState? = null,
) {
    val fraction = scrollBehavior.collapsedFraction
    val density = LocalDensity.current
    val statusBarInset = with(density) {
        TopAppBarDefaults.windowInsets.getTop(this).toDp()
    }
    val expandedHeight = TopAppBarDefaults.MediumAppBarExpandedHeight
    val collapsedHeight = TopAppBarDefaults.MediumAppBarCollapsedHeight
    val barHeight = statusBarInset + expandedHeight + (collapsedHeight - expandedHeight) * fraction
    // 磨砂背景随收拢淡入：顶部（fraction=0）完全透明且**不模糊**（blur=0），上滑后才起磨砂。
    val surfaceColor = hazeSurfaceColor(alpha = TopBarHazeAlpha * fraction)
    val blurRadius = TopBarBlurRadius * fraction
    // 1b：标题收拢时 titleLarge → titleMedium（只缩字号，不隐藏、不换行）。
    val collapsedTitle = MaterialTheme.typography.titleMedium
    val effectiveTitle = if (fraction <= 0f) {
        titleStyle
    } else {
        titleStyle.copy(
            fontSize = lerp(titleStyle.fontSize, collapsedTitle.fontSize, fraction),
            lineHeight = lerp(titleStyle.lineHeight, collapsedTitle.lineHeight, fraction),
        )
    }
    // 图标胶囊底色：未收起 = 页面底色（顶栏透明时它坐在页面上），收起 = 抬升层色
    // （= 顶栏磨砂底，胶囊融进去），随收拢插值。
    val groupColor = lerp(pageSurfaceColor(), raisedSurfaceColor(), fraction)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(barHeight)
            .appHazeEffect(
                state = hazeState,
                background = surfaceColor,
                blurRadius = blurRadius,
                mask = topBarMask(),
            ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = statusBarInset, start = PageGutter, end = PageGutter),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (showBackIcon && onBack != null) {
                // 返回键：与动作组同款单胶囊底（问题 2）。
                TopBarActionGroup(color = groupColor) {
                    PressIconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.go_back),
                        )
                    }
                }
            }
            Row(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = TopBarTitlePadding),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (titleContent != null) {
                    titleContent()
                } else {
                    Text(
                        modifier = Modifier.then(
                            if (onTitleClick == null) {
                                Modifier
                            } else {
                                Modifier.pressClickable(onClick = onTitleClick)
                            },
                        ),
                        text = title,
                        style = effectiveTitle,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    postfixTitle?.invoke()
                }
            }
            val act = actions
            if (act != null) {
                if (groupActions) {
                    TopBarActionGroup(color = groupColor) { act() }
                } else {
                    act()
                }
            }
        }
    }
}
