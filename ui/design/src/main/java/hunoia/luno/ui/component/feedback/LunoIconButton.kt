package hunoia.luno.ui.component.feedback

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import hunoia.luno.ui.component.pressScale

/**
 * 纯图标按钮的唯一实现。M3 `IconButton` 自带 48dp 最小触控区；
 * 可访问名做成必填参数，`contentDescription = null` 在此签名下写不出来。
 * [tint] 为 null 时用按钮位当前的 LocalContentColor（M3 默认）。
 *
 * 点击反馈 = 按压缩放（全站已关 ripple，ui-guidelines §5.3）：图标按钮是最小号目标，
 * 按 compact 档缩。不再提供 `ripple` 开关——没有第二套高亮了。
 */
@Composable
fun LunoIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    tint: Color? = null,
) {
    val contentColor = tint ?: LocalContentColor.current
    val interactionSource = remember { MutableInteractionSource() }
    Box(
        modifier = modifier
            .minimumInteractiveComponentSize()
            .pressScale(interactionSource)
            .clickable(
                interactionSource = interactionSource,
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
}
