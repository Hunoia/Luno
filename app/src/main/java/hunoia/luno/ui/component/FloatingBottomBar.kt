// icon-only bottom bar: click + drag-to-switch, sliding pill indicator.
// Tab 格固定 76dp（BottomBarTabWidth）不 weight —— pill 宽度/位移纯算术，
// 不受 AnimatedVisibility 宽度动画干扰；`+` 圆同根 Row 吃橡皮筋。
package hunoia.luno.ui.component

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseOut
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor as M3LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.util.fastCoerceIn
import androidx.compose.ui.util.fastRoundToInt
import hunoia.luno.ui.animation.DampedDragAnimation
import hunoia.luno.ui.theme.BottomBarHeight
import hunoia.luno.ui.theme.BottomBarIconSize
import hunoia.luno.ui.theme.BottomBarTabWidth
import hunoia.luno.ui.theme.TileInnerPadding
import hunoia.luno.ui.theme.appGlassOutline
import hunoia.luno.ui.theme.appHazeEffect
import dev.chrisbanes.haze.HazeState
import kotlin.math.abs
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@Immutable
class FloatingBottomBarColors(
    val containerColor: Color,
    val indicatorColor: Color,
    val contentColor: Color,
    val activeContentColor: Color,
)

object FloatingBottomBarDefaults {
    @Composable
    fun colors(
        containerColor: Color = MaterialTheme.colorScheme.surfaceContainer,
        indicatorColor: Color = MaterialTheme.colorScheme.primary,
        contentColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
        activeContentColor: Color = indicatorColor,
    ): FloatingBottomBarColors = FloatingBottomBarColors(
        containerColor = containerColor,
        indicatorColor = indicatorColor,
        contentColor = contentColor,
        activeContentColor = activeContentColor,
    )
}

@Composable
fun <T> FloatingBottomBar(
    items: List<T>,
    selectedIndex: () -> Int,
    onSelected: (index: Int) -> Unit,
    hazeState: HazeState,
    modifier: Modifier = Modifier,
    colors: FloatingBottomBarColors = FloatingBottomBarDefaults.colors(),
    iconContent: @Composable (item: T, index: Int) -> Unit,
) {
    val isInDark = androidx.compose.foundation.isSystemInDarkTheme()
    val pillShape = remember { CircleShape }
    // Haze 单层底：blur + 0.65 底色。tint 只放 Haze 一层，组件自身不再画底色，
    // 否则双层叠加发白。
    val hazeBackground = colors.containerColor.copy(alpha = 0.65f)

    val density = LocalDensity.current
    val isLtr = LocalLayoutDirection.current == LayoutDirection.Ltr
    val animationScope = rememberCoroutineScope()
    val tabsCount = items.size

    // 格宽固定（BottomBarTabWidth），一切纯算术：不受 FAB 显隐宽度动画干扰。
    val tabWidthPx = with(density) { BottomBarTabWidth.toPx() }
    val tabsWidthPx = tabWidthPx * tabsCount

    val offsetAnimation = remember { Animatable(0f) }
    val rubberBandPx = with(density) { TileInnerPadding.toPx() }
    val panelOffset by remember(rubberBandPx, tabsWidthPx) {
        derivedStateOf {
            if (tabsWidthPx == 0f) {
                0f
            } else {
                val fraction = (offsetAnimation.value / tabsWidthPx).fastCoerceIn(-1f, 1f)
                rubberBandPx * (if (fraction < 0f) -1f else 1f) * EaseOut.transform(abs(fraction))
            }
        }
    }

    var currentIndex by remember { mutableIntStateOf(selectedIndex()) }
    val selectedIndexUpdated by rememberUpdatedState(selectedIndex)
    val onSelectedUpdated by rememberUpdatedState(onSelected)

    // 拖拽修饰符挂 tabs 区：从 `+` 起手的滑动不带 pill，只有点击。
    fun indexAt(positionX: Float): Int {
        if (tabWidthPx == 0f) return currentIndex
        val logicalX = if (isLtr) positionX else tabsWidthPx - positionX
        return (logicalX / tabWidthPx)
            .toInt()
            .fastCoerceIn(0, tabsCount - 1)
    }

    val dampedDragAnimation = remember(animationScope, tabsCount, density, isLtr) {
        DampedDragAnimation(
            animationScope = animationScope,
            initialValue = currentIndex.toFloat(),
            valueRange = 0f..(tabsCount - 1).toFloat(),
            visibilityThreshold = 0.001f,
            initialScale = 1f,
            pressedScale = 1.2f,
            canDrag = { position ->
                position.x in 0f..tabsWidthPx
            },
            onDragStarted = { position ->
                updateValue(indexAt(position.x).toFloat())
            },
            onDragStopped = {
                val targetIndex = targetValue.fastRoundToInt().fastCoerceIn(0, tabsCount - 1)
                if (currentIndex != targetIndex) {
                    currentIndex = targetIndex
                    onSelectedUpdated(targetIndex)
                }
                updateValue(targetIndex.toFloat())
                animationScope.launch {
                    offsetAnimation.animateTo(0f, spring(1f, 300f, 0.5f))
                }
            },
            onDragCancelled = {
                updateValue(currentIndex.toFloat())
                animationScope.launch {
                    offsetAnimation.animateTo(0f, spring(1f, 300f, 0.5f))
                }
            },
            onDrag = { _, dragAmount ->
                if (tabWidthPx > 0f && dragAmount.x != 0f) {
                    updateValue(
                        (targetValue + dragAmount.x / tabWidthPx * if (isLtr) 1f else -1f)
                            .fastCoerceIn(0f, (tabsCount - 1).toFloat()),
                    )
                    animationScope.launch {
                        offsetAnimation.snapTo(offsetAnimation.value + dragAmount.x)
                    }
                }
            },
        )
    }

    LaunchedEffect(dampedDragAnimation) {
        snapshotFlow { selectedIndexUpdated() }.collectLatest { index ->
            if (currentIndex != index) {
                currentIndex = index
                dampedDragAnimation.animateToValue(index.toFloat())
            }
        }
    }

    val activateTab = remember(dampedDragAnimation) {
        { index: Int ->
            if (currentIndex != index) {
                currentIndex = index
                onSelectedUpdated(index)
            }
            dampedDragAnimation.animateToValue(index.toFloat())
        }
    }

    // 无 ripple 构造：Tab 只有 semantics onClick，按压反馈走 pill 形变。
    val tabsContent: @Composable RowScope.() -> Unit = {
        items.forEachIndexed { index, item ->
            val tabSelected = index == currentIndex
            val tabTint = if (tabSelected) colors.activeContentColor else colors.contentColor
            Box(
                modifier = Modifier
                    .size(
                        width = BottomBarTabWidth,
                        height = BottomBarHeight,
                    )
                    .semantics(mergeDescendants = true) {
                        selected = tabSelected
                        role = Role.Tab
                        onClick {
                            activateTab(index)
                            true
                        }
                    }
                    .onKeyEvent { event ->
                        val isActivationKey = event.key == Key.Enter ||
                            event.key == Key.NumPadEnter ||
                            event.key == Key.Spacebar
                        if (isActivationKey) {
                            if (event.type == KeyEventType.KeyUp) activateTab(index)
                            true
                        } else {
                            false
                        }
                    }
                    .focusable(),
                contentAlignment = Alignment.Center,
            ) {
                CompositionLocalProvider(M3LocalContentColor provides tabTint) {
                    iconContent(item, index)
                }
            }
        }
    }

    // 栏 + `+` 同一个根 Row 吃橡皮筋：整体一起晃，接缝不开合。
    Row(
        modifier = modifier.graphicsLayer { translationX = panelOffset },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.width(BottomBarTabWidth * tabsCount + TileInnerPadding * 2),
            contentAlignment = Alignment.CenterStart,
        ) {
            Row(
                Modifier
                    .dropShadow(
                        shape = pillShape,
                        shadow = Shadow(
                            radius = TileInnerPadding * 2.5f,
                            color = Color.Black,
                            alpha = if (isInDark) 0.2f else 0.1f,
                        ),
                    )
                    // clip 在 haze 之外：方形糊输出裁成胶囊（只裁绘制，不影响采样）。
                    .clip(pillShape)
                    .appHazeEffect(hazeState, hazeBackground)
                    .appGlassOutline(pillShape)
                    .then(dampedDragAnimation.modifier)
                    .height(BottomBarHeight + TileInnerPadding * 2)
                    .padding(TileInnerPadding),
                verticalAlignment = Alignment.CenterVertically,
                content = tabsContent,
            )

            // 滑动指示 pill：跟随拖拽值滑动，按压时放大 + 速度倾斜。
            // 橡皮筋位移由根 Row 统一吃，这里只加 pill 自身位移，别重复加。
            Box(
                modifier = Modifier
                    .padding(horizontal = TileInnerPadding)
                    .graphicsLayer {
                        val progressOffset = dampedDragAnimation.value * tabWidthPx
                        translationX = if (isLtr) progressOffset else -progressOffset
                        scaleX = dampedDragAnimation.scaleX
                        scaleY = dampedDragAnimation.scaleY
                        val velocity = (dampedDragAnimation.velocity / 10f).fastCoerceIn(-0.2f, 0.2f)
                        scaleX /= 1f - velocity * 0.75f
                        scaleY *= 1f - velocity * 0.25f
                    }
                    .clip(pillShape)
                    .background(colors.indicatorColor.copy(alpha = 0.15f), pillShape)
                    .height(BottomBarHeight)
                    .width(BottomBarTabWidth),
            )
        }
    }
}
