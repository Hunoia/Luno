package hunoia.luno.ui.component.menu

import androidx.compose.material3.CheckableDropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

/**
 * [LunoDropdownMenu] 的菜单项：文案、前 / 后图标、选中态、禁用与破坏性色在此统一。
 *
 * - [selected]：走 M3 `CheckableDropdownMenuItem`，选中对勾、无障碍与形状动画由 M3 管，
 *   不再手拼尾部图标（ui-guidelines §4.4.1）。
 * - [isError]：破坏性操作，文案用 `error` 色（如清空 / 删除），[enabled] 不影响该色。
 * - 关闭展开态由调用方在 [onClick] 里处理；[onCheckedChange] 即点击动作，不会因再次
 *   点击已选项而不触发。
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
    CheckableDropdownMenuItem(
        checked = selected,
        onCheckedChange = { onClick() },
        text = {
            // 默认不设色交给 M3（enabled=false 时自动置灰）；isError 用 error 色。
            Text(
                text = text,
                color = if (isError) MaterialTheme.colorScheme.error else Color.Unspecified,
            )
        },
        shapes = MenuDefaults.itemShapes(),
        modifier = modifier,
        leadingIcon = leadingIcon,
        trailingContent = trailingIcon,
        enabled = enabled,
    )
}
