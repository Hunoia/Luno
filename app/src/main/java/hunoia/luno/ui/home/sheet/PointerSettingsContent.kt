package hunoia.luno.ui.home

import hunoia.luno.R
import hunoia.luno.ui.component.MyColumn
import hunoia.luno.ui.component.input.MyTextSlider
import hunoia.luno.config.model.GestureSettings

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource

@Composable
internal fun PointerSettingsContent(
    pointer: GestureSettings.Pointer,
    vm: HomeVM,
    scrollState: ScrollState? = null,
) {
    MyColumn(scrollState = scrollState ?: rememberScrollState()) {
        val currentPointer by rememberUpdatedState(pointer)
        MyTextSlider(
            value = pointer.continuousModeTimeoutMs / 1000f,
            onValueChange = { vm.onPointerContinuousModeTimeoutChange((it * 1000).toLong()) },
            onValueChangeFinished = { vm.savePointerSettings() },
            text = stringResource(id = R.string.pointer_continuous_timeout_plain),
            valueDisplay = stringResource(id = R.string.pointer_continuous_timeout, pointer.continuousModeTimeoutMs / 1000),
            valueRange = 1f..10f,
        )
        MyTextSlider(
            value = pointer.sensitivityX,
            onValueChange = { vm.onPointerChange(pointer.copy(sensitivityX = it)) },
            onValueChangeFinished = { vm.savePointerSettings() },
            text = stringResource(id = R.string.pointer_sensitivity_x_plain),
            valueDisplay = stringResource(id = R.string.pointer_sensitivity_x, pointer.sensitivityX),
            valueRange = 0.5f..4f,
        )
        MyTextSlider(
            value = pointer.sensitivityY,
            onValueChange = { vm.onPointerChange(pointer.copy(sensitivityY = it)) },
            onValueChangeFinished = { vm.savePointerSettings() },
            text = stringResource(id = R.string.pointer_sensitivity_y_plain),
            valueDisplay = stringResource(id = R.string.pointer_sensitivity_y, pointer.sensitivityY),
            valueRange = 0.5f..4f,
        )
        MyTextSlider(
            value = pointer.acceleration,
            onValueChange = { vm.onPointerChange(pointer.copy(acceleration = it)) },
            onValueChangeFinished = { vm.savePointerSettings() },
            text = stringResource(id = R.string.pointer_acceleration_plain),
            valueDisplay = stringResource(id = R.string.pointer_acceleration, pointer.acceleration),
            valueRange = 0f..2f,
        )
        var localCursorSize by remember(pointer.cursorSizeDp) { mutableStateOf(pointer.cursorSizeDp.toFloat()) }
        MyTextSlider(
            value = localCursorSize,
            onValueChange = { localCursorSize = it },
            onValueChangeFinished = {
                vm.onPointerChange(currentPointer.copy(cursorSizeDp = localCursorSize.toInt()))
                vm.savePointerSettings()
            },
            text = stringResource(id = R.string.pointer_cursor_size_plain),
            valueDisplay = stringResource(id = R.string.pointer_cursor_size, localCursorSize.toInt()),
            valueRange = 12f..64f,
        )
        MyTextSlider(
            value = pointer.cursorAlpha,
            onValueChange = { vm.onPointerChange(pointer.copy(cursorAlpha = it)) },
            onValueChangeFinished = { vm.savePointerSettings() },
            text = stringResource(id = R.string.pointer_cursor_alpha_plain),
            valueDisplay = stringResource(id = R.string.pointer_cursor_alpha, (pointer.cursorAlpha * 100).toInt()),
            valueRange = 0.2f..1f,
        )
    }
}
