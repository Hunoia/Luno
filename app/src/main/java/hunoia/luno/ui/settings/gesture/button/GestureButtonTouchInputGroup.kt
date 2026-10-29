package hunoia.luno.ui.settings.gesture.button

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import hunoia.luno.R
import hunoia.luno.config.model.TouchInputConfig
import hunoia.luno.config.model.TouchInputMode
import hunoia.luno.ui.component.input.LunoTextSlider
import hunoia.luno.ui.component.row.LunoGroup
import hunoia.luno.ui.component.row.LunoSelectableRow
import hunoia.luno.ui.component.row.LunoSwitchRow
import hunoia.luno.ui.component.row.segmentedShape

/** 默认窄条宽度（dp）：8dp 已验证能拦住系统边缘手势，又几乎不挡下层。 */
private const val DEFAULT_NARROW_WIDTH_DP = 8

/**
 * 触钮的「触摸来源」组（直接铺在触钮设置页里，与其它组同构）。
 *
 * 两种来源是**手势识别的实现方式**，不是两个独立功能：产出同一份触摸帧、共用同一份
 * 识别状态与动作分发，差别只在"这块像素的触摸从哪来"。
 */
@Composable
fun GestureButtonTouchInputGroup(
    touchInput: TouchInputConfig,
    shizukuReady: Boolean,
    onModeChange: (TouchInputMode) -> Unit,
    onInterceptWidthChange: (Int) -> Unit,
    onCommit: () -> Unit,
) {
    LunoGroup(
        title = stringResource(R.string.touch_input),
        subtitle = stringResource(R.string.touch_input_group_hint),
    ) {
        LunoSelectableRow(
            title = stringResource(R.string.touch_input_mode_accessibility),
            subtitle = stringResource(R.string.touch_input_mode_accessibility_hint),
            selected = touchInput.mode == TouchInputMode.ONLY_ACCESSIBILITY,
            singleSelect = true,
            onSelectedChange = { onModeChange(TouchInputMode.ONLY_ACCESSIBILITY) },
            shape = segmentedShape(0, 3),
        )
        LunoSelectableRow(
            title = stringResource(R.string.touch_input_mode_shizuku),
            subtitle = stringResource(R.string.touch_input_mode_shizuku_hint),
            selected = touchInput.mode == TouchInputMode.ONLY_SHIZUKU,
            singleSelect = true,
            onSelectedChange = { onModeChange(TouchInputMode.ONLY_SHIZUKU) },
            shape = segmentedShape(1, 3),
        )
        LunoSelectableRow(
            title = stringResource(R.string.touch_input_mode_auto),
            subtitle = stringResource(
                if (shizukuReady) {
                    R.string.touch_input_mode_auto_hint_ready
                } else {
                    R.string.touch_input_mode_auto_hint
                },
            ),
            selected = touchInput.mode == TouchInputMode.AUTO,
            singleSelect = true,
            onSelectedChange = { onModeChange(TouchInputMode.AUTO) },
            shape = segmentedShape(2, 3),
        )
    }

    if (touchInput.mode == TouchInputMode.ONLY_ACCESSIBILITY) return

    LunoGroup(title = stringResource(R.string.touch_input_intercept_group)) {
        LunoSwitchRow(
            title = stringResource(R.string.touch_input_intercept_full),
            subtitle = stringResource(R.string.touch_input_intercept_full_hint),
            checked = touchInput.interceptWidthDp <= 0,
            onCheckedChange = { full ->
                onInterceptWidthChange(if (full) 0 else DEFAULT_NARROW_WIDTH_DP)
                onCommit()
            },
            shape = segmentedShape(0, if (touchInput.interceptWidthDp > 0) 2 else 1),
        )
        if (touchInput.interceptWidthDp > 0) {
            LunoTextSlider(
                value = touchInput.interceptWidthDp.toFloat(),
                onValueChange = { onInterceptWidthChange(it.toInt().coerceIn(MIN_WIDTH_DP, MAX_WIDTH_DP)) },
                onValueChangeFinished = onCommit,
                text = stringResource(R.string.touch_input_intercept_width),
                valueRange = MIN_WIDTH_DP.toFloat()..MAX_WIDTH_DP.toFloat(),
                steps = MAX_WIDTH_DP - MIN_WIDTH_DP - 1,
                valueDisplay = { "${it.toInt()} dp" },
                shape = segmentedShape(1, 2),
            )
        }
    }
}

private const val MIN_WIDTH_DP = 4
private const val MAX_WIDTH_DP = 48
