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
import hunoia.luno.ui.theme.ContainerRadius
import hunoia.luno.ui.component.slideToLabel
import hunoia.luno.ui.component.row.LunoSettingsRow
@Composable
fun SideGestureSettings(
    onClick: () -> Unit,
    direction: GestureDirection,
    isLongSlide: Boolean,
    subtitle: String,
    text: String? = null,
    shape: Shape = RoundedCornerShape(ContainerRadius),
) {
    val label = text ?: direction.slideToLabel()
    val rotation = direction.rotation()
    LunoSettingsRow(
        onClick = onClick,
        title = label,
        subtitle = if (subtitle.isNotEmpty()) subtitle else stringResource(id = R.string.action_none),
        subtitleColor = MaterialTheme.colorScheme.primary,
        shape = shape,
        leading = {
            RowIconBadge(
                icon = Icons.AutoMirrored.Filled.ArrowForward,
                rotation = rotation,
            )
        },
    )
}
@Composable
private fun GestureDirection.rotation(): Float = when (this) {
    GestureDirection.Right -> 0f
    GestureDirection.Down -> 90f
    GestureDirection.Left -> 180f
    GestureDirection.Up -> -90f
}