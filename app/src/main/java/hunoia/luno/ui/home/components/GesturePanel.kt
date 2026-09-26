package hunoia.luno.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
import hunoia.luno.ui.theme.ExpressiveMotion
import hunoia.luno.ui.theme.MinItemHeightNoSecondary

@Composable
fun GesturePanel(
    gestureButtons: List<GestureButton>,
    subGestures: List<SubGesture>,
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
    var firstVisible by remember { mutableStateOf(false) }
    var secondVisible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        firstVisible = true
    }
    LaunchedEffect(firstVisible) {
        if (firstVisible) secondVisible = true
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        AnimatedVisibility(
            visible = firstVisible,
            enter = fadeIn(animationSpec = ExpressiveMotion.defaultEffectsSpec()) +
                slideInVertically(animationSpec = ExpressiveMotion.slowSpatialSpec()) { it / 2 },
            exit = fadeOut(animationSpec = ExpressiveMotion.fastEffectsSpec()) +
                slideOutVertically(animationSpec = ExpressiveMotion.fastSpatialSpec()) { it / 2 },
        ) {
        GestureSectionCard(
            title = stringResource(id = R.string.gesture_button),
            onAdd = onAddGesture,
            addText = stringResource(id = R.string.add_gesture_button),
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
        }

        AnimatedVisibility(
            visible = secondVisible,
            enter = fadeIn(animationSpec = ExpressiveMotion.defaultEffectsSpec()) +
                slideInVertically(animationSpec = ExpressiveMotion.slowSpatialSpec()) { it / 2 },
            exit = fadeOut(animationSpec = ExpressiveMotion.fastEffectsSpec()) +
                slideOutVertically(animationSpec = ExpressiveMotion.fastSpatialSpec()) { it / 2 },
        ) {
        GestureSectionCard(
            title = stringResource(id = R.string.sub_gesture_list),
            onAdd = onAddSub,
            addText = stringResource(id = R.string.add_sub_gesture),
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
}

@Composable
private fun GestureSectionCard(
    title: String,
    addText: String,
    onAdd: () -> Unit,
    content: @Composable () -> Unit,
) {
    Surface(
        shape = CardShape,
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(text = title, style = MaterialTheme.typography.titleMedium)
                FilledTonalButton(
                    onClick = onAdd,
                    modifier = Modifier.height(MinItemHeightNoSecondary),
                ) {
                    Text(text = addText, style = MaterialTheme.typography.labelLarge)
                }
            }
            Spacer(Modifier.height(12.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(Modifier.height(12.dp))
            content()
        }
    }
}

@Composable
private fun RenameIcon(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(24.dp)
            .onSingleClick { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Filled.Edit,
            contentDescription = stringResource(R.string.rename),
            modifier = Modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
