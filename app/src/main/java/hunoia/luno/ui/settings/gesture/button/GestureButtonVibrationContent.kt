package hunoia.luno.ui.settings.gesture.button

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import hunoia.luno.R
import hunoia.luno.config.model.GestureButton
import hunoia.luno.ui.component.LunoColumn
import hunoia.luno.ui.component.row.LunoGroup
import hunoia.luno.ui.component.row.LunoSwitchRow
import hunoia.luno.ui.component.row.segmentedShape
import hunoia.luno.ui.theme.ListSpacing

@Composable
fun GestureButtonVibrationContent(
    button: GestureButton,
    vm: GestureButtonSettingsVM
) {
    LunoColumn(verticalArrangement = Arrangement.spacedBy(ListSpacing)) {
        LunoGroup(title = stringResource(R.string.vibration_group_slide)) {
            LunoSwitchRow(
                onCheckedChange = { vm.onSlideVibrateChange(it) },
                checked = button.slideVibrate,
                title = stringResource(R.string.vibration_slide),
                shape = segmentedShape(0, 2),
            )
            LunoSwitchRow(
                onCheckedChange = { vm.onLongSlideVibrateChange(it) },
                checked = button.longSlideVibrate,
                title = stringResource(R.string.vibration_long_slide),
                shape = segmentedShape(1, 2),
            )
        }
        LunoGroup(title = stringResource(R.string.vibration_group_hold)) {
            LunoSwitchRow(
                onCheckedChange = { vm.onSlideHoldVibrateChange(it) },
                checked = button.slideHoldVibrate,
                title = stringResource(R.string.vibration_slide_hold),
                shape = segmentedShape(0, 2),
            )
            LunoSwitchRow(
                onCheckedChange = { vm.onLongSlideHoldVibrateChange(it) },
                checked = button.longSlideHoldVibrate,
                title = stringResource(R.string.vibration_long_slide_hold),
                shape = segmentedShape(1, 2),
            )
        }
        LunoGroup(title = stringResource(R.string.vibration_group_tap)) {
            LunoSwitchRow(
                onCheckedChange = { vm.onTapVibrateChange(it) },
                checked = button.tapVibrate,
                title = stringResource(R.string.vibration_tap),
                shape = segmentedShape(0, 2),
            )
            LunoSwitchRow(
                onCheckedChange = { vm.onLongPressVibrateChange(it) },
                checked = button.longPressVibrate,
                title = stringResource(R.string.vibration_long_press),
                shape = segmentedShape(1, 2),
            )
        }
    }
}
