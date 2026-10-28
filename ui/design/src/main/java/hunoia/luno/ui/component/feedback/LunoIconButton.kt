package hunoia.luno.ui.component.feedback

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * 纯图标按钮的唯一实现。M3 `IconButton` 自带 48dp 最小触控区；
 * 可访问名做成必填参数，`contentDescription = null` 在此签名下写不出来。
 * [tint] 为 null 时用按钮位当前的 LocalContentColor（M3 默认）。
 * [ripple] 为 false 时换成无涟漪实现（触控区与禁用态配色对齐 M3）：打开菜单这类
 * 自身已带外部反馈的操作，涟漪是第二份高亮。
 */
@Composable
fun LunoIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    tint: Color? = null,
    ripple: Boolean = true,
) {
    val contentColor = tint ?: LocalContentColor.current
    if (!ripple) {
        Box(
            modifier = modifier
                .minimumInteractiveComponentSize()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    enabled = enabled,
                    onClick = onClick,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = if (enabled) contentColor else contentColor.copy(alpha = 0.38f),
            )
        }
    } else {
        IconButton(
            onClick = onClick,
            modifier = modifier,
            enabled = enabled,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = contentColor,
            )
        }
    }
}
