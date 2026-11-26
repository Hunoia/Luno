package hunoia.luno.gesture.settings.button

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import hunoia.luno.gesture.R
import hunoia.luno.bridge.ui.DensityProvider
import hunoia.luno.config.defaults.SettingsUiDefaults.MaxDoubleTapTriggerDelayMs
import hunoia.luno.config.defaults.SettingsUiDefaults.MaxHoldTriggerDelayMs
import hunoia.luno.config.defaults.SettingsUiDefaults.MaxLongPressTriggerDelayMs
import hunoia.luno.config.defaults.SettingsUiDefaults.MaxLongSlideTriggerDistance
import hunoia.luno.config.defaults.SettingsUiDefaults.MaxSlideTriggerDistance
import hunoia.luno.config.defaults.SettingsUiDefaults.MinDoubleTapTriggerDelayMs
import hunoia.luno.config.defaults.SettingsUiDefaults.MinGestureSlideDistanceGap
import hunoia.luno.config.defaults.SettingsUiDefaults.MinHoldTriggerDelayMs
import hunoia.luno.config.defaults.SettingsUiDefaults.MinLongPressTriggerDelayMs
import hunoia.luno.config.defaults.SettingsUiDefaults.MinLongSlideTriggerDistance
import hunoia.luno.config.defaults.SettingsUiDefaults.MinSlideTriggerDistance
import hunoia.luno.config.model.GestureButton
import hunoia.luno.ui.component.LunoColumn
import hunoia.luno.ui.component.row.group
import hunoia.luno.ui.theme.DistanceStrokeWidth
import hunoia.luno.ui.theme.DistanceTrackHeight
import hunoia.luno.ui.theme.ListSpacing
import hunoia.luno.ui.theme.PageGutter
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
        holdFireOnRelease = button.holdFireOnRelease,
        onHoldFireOnReleaseChange = vm::onHoldFireOnReleaseChange,
        scrollState = scrollState,
        onValueChangeFinished = vm::saveSettings,
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
    /** 非 null 时在停留时长组下方渲染「停留触发时机」开关；触钮与子手势页都传。 */
    holdFireOnRelease: Boolean? = null,
    onHoldFireOnReleaseChange: ((Boolean) -> Unit)? = null,
    scrollState: androidx.compose.foundation.ScrollState? = null,
    onValueChangeFinished: (() -> Unit)? = null,
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
        Column(verticalArrangement = Arrangement.spacedBy(ListSpacing)) {
            group(title = stringResource(R.string.trigger_distance_slide_group)) {
                sliderRow(
                    value = slideTriggerDistanceDp,
                    onValueChange = { onSlideTriggerDistanceChange(it.dpToPx()) },
                    onValueChangeFinished = onValueChangeFinished,
                    text = stringResource(R.string.trigger_slide_distance),
                    valueDisplay = { "${it.roundToInt()}dp" },
                    valueRange = dynamicSlideRangeDp,
                )
                sliderRow(
                    value = longSlideTriggerDistanceDp,
                    onValueChange = { onLongSlideTriggerDistanceChange(it.dpToPx()) },
                    onValueChangeFinished = onValueChangeFinished,
                    text = stringResource(R.string.trigger_long_slide_distance),
                    valueDisplay = { "${it.roundToInt()}dp" },
                    valueRange = dynamicLongSlideRangeDp,
                )
            }

            DistanceRelationshipPreview(
                slideDistanceDp = slideTriggerDistanceDp,
                longSlideDistanceDp = longSlideTriggerDistanceDp,
                maxDistanceDp = longSlideTriggerDistanceRangeDp.endInclusive,
            )

            group(title = stringResource(R.string.trigger_distance_delay_group)) {
                if (longPressTriggerDelayMs != null && onLongPressTriggerDelayMsChange != null) {
                    sliderRow(
                        value = longPressTriggerDelayMs.toFloat(),
                        onValueChange = onLongPressTriggerDelayMsChange,
                        onValueChangeFinished = { onValueChangeFinished?.invoke() },
                        text = stringResource(R.string.trigger_long_press_delay),
                        valueDisplay = { "${it.toLong()}ms" },
                        valueRange = MinLongPressTriggerDelayMs.toFloat()..MaxLongPressTriggerDelayMs.toFloat(),
                    )
                }
                if (doubleTapTriggerDelayMs != null && onDoubleTapTriggerDelayMsChange != null) {
                    sliderRow(
                        value = doubleTapTriggerDelayMs.toFloat(),
                        onValueChange = onDoubleTapTriggerDelayMsChange,
                        onValueChangeFinished = { onValueChangeFinished?.invoke() },
                        text = stringResource(R.string.trigger_double_tap_delay),
                        valueDisplay = { "${it.toLong()}ms" },
                        valueRange = MinDoubleTapTriggerDelayMs.toFloat()..MaxDoubleTapTriggerDelayMs.toFloat(),
                    )
                }
                sliderRow(
                    value = slideHoldTriggerDelayMs.toFloat(),
                    onValueChange = onSlideHoldTriggerDelayMsChange,
                    onValueChangeFinished = { onValueChangeFinished?.invoke() },
                    text = stringResource(R.string.trigger_slide_hold_delay),
                    valueDisplay = { "${it.toLong()}ms" },
                    valueRange = MinHoldTriggerDelayMs.toFloat()..MaxHoldTriggerDelayMs.toFloat(),
                )
                sliderRow(
                    value = longSlideHoldTriggerDelayMs.toFloat(),
                    onValueChange = onLongSlideHoldTriggerDelayMsChange,
                    onValueChangeFinished = { onValueChangeFinished?.invoke() },
                    text = stringResource(R.string.trigger_long_slide_hold_delay),
                    valueDisplay = { "${it.toLong()}ms" },
                    valueRange = MinHoldTriggerDelayMs.toFloat()..MaxHoldTriggerDelayMs.toFloat(),
                )
            }

            if (holdFireOnRelease != null && onHoldFireOnReleaseChange != null) {
                group(title = stringResource(R.string.trigger_hold_release_group)) {
                    switchRow(
                        title = stringResource(R.string.trigger_hold_fire_on_release),
                        subtitle = stringResource(R.string.trigger_hold_fire_on_release_hint),
                        checked = holdFireOnRelease,
                        onCheckedChange = onHoldFireOnReleaseChange,
                    )
                }
            }
        }
    }
    if (scrollState != null) {
        LunoColumn(scrollState = scrollState, contentPadding = 0.dp) { content() }
    } else {
        content()
    }
}

/**
 * 两个距离阈值的关系示意：底色轨道 + 从起点到短滑阈值的 primary 段 +
 * 短滑到长滑阈值的 tertiary 段，两个阈值各画一个描边圆点。
 */
@Composable
private fun DistanceRelationshipPreview(
    slideDistanceDp: Float,
    longSlideDistanceDp: Float,
    maxDistanceDp: Float,
) {
    val colorScheme = MaterialTheme.colorScheme
    Canvas(
        modifier = Modifier
            .padding(horizontal = PageGutter)
            .fillMaxWidth()
            .height(DistanceTrackHeight)
    ) {
        val centerY = size.height / 2f
        val strokeWidth = DistanceStrokeWidth.toPx()
        val maxPx = maxDistanceDp.dp.toPx().coerceAtLeast(1f)
        fun marker(distanceDp: Float): Float =
            (distanceDp.dp.toPx() / maxPx * size.width).coerceIn(0f, size.width)

        val slideX = marker(slideDistanceDp)
        val longX = marker(longSlideDistanceDp)

        drawLine(
            color = colorScheme.outlineVariant.copy(alpha = 0.45f),
            start = Offset(0f, centerY),
            end = Offset(size.width, centerY),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round,
        )
        drawLine(
            brush = Brush.horizontalGradient(
                listOf(colorScheme.primary.copy(alpha = 0.5f), colorScheme.primary)
            ),
            start = Offset(0f, centerY),
            end = Offset(slideX, centerY),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round,
        )
        drawLine(
            brush = Brush.horizontalGradient(
                listOf(colorScheme.tertiary.copy(alpha = 0.5f), colorScheme.tertiary)
            ),
            start = Offset(slideX, centerY),
            end = Offset(longX, centerY),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round,
        )

        val markerRadius = strokeWidth * 1.35f
        val ringRadius = markerRadius + strokeWidth * 0.45f
        fun drawMarker(x: Float, color: androidx.compose.ui.graphics.Color) {
            drawCircle(color = colorScheme.surface, radius = ringRadius, center = Offset(x, centerY))
            drawCircle(color = color, radius = markerRadius, center = Offset(x, centerY))
        }
        drawMarker(slideX, colorScheme.primary)
        drawMarker(longX, colorScheme.tertiary)
    }
}

private fun Int.pxToDp(): Float = toFloat().pxToDp()

private fun Float.pxToDp(): Float = this / DensityProvider.density

private fun Float.dpToPx(): Float = this * DensityProvider.density

private fun ClosedFloatingPointRange<Float>.toDpRange(): ClosedFloatingPointRange<Float> =
    start.pxToDp()..endInclusive.pxToDp()
