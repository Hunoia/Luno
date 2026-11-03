package hunoia.luno.runtime.pointer

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateOffsetAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import hunoia.luno.config.model.GestureSettings
import kotlinx.coroutines.delay

/** 点击涟漪动画时长（旧主题 AnimRipple 常量，内联）。 */
private const val RIPPLE_MS = 300

@Composable
fun PointerCursor(
    position: Offset,
    modifier: Modifier = Modifier,
    settings: GestureSettings.Pointer = GestureSettings.Pointer(),
    clickPulseKey: Int = 0,
) {
    val trail = remember { mutableStateListOf<Offset>() }
    var pulse by remember { mutableStateOf(0f) }
    val rippleAnim = remember { Animatable(0f) }
    val entranceAnim = remember { Animatable(0f) }
    val accentColor = MaterialTheme.colorScheme.primary
    val renderedPosition by animateOffsetAsState(
        targetValue = position,
        animationSpec = tween(durationMillis = 45, easing = LinearEasing),
        label = "PointerCursorPosition",
    )
    val maxTrailSize = 18
    LaunchedEffect(Unit) {
        entranceAnim.animateTo(1f, animationSpec = tween(durationMillis = 140, easing = LinearEasing))
    }
    LaunchedEffect(renderedPosition) {
        if (renderedPosition.x.isFinite() && renderedPosition.y.isFinite()) {
            val last = trail.lastOrNull()
            if (last == null || (renderedPosition - last).getDistance() >= settings.cursorSizeDp * 0.22f) {
                trail.add(renderedPosition)
                while (trail.size > maxTrailSize) trail.removeAt(0)
            }
        }
    }
    LaunchedEffect(clickPulseKey) {
        if (clickPulseKey == 0) return@LaunchedEffect
        pulse = 1f
        repeat(8) {
            delay(16)
            pulse *= 0.72f
        }
        pulse = 0f
    }
    LaunchedEffect(clickPulseKey) {
        if (clickPulseKey == 0) return@LaunchedEffect
        rippleAnim.snapTo(0f)
        rippleAnim.animateTo(1f, animationSpec = tween(durationMillis = RIPPLE_MS, easing = LinearEasing))
        rippleAnim.snapTo(0f)
    }
    Canvas(modifier = modifier.fillMaxSize()) {
        if (!renderedPosition.x.isFinite() || !renderedPosition.y.isFinite()) return@Canvas
        val visibleAlpha = settings.cursorAlpha * entranceAnim.value
        val baseColor = accentColor.copy(alpha = visibleAlpha)
        val radius = settings.cursorSizeDp.dp.toPx() / 2f * (0.88f + 0.12f * entranceAnim.value)
        val ringRadius = radius * (1f - pulse * 0.12f)
        val wideStroke = radius * 0.95f
        val coreStroke = radius * 0.34f
        trail.zipWithNext().forEachIndexed { index, (start, end) ->
            val progress = (index + 1).toFloat() / trail.size
            val alpha = progress * progress * visibleAlpha
            drawLine(color = baseColor.copy(alpha = alpha * 0.14f), start = start, end = end, strokeWidth = wideStroke, cap = StrokeCap.Round)
            drawLine(color = baseColor.copy(alpha = alpha * 0.42f), start = start, end = end, strokeWidth = coreStroke, cap = StrokeCap.Round)
        }
        if (pulse > 0f) {
            drawCircle(
                color = baseColor.copy(alpha = pulse * 0.26f),
                radius = radius * (1.25f + (1f - pulse) * 1.1f),
                center = renderedPosition,
                style = Stroke(width = 2.dp.toPx()),
            )
        }
        drawCircle(
            color = Color.Black.copy(alpha = 0.75f * visibleAlpha),
            radius = ringRadius,
            center = renderedPosition,
            style = Stroke(width = 4.dp.toPx()),
        )
        drawCircle(
            color = Color.White.copy(alpha = 0.9f * visibleAlpha),
            radius = ringRadius,
            center = renderedPosition,
            style = Stroke(width = 2.dp.toPx()),
        )
        drawCircle(
            color = baseColor,
            radius = ringRadius,
            center = renderedPosition,
            style = Stroke(width = 1.2.dp.toPx()),
        )
        drawCircle(color = Color.Black.copy(alpha = 0.75f * visibleAlpha), radius = radius * 0.16f, center = renderedPosition)
        drawCircle(color = Color.White.copy(alpha = 0.9f * visibleAlpha), radius = radius * 0.1f, center = renderedPosition)
        drawCircle(color = baseColor, radius = radius * 0.06f, center = renderedPosition)
        if (rippleAnim.value > 0f) {
            drawCircle(
                color = baseColor.copy(alpha = (1f - rippleAnim.value) * 0.4f),
                radius = radius * (1f + rippleAnim.value * 3f),
                center = renderedPosition,
                style = Stroke(width = 2.dp.toPx()),
            )
        }
    }
}
