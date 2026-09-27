package hunoia.luno.ui.settings.gesture.button

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Adjust
import androidx.compose.material.icons.filled.Gesture
import androidx.compose.material.icons.filled.Swipe
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import hunoia.luno.R
import hunoia.luno.config.model.ActionPanelStyles
import hunoia.luno.config.model.GestureButton
import hunoia.luno.config.model.GestureDirection
import hunoia.luno.config.model.GestureTriggerType
import hunoia.luno.gesture.GestureFacade
import hunoia.luno.ui.component.ExpressiveRowContent
import hunoia.luno.ui.component.actionTextCompose
import hunoia.luno.ui.component.settings.CompactExpandableSettingsGroup
import hunoia.luno.ui.navigation.ActionSelect
import hunoia.luno.ui.settings.gesture.style.MySideGestureSettings
import hunoia.luno.ui.settings.gesture.style.StyleTrailingButton
import hunoia.luno.ui.theme.CardShape

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
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    CompactExpandableSettingsGroup(
        icon = icon,
        title = title,
        expanded = expanded,
        onExpandedChange = onExpandedChange,
    ) {
        content()
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
            styleGestureButton = gestureButton,
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
            styleGestureButton = gestureButton,
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
    item: @Composable (T) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth().clip(CardShape),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        items.forEach { value ->
            Surface(
                shape = RoundedCornerShape(0.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
            ) {
                item(value)
            }
        }
    }
}

@Composable
fun SlideActionRows(
    styleGestureButton: GestureButton,
    actionsText: @Composable (GestureDirection) -> String,
    onDirectionClick: (GestureDirection) -> Unit,
    hiddenDirections: Set<GestureDirection> = emptySet(),
) {
    val directions = actionCardDirections.filterNot { it in hiddenDirections }
    RowGroup(items = directions) { direction ->
        MySideGestureSettings(
            onClick = { onDirectionClick(direction) },
            gestureButton = styleGestureButton,
            direction = direction,
            isLongSlide = false,
            secondaryText = actionsText(direction),
        )
    }
}

@Composable
fun LongSlideActionRows(
    styleGestureButton: GestureButton,
    actionsText: @Composable (GestureDirection) -> String,
    currentStyle: (GestureDirection) -> ActionPanelStyles,
    onDirectionClick: (GestureDirection) -> Unit,
    onStyleSelect: (GestureDirection) -> Unit,
    hiddenDirections: Set<GestureDirection> = emptySet(),
) {
    val directions = actionCardDirections.filterNot { it in hiddenDirections }
    RowGroup(items = directions) { direction ->
        MySideGestureSettings(
            onClick = { onDirectionClick(direction) },
            gestureButton = styleGestureButton,
            direction = direction,
            isLongSlide = true,
            secondaryText = actionsText(direction),
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
    ExpandableGestureActionCard(
        icon = Icons.Default.Adjust,
        title = stringResource(id = R.string.tap_and_long_press_action),
        expanded = expanded,
        onExpandedChange = onExpandedChange,
    ) {
        RowGroup(
            items = listOf(
                Triple(R.string.tap_action, GestureTriggerType.Tap, gestureButton.tapActions),
                Triple(R.string.double_tap_action, GestureTriggerType.DoubleTap, gestureButton.doubleTapActions),
                Triple(R.string.long_press, GestureTriggerType.LongPress, gestureButton.longPressActions),
            ),
        ) { (labelRes, triggerType, actions) ->
            ExpressiveRowContent(
                onClick = {
                    onNavToActionSelect(
                        ActionSelect(
                            gestureButtonId = gestureButton.id,
                            direction = GestureDirection.Right,
                            triggerType = triggerType,
                        )
                    )
                },
                text = stringResource(id = labelRes),
                secondaryText = actions.actionTextCompose(),
                secondaryTextColor = MaterialTheme.colorScheme.primary,
                icon = { Icon(Icons.Default.Adjust, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
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
            styleGestureButton = gestureButton,
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
            styleGestureButton = gestureButton,
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
