package hunoia.luno.ui.settings.gesture.button

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Adjust
import androidx.compose.material.icons.filled.Gesture
import androidx.compose.material.icons.filled.Swipe
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import hunoia.luno.R
import hunoia.luno.action.api.ActionFacade
import hunoia.luno.config.model.Action
import hunoia.luno.config.model.ActionPanelStyles
import hunoia.luno.config.model.DirectionActions
import hunoia.luno.config.model.GestureButton
import hunoia.luno.config.model.GestureDirection
import hunoia.luno.config.model.GestureTriggerType
import hunoia.luno.gesture.GestureFacade
import hunoia.luno.ui.component.ExpressiveRow
import hunoia.luno.ui.component.actionTextCompose
import hunoia.luno.ui.component.displayNameRes
import hunoia.luno.ui.component.settings.CompactExpandableSettingsGroup
import hunoia.luno.ui.navigation.ActionSelect
import hunoia.luno.ui.settings.gesture.style.MySideGestureSettings
import hunoia.luno.ui.settings.gesture.style.StyleTrailingButton

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

fun DirectionActions.hasAnyConfigured(): Boolean = actions.values.any { it.hasConfiguredAction() }

fun List<Action>.hasConfiguredAction(): Boolean = any { it.value.isNotEmpty() && it.value != ActionFacade.NONE }

@Composable
fun ExpandableGestureActionCard(
    icon: ImageVector,
    title: String,
    summary: String,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    CompactExpandableSettingsGroup(
        icon = icon,
        title = title,
        summary = summary,
        expanded = expanded,
        onExpandedChange = onExpandedChange,
    ) {
        content()
    }
}

@Composable
fun GestureButton.tapActionSummary(): String = labeledActionSummary(
    stringResource(id = R.string.tap_action) to tapActions.actionTextCompose(emptyIfNone = true),
    stringResource(id = R.string.double_tap_action) to doubleTapActions.actionTextCompose(emptyIfNone = true),
    stringResource(id = R.string.long_press) to longPressActions.actionTextCompose(emptyIfNone = true),
)

@Composable
fun DirectionActions.directionActionSummary(): String = labeledActionSummary(
    *actionCardDirections.mapNotNull { direction ->
        val text = actionsBy(direction).actionTextCompose(emptyIfNone = true)
        if (text.isBlank()) null else stringResource(id = direction.displayNameRes) to text
    }.toTypedArray()
)

@Composable
private fun labeledActionSummary(vararg items: Pair<String, String>): String {
    val configured = items.filter { it.second.isNotBlank() }
    if (configured.isEmpty()) return stringResource(id = R.string.action_group_summary_empty)
    val preview = configured.take(2).joinToString(separator = " · ") { (label, text) -> "$label: $text" }
    return stringResource(id = R.string.action_group_summary_more, preview, configured.size)
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
        summary = gestureButton.slideActions.directionActionSummary(),
        expanded = expanded,
        onExpandedChange = onExpandedChange,
    ) {
        SlideActionRows(
            styleGestureButton = gestureButton,
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
        summary = gestureButton.longSlideActions.directionActionSummary(),
        expanded = expanded,
        onExpandedChange = onExpandedChange,
    ) {
        LongSlideActionRows(
            styleGestureButton = gestureButton,
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
fun SlideActionRows(
    styleGestureButton: GestureButton,
    actionsText: @Composable (GestureDirection) -> String,
    onDirectionClick: (GestureDirection) -> Unit,
) {
    actionCardDirections.forEach { direction ->
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
) {
    actionCardDirections.forEach { direction ->
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
        summary = gestureButton.tapActionSummary(),
        expanded = expanded,
        onExpandedChange = onExpandedChange,
    ) {
        ExpressiveRow(
            onClick = {
                onNavToActionSelect(
                    ActionSelect(
                        gestureButtonId = gestureButton.id,
                        direction = GestureDirection.Right,
                        triggerType = GestureTriggerType.Tap,
                    )
                )
            },
            text = stringResource(id = R.string.tap_action),
            secondaryText = gestureButton.tapActions.actionTextCompose(),
            secondaryTextColor = MaterialTheme.colorScheme.primary,
            icon = { Icon(Icons.Default.Adjust, contentDescription = null, tint = MaterialTheme.colorScheme.primary) }
        )
        ExpressiveRow(
            onClick = {
                onNavToActionSelect(
                    ActionSelect(
                        gestureButtonId = gestureButton.id,
                        direction = GestureDirection.Right,
                        triggerType = GestureTriggerType.DoubleTap,
                    )
                )
            },
            text = stringResource(id = R.string.double_tap_action),
            secondaryText = gestureButton.doubleTapActions.actionTextCompose(),
            secondaryTextColor = MaterialTheme.colorScheme.primary,
            icon = { Icon(Icons.Default.Adjust, contentDescription = null, tint = MaterialTheme.colorScheme.primary) }
        )
        ExpressiveRow(
            onClick = {
                onNavToActionSelect(
                    ActionSelect(
                        gestureButtonId = gestureButton.id,
                        direction = GestureDirection.Right,
                        triggerType = GestureTriggerType.LongPress,
                    )
                )
            },
            text = stringResource(id = R.string.long_press),
            secondaryText = gestureButton.longPressActions.actionTextCompose(),
            secondaryTextColor = MaterialTheme.colorScheme.primary,
            icon = { Icon(Icons.Default.Adjust, contentDescription = null, tint = MaterialTheme.colorScheme.primary) }
        )
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
        summary = gestureButton.slideHoldActions.directionActionSummary(),
        expanded = expanded,
        onExpandedChange = onExpandedChange,
    ) {
        SlideActionRows(
            styleGestureButton = gestureButton,
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
        summary = gestureButton.longSlideHoldActions.directionActionSummary(),
        expanded = expanded,
        onExpandedChange = onExpandedChange,
    ) {
        LongSlideActionRows(
            styleGestureButton = gestureButton,
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
