package hunoia.luno.ui.settings.gesture.button
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import hunoia.luno.R
import hunoia.luno.config.model.GestureButton
import hunoia.luno.ui.component.LunoColumn
import hunoia.luno.ui.theme.CardInnerSpacing
import hunoia.luno.ui.component.row.LunoSwitchRow
@Composable
fun GestureButtonVibrationContent(
    button: GestureButton,
    vm: GestureButtonSettingsVM
) {
    LunoColumn(verticalArrangement = Arrangement.spacedBy(CardInnerSpacing)) {
        LunoSwitchRow(
            onCheckedChange = { vm.onSlideVibrateChange(it) },
            checked = button.slideVibrate,
            title = stringResource(R.string.vibration_slide)
        )
        LunoSwitchRow(
            onCheckedChange = { vm.onLongSlideVibrateChange(it) },
            checked = button.longSlideVibrate,
            title = stringResource(R.string.vibration_long_slide)
        )
        LunoSwitchRow(
            onCheckedChange = { vm.onTapVibrateChange(it) },
            checked = button.tapVibrate,
            title = stringResource(R.string.vibration_tap)
        )
        LunoSwitchRow(
            onCheckedChange = { vm.onLongPressVibrateChange(it) },
            checked = button.longPressVibrate,
            title = stringResource(R.string.vibration_long_press)
        )
        LunoSwitchRow(
            onCheckedChange = { vm.onSlideHoldVibrateChange(it) },
            checked = button.slideHoldVibrate,
            title = stringResource(R.string.vibration_slide_hold)
        )
        LunoSwitchRow(
            onCheckedChange = { vm.onLongSlideHoldVibrateChange(it) },
            checked = button.longSlideHoldVibrate,
            title = stringResource(R.string.vibration_long_slide_hold)
        )
    }
}