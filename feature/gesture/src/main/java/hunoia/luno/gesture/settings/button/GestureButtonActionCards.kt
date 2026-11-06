package hunoia.luno.gesture.settings.button
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
import androidx.compose.ui.graphics.Shape
import hunoia.luno.config.model.DirectionActions
import hunoia.luno.config.model.GestureButton
import hunoia.luno.config.model.GestureButtonAngle
import hunoia.luno.config.model.GestureTriggerType
import hunoia.luno.gesture.R
import hunoia.luno.gesture.settings.GestureActionTarget
import hunoia.luno.gesture.settings.style.SideGestureSettings
import hunoia.luno.ui.component.DirectionLabels
import hunoia.luno.ui.component.RowIconBadge
import hunoia.luno.ui.component.actionTextCompose
import hunoia.luno.ui.component.directionLabel
import hunoia.luno.ui.component.row.LunoSettingsRow
import hunoia.luno.ui.theme.ConnectionRadius
import hunoia.luno.ui.theme.ContainerRadius
import hunoia.luno.ui.theme.RowIconSize
import hunoia.luno.ui.theme.SegmentedGap
import hunoia.luno.ui.theme.containerShape

/** 展开内容内部行的圆角：首行接标题用小圆角，最后一行统一收回大圆角。 */
fun actionContentRowShape(index: Int, count: Int): Shape {
    val bottom = if (index == count - 1) ContainerRadius else ConnectionRadius
    return containerShape(
        topStart = ConnectionRadius,
        topEnd = ConnectionRadius,
        bottomStart = bottom,
        bottomEnd = bottom,
    )
}
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
    index: Int = 0,
    count: Int = 1,
    content: @Composable ColumnScope.() -> Unit,
) {
    val topRadius = if (index == 0 || count <= 1) ContainerRadius else ConnectionRadius
    val bottomRadius = if (index == count - 1 || count <= 1) ContainerRadius else ConnectionRadius
    val outerShape = containerShape(
        topStart = topRadius,
        topEnd = topRadius,
        bottomStart = bottomRadius,
        bottomEnd = bottomRadius,
    )
    val rotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        label = "GestureActionCardArrowRotation",
    )
    val headerBottomRadius by animateDpAsState(
        targetValue = if (expanded) ConnectionRadius else bottomRadius,
        label = "GestureActionCardHeaderBottomRadius",
    )
    val headerShape = containerShape(
        topStart = topRadius,
        topEnd = topRadius,
        bottomStart = headerBottomRadius,
        bottomEnd = headerBottomRadius,
    )
    val contentShape = containerShape(
        topStart = ConnectionRadius,
        topEnd = ConnectionRadius,
        bottomStart = ContainerRadius,
        bottomEnd = ContainerRadius,
    )
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(outerShape),
        verticalArrangement = Arrangement.spacedBy(SegmentedGap),
    ) {
        LunoSettingsRow(
            title = title,
            icon = icon,
            onClick = { onExpandedChange(!expanded) },
            shape = headerShape,
            trailing = {
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
                    .clip(contentShape),
                verticalArrangement = Arrangement.spacedBy(SegmentedGap),
            ) {
                content()
            }
        }
    }
}
/**
 * 方向类动作卡共有骨架：Slide / LongSlide / SlideHold / LongSlideHold 四张卡
 * 只差图标、标题、序位、触发类型与动作集。
 */
@Composable
private fun DirectionalActionCard(
    gestureButton: GestureButton,
    onNavToActionSelect: (GestureActionTarget) -> Unit,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    icon: ImageVector,
    title: String,
    index: Int,
    triggerType: GestureTriggerType,
    actions: DirectionActions,
) {
    ExpandableGestureActionCard(
        icon = icon,
        title = title,
        expanded = expanded,
        onExpandedChange = onExpandedChange,
        index = index,
        count = 5,
    ) {
        DirectionalActionRows(
            angle = gestureButton.angle,
            actionsText = { slot -> actions.actionBy(slot).actionTextCompose() },
            onSlotClick = { slot, label ->
                onNavToActionSelect(
                    GestureActionTarget(
                        gestureButtonId = gestureButton.id,
                        slot = slot,
                        directionLabel = label,
                        triggerType = triggerType,
                    )
                )
            },
        )
    }
}

@Composable
fun GestureButtonSlideActionsCard(
    gestureButton: GestureButton,
    onNavToActionSelect: (GestureActionTarget) -> Unit,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
) = DirectionalActionCard(
    gestureButton = gestureButton,
    onNavToActionSelect = onNavToActionSelect,
    expanded = expanded,
    onExpandedChange = onExpandedChange,
    icon = Icons.Default.Swipe,
    title = stringResource(id = R.string.slide_action),
    index = 1,
    triggerType = GestureTriggerType.Slide,
    actions = gestureButton.slideActions,
)

@Composable
fun GestureButtonLongSlideActionsCard(
    gestureButton: GestureButton,
    onNavToActionSelect: (GestureActionTarget) -> Unit,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
) = DirectionalActionCard(
    gestureButton = gestureButton,
    onNavToActionSelect = onNavToActionSelect,
    expanded = expanded,
    onExpandedChange = onExpandedChange,
    icon = Icons.Default.Gesture,
    title = stringResource(id = R.string.long_slide_action),
    index = 3,
    triggerType = GestureTriggerType.LongSlide,
    actions = gestureButton.longSlideActions,
)
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
/** 方向行组：按扇区中心角顺时针排列，零宽扇区不显示。 */
@Composable
private fun DirectionalActionRows(
    angle: GestureButtonAngle,
    actionsText: @Composable (Int) -> String,
    onSlotClick: (Int, String) -> Unit,
) {
    val slots = DirectionLabels.clockwiseSlots(angle.boundaries)
        .filterNot { it in angle.zeroWidthSlots() }
    RowGroup(items = slots) { index, slot ->
        val center = angle.slotCenterDegrees(slot)
        val label = directionLabel(center, angle.count)
        SideGestureSettings(
            onClick = { onSlotClick(slot, label) },
            centerDegrees = center,
            count = angle.count,
            subtitle = actionsText(slot),
            shape = actionContentRowShape(index, slots.size),
        )
    }
}
@Composable
fun GestureButtonTapActionsCard(
    gestureButton: GestureButton,
    onNavToActionSelect: (GestureActionTarget) -> Unit,
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
        index = 0,
        count = 5,
    ) {
        RowGroup(items = tapActions) { index, (labelRes, triggerType, actions) ->
            LunoSettingsRow(
                onClick = {
                    onNavToActionSelect(
                        GestureActionTarget(
                            gestureButtonId = gestureButton.id,
                            slot = 0,
                            triggerType = triggerType,
                        )
                    )
                },
                title = stringResource(id = labelRes),
                subtitle = actions.actionTextCompose(),
                subtitleColor = MaterialTheme.colorScheme.primary,
                shape = actionContentRowShape(index, tapActions.size),
                leading = {
                    RowIconBadge(icon = Icons.Default.Adjust)
                },
            )
        }
    }
}
@Composable
fun GestureButtonSlideHoldActionsCard(
    gestureButton: GestureButton,
    onNavToActionSelect: (GestureActionTarget) -> Unit,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
) = DirectionalActionCard(
    gestureButton = gestureButton,
    onNavToActionSelect = onNavToActionSelect,
    expanded = expanded,
    onExpandedChange = onExpandedChange,
    icon = Icons.Default.Swipe,
    title = stringResource(id = R.string.slide_hold_action),
    index = 2,
    triggerType = GestureTriggerType.SlideHold,
    actions = gestureButton.slideHoldActions,
)

@Composable
fun GestureButtonLongSlideHoldActionsCard(
    gestureButton: GestureButton,
    onNavToActionSelect: (GestureActionTarget) -> Unit,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
) = DirectionalActionCard(
    gestureButton = gestureButton,
    onNavToActionSelect = onNavToActionSelect,
    expanded = expanded,
    onExpandedChange = onExpandedChange,
    icon = Icons.Default.Gesture,
    title = stringResource(id = R.string.long_slide_hold_action),
    index = 4,
    triggerType = GestureTriggerType.LongSlideHold,
    actions = gestureButton.longSlideHoldActions,
)