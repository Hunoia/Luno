package hunoia.luno.gesture.settings.button

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import hunoia.luno.gesture.R
import hunoia.luno.config.model.GestureButton
import hunoia.luno.ui.component.LunoColumn
import hunoia.luno.ui.component.row.LunoGroup
import hunoia.luno.ui.component.row.LunoSegmentedGroup
import hunoia.luno.ui.component.row.LunoSwitchRow
import hunoia.luno.ui.theme.ListSpacing

@Composable
fun GestureButtonVibrationContent(
    button: GestureButton,
    vm: GestureButtonSettingsVM
) {
    // 横向边距由 LunoSheet 统一提供，这里只排纵向间距。
    LunoColumn(contentPadding = 0.dp, verticalArrangement = Arrangement.spacedBy(ListSpacing)) {
        LunoGroup(title = stringResource(R.string.vibration_group_slide)) {
            LunoSegmentedGroup {
                item { shape -> LunoSwitchRow(
                onCheckedChange = { vm.onSlideVibrateChange(it) },
                checked = button.slideVibrate,
                title = stringResource(R.string.vibration_slide),
                shape = shape,
                ) }
                item { shape -> LunoSwitchRow(
                onCheckedChange = { vm.onLongSlideVibrateChange(it) },
                checked = button.longSlideVibrate,
                title = stringResource(R.string.vibration_long_slide),
                shape = shape,
                ) }
            }
        }
        LunoGroup(title = stringResource(R.string.vibration_group_hold)) {
            LunoSegmentedGroup {
                item { shape -> LunoSwitchRow(
                onCheckedChange = { vm.onSlideHoldVibrateChange(it) },
                checked = button.slideHoldVibrate,
                title = stringResource(R.string.vibration_slide_hold),
                shape = shape,
                ) }
                item { shape -> LunoSwitchRow(
                onCheckedChange = { vm.onLongSlideHoldVibrateChange(it) },
                checked = button.longSlideHoldVibrate,
                title = stringResource(R.string.vibration_long_slide_hold),
                shape = shape,
                ) }
            }
        }
        LunoGroup(title = stringResource(R.string.vibration_group_tap)) {
            LunoSegmentedGroup {
                item { shape -> LunoSwitchRow(
                onCheckedChange = { vm.onTapVibrateChange(it) },
                checked = button.tapVibrate,
                title = stringResource(R.string.vibration_tap),
                shape = shape,
                ) }
                item { shape -> LunoSwitchRow(
                onCheckedChange = { vm.onLongPressVibrateChange(it) },
                checked = button.longPressVibrate,
                title = stringResource(R.string.vibration_long_press),
                shape = shape,
                ) }
            }
        }
    }
}
