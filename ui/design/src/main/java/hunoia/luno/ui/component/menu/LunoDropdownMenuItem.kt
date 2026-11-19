package hunoia.luno.ui.component.menu

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

/**
 * [LunoDropdownMenu] 的菜单项：文案、前 / 后图标、选中对勾、禁用与破坏性色在此统一。
 *
 * - [selected]：行尾显示主色对勾（覆盖 [trailingIcon]）。
 * - [isError]：破坏性操作，文案用 `error` 色（如清空 / 删除），[enabled] 不影响该色。
 * - 关闭展开态由调用方在 [onClick] 里处理。
 */
@Composable
fun LunoDropdownMenuItem(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leadingIcon: (@Composable () -> Unit)? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
    selected: Boolean = false,
    enabled: Boolean = true,
    isError: Boolean = false,
) {
    DropdownMenuItem(
        text = {
            // 默认不设色交给 M3（enabled=false 时自动置灰）；isError 用 error 色。
            Text(
                text = text,
                color = if (isError) MaterialTheme.colorScheme.error else Color.Unspecified,
            )
        },
        onClick = onClick,
        modifier = modifier,
        leadingIcon = leadingIcon,
        trailingIcon = trailingIcon ?: if (selected) {
            {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        } else {
            null
        },
        enabled = enabled,
    )
}
