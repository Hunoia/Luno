package hunoia.luno.runtime.gesturepanel

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

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

/**
 * 半径为 [radius] 的圆上，要让相邻两项的**中心直线距离**为 [centerDistance]，
 * 所需的角步长（度）。容量计算与实际布局必须共用这个公式，否则"每层能放几个"
 * 与"实际步长"会不一致，间距一大图标就重叠。
 */
internal fun arcStepDegrees(radius: Float, centerDistance: Float): Float {
    val diameter = radius * 2f
    if (diameter <= centerDistance || diameter <= 0f) return 180f
    val ratio = (centerDistance / diameter).coerceIn(0f, 1f)
    return Math.toDegrees(2.0 * kotlin.math.asin(ratio.toDouble())).toFloat()
}

/** 半径 [radius] 的弧上，在 [arcLength] 度内按 [centerDistance] 中心距最多能放几个。 */
internal fun arcLayerCapacity(radius: Float, centerDistance: Float, arcLength: Int): Int {
    if (radius <= 0f) return 1
    val step = arcStepDegrees(radius, centerDistance)
    if (step <= 0f) return 1
    return kotlin.math.floor(arcLength.toFloat() / step).toInt().coerceAtLeast(1) + 1
}

internal data class ActionPanelSector(
    val sweepDegrees: Float,
    val centerDegrees: Float,
)

internal fun actionPanelSector(parentSize: Size, origin: Offset, centerDegrees: Float, itemSizePx: Float): ActionPanelSector {
    if (parentSize.isEmpty()) return ActionPanelSector(180f, centerDegrees)
    val edgeThreshold = kotlin.math.max(itemSizePx * 2.2f, 96f)
    val cornerThreshold = kotlin.math.max(itemSizePx * 3.0f, 132f)
    val nearLeftCorner = origin.x <= cornerThreshold
    val nearRightCorner = origin.x >= parentSize.width - cornerThreshold
    val nearTopCorner = origin.y <= cornerThreshold
    val nearBottomCorner = origin.y >= parentSize.height - cornerThreshold
    return when {
        nearLeftCorner && nearTopCorner -> ActionPanelSector(90f, 45f)
        nearRightCorner && nearTopCorner -> ActionPanelSector(90f, 135f)
        nearRightCorner && nearBottomCorner -> ActionPanelSector(90f, 225f)
        nearLeftCorner && nearBottomCorner -> ActionPanelSector(90f, 315f)
        origin.x <= edgeThreshold -> ActionPanelSector(180f, 0f)
        origin.x >= parentSize.width - edgeThreshold -> ActionPanelSector(180f, 180f)
        origin.y <= edgeThreshold -> ActionPanelSector(180f, 90f)
        origin.y >= parentSize.height - edgeThreshold -> ActionPanelSector(180f, 270f)
        else -> ActionPanelSector(180f, centerDegrees)
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
    itemGapPx: Float,
    maxAngleDegrees: Float = 180f,
): Offset {
    if (parentSize.isEmpty() || actionCount <= 0) return Offset.Zero
    val spacing = spreadSpacing.coerceIn(0.85f, 1.6f)
    val gap = itemGapPx.coerceAtLeast(0f)
    // 中心距 = 图标直径 + 净间距。角步长由它反推，所以 spreadSpacing（只改半径）
    // 不再像以前那样和步长互相抵消 —— 净间距完全由 itemGapPx 决定。
    val centerDistance = itemSizePx + gap
    val plan = adaptiveLayerPlan(
        parentSize = parentSize,
        origin = origin,
        sector = sector,
        actionCount = actionCount,
        itemSizePx = itemSizePx,
        centerDistance = centerDistance,
        spacing = spacing,
        maxAngleDegrees = maxAngleDegrees,
    )
    val layer = plan.layerForIndex(index)
    val indexInLayer = plan.indexInLayer(index)
    val countInLayer = plan.counts[layer]
    val radius = plan.radii[layer]
    val range = plan.ranges[layer]
    val normalizedCenter = ((sector.centerDegrees % 360f) + 360f) % 360f
    val radiansCenter = Math.toRadians(normalizedCenter.toDouble())
    val horizontal = kotlin.math.abs(kotlin.math.cos(radiansCenter)) >= kotlin.math.abs(kotlin.math.sin(radiansCenter))
    val leftSide = horizontal && kotlin.math.cos(radiansCenter) < 0f
    val angleDegree = if (countInLayer == 1) {
        sector.centerDegrees
    } else if (leftSide) {
        // 水平镜像：左侧从上到下排布，但不改变配置索引的内外层顺序。
        range.endDegrees - range.sweepDegrees * indexInLayer / (countInLayer - 1)
    } else {
        range.startDegrees + range.sweepDegrees * indexInLayer / (countInLayer - 1)
    }
    val radians = Math.toRadians(angleDegree.toDouble())
    val targetCenter = Offset(
        x = origin.x + cos(radians).toFloat() * radius,
        y = origin.y + sin(radians).toFloat() * radius,
    ).coerceInside(parentSize, itemSizePx)
    return targetCenter - origin
}

private data class AdaptiveLayerPlan(
    val counts: List<Int>,
    val radii: List<Float>,
    val ranges: List<ActionPanelArcRange>,
) {
    fun layerForIndex(index: Int): Int {
        var start = 0
        counts.forEachIndexed { layer, count ->
            if (index < start + count) return layer
            start += count
        }
        return counts.lastIndex
    }

    fun indexInLayer(index: Int): Int {
        var start = 0
        counts.forEach { count ->
            if (index < start + count) return index - start
            start += count
        }
        return 0
    }
}

private data class ActionPanelArcRange(
    val startDegrees: Float,
    val endDegrees: Float,
) {
    val sweepDegrees: Float get() = (endDegrees - startDegrees).coerceIn(0f, 360f)
}

private fun adaptiveLayerPlan(
    parentSize: Size,
    origin: Offset,
    sector: ActionPanelSector,
    actionCount: Int,
    itemSizePx: Float,
    centerDistance: Float,
    spacing: Float,
    maxAngleDegrees: Float,
): AdaptiveLayerPlan {
    val radiusFloor = itemSizePx * 1.35f * spacing
    val capacities = mutableListOf<Int>()
    val radii = mutableListOf<Float>()
    val ranges = mutableListOf<ActionPanelArcRange>()
    var capacityTotal = 0
    var layer = 0
    while (capacityTotal < actionCount && layer < 8) {
        val radius = radiusFloor + layer * centerDistance
        val range = actionPanelAvailableArcRange(
            parentSize = parentSize,
            origin = origin,
            centerDegrees = sector.centerDegrees,
            radius = radius,
            itemSizePx = itemSizePx,
            maxAngleDegrees = maxAngleDegrees,
        )
        val capacity = arcLayerCapacity(
            radius = radius,
            centerDistance = centerDistance,
            arcLength = range.sweepDegrees.toInt(),
        )
        capacities += capacity
        radii += radius
        ranges += range
        capacityTotal += capacity
        layer++
    }

    var layerCount = 1
    var total = capacities.firstOrNull() ?: 1
    while (total < actionCount && layerCount < capacities.size) {
        total += capacities[layerCount]
        layerCount++
    }
    val selectedCapacities = capacities.take(layerCount)
    val selectedRadii = radii.take(layerCount)
    val counts = MutableList(layerCount) { 0 }
    var remaining = actionCount
    selectedCapacities.forEachIndexed { index, capacity ->
        // 严格先填满当前层的实际可用容量，再开启下一层；不再为了均衡而提前外扩。
        val count = min(capacity, remaining).coerceAtLeast(1)
        counts[index] = count
        remaining -= count
    }
    return AdaptiveLayerPlan(counts, selectedRadii, ranges.take(layerCount))
}

private fun actionPanelAvailableArcRange(
    parentSize: Size,
    origin: Offset,
    centerDegrees: Float,
    radius: Float,
    itemSizePx: Float,
    maxAngleDegrees: Float,
): ActionPanelArcRange {
    val maximumSweep = maxAngleDegrees.coerceIn(90f, 360f)
    if (parentSize.isEmpty()) {
        return ActionPanelArcRange(
            centerDegrees - maximumSweep / 2f,
            centerDegrees + maximumSweep / 2f,
        )
    }
    val positive = availableAngleSide(parentSize, origin, centerDegrees, radius, itemSizePx, 1f)
    val negative = availableAngleSide(parentSize, origin, centerDegrees, radius, itemSizePx, -1f)
    val availableTotal = (positive + negative).coerceAtLeast(0.001f)
    val availableSweep = min(positive + negative, maximumSweep)
    val negativeShare = negative / availableTotal
    return ActionPanelArcRange(
        startDegrees = centerDegrees - availableSweep * negativeShare,
        endDegrees = centerDegrees + availableSweep * (1f - negativeShare),
    )
}

private fun availableAngleSide(
    parentSize: Size,
    origin: Offset,
    centerDegrees: Float,
    radius: Float,
    itemSizePx: Float,
    direction: Float,
): Float {
    fun fits(offset: Float): Boolean {
        val radians = Math.toRadians((centerDegrees + direction * offset).toDouble())
        val x = origin.x + cos(radians).toFloat() * radius
        val y = origin.y + sin(radians).toFloat() * radius
        val padding = itemSizePx / 2f
        return x in padding..(parentSize.width - padding) &&
            y in padding..(parentSize.height - padding)
    }
    if (fits(180f)) return 180f
    var low = 0f
    var high = 180f
    repeat(18) {
        val middle = (low + high) / 2f
        if (fits(middle)) low = middle else high = middle
    }
    return low
}

internal fun actionPanelContentBounds(
    parentSize: Size,
    origin: Offset,
    sector: ActionPanelSector,
    actionCount: Int,
    itemSizePx: Float,
    spreadSpacing: Float,
    itemGapPx: Float,
    maxAngleDegrees: Float = 180f,
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
            itemGapPx = itemGapPx,
            maxAngleDegrees = maxAngleDegrees,
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
    // 左右触发时保持配置顺序，只镜像几何位置；否则左侧会把内外层顺序反转。
    // 上下触发保留原有的层次方向。
    val radians = Math.toRadians(normalizedCenter.toDouble())
    val horizontal = kotlin.math.abs(kotlin.math.cos(radians)) >= kotlin.math.abs(kotlin.math.sin(radians))
    val reverse = !horizontal
    return if (reverse) actionCount - 1 - index else index
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
