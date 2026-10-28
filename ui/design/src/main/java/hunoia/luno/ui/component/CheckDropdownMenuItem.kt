package hunoia.luno.ui.component

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable

/**
 * 下拉项：选中时行尾显示主色对勾；关闭展开态由调用方在 [onClick] 里处理。
 */
@Composable
fun CheckDropdownMenuItem(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    DropdownMenuItem(
        text = { Text(label) },
        trailingIcon = {
            if (selected) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        },
        onClick = onClick,
    )
}
