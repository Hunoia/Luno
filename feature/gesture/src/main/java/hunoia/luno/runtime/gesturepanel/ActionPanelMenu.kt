package hunoia.luno.runtime.gesturepanel

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.EnterExitState.Visible
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.displayCutoutPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import coil.compose.AsyncImage
import coil.imageLoader
import com.aaron.compose.ktx.clipToBackground
import com.aaron.compose.ktx.toPx
import androidx.lifecycle.compose.collectAsStateWithLifecycle

import hunoia.luno.core.domain.StoredAction
import hunoia.luno.core.domain.launchMiniWindow
import hunoia.luno.core.domain.ActionLibrarySettings
import hunoia.luno.config.model.ActionPanelStyle
import hunoia.luno.config.model.ArcStyle
import hunoia.luno.config.model.GestureSettings
import hunoia.luno.ui.theme.AnimNormal
import hunoia.luno.ui.theme.AnimPanelResize
import hunoia.luno.ui.theme.MiniWindowDefaultHeight
import hunoia.luno.ui.theme.MiniWindowWidth
import hunoia.luno.ui.theme.LargeShape
import kotlinx.coroutines.flow.filter
import hunoia.luno.gesture.vibrateForActionPanel
import hunoia.luno.ui.component.gestureButtonMarkColor
import hunoia.luno.ui.component.TextIconGlyph
import hunoia.luno.ui.component.TextIcon
import hunoia.luno.ui.component.actionIcon
import hunoia.luno.ui.component.actionText
import hunoia.luno.action.model.Action



@Composable
fun ActionPanelMenu(
    actionPanelStyle: ActionPanelStyle,
    actionPanelState: ActionPanelState,
    modifier: Modifier = Modifier,
    gestureSettings: GestureSettings? = null
) {
    AnimatedVisibility(
        modifier = modifier,
        visible = actionPanelState.visible,
        enter = fadeIn(spring(stiffness = Spring.StiffnessMedium)),
        exit = fadeOut(spring(stiffness = Spring.StiffnessMedium))
    ) {
        var parentSize by remember { mutableStateOf(Size.Zero) }
        val resolvedStyle = actionPanelState.actionPanelStyle ?: actionPanelStyle
        val itemSizePx = (resolvedStyle as? ArcStyle)?.itemSize?.toFloat() ?: 48.dp.toPx()
        val actionLibrarySettings by hunoia.luno.config.ConfigProvider.actionLibrarySettings.collectAsStateWithLifecycle(initialValue = ActionLibrarySettings())
        LaunchedEffect(parentSize, itemSizePx) {
            actionPanelState.setLayoutInfo(parentSize, itemSizePx)
        }
        Box(
            modifier = Modifier.onGloballyPositioned { parentSize = it.size.toSize() }
        ) {
            ActionPanelBackdrop(
                modifier = Modifier.matchParentSize(),
                actionPanelState = actionPanelState,
                parentSize = parentSize,
                itemSizePx = itemSizePx,
                accentColor = actionPanelAccentColor(actionPanelState.buttonColor),
            )

            val selectedAction = actionPanelState.selectedAction
            val selectedLabel = actionText(selectedAction, emptyIfNone = false, actionLibraryEntries = actionLibrarySettings.entries)
            val animationSpec = spring<Float>(stiffness = Spring.StiffnessHigh)
            val enter = fadeIn(animationSpec) + scaleIn(animationSpec, 0.9f)
            val exit = fadeOut(animationSpec) + scaleOut(animationSpec, 0.9f)


            AnimatedVisibility(
                modifier = Modifier
                    .align(Alignment.Center)
                    .displayCutoutPadding()
                    .padding(12.dp),
                visible = selectedAction.typeId == "app.launch",
                enter = enter,
                exit = ExitTransition.None
            ) {
                    BoxWithConstraints {
                    Box(
                        modifier = Modifier
                            .let { thisModifier ->
                                val miniWindow = selectedAction.launchMiniWindow()
                                val boxMaxWidth = maxWidth
                                val boxMaxHeight = maxHeight
                                val spec = tween<Dp>(AnimPanelResize.toInt())
                                val width by animateDpAsState(
                                    targetValue = if (miniWindow) MiniWindowWidth else boxMaxWidth,
                                    animationSpec = spec
                                )
                                val height by animateDpAsState(
                                    targetValue = if (miniWindow) MiniWindowDefaultHeight else boxMaxHeight,
                                    animationSpec = spec
                                )
                                thisModifier.size(width = width, height = height)
                            }
                            .background(
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                shape = LargeShape
                            )
                    )
                }
            }

            when (val style = resolvedStyle) {
                is ArcStyle -> {
                    ArcActionPanel(
                        modifier = Modifier.fillMaxSize(),
                        actionPanelStyle = style,
                        actionPanelState = actionPanelState,
                        gestureSettings = gestureSettings
                    )
                }
            }

            val canShowPill = selectedLabel.isNotEmpty() &&
                    actionPanelState.selectedIndex >= 0 &&
                    actionPanelState.visible &&
                    actionPanelState.origin.isSpecified &&
                    !parentSize.isEmpty()
            if (canShowPill) {
                val origin = actionPanelOrigin(parentSize, actionPanelState.origin, itemSizePx)
                val sector = actionPanelSector(parentSize, origin, actionPanelState.direction, itemSizePx)
                val actionBounds = actionPanelContentBounds(
                    parentSize = parentSize,
                    origin = origin,
                    sector = sector,
                    actionCount = actionPanelState.actions.size,
                    itemSizePx = itemSizePx,
                    spreadSpacing = resolvedStyle.spreadSpacing,
                    itemGapPx = resolvedStyle.itemGapPx.toFloat(),
                )
                val pillPosition = actionPanelPillPosition(parentSize, actionBounds, itemSizePx)
                val accentColor = actionPanelAccentColor(actionPanelState.buttonColor)
                SelectedActionPill(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .displayCutoutPadding()
                        .graphicsLayer {
                            translationY = pillPosition.y
                        },
                    action = selectedAction,
                    label = selectedLabel,
                    triggerType = actionPanelState.triggerType,
                    accentColor = accentColor,
                )
            }
        }
    }
}







@Composable
internal fun AnimatedVisibilityScope.ActionPanelSelectableItem(
    actionPanelState: ActionPanelState,
    index: Int,
    action: StoredAction,
    targetAnimOffset: Offset,
    panelOrigin: Offset,
    itemSizePx: Float,
    gestureSettings: GestureSettings?,
    modifier: Modifier,
    shape: androidx.compose.ui.graphics.Shape,
    content: @Composable () -> Unit
) {
    val transition = transition
    var isHovered by remember { mutableStateOf(false) }
    val selected = actionPanelState.isSelected(action)
    val hasSelection = actionPanelState.selectedAction.typeId != Action.NONE_TYPE_ID
    val accentColor = actionPanelAccentColor(actionPanelState.buttonColor)
    val scale by animateFloatAsState(if (selected || isHovered) 1.18f else 1f, spring(stiffness = Spring.StiffnessHigh), label = "actionScale")
    val itemAlpha by animateFloatAsState(if (!hasSelection || selected) 1f else 0.72f, tween(AnimNormal.toInt()), label = "actionAlpha")
    LaunchedEffect(transition, actionPanelState, index, action, panelOrigin, targetAnimOffset) {
        snapshotFlow { actionPanelState.finger }
            .filter { it.isSpecified && !transition.isRunning && transition.currentState == Visible }
            .collect { finger ->
                if (actionPanelHitContains(finger, panelOrigin, targetAnimOffset, itemSizePx)) {
                    if (!actionPanelState.isSelected(action)) {
                        isHovered = true
                        actionPanelState.select(index, action)
                        gestureSettings?.let { vibrateForActionPanel(it) }
                    }
                } else if (actionPanelState.isSelected(action)) {
                    isHovered = false
                    actionPanelState.select(index, StoredAction.of(Action.NONE_TYPE_ID))
                }
            }
    }
    Box(
        modifier = modifier
            .graphicsLayer {
                translationX = targetAnimOffset.x
                translationY = targetAnimOffset.y
                scaleX = scale
                scaleY = scale
                alpha = itemAlpha
            }
            .clipToBackground(color = actionPanelItemColor(accentColor), shape = shape)
            .border(
                width = if (selected) 2.dp else 0.dp,
                color = if (selected) accentColor.copy(alpha = 0.88f) else Color.Transparent,
                shape = shape,
            ),
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}

@Composable
internal fun ActionPanelIcon(action: StoredAction, iconSize: Dp, bitmapIconSize: Dp = iconSize) {
    val actionIcon = actionIcon(action = action)
    when (actionIcon) {
        is ImageVector -> Image(
            modifier = Modifier.size(iconSize),
            imageVector = actionIcon,
            contentDescription = null,
            colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onPrimary)
        )
        is TextIcon -> TextIconGlyph(
            text = actionIcon.text,
            size = iconSize,
            color = MaterialTheme.colorScheme.onPrimary,
        )
        else -> AsyncImage(
            modifier = Modifier
                .size(bitmapIconSize),
            model = actionIcon,
            contentDescription = null,
            imageLoader = LocalContext.current.imageLoader,
        )
    }
}

@Composable
internal fun actionPanelItemColor(accentColor: Color): Color = accentColor

@Composable
internal fun actionPanelAccentColor(buttonColor: Int): Color = gestureButtonMarkColor(buttonColor)
