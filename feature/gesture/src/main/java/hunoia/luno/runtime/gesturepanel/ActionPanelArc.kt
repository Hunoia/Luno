package hunoia.luno.runtime.gesturepanel

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideIn
import androidx.compose.animation.slideOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.toSize
import androidx.compose.ui.util.fastForEachIndexed
import com.aaron.compose.ktx.toDp
import com.aaron.compose.ktx.toPx
import hunoia.luno.config.model.ArcStyle
import hunoia.luno.config.model.GestureSettings
import androidx.compose.ui.platform.LocalDensity

@Composable
internal fun AnimatedVisibilityScope.ArcActionPanel(
    actionPanelStyle: ArcStyle,
    actionPanelState: ActionPanelState,
    modifier: Modifier = Modifier,
    gestureSettings: GestureSettings? = null
) {
    val itemSize = actionPanelStyle.itemSize.toDp()
    val actionCount = actionPanelState.actions.size
    val itemSizePx = itemSize.toPx()
    val density = LocalDensity.current
    // 边距滑杆只改 style，insets 随 placement/密度重算并参与每次布局。
    val insets = remember(actionPanelStyle.placement, density) {
        actionPanelStyle.panelInsets(density.density)
    }
    var parentSize by remember { mutableStateOf(Size.Zero) }
    Box(modifier = modifier) {
        Box(
            modifier = Modifier
                .onGloballyPositioned {
                    parentSize = it.size.toSize()
                }
                .fillMaxSize()
        )

        Box(
            modifier = Modifier
                .run {
                    val origin = remember(actionPanelState) { actionPanelState.origin }
                    graphicsLayer {
                        if (parentSize.isEmpty()) return@graphicsLayer
                        val itemSizeHalf = itemSize.toPx() / 2f
                        val panelOrigin = actionPanelOrigin(parentSize, origin, itemSize.toPx(), insets)
                        val ox = panelOrigin.x
                        val oy = panelOrigin.y
                        translationX = ox - itemSizeHalf
                        translationY = oy - itemSizeHalf
                    }
                }
                .size(itemSize)
        ) {
            val transition = transition
            actionPanelState.actions.fastForEachIndexed { index, action ->
                key(index) {
                    val panelOrigin = actionPanelOrigin(parentSize, actionPanelState.origin, itemSizePx, insets)
                    val sector = actionPanelSector(parentSize, panelOrigin, actionPanelState.centerDegrees, itemSizePx)
                    val displayIndex = actionPanelDisplayIndex(sector, actionCount, index)
                    val targetAnimOffset = remember(
                        parentSize, panelOrigin, sector, actionPanelState.actions.size,
                        displayIndex, actionPanelStyle.spreadSpacing, actionPanelStyle.itemGapPx,
                        actionPanelStyle.maxAngleDegrees, insets,
                    ) {
                        actionPanelItemOffset(
                            parentSize = parentSize,
                            origin = panelOrigin,
                            sector = sector,
                            actionCount = actionCount,
                            index = displayIndex,
                            itemSizePx = itemSizePx,
                            spreadSpacing = actionPanelStyle.spreadSpacing,
                            itemGapPx = actionPanelStyle.itemGapPx.toFloat(),
                            maxAngleDegrees = actionPanelStyle.maxAngleDegrees,
                            insets = insets,
                        )
                    }
                    ActionPanelSelectableItem(
                        actionPanelState = actionPanelState,
                        index = index,
                        action = action,
                        targetAnimOffset = targetAnimOffset,
                        panelOrigin = panelOrigin,
                        itemSizePx = itemSizePx,
                        gestureSettings = gestureSettings,
                        modifier = Modifier
                            .run animateEnterExit@{
                                val stiffness = Spring.StiffnessMedium
                                animateEnterExit(
                                    enter = scaleIn(spring(stiffness = stiffness)) +
                                            slideIn(animationSpec = spring(stiffness = stiffness)) {
                                                IntOffset(-targetAnimOffset.x.toInt(), -targetAnimOffset.y.toInt())
                                            },
                                    exit = scaleOut(spring(stiffness = stiffness)) +
                                            slideOut(animationSpec = spring(stiffness = stiffness)) {
                                                IntOffset(-targetAnimOffset.x.toInt(), -targetAnimOffset.y.toInt())
                                            }
                                )
                            }
                            .fillMaxSize(),
                        shape = CircleShape
                    ) {
                        val iconScale = if (actionPanelState.isSelected(action)) 1.15f else 1f
                        ActionPanelIcon(
                            action = action,
                            iconSize = itemSize * 0.68f,
                            // 应用 Drawable 通常自带透明留白；无圆底时放大绘制框，
                            // 使实际图形面积与彩色圆钮里的系统图标接近。
                            bitmapIconSize = itemSize * actionPanelStyle.appIconScale.coerceIn(0.6f, 1.2f),
                            scale = iconScale,
                        )
                    }
                }
            }
        }
    }
}
