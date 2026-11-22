package hunoia.luno.ui.component.menu

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CheckableDropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import hunoia.luno.ui.theme.LargeShape
import hunoia.luno.ui.theme.MinInteractiveSize
import hunoia.luno.ui.theme.ShapeSmall
import hunoia.luno.ui.theme.SmallShape

/**
 * [LunoDropdownMenu] 的菜单项：文案、前 / 后图标、选中态、禁用与破坏性色在此统一。
 *
 * - [selected]：走 M3 `CheckableDropdownMenuItem`，选中用 M3 selectable 高亮
 *   （`selectedContainerColor`）表达——不画行尾对勾，对勾语义由高亮块替代（ui-guidelines §4.4.1）。
 * - 边距与圆角（参考 FlClash `listItemRadiusOf` 的同心规则）：M3 把色块画成带硬编码
 *   4dp 水平内距的 Surface，容器列又固定 8dp 垂直外距。这里给项加 `padding(horizontal = 4.dp)`，
 *   使色块四边距容器 = 左右 4+4=8dp / 上下 0+8=8dp，**对称 8dp**；色块圆角 =
 *   容器圆角(16dp) − 边距(8dp) = 8dp，与容器同心（容器角色小档 [ShapeSmall]）。`contentPadding`
 *   只内衬文字（8dp 水平呼吸，文字仍距容器边 16dp，与改前一致），不决定色块位置。
 * - 去掉原 12/12 内距后，M3 项无强制最小高，用 `heightIn(min = MinInteractiveSize)` 保住
 *   48dp 点按区与色块高。
 * - 形状用 `MenuDefaults.itemShapes(shape = LargeShape, selectedShape = 选中块圆角)`，
 *   把高亮块收敛到 Luno 容器角色（§2.1.1），不沿用 M3 默认圆角矩形。
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
        shapes = MenuDefaults.itemShapes(
            shape = LargeShape,
            selectedShape = SelectedItemShape,
        ),
        // 色块四边对称 8dp：此 4dp + M3 色块硬编码 4dp = 左右 8dp；容器 8dp 垂直外距 + 0 内距 = 上下 8dp。
        // heightIn 在最外层保住 48dp 点按区与色块高（M3 项无强制最小高）。
        modifier = modifier
            .heightIn(min = MinInteractiveSize)
            .padding(horizontal = 4.dp),
        leadingIcon = leadingIcon,
        trailingContent = trailingIcon,
        enabled = enabled,
        contentPadding = ItemContentPadding,
        colors = MenuDefaults.selectableItemColors(
            // 未选中 / 禁用：容器透明，去掉 M3 每项的内衬色块（两层背景的根因）。
            containerColor = Color.Transparent,
            disabledContainerColor = Color.Transparent,
            // selectedContainerColor 保持 M3 默认：选中的那一行仍是高亮，作为选中指示。
        ),
    )
}

/** 文字内衬：水平 8dp + 垂直 0。只呼吸文字，不决定色块位置（色块由 modifier 4dp + M3 硬编码 4dp 定位）。 */
private val ItemContentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)

/** 选中块圆角 = 容器圆角(16dp) − 边距(8dp) = 8dp，与容器同心（参考 FlClash 同心规则）。 */
/** 选中高亮块：容器角色小档（[ShapeSmall]），主题同源几何，不在组件层拼形状（§2.1.1）。 */
private val SelectedItemShape = SmallShape
