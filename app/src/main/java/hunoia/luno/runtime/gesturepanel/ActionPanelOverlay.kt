package hunoia.luno.runtime.gesturepanel

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import hunoia.luno.ui.theme.ContainerRadius
import hunoia.luno.R
import hunoia.luno.action.model.TriggerType
import hunoia.luno.action.model.StoredAction

@Composable
internal fun ActionPanelBackdrop(
    actionPanelState: ActionPanelState,
    parentSize: Size,
    itemSizePx: Float,
    accentColor: Color,
    modifier: Modifier = Modifier,
) {
    if (parentSize.isEmpty() || !actionPanelState.origin.isSpecified) return
    val origin = actionPanelOrigin(parentSize, actionPanelState.origin, itemSizePx)
    val radius = itemSizePx * 5.8f
    // Brush 必须在 draw lambda 外构造：放在里面会随手指移动每帧新建 list + 3 个 Color
    val brush = remember(accentColor, origin, radius) {
        Brush.radialGradient(
            colors = listOf(
                accentColor.copy(alpha = 0.52f),
                accentColor.copy(alpha = 0.28f),
                Color.Transparent,
            ),
            center = origin,
            radius = radius,
        )
    }
    Canvas(modifier = modifier) {
        drawCircle(brush = brush, radius = radius, center = origin)
    }
}

@Composable
internal fun SelectedActionPill(
    action: StoredAction,
    label: String,
    triggerType: TriggerType,
    accentColor: Color,
    modifier: Modifier = Modifier,
) {
    val longPress = triggerType == TriggerType.LongPress
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(24.dp),
        color = if (longPress) accentColor else MaterialTheme.colorScheme.surfaceContainerHigh,
        shadowElevation = 8.dp,
        tonalElevation = 4.dp,
    ) {
        Row(
            modifier = Modifier
                .widthIn(max = 260.dp)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                modifier = Modifier.size(32.dp),
                shape = CircleShape,
                color = if (longPress) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.18f) else accentColor,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    ActionPanelIcon(action = action, iconSize = 20.dp, bitmapIconSize = 24.dp)
                }
            }
            Spacer(Modifier.width(8.dp))
            Text(
                modifier = Modifier.weight(1f, false),
                text = label,
                maxLines = 1,
                style = MaterialTheme.typography.titleSmall,
                color = if (longPress) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.width(8.dp))
            Surface(
                shape = RoundedCornerShape(ContainerRadius),
                color = if (longPress) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.18f) else accentColor.copy(alpha = 0.18f),
            ) {
                Text(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    text = stringResource(if (longPress) R.string.long_press else R.string.tap),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (longPress) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        }
    }
}
