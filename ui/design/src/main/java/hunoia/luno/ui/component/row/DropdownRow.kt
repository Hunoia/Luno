package hunoia.luno.ui.component.row

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import hunoia.luno.ui.component.menu.LunoDropdownMenu
import hunoia.luno.ui.component.menu.LunoDropdownMenuItem

/**
 * 标题行 + 下拉菜单单选，行尾显示当前选项。
 *
 * 选项较多（≥4）或文案较长时，整行分段选择会把文字挤成省略号，下拉菜单保留完整标签。
 * `subtitle` 传提示文案时，当前选项显示在行尾（trailing + 箭头）；不传时当前选项作副标题。
 * 菜单视觉与日志设置 / 动作库类型菜单一致，单一实现不各页重写。
 */
@Composable
fun LunoDropdownRow(
    title: String,
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    shape: Shape,
    subtitle: String? = null,
    /** 置灰且不展开菜单（条件未满足时用副标题说明原因）。 */
    enabled: Boolean = true,
) {
    var expanded by remember { mutableStateOf(false) }
    val current = options.getOrElse(selectedIndex) { "" }
    Box(modifier = Modifier.fillMaxWidth()) {
        LunoSettingsRow(
            title = title,
            // 无 hint 时当前值作副标题；有 hint（副标题被占）时当前值改走行尾，收起也能看到。
            subtitle = subtitle ?: current,
            shape = shape,
            enabled = enabled,
            trailing = if (subtitle != null) {
                {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            text = current,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            } else {
                null
            },
            onClick = if (enabled) ({ expanded = true }) else null,
        )
        Box(modifier = Modifier.align(Alignment.BottomEnd)) {
            LunoDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
            ) {
                options.forEachIndexed { index, option ->
                    LunoDropdownMenuItem(
                        text = option,
                        selected = index == selectedIndex,
                        onClick = {
                            expanded = false
                            onSelect(index)
                        },
                    )
                }
            }
        }
    }
}
