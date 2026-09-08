package hunoia.luno.ui.settings.gesture.subgesture
import hunoia.luno.ui.theme.*

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import hunoia.luno.R
import hunoia.luno.config.model.GestureButtonAngle
import hunoia.luno.config.model.GestureDirection
import hunoia.luno.ui.component.displayNameRes
import hunoia.luno.config.model.SubGestureAngle
import hunoia.luno.config.model.copyDirectionAngleBoundary
import hunoia.luno.ui.theme.ContentPaddingHorizontal
import hunoia.luno.ui.theme.ContentPaddingVertical
import hunoia.luno.ui.theme.ItemPadding
import hunoia.luno.ui.theme.SectionPadding
import java.lang.Math
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

@Composable
fun SubGestureAngleContent(
    angle: SubGestureAngle,
    onDismiss: () -> Unit,
    onSave: (SubGestureAngle) -> Unit,
    color: Color = MaterialTheme.colorScheme.primary
) {
    val names = listOf(
        stringResource(id = GestureDirection.UpRight.displayNameRes),
        stringResource(id = GestureDirection.Up.displayNameRes),
        stringResource(id = GestureDirection.UpLeft.displayNameRes),
        stringResource(id = GestureDirection.Left.displayNameRes),
        stringResource(id = GestureDirection.DownLeft.displayNameRes),
        stringResource(id = GestureDirection.Down.displayNameRes),
        stringResource(id = GestureDirection.DownRight.displayNameRes),
        stringResource(id = GestureDirection.Right.displayNameRes)
    )
    DirectionAngleContent(
        title = stringResource(id = R.string.sub_gesture_angles),
        boundaries = angle.boundaries,
        resetBoundaries = SubGestureAngle().boundaries,
        names = names,
        onDismiss = onDismiss,
        onSave = { onSave(SubGestureAngle(boundaries = it)) },
        color = color,
    )
}

@Composable
fun GestureButtonAngleContent(
    angle: GestureButtonAngle,
    onDismiss: () -> Unit,
    onSave: (GestureButtonAngle) -> Unit,
    color: Color = MaterialTheme.colorScheme.primary
) {
    val names = listOf(
        stringResource(R.string.direction_up_right),
        stringResource(R.string.top),
        stringResource(R.string.direction_up_left),
        stringResource(R.string.left),
        stringResource(R.string.direction_down_left),
        stringResource(R.string.bottom),
        stringResource(R.string.direction_down_right),
        stringResource(R.string.right),
    )
    DirectionAngleContent(
        title = stringResource(id = R.string.gesture_angles),
        boundaries = angle.boundaries,
        resetBoundaries = GestureButtonAngle().boundaries,
        names = names,
        onDismiss = onDismiss,
        onSave = { onSave(GestureButtonAngle(boundaries = it)) },
        color = color,
    )
}

@Composable
private fun DirectionAngleContent(
    title: String,
    boundaries: List<Float>,
    resetBoundaries: List<Float>,
    names: List<String>,
    onDismiss: () -> Unit,
    onSave: (List<Float>) -> Unit,
    color: Color = MaterialTheme.colorScheme.primary
) {
    var draftBoundaries by remember(boundaries) { mutableStateOf(boundaries) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = ContentPaddingHorizontal, vertical = ContentPaddingVertical)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge
            )
            TextButton(onClick = { draftBoundaries = resetBoundaries }) {
                Icon(Icons.Default.Restore, contentDescription = null)
                Text(text = stringResource(id = R.string.reset))
            }
        }

        Spacer(modifier = Modifier.height(SectionPadding))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(320.dp)
                .background(
                    color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.55f),
                    shape = MaterialTheme.shapes.extraLarge
                )
                .border(
                    width = Spacing1,
                    color = MaterialTheme.colorScheme.outlineVariant,
                    shape = MaterialTheme.shapes.extraLarge
                )
                .padding(horizontal = Spacing24, vertical = Spacing16)
        ) {
            Column {
                SubGestureAngleDial(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp),
                    boundaries = draftBoundaries,
                    onBoundariesChange = { draftBoundaries = it },
                    color = color
                )
                Spacer(modifier = Modifier.height(Spacing12))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    names.forEachIndexed { index, name ->
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = name,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "${sectorWidth(draftBoundaries, index).roundToInt()}°",
                                color = color,
                                textAlign = TextAlign.Center,
                                style = MaterialTheme.typography.titleSmall
                            )
                        }
                    }
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = SectionPadding),
            horizontalArrangement = Arrangement.spacedBy(ItemPadding)
        ) {
            OutlinedButton(
                modifier = Modifier.weight(1f),
                onClick = onDismiss
            ) {
                Text(text = stringResource(id = R.string.cancel))
            }
            Button(
                modifier = Modifier.weight(1f),
                onClick = { onSave(draftBoundaries) }
            ) {
                Text(text = stringResource(id = R.string.confirm))
            }
        }
    }
}

@Composable
private fun SubGestureAngleDial(
    onBoundariesChange: (List<Float>) -> Unit,
    boundaries: List<Float>,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary
) {
    val dragHandleRadius = Spacing10
    val dragHitRadius = 26.dp
    var circleRadius by remember { mutableStateOf(0f) }
    var circleCenter by remember { mutableStateOf(Offset.Zero) }
    var viewBounds by remember { mutableStateOf(Rect.Zero) }

    Canvas(
        modifier = modifier.let {
            val curOnBoundariesChange by rememberUpdatedState(newValue = onBoundariesChange)
            val curBoundaries by rememberUpdatedState(newValue = boundaries)
            it.pointerInput(dragHitRadius) {
                var dragOffset = Offset.Zero
                var candidates = emptyList<Int>()
                var candidateIndex: Int? = null
                detectDragGestures(
                    onDragStart = { offset ->
                        dragOffset = offset
                        candidates = curBoundaries.indices.filter { i ->
                            val degree = curBoundaries[i] * 360f
                            val rad = Math.toRadians(degree.toDouble())
                            val pOffset = Offset(
                                x = circleCenter.x + circleRadius * cos(rad).toFloat(),
                                y = circleCenter.y - circleRadius * sin(rad).toFloat()
                            )
                            Rect(center = pOffset, radius = dragHitRadius.toPx()).contains(offset)
                        }
                        candidateIndex = candidates.singleOrNull()
                    },
                    onDrag = onDrag@{ _, dragAmount ->
                        dragOffset += dragAmount
                        if (!viewBounds.contains(dragOffset)) return@onDrag
                        val curNorm = normalizedAngle(dragOffset, circleCenter)
                        if (candidateIndex == null) {
                            candidateIndex = selectDragTarget(curBoundaries, candidates, curNorm)
                        }
                        val target = candidateIndex ?: return@onDrag
                        val newBoundaries = copyDirectionAngleBoundary(curBoundaries, target, curNorm)
                        curOnBoundariesChange(newBoundaries)
                    },
                    onDragEnd = {
                        dragOffset = Offset.Zero
                        candidates = emptyList()
                        candidateIndex = null
                    },
                    onDragCancel = {
                        dragOffset = Offset.Zero
                        candidates = emptyList()
                        candidateIndex = null
                    }
                )
            }
        }
    ) {
        val dialRadius = size.minDimension * 0.26f
        val lineRadius = size.minDimension * 0.39f
        circleRadius = lineRadius
        val myCenter = center
        circleCenter = myCenter
        viewBounds = Rect(offset = Offset.Zero, size = size)
        val lineWidthPx = 3.dp.toPx()
        val pointRadiusPx = dragHandleRadius.toPx()

        drawCircle(color = color, radius = dialRadius, center = myCenter, alpha = 0.08f)
        drawCircle(
            color = color,
            radius = dialRadius,
            center = myCenter,
            alpha = 0.25f,
            style = Stroke(width = Spacing2.toPx())
        )
        drawCircle(
            color = color,
            radius = lineRadius,
            center = myCenter,
            alpha = 0.18f,
            style = Stroke(width = Spacing1.toPx())
        )

        drawLine(
            color = color.copy(alpha = 0.18f),
            start = Offset(x = myCenter.x - lineRadius, y = myCenter.y),
            end = Offset(x = myCenter.x + lineRadius, y = myCenter.y),
            strokeWidth = Spacing1.toPx(),
        )
        drawLine(
            color = color.copy(alpha = 0.18f),
            start = Offset(x = myCenter.x, y = myCenter.y - lineRadius),
            end = Offset(x = myCenter.x, y = myCenter.y + lineRadius),
            strokeWidth = Spacing1.toPx(),
        )

        boundaries.forEachIndexed { index, bound ->
            val angleRad = bound * 2f * PI.toFloat()
            val offset = Offset(
                x = myCenter.x + lineRadius * cos(angleRad),
                y = myCenter.y - lineRadius * sin(angleRad)
            )
            drawLine(color = color, start = myCenter, end = offset, strokeWidth = lineWidthPx)
            drawCircle(color = Color.White, radius = pointRadiusPx + Spacing2.toPx(), center = offset)
            drawCircle(color = color, radius = pointRadiusPx, center = offset)
        }

        drawCircle(color = color, radius = 7.dp.toPx(), center = myCenter)
        drawCircle(color = Color.White, radius = 3.dp.toPx(), center = myCenter)
    }
}

private fun normalizedAngle(offset: Offset, center: Offset): Float {
    val dx = offset.x - center.x
    val dy = offset.y - center.y
    val angle = atan2(-dy.toDouble(), dx.toDouble())
    var norm = (angle / (2 * PI)).toFloat()
    if (norm < 0f) norm += 1f
    return norm
}

private fun normalizedDiff(a: Float, b: Float): Float {
    val diff = a - b
    return minOf(kotlin.math.abs(diff), kotlin.math.abs(diff - 1f), kotlin.math.abs(diff + 1f))
}

private fun selectDragTarget(
    boundaries: List<Float>,
    candidates: List<Int>,
    newP: Float
): Int? {
    if (candidates.isEmpty()) return null
    if (candidates.size == 1) return candidates.first()

    val inRange = candidates.filter { i ->
        val prev = boundaries[(i + 7) % 8]
        val next = boundaries[(i + 1) % 8]
        if (prev < next) newP in prev..next
        else newP >= prev || newP <= next
    }

    when (inRange.size) {
        1 -> return inRange.first()
        0 -> { /* fall through to baseline heuristic */ }
        else -> return inRange.minBy { normalizedDiff(newP, boundaries[it]) }
    }

    val baseline = candidates.map { boundaries[it] }.average().toFloat()
    return if (newP >= baseline) {
        candidates.maxBy { boundaries[it] }
    } else {
        candidates.minBy { boundaries[it] }
    }
}

private fun sectorWidth(boundaries: List<Float>, index: Int): Float {
    val start = boundaries[index]
    val end = boundaries[(index + 1) % 8]
    return if (end >= start) (end - start) * 360f else (end + 1f - start) * 360f
}
