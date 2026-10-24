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
import hunoia.luno.config.model.DirectionActions
import hunoia.luno.config.model.GestureButton
import hunoia.luno.config.model.GestureDirection
import hunoia.luno.config.model.GestureTriggerType
import hunoia.luno.ui.component.RowIconBadge
import hunoia.luno.ui.component.actionTextCompose
import hunoia.luno.ui.navigation.ActionSelect
import hunoia.luno.ui.settings.gesture.style.SideGestureSettings
import hunoia.luno.ui.theme.ConnectionRadius
import hunoia.luno.ui.theme.ContainerRadius
import hunoia.luno.ui.theme.RowIconSize
import hunoia.luno.ui.theme.SegmentedGap
import hunoia.luno.ui.component.row.LunoSettingsRow

/** 展开内容内部行的圆角：首行接标题用小圆角，最后一行统一收回大圆角。 */
fun actionContentRowShape(index: Int, count: Int): RoundedCornerShape {
    val bottom = if (index == count - 1) ContainerRadius else ConnectionRadius
    return RoundedCornerShape(
        topStart = ConnectionRadius,
        topEnd = ConnectionRadius,
        bottomStart = bottom,
        bottomEnd = bottom,
    )
}
val actionCardDirections = listOf(
    GestureDirection.Left,
    GestureDirection.Up,
    GestureDirection.Right,
    GestureDirection.Down,
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
    index: Int = 0,
    count: Int = 1,
    content: @Composable ColumnScope.() -> Unit,
) {
    val topRadius = if (index == 0 || count <= 1) ContainerRadius else ConnectionRadius
    val bottomRadius = if (index == count - 1 || count <= 1) ContainerRadius else ConnectionRadius
    val outerShape = RoundedCornerShape(
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
    val headerShape = RoundedCornerShape(
        topStart = topRadius,
        topEnd = topRadius,
        bottomStart = headerBottomRadius,
        bottomEnd = headerBottomRadius,
    )
    val contentShape = RoundedCornerShape(
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
 * 只差图标、标题、序位、触发类型与动作集（长滑系另传 isLongSlide 姿态）。
 */
@Composable
private fun DirectionalActionCard(
    gestureButton: GestureButton,
    onNavToActionSelect: (ActionSelect) -> Unit,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    icon: ImageVector,
    title: String,
    index: Int,
    triggerType: GestureTriggerType,
    isLongSlide: Boolean,
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
            hiddenDirections = gestureButton.angle.zeroWidthDirections(),
            actionsText = { direction -> actions.actionBy(direction).actionTextCompose() },
            onDirectionClick = { direction ->
                onNavToActionSelect(
                    ActionSelect(
                        gestureButtonId = gestureButton.id,
                        direction = direction,
                        triggerType = triggerType,
                    )
                )
            },
            isLongSlide = isLongSlide,
        )
    }
}

@Composable
fun GestureButtonSlideActionsCard(
    gestureButton: GestureButton,
    onNavToActionSelect: (ActionSelect) -> Unit,
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
    isLongSlide = false,
    actions = gestureButton.slideActions,
)

@Composable
fun GestureButtonLongSlideActionsCard(
    gestureButton: GestureButton,
    onNavToActionSelect: (ActionSelect) -> Unit,
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
    isLongSlide = true,
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
/** 方向行组：短滑系与长滑系只差 SideGestureSettings 的 isLongSlide 姿态。 */
@Composable
private fun DirectionalActionRows(
    actionsText: @Composable (GestureDirection) -> String,
    onDirectionClick: (GestureDirection) -> Unit,
    isLongSlide: Boolean,
    hiddenDirections: Set<GestureDirection> = emptySet(),
) {
    val directions = actionCardDirections.filterNot { it in hiddenDirections }
    RowGroup(items = directions) { index, direction ->
        SideGestureSettings(
            onClick = { onDirectionClick(direction) },
            direction = direction,
            isLongSlide = isLongSlide,
            subtitle = actionsText(direction),
            shape = actionContentRowShape(index, directions.size),
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
        index = 0,
        count = 5,
    ) {
        RowGroup(items = tapActions) { index, (labelRes, triggerType, actions) ->
            LunoSettingsRow(
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
    onNavToActionSelect: (ActionSelect) -> Unit,
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
    isLongSlide = false,
    actions = gestureButton.slideHoldActions,
)

@Composable
fun GestureButtonLongSlideHoldActionsCard(
    gestureButton: GestureButton,
    onNavToActionSelect: (ActionSelect) -> Unit,
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
    isLongSlide = true,
    actions = gestureButton.longSlideHoldActions,
)