package hunoia.luno.ui.home

import hunoia.luno.ui.theme.*
import hunoia.luno.R
import hunoia.luno.ui.component.ExpressiveSwitchItem
import hunoia.luno.config.defaults.SettingsUiDefaults.GestureButtonColorAlpha
import hunoia.luno.config.model.GestureButton
import hunoia.luno.config.model.SubGesture
import hunoia.luno.ui.component.buttonTextCompose
import hunoia.luno.ui.component.settings.CompactSettingsRow

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.util.fastForEach
import com.aaron.compose.ktx.onSingleClick

@Composable
fun GestureEntryCard(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
) {
    CompactSettingsRow(
        title = title,
        subtitle = subtitle,
        icon = icon,
        onClick = onClick,
        trailing = {
            Icon(
                imageVector = Icons.Default.ArrowDropDown,
                contentDescription = null,
            )
        },
    )
}

@Composable
fun GestureButtonList(
    visible: Boolean,
    buttons: List<GestureButton>,
    onItemClick: (GestureButton) -> Unit,
    onCheckedChange: (GestureButton, Boolean) -> Unit,
    onAddClick: () -> Unit,
    onMarkColorClick: (Any) -> Unit,
    onRenameClick: (GestureButton) -> Unit = {},
) {
    AnimatedVisibility(
        modifier = Modifier.fillMaxWidth(),
        visible = visible,
        enter = expandVertically() + fadeIn(),
        exit = shrinkVertically(animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessLow)) + fadeOut(),
    ) {
        Column(modifier = Modifier.padding(top = Spacing12)) {
            buttons.fastForEach { button ->
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
                        onClick = { onItemClick(button) },
                        onCheckedChange = { onCheckedChange(button, it) },
                        modifier = Modifier.padding(bottom = Spacing8),
                        icon = {
                            Box(
                                modifier = Modifier
                                    .size(Spacing24)
                                    .onSingleClick { onRenameClick(button) },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Edit,
                                    contentDescription = stringResource(R.string.rename),
                                    modifier = Modifier.size(Spacing16),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        },
                    )
                }
            }
            AddEntryButton(
                text = stringResource(id = R.string.add_gesture_button),
                onClick = onAddClick,
            )
        }
    }
}

@Composable
fun SubGestureList(
    visible: Boolean,
    gestures: List<SubGesture>,
    onItemClick: (String) -> Unit,
    onCheckedChange: (SubGesture, Boolean) -> Unit,
    onAddClick: () -> Unit,
    onMarkColorClick: (Any) -> Unit,
    onRenameClick: (SubGesture) -> Unit = {},
) {
    AnimatedVisibility(
        modifier = Modifier.fillMaxWidth(),
        visible = visible,
        enter = expandVertically() + fadeIn(),
        exit = shrinkVertically(animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessLow)) + fadeOut(),
    ) {
        Column(modifier = Modifier.padding(top = Spacing12)) {
            gestures.fastForEach { gesture ->
                key(gesture.id) {
                    ExpressiveSwitchItem(
                        title = gesture.name,
                        checked = gesture.enabled,
                        markColor = Color(gesture.color).copy(alpha = GestureButtonColorAlpha),
                        onMarkColorClick = { onMarkColorClick(gesture) },
                        onClick = { onItemClick(gesture.id) },
                        onCheckedChange = { onCheckedChange(gesture, it) },
                        modifier = Modifier.padding(bottom = Spacing8),
                        icon = {
                            Box(
                                modifier = Modifier
                                    .size(Spacing24)
                                    .onSingleClick { onRenameClick(gesture) },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Edit,
                                    contentDescription = stringResource(R.string.rename),
                                    modifier = Modifier.size(Spacing16),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        },
                    )
                }
            }
            AddEntryButton(
                text = stringResource(id = R.string.add_sub_gesture),
                onClick = onAddClick,
            )
        }
    }
}

@Composable
fun AddEntryButton(text: String, onClick: () -> Unit) {
    FilledTonalButton(
        modifier = Modifier.fillMaxWidth().heightIn(min = MinItemHeightNoSecondary),
        onClick = onClick,
    ) {
        Text(text = text, style = MaterialTheme.typography.titleMedium)
    }
}
