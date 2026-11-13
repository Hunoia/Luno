// icon-only tab row: click + drag-to-switch, sliding pill indicator.
// Tab 格宽固定（BottomBarTabWidth 或窄屏压缩值）不 weight —— pill 宽度/位移纯算术，
// 不受展开动画宽度干扰。玻璃容器由调用方提供（主页三球停靠栏的形变胶囊）。
package hunoia.luno.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material3.LocalContentColor as M3LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.util.fastCoerceIn
import androidx.compose.ui.util.fastRoundToInt
import hunoia.luno.ui.animation.DampedDragAnimation
import hunoia.luno.ui.theme.BottomBarHeight
import hunoia.luno.ui.theme.CapsuleShape
import kotlinx.coroutines.flow.collectLatest

@Immutable
class FloatingBottomBarColors(
    val indicatorColor: Color,
    val contentColor: Color,
    val activeContentColor: Color,
)

object FloatingBottomBarDefaults {
    @Composable
    fun colors(
        indicatorColor: Color = MaterialTheme.colorScheme.primary,
        contentColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
        activeContentColor: Color = indicatorColor,
    ): FloatingBottomBarColors = FloatingBottomBarColors(
        indicatorColor = indicatorColor,
        contentColor = contentColor,
        activeContentColor = activeContentColor,
    )
}

/**
 * 底栏 tab 行：只画 tab、指示 pill 与拖拽切换，不画玻璃容器。
 *
 * 调用方（[HomeDock] 的导航胶囊）负责玻璃、阴影、形变与裁剪；这里按传入的 [tabWidth]
 * 用 [requiredWidth] 测量，父容器窄于 tab 行时只做裁剪，不挤压 tab。停靠栏的 tab 宽会随
 * 功能球显隐变化，因此 [tabWidth] 允许是动画值。
 */
@Composable
fun <T> FloatingTabRow(
    items: List<T>,
    selectedIndex: () -> Int,
    onSelected: (index: Int) -> Unit,
    tabWidth: Dp,
    colors: FloatingBottomBarColors,
    modifier: Modifier = Modifier,
    /** tab 行高；主页三球停靠栏的胶囊内容高是 52，默认沿用整条底栏的 56。 */
    tabHeight: Dp = BottomBarHeight,
    iconContent: @Composable (item: T, index: Int) -> Unit,
) {
    val density = LocalDensity.current
    val isLtr = LocalLayoutDirection.current == LayoutDirection.Ltr
    val animationScope = rememberCoroutineScope()
    val tabsCount = items.size
    val tabWidthPx = with(density) { tabWidth.toPx() }
    // 停靠栏的 tab 宽会随功能球显隐变化（76 ↔ 59.87dp）。手势节点一旦装好就只认 key，
    // 所以这里把宽度读成状态、而不是把 tabWidthPx 放进 remember key：宽度变化时实例不重建，
    // 拖动继续驱动同一个动画，落点也按新宽度换算。否则会拖着一个被丢弃的实例，
    // 表现就是胶囊展开后 pill 不跟手、短拖不切页。
    val tabWidthPxState = rememberUpdatedState(tabWidthPx)

    var currentIndex by remember { mutableIntStateOf(selectedIndex()) }
    val selectedIndexUpdated by rememberUpdatedState(selectedIndex)
    val onSelectedUpdated by rememberUpdatedState(onSelected)

    fun indexAt(positionX: Float): Int {
        val widthPx = tabWidthPxState.value
        if (widthPx == 0f) return currentIndex
        val logicalX = if (isLtr) positionX else widthPx * tabsCount - positionX
        return (logicalX / widthPx)
            .toInt()
            .fastCoerceIn(0, tabsCount - 1)
    }

    val dampedDragAnimation = remember(animationScope, tabsCount, isLtr) {
        DampedDragAnimation(
            animationScope = animationScope,
            initialValue = currentIndex.toFloat(),
            valueRange = 0f..(tabsCount - 1).toFloat(),
            visibilityThreshold = 0.001f,
            initialScale = 1f,
            pressedScale = 1.2f,
            canDrag = { position ->
                position.x in 0f..(tabWidthPxState.value * tabsCount)
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
            },
            onDragCancelled = {
                updateValue(currentIndex.toFloat())
            },
            onDrag = { _, dragAmount ->
                val widthPx = tabWidthPxState.value
                if (widthPx > 0f && dragAmount.x != 0f) {
                    updateValue(
                        (targetValue + dragAmount.x / widthPx * if (isLtr) 1f else -1f)
                            .fastCoerceIn(0f, (tabsCount - 1).toFloat()),
                    )
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
                    .width(tabWidth)
                    .fillMaxHeight()
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

    Box(
        modifier = modifier
            .requiredWidth(tabWidth * tabsCount)
            .height(tabHeight),
    ) {
        Row(
            Modifier
                .fillMaxHeight()
                .then(dampedDragAnimation.modifier),
            verticalAlignment = Alignment.CenterVertically,
            content = tabsContent,
        )

        // 滑动指示 pill：跟随拖拽值滑动，按压时放大 + 速度倾斜。
        Box(
            modifier = Modifier
                .graphicsLayer {
                    val progressOffset = dampedDragAnimation.value * tabWidthPx
                    translationX = if (isLtr) progressOffset else -progressOffset
                    scaleX = dampedDragAnimation.scaleX
                    scaleY = dampedDragAnimation.scaleY
                    val velocity = (dampedDragAnimation.velocity / 10f).fastCoerceIn(-0.2f, 0.2f)
                    scaleX /= 1f - velocity * 0.75f
                    scaleY *= 1f - velocity * 0.25f
                }
                .clip(CapsuleShape)
                .background(colors.indicatorColor.copy(alpha = 0.15f), CapsuleShape)
                .height(tabHeight)
                .width(tabWidth),
        )
    }
}
