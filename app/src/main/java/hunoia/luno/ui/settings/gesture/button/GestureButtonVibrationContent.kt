package hunoia.luno.ui.settings.gesture.button

import hunoia.luno.ui.theme.*
import androidx.compose.ui.unit.dp

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import hunoia.luno.R
import hunoia.luno.config.model.GestureButton
import hunoia.luno.ui.component.ExpressiveSwitchItem
import hunoia.luno.ui.component.MyColumn

@Composable
fun GestureButtonVibrationContent(
    button: GestureButton,
    vm: GestureButtonSettingsVM
) {
    MyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ExpressiveSwitchItem(
            onCheckedChange = { vm.onSlideVibrateChange(it) },
            checked = button.slideVibrate,
            title = stringResource(R.string.vibration_slide)
        )
        ExpressiveSwitchItem(
            onCheckedChange = { vm.onLongSlideVibrateChange(it) },
            checked = button.longSlideVibrate,
            title = stringResource(R.string.vibration_long_slide)
        )
        ExpressiveSwitchItem(
            onCheckedChange = { vm.onTapVibrateChange(it) },
            checked = button.tapVibrate,
            title = stringResource(R.string.vibration_tap)
        )
        ExpressiveSwitchItem(
            onCheckedChange = { vm.onLongPressVibrateChange(it) },
            checked = button.longPressVibrate,
            title = stringResource(R.string.vibration_long_press)
        )
        ExpressiveSwitchItem(
            onCheckedChange = { vm.onSlideHoldVibrateChange(it) },
            checked = button.slideHoldVibrate,
            title = stringResource(R.string.vibration_slide_hold)
        )
        ExpressiveSwitchItem(
            onCheckedChange = { vm.onLongSlideHoldVibrateChange(it) },
            checked = button.longSlideHoldVibrate,
            title = stringResource(R.string.vibration_long_slide_hold)
        )
    }
}
