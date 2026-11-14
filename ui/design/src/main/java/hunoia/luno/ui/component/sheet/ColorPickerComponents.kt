package hunoia.luno.ui.component.sheet

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import hunoia.luno.design.R
import hunoia.luno.ui.component.input.horizontalSliderDrag
import hunoia.luno.ui.theme.CanvasStrokeWidth
import hunoia.luno.ui.theme.CanvasThumbRadius
import hunoia.luno.ui.theme.LargeShape
import hunoia.luno.ui.theme.HueBarTouchHeight
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * 色相 + 饱和度圆盘：角度 = 色相，半径 = 饱和度（圆心为白）。
 *
 * 明度由调用方叠加黑色遮罩，圆盘本身只表达 H/S。按下立即取色，拖动连续取色；
 * 手指超出圆盘时钳到边缘。回调通过 [rememberUpdatedState] 保持最新，避免旧闭包回弹。
 */
@Composable
internal fun HsvWheelPicker(
    hue: Float,
    saturation: Float,
    value: Float,
    onHueSaturationChanged: (hue: Float, saturation: Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val currentOnChange by rememberUpdatedState(onHueSaturationChanged)
    val density = LocalDensity.current
    val thumbRadius = with(density) { CanvasThumbRadius.toPx() }
    val thumbStroke = with(density) { CanvasStrokeWidth.toPx() }
    val wheelDescription = stringResource(R.string.color_picker_wheel)

    Box(
        modifier = modifier
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    fun pick(position: Offset) {
                        val radius = minOf(size.width, size.height) / 2f
                        if (radius <= 0f) return
                        val center = Offset(size.width / 2f, size.height / 2f)
                        val dx = position.x - center.x
                        val dy = position.y - center.y
                        val sat = (hypot(dx, dy) / radius).coerceIn(0f, 1f)
                        val degrees = (Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat() + 360f) % 360f
                        currentOnChange(degrees, sat)
                    }
                    pick(down.position)
                    down.consume()
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) {
                            change.consume()
                            break
                        }
                        pick(change.position)
                        change.consume()
                    }
                }
            }
            .semantics { contentDescription = wheelDescription },
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .drawWithCache {
                    val radius = size.minDimension / 2f
                    val center = Offset(size.width / 2f, size.height / 2f)
                    val saturationBrush = Brush.radialGradient(
                        colors = listOf(Color.White, Color.White.copy(alpha = 0f)),
                        center = center,
                        radius = radius,
                    )
                    onDrawBehind {
                        drawCircle(HueSweepBrush, radius, center)
                        drawCircle(saturationBrush, radius, center)
                        if (value < 1f) {
                            drawCircle(Color.Black.copy(alpha = 1f - value), radius, center)
                        }
                        // 外圈描边，浅色圆盘在浅色 sheet 上也有边界。
                        drawCircle(
                            color = Color.Black.copy(alpha = 0.12f),
                            radius = radius - thumbStroke / 2,
                            center = center,
                            style = Stroke(thumbStroke),
                        )

                        val angle = Math.toRadians(hue.toDouble())
                        val distance = saturation * radius
                        val thumb = Offset(
                            x = center.x + cos(angle).toFloat() * distance,
                            y = center.y + sin(angle).toFloat() * distance,
                        )
                        drawCircle(Color.White, thumbRadius, thumb)
                        drawCircle(
                            Color.Black.copy(alpha = 0.35f),
                            radius = thumbRadius - thumbStroke / 2,
                            center = thumb,
                            style = Stroke(thumbStroke),
                        )
                    }
                },
        )
    }
}

/**
 * 颜色滑块：渐变轨道由 [brush] 铺满整行，M3 Slider 只负责交互与语义。
 * [checker] = true 时先铺棋盘格（透明度滑块用）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ColorSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    brush: Brush,
    label: String,
    valueText: String,
    modifier: Modifier = Modifier,
    checker: Boolean = false,
) {
    val density = LocalDensity.current
    val tile = with(density) { 8.dp.toPx() }
    val thumbSize = CanvasThumbRadius * 2
    val interactionSource = remember { MutableInteractionSource() }
    val latestValue by rememberUpdatedState(value)

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = valueText,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        Spacer(Modifier.height(6.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(HueBarTouchHeight)
                .clip(LargeShape),
            contentAlignment = Alignment.Center,
        ) {
            if (checker) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .drawWithCache {
                            onDrawBehind { drawCheckerboard(tile) }
                        },
                )
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(brush),
            )
            Slider(
                modifier = Modifier.fillMaxWidth(),
                value = value,
                onValueChange = onValueChange,
                valueRange = 0f..1f,
                interactionSource = interactionSource,
                colors = SliderDefaults.colors(
                    // 轨道全透明：渐变由底下两层画，M3 自绘轨会盖掉棋盘。
                    activeTrackColor = Color.Transparent,
                    inactiveTrackColor = Color.Transparent,
                    activeTickColor = Color.Transparent,
                    inactiveTickColor = Color.Transparent,
                ),
                // 白实心 + 深色细环：alpha 再低、底色再浅也不隐形。
                thumb = {
                    Box(
                        modifier = Modifier
                            .size(thumbSize)
                            .drawWithCache {
                                onDrawBehind {
                                    val stroke = CanvasStrokeWidth.toPx()
                                    drawCircle(Color.White, radius = size.minDimension / 2f)
                                    drawCircle(
                                        Color.Black.copy(alpha = 0.3f),
                                        radius = size.minDimension / 2f - stroke / 2,
                                        style = Stroke(stroke),
                                    )
                                }
                            },
                    )
                },
            )
            // 与 LunoSlider 同一手势：只认横向拖动、点按不跳值、纵向让给父级滚动。
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .horizontalSliderDrag(
                        valueRange = 0f..1f,
                        steps = 0,
                        currentValue = { latestValue },
                        interactionSource = interactionSource,
                        onValueChange = onValueChange,
                        onValueChangeFinished = {},
                    ),
            )
        }
    }
}

private fun DrawScope.drawCheckerboard(tile: Float) {
    var row = 0
    var y = 0f
    while (y < size.height) {
        var col = row
        var x = 0f
        while (x < size.width) {
            drawRect(
                color = if (col % 2 == 0) Color.White else CheckerGray,
                topLeft = Offset(x, y),
                size = Size(
                    tile.coerceAtMost(size.width - x),
                    tile.coerceAtMost(size.height - y),
                ),
            )
            x += tile
            col++
        }
        y += tile
        row++
    }
}

/** 透明度棋盘格深色格：中性灰，与明暗主题无关。 */
internal val CheckerGray = Color(0xFFCCCCCC)

/** 色相扫掠渐变是静态的，不要放进 draw lambda —— 拖动时每帧重建。 */
private val HueSweepBrush = Brush.sweepGradient(
    0f / 360f to Color.Red,
    60f / 360f to Color.Yellow,
    120f / 360f to Color.Green,
    180f / 360f to Color.Cyan,
    240f / 360f to Color.Blue,
    300f / 360f to Color.Magenta,
    360f / 360f to Color.Red,
)
