package hunoia.luno.gesture.settings.button

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import hunoia.luno.config.model.GestureButton
import hunoia.luno.gesture.R
import hunoia.luno.ui.component.LunoColumn
import hunoia.luno.ui.component.row.group
import hunoia.luno.ui.theme.ListSpacing

@Composable
fun GestureButtonVibrationContent(
    button: GestureButton,
    vm: GestureButtonSettingsVM,
) {
    // 横向边距由 LunoSheet 统一提供，这里只排纵向间距。
    LunoColumn(contentPadding = 0.dp, verticalArrangement = Arrangement.spacedBy(ListSpacing)) {
        group(title = stringResource(R.string.vibration_group_slide)) {
            switchRow(
                title = stringResource(R.string.vibration_slide),
                checked = button.slideVibrate,
                onCheckedChange = { vm.onSlideVibrateChange(it) },
            )
            switchRow(
                title = stringResource(R.string.vibration_long_slide),
                checked = button.longSlideVibrate,
                onCheckedChange = { vm.onLongSlideVibrateChange(it) },
            )
        }
        group(title = stringResource(R.string.vibration_group_hold)) {
            switchRow(
                title = stringResource(R.string.vibration_slide_hold),
                checked = button.slideHoldVibrate,
                onCheckedChange = { vm.onSlideHoldVibrateChange(it) },
            )
            switchRow(
                title = stringResource(R.string.vibration_long_slide_hold),
                checked = button.longSlideHoldVibrate,
                onCheckedChange = { vm.onLongSlideHoldVibrateChange(it) },
            )
        }
        group(title = stringResource(R.string.vibration_group_tap)) {
            switchRow(
                title = stringResource(R.string.vibration_tap),
                checked = button.tapVibrate,
                onCheckedChange = { vm.onTapVibrateChange(it) },
            )
            switchRow(
                title = stringResource(R.string.vibration_long_press),
                checked = button.longPressVibrate,
                onCheckedChange = { vm.onLongPressVibrateChange(it) },
            )
        }
    }
}
