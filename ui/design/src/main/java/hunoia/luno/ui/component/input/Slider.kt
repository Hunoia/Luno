package hunoia.luno.ui.component.input
import hunoia.luno.ui.theme.CardInnerSpacing
import hunoia.luno.ui.theme.LargeShape
import hunoia.luno.ui.theme.ListItemVerticalPadding
import hunoia.luno.ui.theme.MinItemHeightNoSecondary
import hunoia.luno.ui.theme.SliderInnerPadding
import hunoia.luno.ui.theme.SliderThumbWidth

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
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
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import kotlin.math.abs
import kotlin.math.roundToInt


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
fun LunoSlider(
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

    Box(modifier = modifier) {
        Slider(
            value = localValue,
            onValueChange = { value ->
                localValue = value
                safeOnLiveValueChange?.invoke(value)
                safeOnValueChange(value)
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = enabled,
            onValueChangeFinished = safeOnValueChangeFinished,
            interactionSource = interactionSource,
            valueRange = valueRange,
            steps = stepCount,
        )
        if (enabled) {
            // M3 Slider 自带「点轨道跳值」与按下即改值；这里用上层手势层接管：
            // 只有横向拖动才改值，点击不跳值，竖向 / 交叉轴滑动不消费、交给父级滚动。
            // 无障碍调整仍走底层 Slider 的 semantics，不受这层影响。
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .horizontalSliderDrag(
                        valueRange = valueRange,
                        steps = stepCount,
                        currentValue = { localValue },
                        interactionSource = interactionSource,
                        onValueChange = { value ->
                            localValue = value
                            safeOnLiveValueChange?.invoke(value)
                            safeOnValueChange(value)
                        },
                        onValueChangeFinished = { safeOnValueChangeFinished?.invoke() },
                    ),
            )
        }
    }
}

/** 滑杆只接受横向拖动：点按不跳值，竖向 / 交叉轴先过阈值时不消费、交给父级滚动。 */
internal fun Modifier.horizontalSliderDrag(
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
    currentValue: () -> Float,
    interactionSource: MutableInteractionSource,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit,
): Modifier = pointerInput(valueRange, steps, interactionSource) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        val press = PressInteraction.Press(down.position)
        interactionSource.tryEmit(press)
        val baseValue = currentValue()
        val start = down.position
        // M3 轨道两端各留半个 thumb，拖动映射要扣掉，否则指尖没到边值就到底。
        val trackPx = (size.width - SliderThumbWidth.toPx()).coerceAtLeast(1f)
        var dragging = false
        var dragStart: DragInteraction.Start? = null
        while (true) {
            val event = awaitPointerEvent()
            val change = event.changes.firstOrNull { it.id == down.id } ?: break
            if (change.changedToUp()) {
                if (dragging) change.consume()
                break
            }
            val dx = change.position.x - start.x
            val dy = change.position.y - start.y
            if (!dragging) {
                val slop = viewConfiguration.touchSlop
                if (abs(dx) < slop && abs(dy) < slop) continue
                // 交叉轴先过阈值：不消费，父级 verticalScroll 继续接管。
                if (abs(dy) > abs(dx)) {
                    interactionSource.tryEmit(PressInteraction.Cancel(press))
                    return@awaitEachGesture
                }
                dragging = true
                dragStart = DragInteraction.Start()
                interactionSource.tryEmit(dragStart)
            }
            change.consume()
            val raw = baseValue + dx / trackPx * (valueRange.endInclusive - valueRange.start)
            onValueChange(snapToRange(raw, valueRange, steps))
        }
        if (dragging) {
            dragStart?.let { interactionSource.tryEmit(DragInteraction.Stop(it)) }
            onValueChangeFinished()
        }
        interactionSource.tryEmit(PressInteraction.Release(press))
    }
}

private fun snapToRange(
    raw: Float,
    range: ClosedFloatingPointRange<Float>,
    steps: Int,
): Float {
    val clamped = raw.coerceIn(range.start, range.endInclusive)
    if (steps <= 0) return clamped
    val span = range.endInclusive - range.start
    if (span <= 0f) return range.start
    val interval = span / (steps + 1)
    return (range.start + ((clamped - range.start) / interval).roundToInt() * interval)
        .coerceIn(range.start, range.endInclusive)
}
