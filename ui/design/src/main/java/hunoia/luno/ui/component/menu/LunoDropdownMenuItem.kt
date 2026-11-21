package hunoia.luno.ui.component.menu

import androidx.compose.material3.CheckableDropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import hunoia.luno.ui.theme.LargeShape

/**
 * [LunoDropdownMenu] 的菜单项：文案、前 / 后图标、选中态、禁用与破坏性色在此统一。
 *
 * - [selected]：走 M3 `CheckableDropdownMenuItem`。M3 默认的 selectable 容器色非透明，会把
 *   每一项包成一层圆角色块叠在菜单上（“两层背景”）；这里把未选中容器设为透明，菜单
 *   回到单层背景，选中态只由 M3 selectable 高亮（`selectedContainerColor`）表达——不画
 *   行尾对勾，对勾语义由高亮块替代（ui-guidelines §4.4.1）。
 * - 形状用 `MenuDefaults.itemShapes(shape = LargeShape, selectedShape = LargeShape)`，把
 *   高亮块收敛到 Luno 容器角色（§2.1.1），不沿用 M3 默认圆角矩形。
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
        shapes = MenuDefaults.itemShapes(shape = LargeShape, selectedShape = LargeShape),
        modifier = modifier,
        leadingIcon = leadingIcon,
        trailingContent = trailingIcon,
        enabled = enabled,
        colors = MenuDefaults.selectableItemColors(
            // 未选中 / 禁用：容器透明，去掉 M3 每项的内衬色块（两层背景的根因）。
            containerColor = Color.Transparent,
            disabledContainerColor = Color.Transparent,
            // selectedContainerColor 保持 M3 默认：选中的那一行仍是高亮，作为选中指示。
        ),
    )
}
