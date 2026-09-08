package hunoia.luno.ui.component.panel

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import hunoia.luno.config.model.GestureDirection
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

internal fun actionPanelOrigin(
    parentSize: Size,
    origin: Offset,
    itemSizePx: Float
): Offset {
    if (parentSize.isEmpty()) return origin
    val safePadding = itemSizePx * 0.55f
    return Offset(
        x = origin.x.coerceSafely(safePadding, parentSize.width - safePadding),
        y = origin.y.coerceSafely(safePadding, parentSize.height - safePadding),
    )
}

internal fun GestureDirection.unitVector(): Offset {
    val diagonal = 1f / sqrt(2f)
    return when (this) {
        GestureDirection.Left -> Offset(-1f, 0f)
        GestureDirection.UpLeft -> Offset(-diagonal, -diagonal)
        GestureDirection.Up -> Offset(0f, -1f)
        GestureDirection.UpRight -> Offset(diagonal, -diagonal)
        GestureDirection.Right -> Offset(1f, 0f)
        GestureDirection.DownRight -> Offset(diagonal, diagonal)
        GestureDirection.Down -> Offset(0f, 1f)
        GestureDirection.DownLeft -> Offset(-diagonal, diagonal)
    }
}

internal fun Offset.coerceInside(parentSize: Size, itemSizePx: Float): Offset {
    val padding = itemSizePx / 2f
    return Offset(
        x = x.coerceIn(padding, parentSize.width - padding),
        y = y.coerceIn(padding, parentSize.height - padding)
    )
}

internal fun Float.coerceSafely(minimumValue: Float, maximumValue: Float): Float {
    return if (minimumValue <= maximumValue) coerceIn(minimumValue, maximumValue) else minimumValue
}

internal fun arcLayerCapacity(radius: Float, itemSizePx: Float, minGapPx: Float, arcLength: Int): Int {
    val minDistance = itemSizePx + minGapPx
    val diameter = radius * 2f
    if (diameter <= minDistance) return 1
    val minAngle = Math.toDegrees(2.0 * kotlin.math.asin((minDistance / diameter).coerceAtMost(1f).toDouble())).toFloat()
    return kotlin.math.floor(arcLength.toFloat() / minAngle).toInt().coerceAtLeast(1) + 1
}

internal data class ActionPanelSector(
    val startDegrees: Float,
    val sweepDegrees: Float,
    val centerDegrees: Float,
)

internal fun actionPanelSector(parentSize: Size, origin: Offset, direction: GestureDirection, itemSizePx: Float): ActionPanelSector {
    if (parentSize.isEmpty()) return ActionPanelSector(direction.angleDegrees() - 90f, 180f, direction.angleDegrees())
    val edgeThreshold = kotlin.math.max(itemSizePx * 2.2f, 96f)
    val cornerThreshold = kotlin.math.max(itemSizePx * 3.0f, 132f)
    val nearLeftCorner = origin.x <= cornerThreshold
    val nearRightCorner = origin.x >= parentSize.width - cornerThreshold
    val nearTopCorner = origin.y <= cornerThreshold
    val nearBottomCorner = origin.y >= parentSize.height - cornerThreshold
    return when {
        nearLeftCorner && nearTopCorner -> ActionPanelSector(0f, 90f, 45f)
        nearRightCorner && nearTopCorner -> ActionPanelSector(90f, 90f, 135f)
        nearRightCorner && nearBottomCorner -> ActionPanelSector(180f, 90f, 225f)
        nearLeftCorner && nearBottomCorner -> ActionPanelSector(270f, 90f, 315f)
        origin.x <= edgeThreshold -> ActionPanelSector(-90f, 180f, 0f)
        origin.x >= parentSize.width - edgeThreshold -> ActionPanelSector(90f, 180f, 180f)
        origin.y <= edgeThreshold -> ActionPanelSector(0f, 180f, 90f)
        origin.y >= parentSize.height - edgeThreshold -> ActionPanelSector(180f, 180f, 270f)
        else -> {
            val center = direction.angleDegrees()
            ActionPanelSector(center - 90f, 180f, center)
        }
    }
}

internal fun actionPanelItemOffset(
    parentSize: Size,
    origin: Offset,
    sector: ActionPanelSector,
    actionCount: Int,
    index: Int,
    itemSizePx: Float,
    spreadSpacing: Float,
): Offset {
    if (parentSize.isEmpty() || actionCount <= 0) return Offset.Zero
    val spacing = spreadSpacing.coerceIn(0.85f, 1.6f)
    val minGapPx = itemSizePx * 0.24f * spacing
    val baseRadius = itemSizePx * when (actionCount) {
        1 -> 1.55f
        2 -> 1.75f
        3 -> 1.95f
        else -> 2.15f
    } * spacing
    val maxItemsPerLayer = arcLayerCapacity(
        radius = baseRadius,
        itemSizePx = itemSizePx,
        minGapPx = minGapPx,
        arcLength = sector.sweepDegrees.toInt(),
    )
    val layerCount = ceil(actionCount / maxItemsPerLayer.toFloat()).toInt().coerceAtLeast(1)
    val itemsPerLayer = ceil(actionCount / layerCount.toFloat()).toInt().coerceAtLeast(1)
    val layer = index / itemsPerLayer
    val indexInLayer = index % itemsPerLayer
    val firstIndexInLayer = layer * itemsPerLayer
    val countInLayer = min(itemsPerLayer, actionCount - firstIndexInLayer).coerceAtLeast(1)
    val radius = baseRadius + itemSizePx * layer * 1.18f * spacing
    val arcDegrees = sector.sweepDegrees.coerceIn(90f, 180f)
    val stepDegree = max(24f, 33f / spacing)
    val sweepDegree = if (countInLayer == 1) 0f else min(arcDegrees, stepDegree * (countInLayer - 1))
    val angleDegree = if (countInLayer == 1) sector.centerDegrees else {
        sector.centerDegrees - sweepDegree / 2f + sweepDegree * indexInLayer / (countInLayer - 1)
    }
    val radians = Math.toRadians(angleDegree.toDouble())
    val targetCenter = Offset(
        x = origin.x + cos(radians).toFloat() * radius,
        y = origin.y + sin(radians).toFloat() * radius,
    ).coerceInside(parentSize, itemSizePx)
    return targetCenter - origin
}

internal fun actionPanelContentBounds(
    parentSize: Size,
    origin: Offset,
    sector: ActionPanelSector,
    actionCount: Int,
    itemSizePx: Float,
    spreadSpacing: Float,
): Rect {
    if (parentSize.isEmpty() || actionCount <= 0) return Rect(origin, Size.Zero)
    val halfItem = itemSizePx * 0.62f
    var left = Float.POSITIVE_INFINITY
    var top = Float.POSITIVE_INFINITY
    var right = Float.NEGATIVE_INFINITY
    var bottom = Float.NEGATIVE_INFINITY
    repeat(actionCount) { index ->
        val displayIndex = actionPanelDisplayIndex(sector, actionCount, index)
        val center = origin + actionPanelItemOffset(
            parentSize = parentSize,
            origin = origin,
            sector = sector,
            actionCount = actionCount,
            index = displayIndex,
            itemSizePx = itemSizePx,
            spreadSpacing = spreadSpacing,
        )
        left = min(left, center.x - halfItem)
        top = min(top, center.y - halfItem)
        right = max(right, center.x + halfItem)
        bottom = max(bottom, center.y + halfItem)
    }
    return Rect(left, top, right, bottom)
}

internal fun actionPanelPillPosition(parentSize: Size, actionBounds: Rect, itemSizePx: Float): Offset {
    val pillHeight = itemSizePx * 1.35f
    val margin = itemSizePx * 0.55f
    val gap = itemSizePx * 0.45f
    val topY = margin
    val bottomY = parentSize.height - pillHeight - margin
    val aboveY = actionBounds.top - gap - pillHeight
    val belowY = actionBounds.bottom + gap
    val aboveFits = aboveY >= topY
    val belowFits = belowY <= bottomY
    val y = when {
        aboveFits && belowFits -> {
            val aboveDistance = actionBounds.top - topY
            val belowDistance = bottomY - actionBounds.bottom
            if (aboveDistance <= belowDistance) aboveY else belowY
        }
        aboveFits -> aboveY
        belowFits -> belowY
        actionBounds.center.y > parentSize.height / 2f -> topY
        else -> bottomY
    }
    return Offset(0f, y.coerceSafely(topY, bottomY))
}

internal fun actionPanelDisplayIndex(sector: ActionPanelSector, actionCount: Int, index: Int): Int {
    if (actionCount <= 2) return index
    val normalizedCenter = ((sector.centerDegrees % 360f) + 360f) % 360f
    val reverse = normalizedCenter in 90f..270f
    return if (reverse) actionCount - 1 - index else index
}

private fun GestureDirection.angleDegrees(): Float = when (this) {
    GestureDirection.Right -> 0f
    GestureDirection.DownRight -> 45f
    GestureDirection.Down -> 90f
    GestureDirection.DownLeft -> 135f
    GestureDirection.Left -> 180f
    GestureDirection.UpLeft -> 225f
    GestureDirection.Up -> 270f
    GestureDirection.UpRight -> 315f
}

internal fun actionPanelHitContains(
    finger: Offset,
    panelOrigin: Offset,
    targetAnimOffset: Offset,
    itemSizePx: Float
): Boolean {
    val targetCenter = panelOrigin + targetAnimOffset
    val halfSize = itemSizePx / 2f
    return finger.x in (targetCenter.x - halfSize)..(targetCenter.x + halfSize) &&
            finger.y in (targetCenter.y - halfSize)..(targetCenter.y + halfSize)
}
