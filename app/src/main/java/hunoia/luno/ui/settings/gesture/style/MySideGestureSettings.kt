package hunoia.luno.ui.settings.gesture.style

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import hunoia.luno.R
import hunoia.luno.config.model.GestureButton
import hunoia.luno.config.model.GestureDirection
import hunoia.luno.ui.component.ExpressiveRow
import hunoia.luno.ui.theme.Spacing20
import hunoia.luno.ui.theme.Spacing32

@Composable
fun MySideGestureSettings(
    onClick: () -> Unit,
    gestureButton: GestureButton,
    direction: GestureDirection,
    isLongSlide: Boolean,
    secondaryText: String,
    text: String? = null,
    trailing: (@Composable () -> Unit)? = null
) {
    val accent = if (isLongSlide) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.primaryContainer
    val onAccent = if (isLongSlide) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onPrimaryContainer
    val label = text ?: direction.label()
    val rotation = direction.rotation()
    ExpressiveRow(
        onClick = onClick,
        text = label,
        secondaryText = if (secondaryText.isNotEmpty()) secondaryText else stringResource(id = R.string.action_none),
        secondaryTextColor = MaterialTheme.colorScheme.primary,
        trailing = trailing,
        icon = {
            Surface(
                modifier = Modifier.size(Spacing32),
                shape = MaterialTheme.shapes.medium,
                color = accent,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        modifier = Modifier
                            .graphicsLayer { rotationZ = rotation }
                            .size(Spacing20),
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = null,
                        tint = onAccent,
                    )
                }
            }
        },
    )
}

@Composable
private fun GestureDirection.label(): String = when (this) {
    GestureDirection.Left -> stringResource(R.string.slide_to_left)
    GestureDirection.UpLeft -> stringResource(R.string.slide_to_top_left)
    GestureDirection.Up -> stringResource(R.string.slide_to_top)
    GestureDirection.UpRight -> stringResource(R.string.slide_to_top_right)
    GestureDirection.Right -> stringResource(R.string.slide_to_right)
    GestureDirection.DownRight -> stringResource(R.string.slide_to_bottom_right)
    GestureDirection.Down -> stringResource(R.string.slide_to_bottom)
    GestureDirection.DownLeft -> stringResource(R.string.slide_to_bottom_left)
}

private fun GestureDirection.rotation(): Float = when (this) {
    GestureDirection.Right -> 0f
    GestureDirection.DownRight -> 45f
    GestureDirection.Down -> 90f
    GestureDirection.DownLeft -> 135f
    GestureDirection.Left -> 180f
    GestureDirection.UpLeft -> -135f
    GestureDirection.Up -> -90f
    GestureDirection.UpRight -> -45f
}
