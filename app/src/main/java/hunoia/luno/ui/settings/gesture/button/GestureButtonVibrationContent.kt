package hunoia.luno.ui.settings.gesture.button

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import hunoia.luno.R
import hunoia.luno.config.model.GestureButton
import hunoia.luno.ui.component.MyColumn
import hunoia.luno.ui.component.SegmentedSwitchRow
import hunoia.luno.ui.theme.CardInnerSpacing

@Composable
fun GestureButtonVibrationContent(
    button: GestureButton,
    vm: GestureButtonSettingsVM
) {
    MyColumn(verticalArrangement = Arrangement.spacedBy(CardInnerSpacing)) {
        SegmentedSwitchRow(
            onCheckedChange = { vm.onSlideVibrateChange(it) },
            checked = button.slideVibrate,
            title = stringResource(R.string.vibration_slide)
        )
        SegmentedSwitchRow(
            onCheckedChange = { vm.onLongSlideVibrateChange(it) },
            checked = button.longSlideVibrate,
            title = stringResource(R.string.vibration_long_slide)
        )
        SegmentedSwitchRow(
            onCheckedChange = { vm.onTapVibrateChange(it) },
            checked = button.tapVibrate,
            title = stringResource(R.string.vibration_tap)
        )
        SegmentedSwitchRow(
            onCheckedChange = { vm.onLongPressVibrateChange(it) },
            checked = button.longPressVibrate,
            title = stringResource(R.string.vibration_long_press)
        )
        SegmentedSwitchRow(
            onCheckedChange = { vm.onSlideHoldVibrateChange(it) },
            checked = button.slideHoldVibrate,
            title = stringResource(R.string.vibration_slide_hold)
        )
        SegmentedSwitchRow(
            onCheckedChange = { vm.onLongSlideHoldVibrateChange(it) },
            checked = button.longSlideHoldVibrate,
            title = stringResource(R.string.vibration_long_slide_hold)
        )
    }
}
