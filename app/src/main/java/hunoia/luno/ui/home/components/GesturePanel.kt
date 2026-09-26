package hunoia.luno.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastForEach
import com.aaron.compose.ktx.onSingleClick
import hunoia.luno.R
import hunoia.luno.config.defaults.SettingsUiDefaults.GestureButtonColorAlpha
import hunoia.luno.config.model.GestureButton
import hunoia.luno.config.model.SubGesture
import hunoia.luno.ui.component.ExpressiveSwitchItem
import hunoia.luno.ui.component.buttonTextCompose
import hunoia.luno.ui.theme.CardShape

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
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        GestureSectionCard(
            title = stringResource(id = R.string.gesture_button_count, gestureButtons.size),
            addContentDescription = stringResource(id = R.string.add_gesture_button),
            expanded = expandedSection == GesturePanelSection.TouchButton,
            onExpandedChange = { expanded ->
                onExpandedSectionChange(
                    if (expanded) GesturePanelSection.TouchButton else null
                )
            },
            onAdd = onAddGesture,
        ) {
            gestureButtons.fastForEach { button ->
                key(button) {
                    val markColor = when (button.color == android.graphics.Color.TRANSPARENT) {
                        true -> MaterialTheme.colorScheme.primary.copy(alpha = GestureButtonColorAlpha)
                        else -> Color(button.color).copy(alpha = GestureButtonColorAlpha)
                    }
                    ExpressiveSwitchItem(
                        title = button.buttonTextCompose(),
                        checked = button.enabled,
                        markColor = markColor,
                        onMarkColorClick = { onMarkColorClick(button) },
                        onClick = { onGestureButtonClick(button) },
                        onCheckedChange = { onGestureCheckedChange(button, it) },
                        modifier = Modifier.padding(bottom = 8.dp),
                        icon = {
                            RenameIcon(onClick = { onGestureButtonRename(button) })
                        },
                    )
                }
            }
        }

        GestureSectionCard(
            title = stringResource(id = R.string.sub_gesture_count, subGestures.size),
            addContentDescription = stringResource(id = R.string.add_sub_gesture),
            expanded = expandedSection == GesturePanelSection.SubGesture,
            onExpandedChange = { expanded ->
                onExpandedSectionChange(
                    if (expanded) GesturePanelSection.SubGesture else null
                )
            },
            onAdd = onAddSub,
        ) {
            subGestures.fastForEach { gesture ->
                key(gesture.id) {
                    ExpressiveSwitchItem(
                        title = gesture.name,
                        checked = gesture.enabled,
                        markColor = Color(gesture.color).copy(alpha = GestureButtonColorAlpha),
                        onMarkColorClick = { onMarkColorClick(gesture) },
                        onClick = { onSubGestureClick(gesture.id) },
                        onCheckedChange = { onSubCheckedChange(gesture, it) },
                        modifier = Modifier.padding(bottom = 8.dp),
                        icon = {
                            RenameIcon(onClick = { onSubGestureRename(gesture) })
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun GestureSectionCard(
    title: String,
    addContentDescription: String,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onAdd: () -> Unit,
    content: @Composable () -> Unit,
) {
    val rotation by animateFloatAsState(
        targetValue = if (expanded) 0f else -90f,
        label = "GestureSectionArrowRotation",
    )
    val headerInteractionSource = remember { MutableInteractionSource() }
    Surface(
        shape = CardShape,
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(
                        interactionSource = headerInteractionSource,
                        indication = null,
                    ) { onExpandedChange(!expanded) },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Filled.ArrowDropDown,
                    contentDescription = null,
                    modifier = Modifier
                        .size(24.dp)
                        .graphicsLayer { rotationZ = rotation },
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                SmallIconAction(
                    imageVector = Icons.Filled.Add,
                    contentDescription = addContentDescription,
                    onClick = onAdd,
                )
            }
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(),
                exit = shrinkVertically(),
            ) {
                Column {
                    Spacer(Modifier.height(12.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Spacer(Modifier.height(12.dp))
                    content()
                }
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
