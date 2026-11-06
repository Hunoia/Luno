package hunoia.luno.gesture.settings.style

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.stringResource
import hunoia.luno.gesture.R
import hunoia.luno.ui.component.DirectionLabels
import hunoia.luno.ui.component.RowIconBadge
import hunoia.luno.ui.component.directionLabel
import hunoia.luno.ui.component.row.LunoSettingsRow
import hunoia.luno.ui.theme.LargeShape

/**
 * 方向动作行：标题/图标都由扇区中心角派生（罗盘名 + 角度、箭头按角度旋转），
 * 不再依赖固定四向枚举。
 */
@Composable
fun SideGestureSettings(
    onClick: () -> Unit,
    centerDegrees: Float,
    count: Int,
    subtitle: String,
    shape: Shape = LargeShape,
) {
    LunoSettingsRow(
        onClick = onClick,
        title = directionLabel(centerDegrees, count),
        subtitle = if (subtitle.isNotEmpty()) subtitle else stringResource(id = hunoia.luno.actions.R.string.action_none),
        subtitleColor = MaterialTheme.colorScheme.primary,
        shape = shape,
        leading = {
            RowIconBadge(
                icon = Icons.AutoMirrored.Filled.ArrowForward,
                rotation = DirectionLabels.iconRotation(centerDegrees, count),
            )
        },
    )
}
