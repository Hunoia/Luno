package hunoia.luno.ui.settings.action

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import hunoia.luno.design.R
import hunoia.luno.ui.component.PressIconButton

/**
 * 整屏调整页（指针 / 小窗）的顶部按钮条：不画底、不加标题，
 * 只有「左取消，右重置 + 确认」三个圆形按钮，内边距避开状态栏与挖孔。
 * 两页共用，避免各写一套。
 */
@Composable
fun AdjustTopBar(
    onCancel: () -> Unit,
    onReset: () -> Unit,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier,
    confirmTint: Color = MaterialTheme.colorScheme.primary,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.statusBars.union(WindowInsets.displayCutout))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        AdjustCircleButton(
            icon = Icons.Default.Close,
            contentDescription = stringResource(R.string.cancel),
            tint = Color.White,
            onClick = onCancel,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AdjustCircleButton(
                icon = Icons.Default.Refresh,
                contentDescription = stringResource(R.string.reset),
                tint = Color.White,
                onClick = onReset,
            )
            AdjustCircleButton(
                icon = Icons.Default.Check,
                contentDescription = stringResource(R.string.confirm),
                tint = confirmTint,
                onClick = onConfirm,
            )
        }
    }
}

/** 白色 10% 底 + 圆形的动作按钮，调整页顶部与框内微调共用同一外形。 */
@Composable
fun AdjustCircleButton(
    icon: ImageVector,
    contentDescription: String,
    tint: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    PressIconButton(
        onClick = onClick,
        modifier = modifier.background(Color.White.copy(alpha = 0.10f), CircleShape),
    ) {
        Icon(imageVector = icon, contentDescription = contentDescription, tint = tint)
    }
}
