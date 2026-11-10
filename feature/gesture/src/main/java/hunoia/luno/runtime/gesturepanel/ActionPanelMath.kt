package hunoia.luno.runtime.gesturepanel

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import hunoia.luno.config.model.ArcStyle
import hunoia.luno.config.model.coerced
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/** 扇菜单的四个边距（px）：与网格面板/快捷启动的屏幕让位口径一致。 */
internal data class ActionPanelInsets(
    val leftPx: Float = 0f,
    val topPx: Float = 0f,
    val rightPx: Float = 0f,
    val bottomPx: Float = 0f,
)

/** 样式里的 dp 边距 → 当前密度下的 px；越界值先夹到 0–60dp。 */
internal fun ArcStyle.panelInsets(density: Float): ActionPanelInsets {
    val coerced = placement.coerced()
    return ActionPanelInsets(
        leftPx = coerced.sideMarginDp * density,
        topPx = coerced.topGapDp * density,
        rightPx = coerced.sideMarginDp * density,
        bottomPx = coerced.bottomGapDp * density,
    )
}

/** 夹取区间上下界倒挂时（图标 + 边距比屏幕还大）退化为区间中点，避免把扇心推出屏外。 */
private fun safeRange(minimumValue: Float, maximumValue: Float): Pair<Float, Float> {
    if (minimumValue <= maximumValue) return minimumValue to maximumValue
    val middle = (minimumValue + maximumValue) / 2f
    return middle to middle
}

internal fun actionPanelOrigin(
    parentSize: Size,
    origin: Offset,
    itemSizePx: Float,
    insets: ActionPanelInsets = ActionPanelInsets(),
): Offset {
    if (parentSize.isEmpty()) return origin
    val safePadding = itemSizePx * 0.55f
    val (xMin, xMax) = safeRange(
        insets.leftPx + safePadding,
        parentSize.width - insets.rightPx - safePadding,
    )
    val (yMin, yMax) = safeRange(
        insets.topPx + safePadding,
        parentSize.height - insets.bottomPx - safePadding,
    )
    return Offset(
        x = origin.x.coerceIn(xMin, xMax),
        y = origin.y.coerceIn(yMin, yMax),
    )
}

internal fun Offset.coerceInside(
    parentSize: Size,
    itemSizePx: Float,
    insets: ActionPanelInsets = ActionPanelInsets(),
): Offset {
    val padding = itemSizePx / 2f
    val (xMin, xMax) = safeRange(
        padding + insets.leftPx,
        parentSize.width - padding - insets.rightPx,
    )
    val (yMin, yMax) = safeRange(
        padding + insets.topPx,
        parentSize.height - padding - insets.bottomPx,
    )
    return Offset(
        x = x.coerceIn(xMin, xMax),
        y = y.coerceIn(yMin, yMax),
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

/**
 * 弧接近整圆时为「闭合环」：首尾两项的角距不足一步，若仍按
 * `floor(arc/step)+1` 容量 + `sweep/(count-1)` 分布，首尾会落在同一位置
 * （360° 完全重合，355° 首尾中心距只剩十几 px）。
 */
internal fun isClosedArc(sweepDegrees: Float, stepDegrees: Float): Boolean =
    sweepDegrees > 0f && stepDegrees > 0f && 360f - sweepDegrees < stepDegrees

/**
 * 半径 [radius] 的弧上，在 [arcLength] 度内按 [centerDistance] 中心距最多能放几个。
 * 先取整再决定 `+1`：可用弧不足一步时只有 1 个位置，不能先 `coerceAtLeast(1)` 再加，
 * 否则会声称能放 2 个（这正是加边距后图标部分重叠的来源）。
 */
internal fun arcLayerCapacity(radius: Float, centerDistance: Float, arcLength: Float): Int {
    if (radius <= 0f || arcLength <= 0f) return 0
    val step = arcStepDegrees(radius, centerDistance)
    if (step <= 0f) return 1
    val fit = kotlin.math.floor(arcLength / step).toInt()
    // 闭合环不重复计入首尾同一个角度。
    return if (isClosedArc(arcLength, step)) fit.coerceAtLeast(1) else fit + 1
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
    insets: ActionPanelInsets = ActionPanelInsets(),
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
        insets = insets,
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
    val closed = isClosedArc(range.sweepDegrees, arcStepDegrees(radius, centerDistance))
    val angleDegree = when {
        // 只有一项时落在可用弧中点：中心方向放不下会先平移弧，仍用原中心会被 clamp 回屏边。
        countInLayer == 1 -> range.startDegrees + range.sweepDegrees / 2f
        // 闭合环按整圈均分，除 count；否则用两端点，除 count-1。
        closed -> range.startDegrees + range.sweepDegrees * indexInLayer / countInLayer
        // 水平镜像：左侧从上到下排布，但不改变配置索引的内外层顺序。
        leftSide -> range.endDegrees - range.sweepDegrees * indexInLayer / (countInLayer - 1)
        else -> range.startDegrees + range.sweepDegrees * indexInLayer / (countInLayer - 1)
    }
    val radians = Math.toRadians(angleDegree.toDouble())
    val targetCenter = Offset(
        x = origin.x + cos(radians).toFloat() * radius,
        y = origin.y + sin(radians).toFloat() * radius,
    ).coerceInside(parentSize, itemSizePx, insets)
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
    insets: ActionPanelInsets,
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
            insets = insets,
        )
        val capacity = arcLayerCapacity(
            radius = radius,
            centerDistance = centerDistance,
            arcLength = range.sweepDegrees,
        )
        // 该半径完全放不下（圆弧已在屏幕外）：停止外扩，避免把剩余图标堆到同一被夹取点。
        if (capacity <= 0) break
        capacities += capacity
        radii += radius
        ranges += range
        capacityTotal += capacity
        layer++
    }

    if (capacities.isEmpty()) {
        // 极端情况（屏幕/方向完全放不下）：退回一层堆叠，至少不漏动作、不越界崩溃。
        capacities += actionCount
        radii += radiusFloor
        ranges += ActionPanelArcRange(sector.centerDegrees, sector.centerDegrees)
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
    // 层数用尽仍放不下时把余量压到最后一层（会重叠，但只发生在极端数量/极小屏）。
    if (remaining > 0) counts[counts.lastIndex] += remaining
    return AdaptiveLayerPlan(counts, selectedRadii, ranges.take(layerCount))
}

private fun actionPanelAvailableArcRange(
    parentSize: Size,
    origin: Offset,
    centerDegrees: Float,
    radius: Float,
    itemSizePx: Float,
    maxAngleDegrees: Float,
    insets: ActionPanelInsets,
): ActionPanelArcRange {
    val maximumSweep = maxAngleDegrees.coerceIn(90f, 360f)
    if (parentSize.isEmpty()) {
        return ActionPanelArcRange(
            centerDegrees - maximumSweep / 2f,
            centerDegrees + maximumSweep / 2f,
        )
    }
    // 请求的方向在该半径上放不下（比如朝屏幕边缘展开、又加了边距）时，平移到最近的可用方向。
    // 不平移就会得到 0 宽度弧，图标被 clamp 回同一点，表现为叠在一起。
    val shift = nearestFittingOffset(parentSize, origin, centerDegrees, radius, itemSizePx, insets)
        ?: return ActionPanelArcRange(centerDegrees, centerDegrees)
    val effectiveCenter = centerDegrees + shift
    val positive = availableAngleSide(parentSize, origin, effectiveCenter, radius, itemSizePx, insets, 1f)
    val negative = availableAngleSide(parentSize, origin, effectiveCenter, radius, itemSizePx, insets, -1f)
    val availableTotal = (positive + negative).coerceAtLeast(0.001f)
    val availableSweep = min(positive + negative, maximumSweep)
    val negativeShare = negative / availableTotal
    return ActionPanelArcRange(
        startDegrees = effectiveCenter - availableSweep * negativeShare,
        endDegrees = effectiveCenter + availableSweep * (1f - negativeShare),
    )
}

/** 该半径、该方向上的点是否仍在「图标半径 + 边距」后的安全区内。 */
private fun panelFits(
    parentSize: Size,
    origin: Offset,
    centerDegrees: Float,
    radius: Float,
    itemSizePx: Float,
    insets: ActionPanelInsets,
    offsetDegrees: Float,
): Boolean {
    val padding = itemSizePx / 2f
    val (xMin, xMax) = safeRange(padding + insets.leftPx, parentSize.width - padding - insets.rightPx)
    val (yMin, yMax) = safeRange(padding + insets.topPx, parentSize.height - padding - insets.bottomPx)
    val radians = Math.toRadians((centerDegrees + offsetDegrees).toDouble())
    val x = origin.x + cos(radians).toFloat() * radius
    val y = origin.y + sin(radians).toFloat() * radius
    return x in xMin..xMax && y in yMin..yMax
}

/** 中心方向放不下时，从近到远找最近的可放方向（带符号，度）；整圈都放不下返回 null。 */
private fun nearestFittingOffset(
    parentSize: Size,
    origin: Offset,
    centerDegrees: Float,
    radius: Float,
    itemSizePx: Float,
    insets: ActionPanelInsets,
): Float? {
    if (panelFits(parentSize, origin, centerDegrees, radius, itemSizePx, insets, 0f)) return 0f
    for (delta in 1..180) {
        if (panelFits(parentSize, origin, centerDegrees, radius, itemSizePx, insets, -delta.toFloat())) {
            return -delta.toFloat()
        }
        if (panelFits(parentSize, origin, centerDegrees, radius, itemSizePx, insets, delta.toFloat())) {
            return delta.toFloat()
        }
    }
    return null
}

/** 弧从中心往外扫的采样步长：太小浪费算力，太大可能漏掉窄的可放区间。 */
private const val ArcProbeStepDegrees = 5f

private fun availableAngleSide(
    parentSize: Size,
    origin: Offset,
    centerDegrees: Float,
    radius: Float,
    itemSizePx: Float,
    insets: ActionPanelInsets,
    direction: Float,
): Float {
    if (!panelFits(parentSize, origin, centerDegrees, radius, itemSizePx, insets, 0f)) return 0f
    // 不能只测 180° 后二分：靠近屏幕边缘时圆弧会「穿出又穿回来」，180° 能放不代表中间都能放。
    // 按 5° 扫出第一个不 fit 的区间，再在区间内二分收敛。
    var lastFit = 0f
    var firstFail = -1f
    var probe = ArcProbeStepDegrees
    while (probe <= 180f) {
        if (panelFits(parentSize, origin, centerDegrees, radius, itemSizePx, insets, direction * probe)) {
            lastFit = probe
            probe += ArcProbeStepDegrees
        } else {
            firstFail = probe
            break
        }
    }
    if (firstFail < 0f) return 180f
    var low = lastFit
    var high = firstFail
    repeat(14) {
        val middle = (low + high) / 2f
        if (panelFits(parentSize, origin, centerDegrees, radius, itemSizePx, insets, direction * middle)) {
            low = middle
        } else {
            high = middle
        }
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
    insets: ActionPanelInsets = ActionPanelInsets(),
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
            insets = insets,
        )
        left = min(left, center.x - halfItem)
        top = min(top, center.y - halfItem)
        right = max(right, center.x + halfItem)
        bottom = max(bottom, center.y + halfItem)
    }
    return Rect(left, top, right, bottom)
}

internal fun actionPanelPillPosition(
    parentSize: Size,
    actionBounds: Rect,
    itemSizePx: Float,
    insets: ActionPanelInsets = ActionPanelInsets(),
): Offset {
    val pillHeight = itemSizePx * 1.35f
    val margin = itemSizePx * 0.55f
    val gap = itemSizePx * 0.45f
    val (topY, bottomY) = safeRange(
        margin + insets.topPx,
        parentSize.height - pillHeight - margin - insets.bottomPx,
    )
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
