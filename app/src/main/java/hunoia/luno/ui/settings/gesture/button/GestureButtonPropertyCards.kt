package hunoia.luno.ui.settings.gesture.button
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import hunoia.luno.R
import hunoia.luno.config.defaults.SettingsUiDefaults.MaxGestureButtonArea
import hunoia.luno.config.defaults.SettingsUiDefaults.MinGestureButtonLength
import hunoia.luno.config.model.GestureButton
import hunoia.luno.ui.component.input.LunoTextSlider
import hunoia.luno.ui.component.row.LunoGroup
import hunoia.luno.ui.component.row.LunoSettingsRow
import hunoia.luno.ui.component.row.LunoSwitchRow
import hunoia.luno.ui.component.row.segmentedShape
@Composable
fun GestureButtonPhysicalParamsCard(
    gestureButton: GestureButton,
    mirrorHorizontal: Boolean,
    vm: GestureButtonSettingsVM,
    onAngleClick: () -> Unit,
    onVibrationClick: () -> Unit,
    onTriggerDistanceClick: () -> Unit,
) {
    LunoGroup(
        title = stringResource(id = R.string.physical_params),
    ) {
        LunoSettingsRow(
            onClick = onAngleClick,
            title = stringResource(id = R.string.gesture_angles),
            icon = Icons.Default.Straighten,
            shape = segmentedShape(0, 8),
        )
        LunoSettingsRow(
            onClick = onVibrationClick,
            title = stringResource(id = R.string.gesture_button_vibration),
            icon = Icons.Default.Vibration,
            shape = segmentedShape(1, 8),
        )
        LunoSettingsRow(
            onClick = onTriggerDistanceClick,
            title = stringResource(id = R.string.gesture_button_trigger_distance),
            icon = Icons.Default.Settings,
            shape = segmentedShape(2, 8),
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
        LunoTextSlider(
            value = displayedX,
            onValueChange = {
                localBtnX = it
                vm.onGestureButtonXChange(it)
            },
            onValueChangeFinished = {
                vm.onGestureButtonAdjustFinish()
            },
            text = stringResource(id = R.string.gesture_button_x),
            valueDisplay = { "${(it * 100).toInt()}%" },
            valueRange = 0f..maxX,
            shape = segmentedShape(3, 8),
        )
        LunoTextSlider(
            value = displayedY,
            onValueChange = {
                localBtnY = it
                vm.onGestureButtonYChange(it)
            },
            onValueChangeFinished = {
                vm.onGestureButtonAdjustFinish()
            },
            text = stringResource(id = R.string.gesture_button_y),
            valueDisplay = { "${(it * 100).toInt()}%" },
            valueRange = 0f..maxY,
            shape = segmentedShape(4, 8),
        )
        LunoTextSlider(
            value = displayedWidth,
            onValueChange = {
                localBtnWidth = it
                vm.onGestureButtonWidthChange(it)
            },
            onValueChangeFinished = {
                vm.onGestureButtonAdjustFinish()
            },
            text = stringResource(id = R.string.gesture_button_width),
            valueDisplay = { "${(it * 100).toInt()}%" },
            valueRange = MinGestureButtonLength..maxWidth,
            shape = segmentedShape(5, 8),
        )
        LunoTextSlider(
            value = displayedHeight,
            onValueChange = {
                localBtnHeight = it
                vm.onGestureButtonHeightChange(it)
            },
            onValueChangeFinished = {
                vm.onGestureButtonAdjustFinish()
            },
            text = stringResource(id = R.string.gesture_button_height),
            valueDisplay = { "${(it * 100).toInt()}%" },
            valueRange = MinGestureButtonLength..maxHeight,
            shape = segmentedShape(6, 8),
        )
        LunoSwitchRow(
            onCheckedChange = { vm.onGestureButtonMirrorHorizontalChange(it) },
            checked = mirrorHorizontal,
            title = stringResource(id = R.string.gesture_button_mirror),
            shape = segmentedShape(7, 8),
        )
    }
}