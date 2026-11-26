package hunoia.luno.ui.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState
import hunoia.luno.ui.theme.BottomBarIconSize
import hunoia.luno.ui.theme.BottomBarTabWidth
import hunoia.luno.ui.theme.DockBallSize
import hunoia.luno.ui.theme.DockCapsuleHeight
import hunoia.luno.ui.theme.DockGap
import hunoia.luno.ui.theme.DockGutter
import hunoia.luno.ui.theme.DockTabHeight
import hunoia.luno.ui.theme.MotionSubtle
import hunoia.luno.ui.theme.MotionSwapOut
import hunoia.luno.ui.theme.TileInnerPadding
import hunoia.luno.ui.theme.glassCapsule

/** 主页底栏当前展开的导航球。功能球永不展开，所以没有第三态。 */
enum class HomeDockSide { NAV, SECONDARY }

val DefaultHomeDockSide = HomeDockSide.NAV

/** 导航球展开后的一个 tab：纯图标，名称只进无障碍描述。 */
@Immutable
class HomeDockTab(
    val id: String,
    val icon: ImageVector,
    val label: String,
)

/**
 * 三球停靠栏的几何：宽屏限宽、窄屏压缩 tab。
 *
 * 一行三槽：展开的胶囊 + 主导航球 + 记录导航球，槽与槽之间各一个间隙；右端的功能球槽
 * 显隐由页面决定。tab 宽按 `[reservedWidth]` 扣掉固定占位与胶囊内边距后均分：
 * `(dockWidth - reservedWidth - 2×内边距) / tabCount`。
 *
 * 算术委托给 [FloatingBarLayout]，与类型 chip 底栏共用同一份；这里只固定「收起槽 = 球 + 间隙」
 * 这条三球专属规则。`BottomBarTabWidth` / `TileInnerPadding` 由调用方传入，避免这里触发
 * `Dimension.kt` 的类初始化（见 DockDimension.kt 的说明）。
 */
object HomeDockLayout {
    /** 一个收起槽的固定宽度：60dp 球 + 6dp 间隙。 */
    val CollapsedSlot: Dp = DockBallSize + DockGap

    fun dockWidth(availableWidth: Dp): Dp = FloatingBarLayout.barWidth(availableWidth)

    /**
     * [reservedWidth] 是同一行里不属于 tab 的占位：左侧导航球（恒在）加上右端功能球槽
     * （隐藏时为 0）。由调用方按当前动画宽度算好传入，见 HomeDock 的 `functionBallReservedWidth`。
     */
    fun tabWidth(
        dockWidth: Dp,
        tabCount: Int,
        maxTabWidth: Dp,
        innerPadding: Dp,
        reservedWidth: Dp = CollapsedSlot * 2,
    ): Dp = FloatingBarLayout.tabWidth(
        barWidth = dockWidth,
        tabCount = tabCount,
        maxTabWidth = maxTabWidth,
        innerPadding = innerPadding,
        reservedWidth = reservedWidth,
    )

    fun capsuleWidth(tabWidth: Dp, tabCount: Int, innerPadding: Dp): Dp =
        FloatingBarLayout.capsuleWidth(tabWidth, tabCount, innerPadding)

    /** 整组实际宽度：胶囊 + 左侧导航球 + 右端收起球（功能球槽隐藏时为 0）。 */
    fun rowWidth(capsuleWidth: Dp, trailingSlotWidth: Dp = CollapsedSlot): Dp =
        capsuleWidth + CollapsedSlot + trailingSlotWidth
}

/**
 * 功能球槽位为胶囊预留的宽度：一颗球加左侧间隙恒占，槽位再加它与 secondary 之间的间隙
 * （随球宽比例收放，宽度归零时间隙也归零）与球自身的动画宽度。全部连续，无 6dp 快照。
 */
private fun functionBallReservedWidth(ballWidth: Dp): Dp =
    (DockBallSize + DockGap) + DockGap * (ballWidth / DockBallSize) + ballWidth

/**
 * 主页三球停靠栏：左主导航球 / 中记录导航球 / 右功能球。
 *
 * 两颗导航球互斥展开：点收起的那颗会切到它显示的 tab 并展开（回调由调用方负责切页）；
 * 点已展开的球无反应。功能球永远是小球，直接在收起态执行。
 *
 * 收起态与展开态同高（60dp）：球径 = 胶囊外高，横向形变，内容交叉淡入。
 * 玻璃（阴影 / 裁形 / 毛玻璃 / 描边 / 内边距）挂在形变节点本身，动画期间阴影不被硬裁。
 */
@Composable
fun HomeDock(
    navTabs: List<HomeDockTab>,
    navSelectedIndex: Int,
    onNavSelected: (Int) -> Unit,
    navBallIcon: ImageVector,
    navBallLabel: String,
    navBallClickLabel: String,
    onNavBallClick: () -> Unit,
    secondaryTabs: List<HomeDockTab>,
    secondarySelectedIndex: Int,
    onSecondarySelected: (Int) -> Unit,
    secondaryBallIcon: ImageVector,
    secondaryBallLabel: String,
    secondaryBallClickLabel: String,
    onSecondaryBallClick: () -> Unit,
    functionBallIcon: ImageVector,
    functionBallLabel: String,
    onFunctionBallClick: () -> Unit,
    showFunctionBall: Boolean,
    expandedSide: HomeDockSide,
    hazeState: HazeState,
    modifier: Modifier = Modifier,
    functionMenu: @Composable () -> Unit = {},
    colors: FloatingBottomBarColors = FloatingBottomBarDefaults.colors(),
) {
    BoxWithConstraints(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center,
    ) {
        // 左右各留 DockGutter；整组在限宽内居中，不再把球拉到两端。
        val dockWidth = HomeDockLayout.dockWidth(maxWidth - DockGutter * 2)
        // 功能球槽位的宽度走动画（0↔60dp）：宽度归零时球连同它和 secondary 之间的间隙一起
        // 连续收走（间隙随宽度比例收放），胶囊的收回与功能球的出现同一条曲线，
        // 不再「行先跳宽、间隙再硬消失」。
        val functionBallWidth by animateDpAsState(
            targetValue = if (showFunctionBall) DockBallSize else 0.dp,
            animationSpec = tween(MotionSubtle),
            label = "dockFunctionBallWidth",
        )
        // 球与 secondary 之间的间隙随球宽比例收放：球宽→0 时间隙→0，两端无 6dp 快照。
        val functionGap = DockGap * (functionBallWidth / DockBallSize)
        // 轻淡入：前段快速到全不透明（宽度过半前基本实心），滑出反向。
        val functionBallAlpha = (functionBallWidth / DockBallSize * 2f).coerceIn(0f, 1f)
        // 槽位还有剩余宽度时保留（正在收走），宽度归零才算真的隐藏：隐藏态布局与原来完全一致。
        val functionBallVisible = showFunctionBall || functionBallWidth > 0.dp
        val navTabWidth = HomeDockLayout.tabWidth(
            dockWidth = dockWidth,
            tabCount = navTabs.size,
            maxTabWidth = BottomBarTabWidth,
            innerPadding = TileInnerPadding,
            reservedWidth = functionBallReservedWidth(functionBallWidth),
        )
        val secondaryTabWidth = HomeDockLayout.tabWidth(
            dockWidth = dockWidth,
            tabCount = secondaryTabs.size,
            maxTabWidth = BottomBarTabWidth,
            innerPadding = TileInnerPadding,
            reservedWidth = functionBallReservedWidth(functionBallWidth),
        )

        Box(
            modifier = Modifier
                .width(dockWidth)
                .height(DockCapsuleHeight)
                // 停靠栏矩形范围内的空白先兜底消费：不挂 pointerInput 的区域会漏到下层列表行。
                .swallowTouches(),
            contentAlignment = Alignment.BottomCenter,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                DockSlot(
                    expanded = expandedSide == HomeDockSide.NAV,
                    tabWidth = navTabWidth,
                    tabCount = navTabs.size,
                    tabs = navTabs,
                    selectedIndex = navSelectedIndex,
                    onSelected = onNavSelected,
                    expandedAlignment = Alignment.CenterStart,
                    ballIcon = navBallIcon,
                    ballTint = colors.contentColor,
                    ballLabel = navBallLabel,
                    ballOnClickLabel = navBallClickLabel,
                    onBallClick = onNavBallClick,
                    hazeState = hazeState,
                    colors = colors,
                )

                Spacer(Modifier.width(DockGap))

                DockSlot(
                    expanded = expandedSide == HomeDockSide.SECONDARY,
                    tabWidth = secondaryTabWidth,
                    tabCount = secondaryTabs.size,
                    tabs = secondaryTabs,
                    selectedIndex = secondarySelectedIndex,
                    onSelected = onSecondarySelected,
                    expandedAlignment = Alignment.Center,
                    ballIcon = secondaryBallIcon,
                    ballTint = colors.contentColor,
                    ballLabel = secondaryBallLabel,
                    ballOnClickLabel = secondaryBallClickLabel,
                    onBallClick = onSecondaryBallClick,
                    hazeState = hazeState,
                    colors = colors,
                )

                // 功能球槽：整颗 60dp 球从右缘平移滑入/滑出（前段轻淡入），不再随宽度缩放成
                // 「压缩小球」；球与 secondary 之间是随球宽收放的间隙，槽位移除时已归零。
                if (functionBallVisible) {
                    Spacer(Modifier.width(functionGap))
                    Box(
                        modifier = Modifier
                            .width(functionBallWidth)
                            .height(DockCapsuleHeight)
                            .graphicsLayer {
                                translationX = (DockBallSize - functionBallWidth).toPx()
                                alpha = functionBallAlpha
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        FunctionBall(
                            icon = functionBallIcon,
                            label = functionBallLabel,
                            tint = colors.contentColor,
                            onClick = onFunctionBallClick,
                            hazeState = hazeState,
                            menu = functionMenu,
                        )
                    }
                }
            }
        }
    }
}

/**
 * 一颗可展开的导航球：收起时是 60dp 球，展开时按 tab 数算宽。
 *
 * 点按区在玻璃内边距之外：收起态整颗球都能命中。展开态由 tab 行自己接收点击。
 */
@Composable
private fun DockSlot(
    expanded: Boolean,
    tabWidth: Dp,
    tabCount: Int,
    tabs: List<HomeDockTab>,
    selectedIndex: Int,
    onSelected: (Int) -> Unit,
    expandedAlignment: Alignment,
    ballIcon: ImageVector,
    ballTint: Color,
    ballLabel: String,
    ballOnClickLabel: String,
    onBallClick: () -> Unit,
    hazeState: HazeState,
    colors: FloatingBottomBarColors,
) {
    // tab 宽由调用方（HomeDock）按功能球动画宽度逐帧算好传入，这里不再叠一层 tween 追逐，
    // 否则胶囊宽度会落后整行位移一两个帧、收尾再「追上来」。
    val expandedWidth = HomeDockLayout.capsuleWidth(
        tabWidth = tabWidth,
        tabCount = tabCount,
        innerPadding = TileInnerPadding,
    )
    // 球 ↔ 胶囊的形变走单一 progress：展开态宽度 = 胶囊宽（实时跟父级 tab 宽），
    // 收起态 = 球径，过渡按同一 180ms 曲线线性插值。
    val expandProgress by animateFloatAsState(
        targetValue = if (expanded) 1f else 0f,
        animationSpec = tween(MotionSubtle),
        label = "dockExpandProgress",
    )
    val width = DockBallSize + (expandedWidth - DockBallSize) * expandProgress
    // 交叉淡入错峰：进场延迟 = 形变时长 − 淡入时长。展开时球图标先在头 2/3 退场、
    // tab 行在胶囊接近全开时才完全可见；收起时反过来。两态共用同一对 spec，方向自动对称。
    val contentIn = remember {
        fadeIn(tween(MotionSwapOut, delayMillis = MotionSubtle - MotionSwapOut))
    }
    val contentOut = remember { fadeOut(tween(MotionSwapOut)) }
    val clickModifier = if (expanded) {
        Modifier
    } else {
        Modifier
            .semantics { contentDescription = ballLabel }
            .pressClickable(
                role = Role.Button,
                onClickLabel = ballOnClickLabel,
                onClick = onBallClick,
            )
    }

    Box(
        modifier = Modifier
            .width(width)
            .height(DockCapsuleHeight)
            .then(clickModifier)
            .glassCapsule(hazeState),
        contentAlignment = Alignment.Center,
    ) {
        AnimatedVisibility(
            visible = !expanded,
            enter = contentIn,
            exit = contentOut,
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = ballIcon,
                    contentDescription = null,
                    tint = ballTint,
                    modifier = Modifier.size(BottomBarIconSize),
                )
            }
        }
        AnimatedVisibility(
            visible = expanded,
            modifier = Modifier.align(expandedAlignment),
            enter = contentIn,
            exit = contentOut,
        ) {
            FloatingTabRow(
                items = tabs,
                selectedIndex = { selectedIndex },
                onSelected = onSelected,
                tabWidth = tabWidth,
                tabHeight = DockTabHeight,
                colors = colors,
                iconContent = { item, _ ->
                    Icon(
                        imageVector = item.icon,
                        contentDescription = item.label,
                        modifier = Modifier.size(BottomBarIconSize),
                    )
                },
            )
        }
    }
}

/**
 * 功能球：永不展开，收起态直接执行当前页的「添加」。
 *
 * 当前页没有可添加内容时整颗球不渲染（由调用方的 `showFunctionBall` 控制），
 * 而不是显示一个点不动的球；图标跟随具体动作（动作库 +、习惯/待办/趋势各自的添加）。
 */
@Composable
private fun FunctionBall(
    icon: ImageVector,
    label: String,
    tint: Color,
    onClick: () -> Unit,
    hazeState: HazeState,
    menu: @Composable () -> Unit = {},
) {
    Box(
        modifier = Modifier
            .size(DockBallSize)
            .semantics { contentDescription = label }
            .pressClickable(
                role = Role.Button,
                onClick = onClick,
            )
            .glassCapsule(hazeState),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(BottomBarIconSize),
        )
        menu()
    }
}
