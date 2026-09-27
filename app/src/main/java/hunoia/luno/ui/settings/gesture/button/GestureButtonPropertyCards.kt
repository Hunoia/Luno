package hunoia.luno.ui.settings.gesture.button
import hunoia.luno.ui.theme.*

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import hunoia.luno.R
import hunoia.luno.config.defaults.SettingsUiDefaults.MaxGestureButtonArea
import hunoia.luno.config.defaults.SettingsUiDefaults.MinGestureButtonLength
import hunoia.luno.config.defaults.SettingsUiDefaults.GestureButtonColorAlpha
import hunoia.luno.config.model.GestureButton
import hunoia.luno.ui.component.input.MyTextSlider
import hunoia.luno.ui.component.settings.CompactSettingsGroup
import hunoia.luno.ui.component.settings.CompactSettingsRow
import hunoia.luno.ui.component.settings.CompactSettingsSwitchRow

@Composable
fun GestureButtonPhysicalParamsCard(
    gestureButton: GestureButton,
    mirrorHorizontal: Boolean,
    vm: GestureButtonSettingsVM,
    onAngleClick: () -> Unit,
    onVibrationClick: () -> Unit,
    onTriggerDistanceClick: () -> Unit,
) {
    CompactSettingsGroup(
        title = stringResource(id = R.string.physical_params),
        subtitle = stringResource(id = R.string.physical_params_subtitle),
    ) {
        CompactSettingsRow(
            onClick = onAngleClick,
            title = stringResource(id = R.string.gesture_angles),
            subtitle = stringResource(id = R.string.gesture_button_angles_hint),
            icon = Icons.Default.Straighten,
        )
        CompactSettingsRow(
            onClick = onVibrationClick,
            title = stringResource(id = R.string.gesture_button_vibration),
            subtitle = stringResource(id = R.string.vibration_hint),
            icon = Icons.Default.Vibration,
        )
        CompactSettingsRow(
            onClick = onTriggerDistanceClick,
            title = stringResource(id = R.string.gesture_button_trigger_distance),
            icon = Icons.Default.Settings,
        )
        val maxWidth = minOf(
            (1f - gestureButton.bounds.x).coerceAtLeast(MinGestureButtonLength),
            MaxGestureButtonArea / gestureButton.bounds.height.coerceAtLeast(MinGestureButtonLength),
        ).coerceAtLeast(MinGestureButtonLength)
        val maxHeight = minOf(
            (1f - gestureButton.bounds.y).coerceAtLeast(MinGestureButtonLength),
            MaxGestureButtonArea / gestureButton.bounds.width.coerceAtLeast(MinGestureButtonLength),
        ).coerceAtLeast(MinGestureButtonLength)
        var localBtnWidth by remember(gestureButton.bounds.width) { mutableStateOf(gestureButton.bounds.width.coerceIn(MinGestureButtonLength, maxWidth)) }
        var localBtnHeight by remember(gestureButton.bounds.height) { mutableStateOf(gestureButton.bounds.height.coerceIn(MinGestureButtonLength, maxHeight)) }
        var localBtnX by remember(gestureButton.bounds.x) { mutableStateOf(gestureButton.bounds.x) }
        var localBtnY by remember(gestureButton.bounds.y) { mutableStateOf(gestureButton.bounds.y) }
        val maxX = (1f - gestureButton.bounds.width).coerceAtLeast(0f)
        val maxY = (1f - gestureButton.bounds.height).coerceAtLeast(0f)
        val displayedX = localBtnX.coerceIn(0f, maxX)
        val displayedY = localBtnY.coerceIn(0f, maxY)
        val displayedWidth = localBtnWidth.coerceIn(MinGestureButtonLength, maxWidth)
        val displayedHeight = localBtnHeight.coerceIn(MinGestureButtonLength, maxHeight)
        MyTextSlider(
            value = displayedX,
            onValueChange = {
                localBtnX = it
                vm.onGestureButtonXChange(it)
            },
            onValueChangeFinished = {
                vm.onGestureButtonAdjustFinish()
            },
            text = stringResource(id = R.string.gesture_button_x),
            valueDisplay = "${(displayedX * 100).toInt()}%",
            valueRange = 0f..maxX
        )
        MyTextSlider(
            value = displayedY,
            onValueChange = {
                localBtnY = it
                vm.onGestureButtonYChange(it)
            },
            onValueChangeFinished = {
                vm.onGestureButtonAdjustFinish()
            },
            text = stringResource(id = R.string.gesture_button_y),
            valueDisplay = "${(displayedY * 100).toInt()}%",
            valueRange = 0f..maxY
        )
        MyTextSlider(
            value = displayedWidth,
            onValueChange = {
                localBtnWidth = it
                vm.onGestureButtonWidthChange(it)
            },
            onValueChangeFinished = {
                vm.onGestureButtonAdjustFinish()
            },
            text = stringResource(id = R.string.gesture_button_width),
            valueDisplay = "${(displayedWidth * 100).toInt()}%",
            valueRange = MinGestureButtonLength..maxWidth
        )
        MyTextSlider(
            value = displayedHeight,
            onValueChange = {
                localBtnHeight = it
                vm.onGestureButtonHeightChange(it)
            },
            onValueChangeFinished = {
                vm.onGestureButtonAdjustFinish()
            },
            text = stringResource(id = R.string.gesture_button_height),
            valueDisplay = "${(displayedHeight * 100).toInt()}%",
            valueRange = MinGestureButtonLength..maxHeight
        )
        CompactSettingsSwitchRow(
            onCheckedChange = { vm.onGestureButtonMirrorHorizontalChange(it) },
            checked = mirrorHorizontal,
            title = stringResource(id = R.string.gesture_button_mirror),
            subtitle = stringResource(id = R.string.gesture_button_mirror_hint),
        )
    }
}
