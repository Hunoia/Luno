package hunoia.luno.gesture.settings.subgesture

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import hunoia.luno.gesture.R
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
        boundaries = angle.boundaries,
        onDismissRequest = onDismissRequest,
        onConfirm = { onConfirm(GestureButtonAngle(boundaries = it)) },
        color = color,
    )
}

/**
 * 方向角度编辑：方向数（1..8）+ 逐边界拖动 + 扇区中心显示方向名/扇区宽度。
 *
 * 方向数变化时保留槽 0 起点并等分圆周；确认后由调用方按「数量是否变化」决定清空动作组。
 * 扇区宽度仍可单独拖动（允许拖成 0° 关闭该方向）。
 */
@Composable
private fun DirectionAngleContent(
    boundaries: List<Float>,
    onDismissRequest: () -> Unit,
    onConfirm: (List<Float>) -> Unit,
    color: Color = MaterialTheme.colorScheme.primary
) {
    var draftBoundaries by remember(boundaries) { mutableStateOf(boundaries) }
    var dragHint by remember { mutableStateOf<DialDragHint?>(null) }

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
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            IconButton(onClick = {
                draftBoundaries = DirectionLayoutDefaults.defaultBoundaries(draftBoundaries.size)
            }) {
                Icon(
                    Icons.Default.Restore,
                    contentDescription = stringResource(id = hunoia.luno.design.R.string.reset),
                )
            }
            DirectionCountStepper(
                count = draftBoundaries.size,
                onCountChange = ::changeCount,
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .background(
                    color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.55f),
                    shape = ExtraLargeShape
                )
                .border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outlineVariant,
                    shape = ExtraLargeShape
                )
                .padding(16.dp)
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                SubGestureAngleDial(
                    modifier = Modifier.fillMaxSize(),
                    boundaries = draftBoundaries,
                    onBoundariesChange = { draftBoundaries = it },
                    onDragHint = { dragHint = it },
                    color = color
                )
                SectorLabels(
                    boundaries = draftBoundaries,
                    color = color,
                )
                dragHint?.let { DialDragHintChip(it) }
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
            style = MaterialTheme.typography.titleMedium,
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

/** 扇区中心的方向图标 + 名称 + 扇区宽度：方向数 ≤ 4 时显示宽度，更多时只显示名称。 */
@Composable
private fun SectorLabels(
    boundaries: List<Float>,
    color: Color,
) {
    var size by remember { mutableStateOf(IntSize.Zero) }
    val density = LocalDensity.current
    val count = boundaries.size
    val showWidth = count <= MaxSectorWidthLabelCount
    // 单方向要放「任意方向 360°」，其余方向是两字罗盘名 + 可选宽度。
    val labelWidth = if (count == 1) 148.dp else 64.dp
    val boxWidthPx = with(density) { labelWidth.toPx() }
    val boxHeightPx = with(density) { SectorLabelHeight.toPx() }
    val radius = minOf(size.width, size.height).toFloat() * 0.30f
    Box(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { size = it }
    ) {
        if (size == IntSize.Zero) return@Box
        boundaries.indices.forEach { slot ->
            val widthDegrees = sectorWidthDegrees(boundaries, slot).roundToInt()
            val disabled = widthDegrees == 0
            val center = sectorCenterDegrees(boundaries, slot)
            val name = directionLabel(center, count, withAngle = false)
            // 0° 宽扇区已等于「关闭」，无论方向数多少都要显式标出来。
            val widthText = when {
                disabled -> stringResource(hunoia.luno.design.R.string.close)
                showWidth -> "$widthDegrees°"
                else -> null
            }
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
                        tint = if (disabled) {
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        } else {
                            color
                        },
                        modifier = Modifier
                            .size(12.dp)
                            .graphicsLayer { rotationZ = DirectionLabels.iconRotation(center, count) },
                    )
                    Text(
                        text = if (widthText == null) name else "$name $widthText",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (disabled) {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

private const val MaxSectorWidthLabelCount = 4

private val SectorLabelHeight = 18.dp

private data class DialDragHint(val position: Offset, val degrees: Int)

@Composable
private fun DialDragHintChip(hint: DialDragHint) {
    val density = LocalDensity.current
    val chipWidth = 52.dp
    val chipHeight = 22.dp
    val widthPx = with(density) { chipWidth.toPx() }
    val heightPx = with(density) { chipHeight.toPx() }
    Box(
        modifier = Modifier
            .offset {
                IntOffset(
                    (hint.position.x - widthPx / 2f).roundToInt(),
                    (hint.position.y - heightPx / 2f).roundToInt(),
                )
            }
            .size(chipWidth, chipHeight)
            .background(MaterialTheme.colorScheme.inverseSurface, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "${hint.degrees}°",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.inverseOnSurface,
            maxLines = 1,
        )
    }
}

@Composable
private fun SubGestureAngleDial(
    onBoundariesChange: (List<Float>) -> Unit,
    boundaries: List<Float>,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    onDragHint: (DialDragHint?) -> Unit = {},
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
            val curOnDragHint by rememberUpdatedState(newValue = onDragHint)
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
                        candidateIndex?.let { index ->
                            curOnDragHint(
                                DialDragHint(
                                    position = handlePosition(
                                        circleCenter,
                                        circleRadius,
                                        curBoundaries[index],
                                    ),
                                    degrees = normalizeDegrees(curBoundaries[index] * 360f),
                                )
                            )
                        }
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
                        curOnDragHint(
                            DialDragHint(
                                position = handlePosition(circleCenter, circleRadius, curNorm),
                                degrees = normalizeDegrees(curNorm * 360f),
                            )
                        )
                    },
                    onDragEnd = {
                        dragOffset = Offset.Zero
                        candidates = emptyList()
                        candidateIndex = null
                        curOnDragHint(null)
                    },
                    onDragCancel = {
                        dragOffset = Offset.Zero
                        candidates = emptyList()
                        candidateIndex = null
                        curOnDragHint(null)
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

private fun handlePosition(center: Offset, radius: Float, normalized: Float): Offset {
    val rad = normalized * 2f * PI.toFloat()
    return Offset(
        x = center.x + radius * cos(rad),
        y = center.y - radius * sin(rad),
    )
}

private fun normalizeDegrees(degrees: Float): Int =
    ((degrees.roundToInt() % 360) + 360) % 360

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
