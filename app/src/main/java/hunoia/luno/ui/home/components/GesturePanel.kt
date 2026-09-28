package hunoia.luno.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastForEachIndexed
import com.aaron.compose.ktx.onSingleClick
import hunoia.luno.R
import hunoia.luno.config.defaults.SettingsUiDefaults.GestureButtonColorAlpha
import hunoia.luno.config.model.GestureButton
import hunoia.luno.config.model.SubGesture
import hunoia.luno.ui.component.buttonTextCompose
import hunoia.luno.ui.component.segmentedShape
import hunoia.luno.ui.theme.ContainerRadius
import hunoia.luno.ui.theme.MarkColorSize
import hunoia.luno.ui.theme.SegmentedGap

enum class GesturePanelSection {
    TouchButton,
    SubGesture,
}

@Composable
fun GesturePanel(
    gestureButtons: List<GestureButton>,
    subGestures: List<SubGesture>,
    expandedSection: GesturePanelSection?,
    onExpandedSectionChange: (GesturePanelSection?) -> Unit,
    onGestureButtonClick: (GestureButton) -> Unit,
    onSubGestureClick: (String) -> Unit,
    onGestureCheckedChange: (GestureButton, Boolean) -> Unit,
    onSubCheckedChange: (SubGesture, Boolean) -> Unit,
    onAddGesture: () -> Unit,
    onAddSub: () -> Unit,
    onMarkColorClick: (Any) -> Unit,
    onGestureButtonRename: (GestureButton) -> Unit = {},
    onSubGestureRename: (SubGesture) -> Unit = {},
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        GestureSection(
            title = stringResource(id = R.string.gesture_button),
            addContentDescription = stringResource(id = R.string.add_gesture_button),
            expanded = expandedSection == GesturePanelSection.TouchButton,
            itemCount = gestureButtons.size,
            onExpandedChange = { expanded ->
                onExpandedSectionChange(
                    if (expanded) GesturePanelSection.TouchButton else null
                )
            },
            onAdd = onAddGesture,
        ) {
            gestureButtons.fastForEachIndexed { index, button ->
                key(button.id) {
                    val markColor = when (button.color == android.graphics.Color.TRANSPARENT) {
                        true -> MaterialTheme.colorScheme.primary.copy(alpha = GestureButtonColorAlpha)
                        else -> Color(button.color).copy(alpha = GestureButtonColorAlpha)
                    }
                    SegmentedSwitchRow(
                        title = button.buttonTextCompose(),
                        checked = button.enabled,
                        onCheckedChange = { onGestureCheckedChange(button, it) },
                        onClick = { onGestureButtonClick(button) },
                        shape = segmentedShape(index + 1, gestureButtons.size + 1),
                        leadingContent = {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                RenameIcon(onClick = { onGestureButtonRename(button) })
                                Box(
                                    modifier = Modifier
                                        .size(MarkColorSize)
                                        .background(color = markColor, shape = CircleShape)
                                        .clickable(
                                            indication = null,
                                            interactionSource = null,
                                            onClick = { onMarkColorClick(button) },
                                        ),
                                )
                            }
                        },
                    )
                }
            }
        }

        GestureSection(
            title = stringResource(id = R.string.sub_gesture),
            addContentDescription = stringResource(id = R.string.add_sub_gesture),
            expanded = expandedSection == GesturePanelSection.SubGesture,
            itemCount = subGestures.size,
            onExpandedChange = { expanded ->
                onExpandedSectionChange(
                    if (expanded) GesturePanelSection.SubGesture else null
                )
            },
            onAdd = onAddSub,
        ) {
            subGestures.fastForEachIndexed { index, gesture ->
                key(gesture.id) {
                    SegmentedSwitchRow(
                        title = gesture.name,
                        checked = gesture.enabled,
                        onCheckedChange = { onSubCheckedChange(gesture, it) },
                        onClick = { onSubGestureClick(gesture.id) },
                        shape = segmentedShape(index + 1, subGestures.size + 1),
                        leadingContent = {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                RenameIcon(onClick = { onSubGestureRename(gesture) })
                                Box(
                                    modifier = Modifier
                                        .size(MarkColorSize)
                                        .background(
                                            color = Color(gesture.color).copy(alpha = GestureButtonColorAlpha),
                                            shape = CircleShape,
                                        )
                                        .clickable(
                                            indication = null,
                                            interactionSource = null,
                                            onClick = { onMarkColorClick(gesture) },
                                        ),
                                )
                            }
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun GestureSection(
    title: String,
    addContentDescription: String,
    expanded: Boolean,
    itemCount: Int,
    onExpandedChange: (Boolean) -> Unit,
    onAdd: () -> Unit,
    content: @Composable () -> Unit,
) {
    val rotation by animateFloatAsState(
        targetValue = if (expanded) 0f else -90f,
        label = "GestureSectionArrowRotation",
    )
    val colorScheme = MaterialTheme.colorScheme
    val totalItems = if (expanded) itemCount + 1 else 1
    val titleShape = segmentedShape(0, totalItems)

    Column(verticalArrangement = Arrangement.spacedBy(SegmentedGap)) {
        ListItem(
            modifier = Modifier.clickable(onClick = { onExpandedChange(!expanded) }).clip(titleShape),
            headlineContent = { Text(text = title, maxLines = 1) },
            leadingContent = {
                Icon(
                    imageVector = Icons.Filled.ArrowDropDown,
                    contentDescription = null,
                    modifier = Modifier
                        .size(24.dp)
                        .graphicsLayer { rotationZ = rotation },
                    tint = colorScheme.onSurfaceVariant,
                )
            },
            trailingContent = {
                SmallIconAction(
                    imageVector = Icons.Filled.Add,
                    contentDescription = addContentDescription,
                    onClick = onAdd,
                )
            },
            colors = ListItemDefaults.colors(
                containerColor = colorScheme.surfaceBright,
                headlineColor = colorScheme.onSurface,
                leadingIconColor = colorScheme.onSurfaceVariant,
                trailingIconColor = colorScheme.onSurfaceVariant,
            ),
        )

        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(),
            exit = shrinkVertically(),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(SegmentedGap)) {
                content()
            }
        }
    }
}

@Composable
private fun RenameIcon(onClick: () -> Unit) {
    SmallIconAction(
        imageVector = Icons.Filled.Edit,
        contentDescription = stringResource(R.string.rename),
        onClick = onClick,
    )
}

@Composable
private fun SmallIconAction(
    imageVector: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(24.dp)
            .onSingleClick { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = imageVector,
            contentDescription = contentDescription,
            modifier = Modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
