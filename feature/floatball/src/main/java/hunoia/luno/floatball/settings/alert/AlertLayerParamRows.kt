package hunoia.luno.floatball.settings.alert

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import hunoia.luno.ui.component.PressFilterChip
import hunoia.luno.ui.component.input.LunoTextSlider
import kotlin.math.roundToInt

/**
 * 层编辑器的参数控件：浮点滑杆 / 整数滑杆 / 选项胶囊。
 *
 * 滑杆拖动只改本地预览，松手一次性落盘（与设置页口径一致）；不做输入框，
 * 避免中文输入法与滑杆抢焦点。
 */

@Composable
internal fun LayerFloatSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    decimals: Int = 2,
    shape: Shape,
    onCommit: (Float) -> Unit,
) {
    var local by remember(value, range) { mutableFloatStateOf(value.coerceIn(range)) }
    LunoTextSlider(
        value = local,
        onValueChange = { local = it },
        onValueChangeFinished = { onCommit(local.coerceIn(range)) },
        text = label,
        valueDisplay = { it.format(decimals) },
        valueRange = range,
        shape = shape,
    )
}

@Composable
internal fun LayerIntSlider(
    label: String,
    value: Int,
    range: IntRange,
    suffix: String = "",
    shape: Shape,
    onCommit: (Int) -> Unit,
) {
    var local by remember(value, range) { mutableFloatStateOf(value.toFloat()) }
    LunoTextSlider(
        value = local,
        onValueChange = { local = it },
        onValueChangeFinished = { onCommit(local.roundToInt().coerceIn(range)) },
        text = label,
        valueDisplay = { "${it.roundToInt()}$suffix" },
        valueRange = range.first.toFloat()..range.last.toFloat(),
        shape = shape,
    )
}

/** 选项胶囊（形态 / 区域 / 形状 / 方向）。 */
@Composable
internal fun <T> ChipSelector(
    label: String,
    options: List<T>,
    selected: T,
    optionLabel: @Composable (T) -> String,
    onSelect: (T) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
    ) {
        if (label.isNotBlank()) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 6.dp),
            )
        }
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            options.forEach { option ->
                PressFilterChip(
                    selected = option == selected,
                    onClick = { onSelect(option) },
                    label = { Text(optionLabel(option)) },
                )
            }
        }
    }
}

private fun Float.format(decimals: Int): String = when (decimals) {
    0 -> roundToInt().toString()
    1 -> "%.1f".format(this)
    else -> "%.2f".format(this)
}