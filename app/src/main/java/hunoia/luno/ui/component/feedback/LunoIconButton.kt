package hunoia.luno.ui.component.feedback

import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * 纯图标按钮的唯一实现（见 docs/ui-spec.md §7）。
 *
 * M3 `IconButton` 自带 48dp 最小触控区；这里把可访问名做成必填参数，
 * `contentDescription = null` 在此签名下写不出来。
 */
@Composable
fun LunoIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    IconButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
        )
    }
}
