package hunoia.luno.ui.component
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import hunoia.luno.R
import hunoia.luno.config.model.MiniWindowSettings
import hunoia.luno.ui.component.input.LunoTextSlider
import kotlin.math.roundToInt
import hunoia.luno.ui.component.row.LunoSwitchRow
@Composable
fun MiniWindowControls(
    settings: MiniWindowSettings,
    onSettingsChange: (MiniWindowSettings) -> Unit,
) {
    var horizontalBias by remember(settings.horizontalBias) { mutableStateOf(settings.horizontalBias) }
    var verticalBias by remember(settings.verticalBias) { mutableStateOf(settings.verticalBias) }
    var widthFraction by remember(settings.widthFraction) { mutableStateOf(settings.widthFraction) }
    var heightFraction by remember(settings.heightFraction) { mutableStateOf(settings.heightFraction) }
    LunoSwitchRow(
        title = stringResource(R.string.custom_position_size),
        subtitle = stringResource(R.string.mini_window_position_hint),
        checked = settings.overrideBounds,
        onCheckedChange = { onSettingsChange(settings.copy(overrideBounds = it)) },
    )
    LunoTextSlider(
        value = horizontalBias,
        onValueChange = { horizontalBias = it.coerceIn(-1f, 1f) },
        onValueChangeFinished = { onSettingsChange(settings.copy(horizontalBias = horizontalBias)) },
        text = stringResource(R.string.horizontal_offset),
        valueDisplay = { "${(it * 100).roundToInt()}%" },
        valueRange = -1f..1f,
    )
    LunoTextSlider(
        value = verticalBias,
        onValueChange = { verticalBias = it.coerceIn(-1f, 1f) },
        onValueChangeFinished = { onSettingsChange(settings.copy(verticalBias = verticalBias)) },
        text = stringResource(R.string.vertical_offset),
        valueDisplay = { "${(it * 100).roundToInt()}%" },
        valueRange = -1f..1f,
    )
    LunoTextSlider(
        value = widthFraction,
        onValueChange = { widthFraction = it.coerceIn(0.2f, 1.5f) },
        onValueChangeFinished = { onSettingsChange(settings.copy(widthFraction = widthFraction)) },
        text = stringResource(R.string.width),
        valueDisplay = { "${(it * 100).roundToInt()}%" },
        valueRange = 0.2f..1.5f,
    )
    LunoTextSlider(
        value = heightFraction,
        onValueChange = { heightFraction = it.coerceIn(0.2f, 1.5f) },
        onValueChangeFinished = { onSettingsChange(settings.copy(heightFraction = heightFraction)) },
        text = stringResource(R.string.height),
        valueDisplay = { "${(it * 100).roundToInt()}%" },
        valueRange = 0.2f..1.5f,
    )
}