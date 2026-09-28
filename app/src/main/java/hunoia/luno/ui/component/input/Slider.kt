package hunoia.luno.ui.component.input
import hunoia.luno.ui.theme.*

import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.unit.dp
import hunoia.luno.ui.theme.MinItemHeightNoSecondary


@Composable
private fun SliderSurface(
    text: String,
    modifier: Modifier = Modifier,
    valueDisplay: String? = null,
    sliderValueHint: Pair<String, String>? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = MinItemHeightNoSecondary)
                .padding(vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier
                    .padding(horizontal = 12.dp)
                    .fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    modifier = Modifier.widthIn(max = SliderTextMaxWidth),
                    text = text,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1
                )
                if (valueDisplay != null) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = valueDisplay,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1
                    )
                }
            }
            if (sliderValueHint != null) {
                Box(
                    modifier = Modifier
                        .padding(horizontal = 12.dp)
                        .fillMaxWidth()
                ) {
                    Text(
                        modifier = Modifier.align(Alignment.CenterStart),
                        text = sliderValueHint.first,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 1
                    )
                    Text(
                        modifier = Modifier.align(Alignment.CenterEnd),
                        text = sliderValueHint.second,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 1
                    )
                }
            }
            content()
        }
    }
}

@Composable
fun MyTextSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    text: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    sliderValueHint: Pair<String, String>? = null,
    valueDisplay: String? = null,
    onValueChangeFinished: (() -> Unit)? = null,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    steps: Int? = null,
) {
    SliderSurface(
        modifier = modifier,
        text = text,
        valueDisplay = valueDisplay,
        sliderValueHint = sliderValueHint,
    ) {
        MySlider(
            modifier = Modifier.padding(horizontal = 12.dp),
            enabled = enabled,
            value = value,
            onValueChange = onValueChange,
            onValueChangeFinished = onValueChangeFinished,
            valueRange = valueRange,
            steps = steps,
        )
    }
}

@Composable
fun MyTextRangeSlider(
    value: ClosedFloatingPointRange<Float>,
    onValueChange: (ClosedFloatingPointRange<Float>) -> Unit,
    text: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    sliderValueHint: Pair<String, String>? = null,
    onValueChangeFinished: (() -> Unit)? = null,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f
) {
    SliderSurface(
        modifier = modifier,
        text = text,
        sliderValueHint = sliderValueHint,
    ) {
        MyRangeSlider(
            modifier = Modifier.padding(horizontal = 12.dp - 6.dp),
            enabled = enabled,
            value = value,
            onValueChange = onValueChange,
            onValueChangeFinished = onValueChangeFinished,
            valueRange = valueRange
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MySlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onValueChangeFinished: (() -> Unit)? = null,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    steps: Int? = null,
) {
    val coercedValue = value.coerceIn(valueRange.start, valueRange.endInclusive)
    val interactionSource = remember { MutableInteractionSource() }
    val stepCount = steps ?: 0
    var localValue by remember { mutableStateOf(coercedValue) }
    var isDragging by remember { mutableStateOf(false) }
    val safeOnValueChange by rememberUpdatedState(onValueChange)
    val safeOnValueChangeFinished by rememberUpdatedState(onValueChangeFinished)

    LaunchedEffect(coercedValue) {
        if (!isDragging) localValue = coercedValue
    }

    LaunchedEffect(interactionSource) {
        interactionSource.interactions.collect { interaction ->
            when (interaction) {
                is DragInteraction.Start -> isDragging = true
                is DragInteraction.Stop -> isDragging = false
                is PressInteraction.Release, is PressInteraction.Cancel -> isDragging = false
            }
        }
    }

    Slider(
        value = localValue,
        onValueChange = { value ->
            localValue = value
            if (isDragging) safeOnValueChange(value)
        },
        modifier = modifier,
        enabled = enabled,
        onValueChangeFinished = safeOnValueChangeFinished,
        interactionSource = interactionSource,
        valueRange = valueRange,
        steps = stepCount,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyRangeSlider(
    value: ClosedFloatingPointRange<Float>,
    onValueChange: (ClosedFloatingPointRange<Float>) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onValueChangeFinished: (() -> Unit)? = null,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f
) {
    val coercedValue = value.start.coerceIn(valueRange.start, valueRange.endInclusive)..
        value.endInclusive.coerceIn(valueRange.start, valueRange.endInclusive)
    var localValue by remember { mutableStateOf(coercedValue) }

    LaunchedEffect(coercedValue) {
        localValue = coercedValue
    }

    RangeSlider(
        value = localValue,
        onValueChange = { value ->
            localValue = value
            onValueChange(value)
        },
        modifier = modifier,
        enabled = enabled,
        onValueChangeFinished = onValueChangeFinished,
        valueRange = valueRange,
    )
}
