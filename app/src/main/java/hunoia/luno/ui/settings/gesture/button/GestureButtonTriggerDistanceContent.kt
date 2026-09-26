package hunoia.luno.ui.settings.gesture.button

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import hunoia.luno.R
import hunoia.luno.bridge.DensityProvider
import hunoia.luno.config.defaults.SettingsUiDefaults.MaxLongPressTriggerDelayMs
import hunoia.luno.config.defaults.SettingsUiDefaults.MaxDoubleTapTriggerDelayMs
import hunoia.luno.config.defaults.SettingsUiDefaults.MaxHoldTriggerDelayMs
import hunoia.luno.config.defaults.SettingsUiDefaults.MaxLongSlideTriggerDistance
import hunoia.luno.config.defaults.SettingsUiDefaults.MaxSlideTriggerDistance
import hunoia.luno.config.defaults.SettingsUiDefaults.MinGestureSlideDistanceGap
import hunoia.luno.config.defaults.SettingsUiDefaults.MinLongPressTriggerDelayMs
import hunoia.luno.config.defaults.SettingsUiDefaults.MinDoubleTapTriggerDelayMs
import hunoia.luno.config.defaults.SettingsUiDefaults.MinHoldTriggerDelayMs
import hunoia.luno.config.defaults.SettingsUiDefaults.MinLongSlideTriggerDistance
import hunoia.luno.config.defaults.SettingsUiDefaults.MinSlideTriggerDistance
import hunoia.luno.config.model.GestureButton
import hunoia.luno.ui.component.MyColumn
import hunoia.luno.ui.component.input.MyTextSlider
import kotlin.math.roundToInt

@Composable
fun GestureButtonTriggerDistanceContent(
    button: GestureButton,
    vm: GestureButtonSettingsVM
) {
    val scrollState = rememberScrollState()
    GestureSlideTriggerDistanceContent(
        slideTriggerDistance = button.slideTriggerDistance,
        onSlideTriggerDistanceChange = vm::onSlideTriggerDistanceChange,
        slideTriggerDistanceRange = MinSlideTriggerDistance.toFloat()..MaxSlideTriggerDistance.toFloat(),
        longPressTriggerDelayMs = button.longPressTriggerDelayMs,
        onLongPressTriggerDelayMsChange = vm::onLongPressTriggerDelayMsChange,
        doubleTapTriggerDelayMs = button.doubleTapTriggerDelayMs,
        onDoubleTapTriggerDelayMsChange = vm::onDoubleTapTriggerDelayMsChange,
        longSlideTriggerDistance = button.longSlideTriggerDistance,
        onLongSlideTriggerDistanceChange = vm::onLongSlideTriggerDistanceChange,
        slideHoldTriggerDelayMs = button.slideHoldTriggerDelayMs,
        onSlideHoldTriggerDelayMsChange = vm::onSlideHoldTriggerDelayMsChange,
        longSlideHoldTriggerDelayMs = button.longSlideHoldTriggerDelayMs,
        onLongSlideHoldTriggerDelayMsChange = vm::onLongSlideHoldTriggerDelayMsChange,
        scrollState = scrollState,
    )
}

@Composable
fun GestureSlideTriggerDistanceContent(
    slideTriggerDistance: Int,
    onSlideTriggerDistanceChange: (Float) -> Unit,
    slideTriggerDistanceRange: ClosedFloatingPointRange<Float>,
    longPressTriggerDelayMs: Long? = null,
    onLongPressTriggerDelayMsChange: ((Float) -> Unit)? = null,
    doubleTapTriggerDelayMs: Long? = null,
    onDoubleTapTriggerDelayMsChange: ((Float) -> Unit)? = null,
    longSlideTriggerDistance: Int,
    onLongSlideTriggerDistanceChange: (Float) -> Unit,
    longSlideTriggerDistanceRange: ClosedFloatingPointRange<Float> = MinLongSlideTriggerDistance.toFloat()..MaxLongSlideTriggerDistance.toFloat(),
    slideHoldTriggerDelayMs: Long,
    onSlideHoldTriggerDelayMsChange: (Float) -> Unit,
    longSlideHoldTriggerDelayMs: Long,
    onLongSlideHoldTriggerDelayMsChange: (Float) -> Unit,
    scrollState: androidx.compose.foundation.ScrollState? = null,
) {
    val slideTriggerDistanceDp = slideTriggerDistance.pxToDp()
    val slideTriggerDistanceRangeDp = slideTriggerDistanceRange.toDpRange()
    val longSlideTriggerDistanceDp = longSlideTriggerDistance.pxToDp()
    val longSlideTriggerDistanceRangeDp = longSlideTriggerDistanceRange.toDpRange()
    val minGapDp = MinGestureSlideDistanceGap.pxToDp()
    val dynamicSlideMaxDp = minOf(slideTriggerDistanceRangeDp.endInclusive, longSlideTriggerDistanceDp - minGapDp)
        .coerceAtLeast(slideTriggerDistanceRangeDp.start)
    val dynamicLongSlideMinDp = maxOf(longSlideTriggerDistanceRangeDp.start, slideTriggerDistanceDp + minGapDp)
        .coerceAtMost(longSlideTriggerDistanceRangeDp.endInclusive)
    val dynamicSlideRangeDp = slideTriggerDistanceRangeDp.start..dynamicSlideMaxDp
    val dynamicLongSlideRangeDp = dynamicLongSlideMinDp..longSlideTriggerDistanceRangeDp.endInclusive

    val content: @Composable () -> Unit = {
        SliderGroupLabel(text = stringResource(R.string.trigger_distance_slide_group))
        DistanceTextSlider(
            valueDp = slideTriggerDistanceDp,
            onValueDpChange = { onSlideTriggerDistanceChange(it.dpToPx()) },
            text = stringResource(R.string.trigger_slide_distance),
            valueRangeDp = dynamicSlideRangeDp,
        )
        DistanceTextSlider(
            valueDp = longSlideTriggerDistanceDp,
            onValueDpChange = { onLongSlideTriggerDistanceChange(it.dpToPx()) },
            text = stringResource(R.string.trigger_long_slide_distance),
            valueRangeDp = dynamicLongSlideRangeDp,
        )
        DistanceRelationshipPreview(
            slideDistanceDp = slideTriggerDistanceDp,
            longSlideDistanceDp = longSlideTriggerDistanceDp,
            maxDistanceDp = longSlideTriggerDistanceRangeDp.endInclusive,
        )

        SliderGroupLabel(text = stringResource(R.string.trigger_distance_delay_group))
        if (longPressTriggerDelayMs != null && onLongPressTriggerDelayMsChange != null) {
            MyTextSlider(
                value = longPressTriggerDelayMs.toFloat(),
                onValueChange = onLongPressTriggerDelayMsChange,
                text = stringResource(R.string.trigger_long_press_delay),
                valueDisplay = "${longPressTriggerDelayMs}ms",
                valueRange = MinLongPressTriggerDelayMs.toFloat()..MaxLongPressTriggerDelayMs.toFloat(),
            )
        }
        if (doubleTapTriggerDelayMs != null && onDoubleTapTriggerDelayMsChange != null) {
            MyTextSlider(
                value = doubleTapTriggerDelayMs.toFloat(),
                onValueChange = onDoubleTapTriggerDelayMsChange,
                text = stringResource(R.string.trigger_double_tap_delay),
                valueDisplay = "${doubleTapTriggerDelayMs}ms",
                valueRange = MinDoubleTapTriggerDelayMs.toFloat()..MaxDoubleTapTriggerDelayMs.toFloat(),
            )
        }
        MyTextSlider(
            value = slideHoldTriggerDelayMs.toFloat(),
            onValueChange = onSlideHoldTriggerDelayMsChange,
            text = stringResource(R.string.trigger_slide_hold_delay),
            valueDisplay = "${slideHoldTriggerDelayMs}ms",
            valueRange = MinHoldTriggerDelayMs.toFloat()..MaxHoldTriggerDelayMs.toFloat(),
        )
        MyTextSlider(
            value = longSlideHoldTriggerDelayMs.toFloat(),
            onValueChange = onLongSlideHoldTriggerDelayMsChange,
            text = stringResource(R.string.trigger_long_slide_hold_delay),
            valueDisplay = "${longSlideHoldTriggerDelayMs}ms",
            valueRange = MinHoldTriggerDelayMs.toFloat()..MaxHoldTriggerDelayMs.toFloat(),
        )
    }
    if (scrollState != null) {
        MyColumn(scrollState = scrollState) { content() }
    } else {
        content()
    }
}

@Composable
private fun SliderGroupLabel(text: String) {
    Text(
        modifier = Modifier.padding(horizontal = 12.dp),
        text = text,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        style = MaterialTheme.typography.labelMedium,
        maxLines = 1,
    )
}

@Composable
private fun DistanceTextSlider(
    valueDp: Float,
    onValueDpChange: (Float) -> Unit,
    text: String,
    valueRangeDp: ClosedFloatingPointRange<Float>,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        MyTextSlider(
            value = valueDp,
            onValueChange = onValueDpChange,
            text = text,
            valueDisplay = "${valueDp.roundToInt()}dp",
            valueRange = valueRangeDp,
            sliderValueHint = valueRangeDp.hintText(),
        )
        DistancePreviewBar(valueDp = valueDp)
    }
}

@Composable
private fun DistanceRelationshipPreview(
    slideDistanceDp: Float,
    longSlideDistanceDp: Float,
    maxDistanceDp: Float,
) {
    val colorScheme = MaterialTheme.colorScheme
    Canvas(
        modifier = Modifier
            .padding(horizontal = 12.dp)
            .fillMaxWidth()
            .height(8.dp)
    ) {
        val centerY = size.height / 2f
        val strokeWidth = 4.dp.toPx()
        val maxPx = maxDistanceDp.dp.toPx().coerceAtLeast(1f)
        fun marker(distanceDp: Float): Float = (distanceDp.dp.toPx() / maxPx * size.width).coerceIn(0f, size.width)
        drawLine(
            color = colorScheme.outlineVariant,
            start = Offset(0f, centerY),
            end = Offset(size.width, centerY),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round,
        )
        drawLine(
            color = colorScheme.primary,
            start = Offset(0f, centerY),
            end = Offset(marker(slideDistanceDp), centerY),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round,
        )
        drawLine(
            color = colorScheme.tertiary,
            start = Offset(marker(slideDistanceDp), centerY),
            end = Offset(marker(longSlideDistanceDp), centerY),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round,
        )
    }
}

@Composable
private fun DistancePreviewBar(
    valueDp: Float,
) {
    val colorScheme = MaterialTheme.colorScheme
    Canvas(
        modifier = Modifier
            .padding(horizontal = 12.dp)
            .fillMaxWidth()
            .height(8.dp)
    ) {
        val centerY = size.height / 2f
        val strokeWidth = 4.dp.toPx()
        val previewWidth = valueDp.dp.toPx().coerceIn(0f, size.width)
        drawLine(
            color = colorScheme.tertiary,
            start = Offset(0f, centerY),
            end = Offset(previewWidth, centerY),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round,
        )
    }
}

private fun Int.pxToDp(): Float = toFloat().pxToDp()

private fun Float.pxToDp(): Float = this / DensityProvider.density

private fun Float.dpToPx(): Float = this * DensityProvider.density

private fun ClosedFloatingPointRange<Float>.toDpRange(): ClosedFloatingPointRange<Float> =
    start.pxToDp()..endInclusive.pxToDp()

private fun ClosedFloatingPointRange<Float>.hintText(): Pair<String, String> =
    "${start.roundToInt()}dp" to "${endInclusive.roundToInt()}dp"
