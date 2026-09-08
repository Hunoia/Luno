package hunoia.luno.ui.component.input
import hunoia.luno.ui.theme.*

import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
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
import hunoia.luno.ui.theme.ContentPaddingHorizontal
import hunoia.luno.ui.theme.ContentPaddingVerticalWithSection
import hunoia.luno.ui.theme.IconTextPadding
import hunoia.luno.ui.theme.MinItemHeightNoSecondary
import kotlin.math.roundToInt

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
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = MinItemHeightNoSecondary)
                .padding(vertical = ContentPaddingVerticalWithSection),
            verticalArrangement = Arrangement.spacedBy(IconTextPadding)
        ) {
            Row(
                modifier = Modifier
                    .padding(horizontal = ContentPaddingHorizontal)
                    .fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    modifier = Modifier                .widthIn(max = SliderTextMaxWidth),
                    text = text,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1
                )
                if (valueDisplay != null) {
                    Spacer(modifier = Modifier.width(IconTextPadding))
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
                        .padding(horizontal = ContentPaddingHorizontal)
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
            MySlider(
                modifier = Modifier
                    .padding(horizontal = ContentPaddingHorizontal)
                    .height(SliderTrackHeight),
                enabled = enabled,
                value = value,
                onValueChange = onValueChange,
                onValueChangeFinished = onValueChangeFinished,
                valueRange = valueRange,
                steps = steps,
            )
        }
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
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = MinItemHeightNoSecondary)
                .padding(vertical = ContentPaddingVerticalWithSection),
            verticalArrangement = Arrangement.spacedBy(IconTextPadding)
        ) {
            Text(
                modifier = Modifier
                    .padding(horizontal = ContentPaddingHorizontal)
                    .width(IntrinsicSize.Max),
                text = text,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1
            )
            if (sliderValueHint != null) {
                Box(
                    modifier = Modifier
                        .padding(horizontal = ContentPaddingHorizontal)
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
            MyRangeSlider(
                modifier = Modifier
                    .padding(horizontal = ContentPaddingHorizontal - Spacing6)
                    .height(SliderTrackHeight),
                enabled = enabled,
                value = value,
                onValueChange = onValueChange,
                onValueChangeFinished = onValueChangeFinished,
                valueRange = valueRange
            )
        }
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
    val colorScheme = MaterialTheme.colorScheme
    val interactionSource = remember { MutableInteractionSource() }
    val colors = SliderDefaults.colors(thumbColor = colorScheme.tertiary)
    var isDragging by remember { mutableStateOf(false) }
    val safeOnValueChange by rememberUpdatedState(onValueChange)
    val safeOnValueChangeFinished by rememberUpdatedState(onValueChangeFinished)

    LaunchedEffect(interactionSource) {
        interactionSource.interactions.collect { interaction ->
            when (interaction) {
                is DragInteraction.Start -> isDragging = true
                is DragInteraction.Stop -> {
                    if (isDragging) safeOnValueChangeFinished?.invoke()
                    isDragging = false
                }
                is PressInteraction.Release, is PressInteraction.Cancel -> {
                    isDragging = false
                }
            }
        }
    }

    Slider(
        modifier = modifier,
        enabled = enabled,
        value = coercedValue,
        onValueChange = { if (isDragging) safeOnValueChange(it) },
        onValueChangeFinished = { },
        interactionSource = interactionSource,
        colors = colors,
        valueRange = valueRange,
        steps = steps ?: 0,
        thumb = {
            SliderDefaults.Thumb(
                modifier = Modifier
                    .requiredSize(Spacing20)
                    .drawWithContent {
                        drawContent()
                        if (enabled) {
                            drawCircle(
                                color = colorScheme.onPrimary,
                                radius = 7.dp.toPx()
                            )
                        }
                    },
                interactionSource = interactionSource,
                colors = colors,
                enabled = enabled
            )
        },
        track = { sliderState ->
            SliderDefaults.Track(
                modifier = Modifier.height(8.dp),
                colors = colors,
                enabled = enabled,
                sliderState = sliderState,
                thumbTrackGapSize = 0.dp
            )
        }
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
    val colorScheme = MaterialTheme.colorScheme
    val startInteractionSource = remember { MutableInteractionSource() }
    val endInteractionSource = remember { MutableInteractionSource() }
    val colors = SliderDefaults.colors(thumbColor = colorScheme.primary)
    val thumb: @Composable (MutableInteractionSource) -> Unit = { interactionSource ->
        SliderDefaults.Thumb(
            modifier = Modifier
                .requiredSize(Spacing20)
                .drawWithContent {
                    drawContent()
                    if (enabled) {
                        drawCircle(
                            color = colorScheme.onPrimary,
                            radius = 7.dp.toPx()
                        )
                    }
                },
            interactionSource = interactionSource,
            colors = colors,
            enabled = enabled
        )
    }
    RangeSlider(
        modifier = modifier,
        enabled = enabled,
        value = coercedValue,
        onValueChange = onValueChange,
        onValueChangeFinished = onValueChangeFinished,
        startInteractionSource = startInteractionSource,
        endInteractionSource = endInteractionSource,
        colors = colors,
        valueRange = valueRange,
        startThumb = {
            thumb(startInteractionSource)
        },
        endThumb = {
            thumb(endInteractionSource)
        },
        track = { sliderState ->
            SliderDefaults.Track(
                modifier = Modifier.height(8.dp),
                colors = colors,
                enabled = enabled,
                rangeSliderState = sliderState,
                thumbTrackGapSize = 0.dp
            )
        }
    )
}
