package hunoia.luno.ui.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import hunoia.luno.R
import hunoia.luno.config.model.MiniWindowSettings
import hunoia.luno.ui.component.input.MyTextSlider
import kotlin.math.roundToInt

@Composable
fun MiniWindowControls(
    settings: MiniWindowSettings,
    onChange: (MiniWindowSettings) -> Unit,
) {
    var horizontalBias by remember(settings.horizontalBias) { mutableStateOf(settings.horizontalBias) }
    var verticalBias by remember(settings.verticalBias) { mutableStateOf(settings.verticalBias) }
    var widthFraction by remember(settings.widthFraction) { mutableStateOf(settings.widthFraction) }
    var heightFraction by remember(settings.heightFraction) { mutableStateOf(settings.heightFraction) }
    SegmentedSwitchRow(
        title = stringResource(R.string.custom_position_size),
        subtitle = stringResource(R.string.mini_window_position_hint),
        checked = settings.overrideBounds,
        onCheckedChange = { onChange(settings.copy(overrideBounds = it)) },
    )
    MyTextSlider(
        value = horizontalBias,
        onValueChange = { horizontalBias = it.coerceIn(-1f, 1f) },
        onValueChangeFinished = { onChange(settings.copy(horizontalBias = horizontalBias)) },
        text = stringResource(R.string.horizontal_offset),
        valueDisplay = "${(horizontalBias * 100).roundToInt()}%",
        valueRange = -1f..1f,
    )
    MyTextSlider(
        value = verticalBias,
        onValueChange = { verticalBias = it.coerceIn(-1f, 1f) },
        onValueChangeFinished = { onChange(settings.copy(verticalBias = verticalBias)) },
        text = stringResource(R.string.vertical_offset),
        valueDisplay = "${(verticalBias * 100).roundToInt()}%",
        valueRange = -1f..1f,
    )
    MyTextSlider(
        value = widthFraction,
        onValueChange = { widthFraction = it.coerceIn(0.2f, 1.5f) },
        onValueChangeFinished = { onChange(settings.copy(widthFraction = widthFraction)) },
        text = stringResource(R.string.width),
        valueDisplay = "${(widthFraction * 100).roundToInt()}%",
        valueRange = 0.2f..1.5f,
    )
    MyTextSlider(
        value = heightFraction,
        onValueChange = { heightFraction = it.coerceIn(0.2f, 1.5f) },
        onValueChangeFinished = { onChange(settings.copy(heightFraction = heightFraction)) },
        text = stringResource(R.string.height),
        valueDisplay = "${(heightFraction * 100).roundToInt()}%",
        valueRange = 0.2f..1.5f,
    )
}
