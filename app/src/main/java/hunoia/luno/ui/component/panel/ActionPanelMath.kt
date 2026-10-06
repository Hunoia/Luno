package hunoia.luno.ui.component.panel

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import hunoia.luno.config.model.GestureDirection
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
    itemGapPx: Float,
): Offset {
    if (parentSize.isEmpty() || actionCount <= 0) return Offset.Zero
    val spacing = spreadSpacing.coerceIn(0.85f, 1.6f)
    val gap = itemGapPx.coerceAtLeast(0f)
    // 中心距 = 图标直径 + 净间距。角步长由它反推，所以 spreadSpacing（只改半径）
    // 不再像以前那样和步长互相抵消 —— 净间距完全由 itemGapPx 决定。
    val centerDistance = itemSizePx + gap
    val baseRadius = itemSizePx * when (actionCount) {
        1 -> 1.55f
        2 -> 1.75f
        3 -> 1.95f
        else -> 2.15f
    } * spacing
    val maxItemsPerLayer = arcLayerCapacity(
        radius = baseRadius,
        centerDistance = centerDistance,
        arcLength = sector.sweepDegrees.toInt(),
    )
    // 按占用来分层：每层先铺满 maxItemsPerLayer（由半径/净间距/扇区弧长决定）再往外扩一层。
    // 不用 ceil(count/layerCount) 均分 —— 那会让内层没铺满就把外层开出来。
    val layer = index / maxItemsPerLayer
    val indexInLayer = index % maxItemsPerLayer
    val firstIndexInLayer = layer * maxItemsPerLayer
    val countInLayer = min(maxItemsPerLayer, actionCount - firstIndexInLayer).coerceAtLeast(1)
    // 层间距与层内角间距用同一个净间距，避免两层贴在一起
    val radius = baseRadius + layer * centerDistance
    val arcDegrees = sector.sweepDegrees.coerceIn(90f, 180f)
    val stepDegree = arcStepDegrees(radius, centerDistance)
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
    itemGapPx: Float,
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
    GestureDirection.Down -> 90f
    GestureDirection.Left -> 180f
    GestureDirection.Up -> 270f
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
