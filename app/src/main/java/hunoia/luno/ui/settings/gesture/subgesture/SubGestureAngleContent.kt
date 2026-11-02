package hunoia.luno.ui.settings.gesture.subgesture

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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import hunoia.luno.R
import hunoia.luno.config.model.DirectionLayoutDefaults
import hunoia.luno.config.model.GestureButtonAngle
import hunoia.luno.config.model.SubGestureAngle
import hunoia.luno.config.model.copyDirectionAngleBoundary
import hunoia.luno.config.model.evenBoundaries
import hunoia.luno.config.model.normalizedAngle
import hunoia.luno.config.model.sectorCenterDegrees
import hunoia.luno.config.model.sectorWidthDegrees
import hunoia.luno.ui.component.DirectionLabels
import hunoia.luno.ui.component.directionLabel
import hunoia.luno.ui.component.sheet.SheetActionButtons
import hunoia.luno.ui.theme.CanvasHitRadius
import hunoia.luno.ui.theme.CanvasThumbRadius
import hunoia.luno.ui.theme.ContentBottom
import hunoia.luno.ui.theme.ExtraLargeShape
import hunoia.luno.ui.theme.ListItemVerticalPadding
import hunoia.luno.ui.theme.PageGutter
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

@Composable
fun SubGestureAngleContent(
    angle: SubGestureAngle,
    onDismissRequest: () -> Unit,
    onConfirm: (SubGestureAngle) -> Unit,
    color: Color = MaterialTheme.colorScheme.primary
) {
    DirectionAngleContent(
        title = stringResource(id = R.string.sub_gesture_angles),
        boundaries = angle.boundaries,
        onDismissRequest = onDismissRequest,
        onConfirm = { onConfirm(SubGestureAngle(boundaries = it)) },
        color = color,
    )
}

@Composable
fun GestureButtonAngleContent(
    angle: GestureButtonAngle,
    onDismissRequest: () -> Unit,
    onConfirm: (GestureButtonAngle) -> Unit,
    color: Color = MaterialTheme.colorScheme.primary
) {
    DirectionAngleContent(
        title = stringResource(id = R.string.gesture_angles),
        boundaries = angle.boundaries,
        onDismissRequest = onDismissRequest,
        onConfirm = { onConfirm(GestureButtonAngle(boundaries = it)) },
        color = color,
    )
}

/**
 * 方向角度编辑：方向数（1..8）+ 逐边界拖动 + 扇区中心实时显示方向名/角度。
 *
 * 方向数变化时保留槽 0 起点并等分圆周；确认后由调用方按「数量是否变化」决定清空动作组。
 * 扇区宽度仍可单独拖动（允许拖成 0° 关闭该方向）。
 */
@Composable
private fun DirectionAngleContent(
    title: String,
    boundaries: List<Float>,
    onDismissRequest: () -> Unit,
    onConfirm: (List<Float>) -> Unit,
    color: Color = MaterialTheme.colorScheme.primary
) {
    var draftBoundaries by remember(boundaries) { mutableStateOf(boundaries) }

    fun changeCount(count: Int) {
        if (count == draftBoundaries.size) return
        draftBoundaries = evenBoundaries(count, draftBoundaries.first())
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = PageGutter, vertical = ListItemVerticalPadding)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge
            )
            TextButton(onClick = {
                draftBoundaries = DirectionLayoutDefaults.defaultBoundaries(draftBoundaries.size)
            }) {
                Icon(Icons.Default.Restore, contentDescription = null)
                Text(text = stringResource(id = R.string.reset))
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        DirectionCountStepper(
            count = draftBoundaries.size,
            onCountChange = ::changeCount,
        )

        Spacer(modifier = Modifier.height(12.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 320.dp)
                .background(
                    color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.55f),
                    shape = ExtraLargeShape
                )
                .border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outlineVariant,
                    shape = ExtraLargeShape
                )
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Column {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(250.dp)
                ) {
                    SubGestureAngleDial(
                        modifier = Modifier.fillMaxWidth().height(250.dp),
                        boundaries = draftBoundaries,
                        onBoundariesChange = { draftBoundaries = it },
                        color = color
                    )
                    SectorLabels(
                        boundaries = draftBoundaries,
                        color = color,
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(
                        R.string.direction_sector_summary,
                        draftBoundaries.size,
                    ) + " · " + stringResource(
                        R.string.direction_sector_custom,
                        draftBoundaries.indices.joinToString(" / ") {
                            "${sectorWidthDegrees(draftBoundaries, it).roundToInt()}°"
                        },
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        SheetActionButtons(
            onDismiss = onDismissRequest,
            onConfirm = { onConfirm(draftBoundaries) },
            modifier = Modifier.padding(top = ContentBottom),
        )
    }
}

@Composable
private fun DirectionCountStepper(
    count: Int,
    onCountChange: (Int) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = stringResource(R.string.direction_count),
            style = MaterialTheme.typography.bodyLarge,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(
                onClick = { onCountChange(count - 1) },
                enabled = count > DirectionLayoutDefaults.MinCount,
            ) {
                Icon(
                    Icons.Default.Remove,
                    contentDescription = stringResource(R.string.direction_count_remove),
                )
            }
            Text(
                text = count.toString(),
                style = MaterialTheme.typography.titleLarge,
            )
            IconButton(
                onClick = { onCountChange(count + 1) },
                enabled = count < DirectionLayoutDefaults.MaxCount,
            ) {
                Icon(
                    Icons.Default.Add,
                    contentDescription = stringResource(R.string.direction_count_add),
                )
            }
        }
    }
}

/** 扇区中心的方向图标 + 名称 + 角度：跟随拖动实时更新。 */
@Composable
private fun SectorLabels(
    boundaries: List<Float>,
    color: Color,
) {
    var size by remember { mutableStateOf(IntSize.Zero) }
    val density = LocalDensity.current
    // 单方向要放「任意方向」四个字，其余方向是两字罗盘名。
    val labelWidth = if (boundaries.size == 1) 76.dp else 56.dp
    val boxWidthPx = with(density) { labelWidth.toPx() }
    val boxHeightPx = with(density) { SectorLabelHeight.toPx() }
    val radius = minOf(size.width, size.height).toFloat() * 0.30f
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(250.dp)
            .onSizeChanged { size = it }
    ) {
        if (size == IntSize.Zero) return@Box
        boundaries.indices.forEach { slot ->
            // 零宽扇区等于「关闭这个方向」：不在盘面上重复叠一个标签。
            if (sectorWidthDegrees(boundaries, slot) == 0f) return@forEach
            val center = sectorCenterDegrees(boundaries, slot)
            val rad = center / 360.0 * 2 * PI
            val x = size.width / 2f + radius * cos(rad).toFloat()
            val y = size.height / 2f - radius * sin(rad).toFloat()
            Box(
                modifier = Modifier
                    .offset {
                        IntOffset(
                            (x - boxWidthPx / 2f).roundToInt(),
                            (y - boxHeightPx / 2f).roundToInt(),
                        )
                    }
                    .size(labelWidth, SectorLabelHeight),
                contentAlignment = Alignment.Center,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = null,
                        tint = color,
                        modifier = Modifier
                            .size(12.dp)
                            .graphicsLayer { rotationZ = DirectionLabels.iconRotation(center, boundaries.size) },
                    )
                    Text(
                        text = directionLabel(center, boundaries.size, withAngle = false),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

private val SectorLabelHeight = 18.dp

@Composable
private fun SubGestureAngleDial(
    onBoundariesChange: (List<Float>) -> Unit,
    boundaries: List<Float>,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary
) {
    val dragHandleRadius = CanvasThumbRadius
    val dragHitRadius = CanvasHitRadius
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
            style = Stroke(width = 2.dp.toPx())
        )
        drawCircle(
            color = color,
            radius = lineRadius,
            center = myCenter,
            alpha = 0.18f,
            style = Stroke(width = 1.dp.toPx())
        )

        drawLine(
            color = color.copy(alpha = 0.18f),
            start = Offset(x = myCenter.x - lineRadius, y = myCenter.y),
            end = Offset(x = myCenter.x + lineRadius, y = myCenter.y),
            strokeWidth = 1.dp.toPx(),
        )
        drawLine(
            color = color.copy(alpha = 0.18f),
            start = Offset(x = myCenter.x, y = myCenter.y - lineRadius),
            end = Offset(x = myCenter.x, y = myCenter.y + lineRadius),
            strokeWidth = 1.dp.toPx(),
        )

        boundaries.forEachIndexed { index, bound ->
            val angleRad = bound * 2f * PI.toFloat()
            val offset = Offset(
                x = myCenter.x + lineRadius * cos(angleRad),
                y = myCenter.y - lineRadius * sin(angleRad)
            )
            drawLine(color = color, start = myCenter, end = offset, strokeWidth = lineWidthPx)
            drawCircle(color = Color.White, radius = pointRadiusPx + 2.dp.toPx(), center = offset)
            drawCircle(color = color, radius = pointRadiusPx, center = offset)
        }

        drawCircle(color = color, radius = 7.dp.toPx(), center = myCenter)
        drawCircle(color = Color.White, radius = 3.dp.toPx(), center = myCenter)
    }
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
        val prev = boundaries[(i + boundaries.size - 1) % boundaries.size]
        val next = boundaries[(i + 1) % boundaries.size]
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
