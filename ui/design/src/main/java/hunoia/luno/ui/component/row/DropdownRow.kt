package hunoia.luno.ui.component.row

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import hunoia.luno.ui.component.CheckDropdownMenuItem
import hunoia.luno.ui.theme.ElevationSmall
import hunoia.luno.ui.theme.LargeShape

/**
 * 标题行 + 下拉菜单单选，行尾显示当前选项。
 *
 * 选项较多（≥4）或文案较长时，比 `ChoiceRow` 的整行分段选择更稳：
 * 分段按钮会把文字挤成省略号，下拉菜单则保留完整标签。
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
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = Modifier.fillMaxWidth()) {
        LunoSettingsRow(
            title = title,
            subtitle = subtitle ?: options.getOrElse(selectedIndex) { "" },
            shape = shape,
            onClick = { expanded = true },
        )
        Box(modifier = Modifier.align(Alignment.BottomEnd)) {
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                shape = LargeShape,
                tonalElevation = ElevationSmall,
                shadowElevation = 0.dp,
            ) {
                options.forEachIndexed { index, option ->
                    CheckDropdownMenuItem(
                        label = option,
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
