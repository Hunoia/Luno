package hunoia.luno.ui.component.sheet

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import hunoia.luno.ui.theme.LargeShape
import hunoia.luno.ui.theme.CanvasStrokeWidth
import hunoia.luno.ui.theme.CanvasThumbRadius

@Composable
internal fun HsvRectPicker(
    hue: Float,
    saturation: Float,
    value: Float,
    onColorChanged: (hue: Float, saturation: Float, value: Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val thumbRadius = with(density) { CanvasThumbRadius.toPx() }
    val thumbStroke = with(density) { CanvasStrokeWidth.toPx() }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(LargeShape)
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { offset ->
                        val s = (offset.x / size.width).coerceIn(0f, 1f)
                        val v = (1f - offset.y / size.height).coerceIn(0f, 1f)
                        onColorChanged(hue, s, v)
                    },
                    onDrag = { change, _ ->
                        change.consume()
                        val s = (change.position.x / size.width).coerceIn(0f, 1f)
                        val v = (1f - change.position.y / size.height).coerceIn(0f, 1f)
                        onColorChanged(hue, s, v)
                    },
                )
            }
    ) {
        // Brush 在 composable 侧按 hue 缓存：拖动选色每帧重绘，不能在 draw lambda 里重建
        val whiteToHueBrush = remember(hue) {
            Brush.horizontalGradient(0f to Color.White, 1f to Color.hsv(hue, 1f, 1f))
        }
        Canvas(Modifier.fillMaxSize()) {
            drawRect(whiteToHueBrush)
            drawRect(shadeBrush)

            val thumbX = saturation * size.width
            val thumbY = (1f - value) * size.height
            drawCircle(Color.White, thumbRadius, Offset(thumbX, thumbY))
            drawCircle(
                Color.Black.copy(alpha = 0.3f),
                thumbRadius - thumbStroke,
                Offset(thumbX, thumbY),
                style = Stroke(thumbStroke),
            )
        }
    }
}

/** 明度遮罩渐变是静态的，不要放进 Canvas 的 draw lambda */
private val shadeBrush = Brush.verticalGradient(0f to Color.Transparent, 1f to Color.Black)
