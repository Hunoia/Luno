package hunoia.luno.picker.components.rowsettings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import hunoia.luno.ui.component.input.LunoTextSlider
import hunoia.luno.ui.component.row.ChoiceRow
import hunoia.luno.ui.component.row.LunoGroup
import hunoia.luno.ui.component.row.LunoSelectableRow
import hunoia.luno.ui.component.row.selectedSegmentedShape
import hunoia.luno.ui.theme.LargeShape
import hunoia.luno.ui.theme.PageGutter

/**
 * 行设置的统一排版：一个参数 = 一个控件，参数名只出现一次。
 *
 * - 2–3 个互斥选项：[SettingChoiceRow]（行内分段，复用 [ChoiceRow]）。
 * - 4 个以上互斥选项：[SettingChoiceList]（分组标题 + 单选列表）。
 * - 多选列表：[SettingMultiChoiceList]（同上，选中态表达多选）。
 * - 数值 / 时长：[SettingSlider]（复用 [LunoTextSlider]）。
 *
 * 弹层内容由这些组件自己带 [PageGutter] 左右留白：分组标题由 [LunoGroup] 提供，
 * 卡片与滑块自己加，避免外层再套一层 padding 造成双重缩进。
 */

@Composable
internal fun SettingChoiceRow(
    title: String,
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = PageGutter),
    ) {
        ChoiceRow(
            title = title,
            options = options,
            selectedIndex = selectedIndex,
            onSelect = onSelect,
            shape = LargeShape,
        )
    }
}

@Composable
internal fun SettingChoiceList(
    title: String,
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
) {
    val flags = List(options.size) { it == selectedIndex }
    LunoGroup(title = title) {
        options.forEachIndexed { index, label ->
            LunoSelectableRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = PageGutter),
                title = label,
                selected = index == selectedIndex,
                onSelectedChange = { checked -> if (checked) onSelect(index) },
                singleSelect = true,
                shape = selectedSegmentedShape(index, options.size, flags),
            )
        }
    }
}

@Composable
internal fun SettingMultiChoiceList(
    title: String,
    options: List<Pair<String, String>>,
    selectedIds: Set<String>,
    onToggle: (id: String, checked: Boolean) -> Unit,
) {
    val flags = options.map { it.first in selectedIds }
    LunoGroup(title = title) {
        options.forEachIndexed { index, (id, label) ->
            LunoSelectableRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = PageGutter),
                title = label,
                selected = id in selectedIds,
                onSelectedChange = { checked -> onToggle(id, checked) },
                shape = selectedSegmentedShape(index, options.size, flags),
            )
        }
    }
}

@Composable
internal fun SettingSlider(
    value: Float,
    title: String,
    valueRange: ClosedFloatingPointRange<Float>,
    valueDisplay: @Composable (Float) -> String,
    onValueChange: (Float) -> Unit,
) {
    LunoTextSlider(
        value = value,
        onValueChange = onValueChange,
        text = title,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = PageGutter),
        valueDisplay = valueDisplay,
        valueRange = valueRange,
    )
}
