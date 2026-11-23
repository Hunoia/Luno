package hunoia.luno.ui.component.input

import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerColors
import androidx.compose.material3.TimePickerDefaults
import androidx.compose.material3.TimePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

/**
 * 时间选择器（表盘）：M3 1.5 `TimePicker` 的 Luno 封装。
 *
 * 架构约束：页面 / feature 不直接引 M3 组件（`UiBoundaryTest` 对 M3 `Slider`、指示器、菜单
 * 同类门禁），表盘在 `:ui:design` 收敛，对外只暴露 [TimePickerState]。
 *
 * 容器色置透明：M3 默认会画一层浅色容器底，表盘嵌在底部锚定卡片里时只留表盘本体，
 * 否则卡片内多出一块白底。
 *
 * 铺满宽度并居中：`AnchoredDialog` 的 [androidx.compose.foundation.layout.Column] 默认
 * `horizontalAlignment = Start`，表盘只报自身尺寸（wrap-content）会被顶到卡片左边；
 * 用 `Box(contentAlignment = Center)` 包一层，不依赖 M3 内部是否自己居中。
 */
@Composable
fun LunoTimePicker(
    state: TimePickerState,
    modifier: Modifier = Modifier,
    colors: TimePickerColors = TimePickerDefaults.colors(containerColor = Color.Transparent),
) {
    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center,
    ) {
        TimePicker(
            state = state,
            colors = colors,
        )
    }
}

/**
 * 表盘状态：固定 24 小时制（0–23），[TimePickerState.hour] 直接就是「一天中的小时」，
 * 不用按 AM/PM 归一。原来滚轮的两位数字显示同样是 00–23，制式观感不变。
 */
@Composable
fun rememberLunoTimePickerState(hour: Int, minute: Int): TimePickerState =
    rememberTimePickerState(hour, minute, true)
