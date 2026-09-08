package hunoia.luno.ui.component.color

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import hunoia.luno.ui.theme.Spacing10
import hunoia.luno.ui.theme.Spacing16

@Composable
fun TabChip(
    selected: Boolean,
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        onClick = onClick,
        shape = MaterialTheme.shapes.large,
        color = if (selected) MaterialTheme.colorScheme.primaryContainer
        else MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Text(
            modifier = Modifier.padding(horizontal = Spacing16, vertical = Spacing10),
            text = text,
            style = MaterialTheme.typography.titleMedium,
            color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
            else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
fun HsvRectPicker(
    hue: Float,
    saturation: Float,
    value: Float,
    onColorChanged: (hue: Float, saturation: Float, value: Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val thumbRadius = with(density) { 10.dp.toPx() }
    val thumbStroke = with(density) { 2.dp.toPx() }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(MaterialTheme.shapes.large)
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
        Canvas(Modifier.fillMaxSize()) {
            val hueColor = Color.hsv(hue, 1f, 1f)
            drawRect(
                Brush.horizontalGradient(
                    0f to Color.White,
                    1f to hueColor,
                ),
            )
            drawRect(
                Brush.verticalGradient(
                    0f to Color.Transparent,
                    1f to Color.Black,
                ),
            )

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
