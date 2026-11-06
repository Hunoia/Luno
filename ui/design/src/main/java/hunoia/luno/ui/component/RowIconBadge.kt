package hunoia.luno.ui.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import hunoia.luno.ui.theme.RowIconBadgeIconSize
import hunoia.luno.ui.theme.RowIconBadgeSize

@Composable
fun RowIconBadge(
    icon: ImageVector,
    modifier: Modifier = Modifier,
    rotation: Float = 0f,
) {
    val colorScheme = MaterialTheme.colorScheme
    Surface(
        modifier = modifier.size(RowIconBadgeSize),
        // 32dp 图标底：容器档上限 h/2.96 = 10.8dp，形状上已接近正圆（
        // 旧实现 20dp 被 clamp 到 16dp 也正好是圆），故归 1:1 的正圆角色（§2.1.1）。
        shape = CircleShape,
        color = colorScheme.primaryContainer,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                modifier = Modifier
                    .graphicsLayer { rotationZ = rotation }
                    .size(RowIconBadgeIconSize),
                imageVector = icon,
                contentDescription = null,
                tint = colorScheme.onPrimaryContainer,
            )
        }
    }
}
