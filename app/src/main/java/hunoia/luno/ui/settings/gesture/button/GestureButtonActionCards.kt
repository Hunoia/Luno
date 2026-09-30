package hunoia.luno.ui.settings.gesture.button

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Adjust
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Gesture
import androidx.compose.material.icons.filled.Swipe
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import hunoia.luno.R
import hunoia.luno.config.model.ActionPanelStyles
import hunoia.luno.config.model.GestureButton
import hunoia.luno.config.model.GestureDirection
import hunoia.luno.config.model.GestureTriggerType
import hunoia.luno.gesture.GestureFacade
import hunoia.luno.ui.component.SegmentedSettingsRow
import hunoia.luno.ui.component.actionTextCompose
import hunoia.luno.ui.component.segmentedShape
import hunoia.luno.ui.navigation.ActionSelect
import hunoia.luno.ui.settings.gesture.style.MySideGestureSettings
import hunoia.luno.ui.settings.gesture.style.StyleTrailingButton
import hunoia.luno.ui.theme.ConnectionRadius
import hunoia.luno.ui.theme.ContainerRadius
import hunoia.luno.ui.theme.RowIconSize
import hunoia.luno.ui.theme.SegmentedGap

val actionCardDirections = listOf(
    GestureDirection.Left,
    GestureDirection.UpLeft,
    GestureDirection.Up,
    GestureDirection.UpRight,
    GestureDirection.Right,
    GestureDirection.DownRight,
    GestureDirection.Down,
    GestureDirection.DownLeft,
)

enum class GestureActionGroup {
    Tap,
    Slide,
    SlideHold,
    LongSlideHold,
    LongSlide,
}

@Composable
fun ExpandableGestureActionCard(
    icon: ImageVector,
    title: String,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    val rotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        label = "GestureActionCardArrowRotation",
    )
    val headerBottomRadius by animateDpAsState(
        targetValue = if (expanded) ConnectionRadius else ContainerRadius,
        label = "GestureActionCardHeaderBottomRadius",
    )
    val headerShape = RoundedCornerShape(
        topStart = ContainerRadius,
        topEnd = ContainerRadius,
        bottomStart = headerBottomRadius,
        bottomEnd = headerBottomRadius,
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(ContainerRadius)),
        verticalArrangement = Arrangement.spacedBy(SegmentedGap),
    ) {
        SegmentedSettingsRow(
            title = title,
            icon = icon,
            onClick = { onExpandedChange(!expanded) },
            shape = headerShape,
            trailingContent = {
                Icon(
                    modifier = Modifier
                        .size(RowIconSize)
                        .graphicsLayer { rotationZ = rotation },
                    imageVector = Icons.Default.ExpandMore,
                    contentDescription = null,
                )
            },
        )
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut(),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(segmentedShape(1, 2)),
                verticalArrangement = Arrangement.spacedBy(SegmentedGap),
            ) {
                content()
            }
        }
    }
}

@Composable
fun GestureButtonSlideActionsCard(
    gestureButton: GestureButton,
    onNavToActionSelect: (ActionSelect) -> Unit,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
) {
    ExpandableGestureActionCard(
        icon = Icons.Default.Swipe,
        title = stringResource(id = R.string.slide_action),
        expanded = expanded,
        onExpandedChange = onExpandedChange,
    ) {
        SlideActionRows(
            hiddenDirections = gestureButton.angle.zeroWidthDirections(),
            actionsText = { direction -> gestureButton.slideActions.actionsBy(direction).actionTextCompose() },
            onDirectionClick = { direction ->
                onNavToActionSelect(
                    ActionSelect(
                        gestureButtonId = gestureButton.id,
                        direction = direction,
                        triggerType = GestureTriggerType.Slide,
                    )
                )
            },
        )
    }
}

@Composable
fun GestureButtonLongSlideActionsCard(
    gestureButton: GestureButton,
    onNavToActionSelect: (ActionSelect) -> Unit,
    onStyleSelect: (GestureDirection) -> Unit,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
) {
    ExpandableGestureActionCard(
        icon = Icons.Default.Gesture,
        title = stringResource(id = R.string.long_slide_action),
        expanded = expanded,
        onExpandedChange = onExpandedChange,
    ) {
        LongSlideActionRows(
            hiddenDirections = gestureButton.angle.zeroWidthDirections(),
            actionsText = { direction -> gestureButton.longSlideActions.actionsBy(direction).actionTextCompose() },
            currentStyle = { direction -> GestureFacade.styleBy(gestureButton.longSlideActionPanelStyles, direction) },
            onDirectionClick = { direction ->
                onNavToActionSelect(
                    ActionSelect(
                        gestureButtonId = gestureButton.id,
                        direction = direction,
                        triggerType = GestureTriggerType.LongSlide,
                    )
                )
            },
            onStyleSelect = onStyleSelect,
        )
    }
}

@Composable
private fun <T> RowGroup(
    items: List<T>,
    item: @Composable (Int, T) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(SegmentedGap),
    ) {
        items.forEachIndexed { index, value ->
            item(index, value)
        }
    }
}

@Composable
fun SlideActionRows(
    actionsText: @Composable (GestureDirection) -> String,
    onDirectionClick: (GestureDirection) -> Unit,
    hiddenDirections: Set<GestureDirection> = emptySet(),
) {
    val directions = actionCardDirections.filterNot { it in hiddenDirections }
    RowGroup(items = directions) { index, direction ->
        MySideGestureSettings(
            onClick = { onDirectionClick(direction) },
            direction = direction,
            isLongSlide = false,
            secondaryText = actionsText(direction),
            shape = segmentedShape(index, directions.size),
        )
    }
}

@Composable
fun LongSlideActionRows(
    actionsText: @Composable (GestureDirection) -> String,
    currentStyle: (GestureDirection) -> ActionPanelStyles,
    onDirectionClick: (GestureDirection) -> Unit,
    onStyleSelect: (GestureDirection) -> Unit,
    hiddenDirections: Set<GestureDirection> = emptySet(),
) {
    val directions = actionCardDirections.filterNot { it in hiddenDirections }
    RowGroup(items = directions) { index, direction ->
        MySideGestureSettings(
            onClick = { onDirectionClick(direction) },
            direction = direction,
            isLongSlide = true,
            secondaryText = actionsText(direction),
            shape = segmentedShape(index, directions.size),
            trailing = {
                StyleTrailingButton(
                    currentStyle = currentStyle(direction),
                    onClick = { onStyleSelect(direction) }
                )
            }
        )
    }
}

@Composable
fun GestureButtonTapActionsCard(
    gestureButton: GestureButton,
    onNavToActionSelect: (ActionSelect) -> Unit,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
) {
    val tapActions = listOf(
        Triple(R.string.tap_action, GestureTriggerType.Tap, gestureButton.tapActions),
        Triple(R.string.double_tap_action, GestureTriggerType.DoubleTap, gestureButton.doubleTapActions),
        Triple(R.string.long_press, GestureTriggerType.LongPress, gestureButton.longPressActions),
    )
    ExpandableGestureActionCard(
        icon = Icons.Default.Adjust,
        title = stringResource(id = R.string.tap_and_long_press_action),
        expanded = expanded,
        onExpandedChange = onExpandedChange,
    ) {
        RowGroup(items = tapActions) { index, (labelRes, triggerType, actions) ->
            SegmentedSettingsRow(
                onClick = {
                    onNavToActionSelect(
                        ActionSelect(
                            gestureButtonId = gestureButton.id,
                            direction = GestureDirection.Right,
                            triggerType = triggerType,
                        )
                    )
                },
                title = stringResource(id = labelRes),
                subtitle = actions.actionTextCompose(),
                secondaryTextColor = MaterialTheme.colorScheme.primary,
                shape = segmentedShape(index, tapActions.size),
                leadingContent = {
                    Icon(
                        modifier = Modifier.size(RowIconSize),
                        imageVector = Icons.Default.Adjust,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                },
            )
        }
    }
}

@Composable
fun GestureButtonSlideHoldActionsCard(
    gestureButton: GestureButton,
    onNavToActionSelect: (ActionSelect) -> Unit,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
) {
    ExpandableGestureActionCard(
        icon = Icons.Default.Swipe,
        title = stringResource(id = R.string.slide_hold_action),
        expanded = expanded,
        onExpandedChange = onExpandedChange,
    ) {
        SlideActionRows(
            hiddenDirections = gestureButton.angle.zeroWidthDirections(),
            actionsText = { direction -> gestureButton.slideHoldActions.actionsBy(direction).actionTextCompose() },
            onDirectionClick = { direction ->
                onNavToActionSelect(
                    ActionSelect(
                        gestureButtonId = gestureButton.id,
                        direction = direction,
                        triggerType = GestureTriggerType.SlideHold,
                    )
                )
            },
        )
    }
}

@Composable
fun GestureButtonLongSlideHoldActionsCard(
    gestureButton: GestureButton,
    onNavToActionSelect: (ActionSelect) -> Unit,
    onStyleSelect: (GestureDirection) -> Unit,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
) {
    ExpandableGestureActionCard(
        icon = Icons.Default.Gesture,
        title = stringResource(id = R.string.long_slide_hold_action),
        expanded = expanded,
        onExpandedChange = onExpandedChange,
    ) {
        LongSlideActionRows(
            hiddenDirections = gestureButton.angle.zeroWidthDirections(),
            actionsText = { direction -> gestureButton.longSlideHoldActions.actionsBy(direction).actionTextCompose() },
            currentStyle = { direction -> GestureFacade.styleBy(gestureButton.longSlideActionPanelStyles, direction) },
            onDirectionClick = { direction ->
                onNavToActionSelect(
                    ActionSelect(
                        gestureButtonId = gestureButton.id,
                        direction = direction,
                        triggerType = GestureTriggerType.LongSlideHold,
                    )
                )
            },
            onStyleSelect = { direction ->
                onExpandedChange(true)
                onStyleSelect(direction)
            },
        )
    }
}
