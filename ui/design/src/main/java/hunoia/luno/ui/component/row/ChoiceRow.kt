package hunoia.luno.ui.component.row

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import hunoia.luno.ui.theme.CardInnerSpacing

/**
 * 标题（+ 可选副标题）+ 整行分段选择。
 *
 * 2–4 个互斥选项时比塞在行尾可读性好，也不会在 row 的紧凑尾部里把文字挤成省略号。
 * 单一实现：链接分发设置页与规则编辑页共用，不在页面里各写一套。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChoiceRow(
    title: String,
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    shape: Shape,
    subtitle: String? = null,
) {
    Surface(
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceBright,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(
                horizontal = CardInnerSpacing * 2,
                vertical = CardInnerSpacing * 2,
            ),
            verticalArrangement = Arrangement.spacedBy(CardInnerSpacing * 2),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(text = title, style = MaterialTheme.typography.bodyLarge)
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                options.forEachIndexed { index, option ->
                    SegmentedButton(
                        selected = index == selectedIndex,
                        onClick = { onSelect(index) },
                        shape = SegmentedButtonDefaults.itemShape(
                            count = options.size,
                            index = index,
                        ),
                    ) {
                        Text(text = option, maxLines = 1)
                    }
                }
            }
        }
    }
}
