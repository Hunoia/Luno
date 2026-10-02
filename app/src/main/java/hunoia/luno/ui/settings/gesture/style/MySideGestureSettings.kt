package hunoia.luno.ui.settings.gesture.style

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.stringResource
import hunoia.luno.R
import hunoia.luno.config.model.GestureDirection
import hunoia.luno.ui.component.RowIconBadge
import hunoia.luno.ui.component.SegmentedSettingsRow
import hunoia.luno.ui.theme.ContainerRadius


@Composable
fun MySideGestureSettings(
    onClick: () -> Unit,
    direction: GestureDirection,
    isLongSlide: Boolean,
    secondaryText: String,
    text: String? = null,
    trailing: (@Composable () -> Unit)? = null,
    shape: Shape = RoundedCornerShape(ContainerRadius),
) {
    val label = text ?: direction.label()
    val rotation = direction.rotation()
    SegmentedSettingsRow(
        onClick = onClick,
        title = label,
        subtitle = if (secondaryText.isNotEmpty()) secondaryText else stringResource(id = R.string.action_none),
        secondaryTextColor = MaterialTheme.colorScheme.primary,
        trailingContent = trailing,
        shape = shape,
        leadingContent = {
            RowIconBadge(
                icon = Icons.AutoMirrored.Filled.ArrowForward,
                rotation = rotation,
            )
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
