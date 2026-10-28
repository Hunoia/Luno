package hunoia.luno.ui.component.input
import hunoia.luno.ui.theme.CardInnerSpacing
import hunoia.luno.ui.theme.LargeShape
import hunoia.luno.ui.theme.ListItemVerticalPadding
import hunoia.luno.ui.theme.MinItemHeightNoSecondary
import hunoia.luno.ui.theme.SliderInnerPadding

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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape


@Composable
private fun SliderSurface(
    text: String,
    modifier: Modifier = Modifier,
    shape: Shape = LargeShape,
    valueDisplay: String? = null,
    sliderValueHint: Pair<String, String>? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceBright,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = MinItemHeightNoSecondary)
                .padding(vertical = ListItemVerticalPadding),
            verticalArrangement = Arrangement.spacedBy(CardInnerSpacing)
        ) {
            Row(
                modifier = Modifier
                    .padding(horizontal = SliderInnerPadding)
                    .fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = text,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1
                )
                if (valueDisplay != null) {
                    Spacer(modifier = Modifier.width(CardInnerSpacing))
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
                        .padding(horizontal = SliderInnerPadding)
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
fun LunoTextSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    text: String,
    modifier: Modifier = Modifier,
    shape: Shape = LargeShape,
    enabled: Boolean = true,
    sliderValueHint: Pair<String, String>? = null,
    valueDisplay: (@Composable (Float) -> String)? = null,
    onValueChangeFinished: (() -> Unit)? = null,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    steps: Int? = null,
) {
    // 拖动中的实时值。外部 value 在 onValueChangeFinished 之前不变（松手才落盘），
    // 标签读它会冻住。这里挂 LunoSlider 自己的 localValue，即 steps 量化 /
    // range 钳制后滑块实际显示的那一份。
    var liveValue by remember { mutableFloatStateOf(value) }

    SliderSurface(
        modifier = modifier,
        shape = shape,
        text = text,
        valueDisplay = valueDisplay?.invoke(liveValue),
        sliderValueHint = sliderValueHint,
    ) {
        LunoSlider(
            modifier = Modifier.padding(horizontal = SliderInnerPadding),
            enabled = enabled,
            value = value,
            onValueChange = onValueChange,
            onLiveValueChange = { liveValue = it },
            onValueChangeFinished = onValueChangeFinished,
            valueRange = valueRange,
            steps = steps,
        )
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LunoSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onValueChangeFinished: (() -> Unit)? = null,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    steps: Int? = null,
    onLiveValueChange: ((Float) -> Unit)? = null,
) {
    val coercedValue = value.coerceIn(valueRange.start, valueRange.endInclusive)
    val interactionSource = remember { MutableInteractionSource() }
    val stepCount = steps ?: 0
    var localValue by remember { mutableStateOf(coercedValue) }
    var isDragging by remember { mutableStateOf(false) }
    val safeOnValueChange by rememberUpdatedState(onValueChange)
    val safeOnValueChangeFinished by rememberUpdatedState(onValueChangeFinished)
    val safeOnLiveValueChange by rememberUpdatedState(onLiveValueChange)

    LaunchedEffect(coercedValue) {
        if (!isDragging) {
            localValue = coercedValue
            safeOnLiveValueChange?.invoke(coercedValue)
        }
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
            safeOnLiveValueChange?.invoke(value)
            safeOnValueChange(value)
        },
        modifier = modifier,
        enabled = enabled,
        onValueChangeFinished = safeOnValueChangeFinished,
        interactionSource = interactionSource,
        valueRange = valueRange,
        steps = stepCount,
    )
}
