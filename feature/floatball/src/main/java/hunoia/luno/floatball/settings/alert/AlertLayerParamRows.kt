package hunoia.luno.floatball.settings.alert

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Shape
import hunoia.luno.ui.component.input.LunoTextSlider
import hunoia.luno.ui.component.row.LunoDropdownRow
import kotlin.math.roundToInt
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

/**
 * 层编辑器的参数控件：浮点滑杆 / 整数滑杆 / 单选下拉。
 *
 * 滑杆拖动只改本地预览，松手一次性落盘（与设置页口径一致）；单选一律走
 * [LunoDropdownRow]（ui-guidelines §4.4：设置值不用行内 chip），不再有 ChipSelector。
 */

@Composable
internal fun LayerFloatSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    decimals: Int = 2,
    shape: Shape,
    display: ((Float) -> String)? = null,
    onCommit: (Float) -> Unit,
) {
    var local by remember(value, range) { mutableFloatStateOf(value.coerceIn(range)) }
    LunoTextSlider(
        value = local,
        onValueChange = { local = it },
        onValueChangeFinished = { onCommit(local.coerceIn(range)) },
        text = label,
        valueDisplay = { current -> display?.invoke(current) ?: current.format(decimals) },
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

/** 枚举 / 可空枚举的单选下拉：行尾显示当前值，点开菜单选。 */
@Composable
internal fun <T> LayerDropdownRow(
    title: String,
    options: List<T>,
    selected: T,
    shape: Shape,
    optionLabel: @Composable (T) -> String,
    onSelect: (T) -> Unit,
) {
    LunoDropdownRow(
        title = title,
        options = options.map { optionLabel(it) },
        selectedIndex = options.indexOf(selected).coerceAtLeast(0),
        onSelect = { index -> onSelect(options[index]) },
        shape = shape,
    )
}

private fun Float.format(decimals: Int): String = when (decimals) {
    0 -> roundToInt().toString()
    1 -> "%.1f".format(this)
    else -> "%.2f".format(this)
}

/** 屏幕坐标系的 8 方位箭头（0 = 右，90 = 下，270 = 上）。 */
internal fun directionArrow(deg: Float): String {
    val index = (((deg / 45f).roundToInt() % 8) + 8) % 8
    return listOf("→", "↘", "↓", "↙", "←", "↖", "↑", "↗")[index]
}