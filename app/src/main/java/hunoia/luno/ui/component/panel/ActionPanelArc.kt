package hunoia.luno.ui.component.panel

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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.toSize
import androidx.compose.ui.util.fastForEachIndexed
import com.aaron.compose.ktx.toDp
import com.aaron.compose.ktx.toPx
import hunoia.luno.config.model.Action
import hunoia.luno.config.model.ArcStyle
import hunoia.luno.config.model.GestureSettings

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
                        val panelOrigin = actionPanelOrigin(parentSize, origin, itemSize.toPx())
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
                    val panelOrigin = actionPanelOrigin(parentSize, actionPanelState.origin, itemSizePx)
                    val sector = actionPanelSector(parentSize, panelOrigin, actionPanelState.direction, itemSizePx)
                    val displayIndex = actionPanelDisplayIndex(sector, actionCount, index)
                    val targetAnimOffset = remember(parentSize, panelOrigin, sector, actionPanelState.actions.size, displayIndex, actionPanelStyle.spreadSpacing) {
                        actionPanelItemOffset(
                            parentSize = parentSize,
                            origin = panelOrigin,
                            sector = sector,
                            actionCount = actionCount,
                            index = displayIndex,
                            itemSizePx = itemSizePx,
                            spreadSpacing = actionPanelStyle.spreadSpacing,
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
                        ActionPanelIcon(action = action, iconSize = itemSize * 0.65f, bitmapIconSize = itemSize * 0.82f)
                    }
                }
            }
        }
    }
}
