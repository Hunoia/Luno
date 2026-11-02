package hunoia.luno.gridpanel.geometry

/** 面板/项缩放手柄所在的角。 */
enum class ResizeCorner {
    TOP_START,
    TOP_END,
    BOTTOM_START,
    BOTTOM_END,
}

val ResizeCorner.isLeft: Boolean
    get() = this == ResizeCorner.TOP_START || this == ResizeCorner.BOTTOM_START

val ResizeCorner.isTop: Boolean
    get() = this == ResizeCorner.TOP_START || this == ResizeCorner.TOP_END

/**
 * 选整块面板的缩放手柄角：取「离相邻两条屏边都最远」的角。
 *
 * 面板贴右侧和底部时，左上角两侧余量最大 → 手柄落在左上；
 * 贴左/上时自然落到右下。评分取两个余量的较小值，避免一边很远、
 * 另一边已经贴边仍被选中。
 */
fun choosePanelResizeCorner(
    cardLeft: Float,
    cardTop: Float,
    cardWidth: Float,
    cardHeight: Float,
    screenWidth: Float,
    screenHeight: Float,
    insetsTop: Int,
    insetsBottom: Int,
    marginPx: Float,
): ResizeCorner {
    val leftBound = marginPx
    val rightBound = (screenWidth - marginPx).coerceAtLeast(leftBound)
    val topBound = (insetsTop + marginPx).coerceAtLeast(0f)
    val bottomBound = (screenHeight - insetsBottom - marginPx).coerceAtLeast(topBound)
    val leftGap = (cardLeft - leftBound).coerceAtLeast(0f)
    val rightGap = (rightBound - (cardLeft + cardWidth)).coerceAtLeast(0f)
    val topGap = (cardTop - topBound).coerceAtLeast(0f)
    val bottomGap = (bottomBound - (cardTop + cardHeight)).coerceAtLeast(0f)

    fun score(horizontal: Float, vertical: Float): Float = minOf(horizontal, vertical)

    return listOf(
        ResizeCorner.TOP_START to score(leftGap, topGap),
        ResizeCorner.TOP_END to score(rightGap, topGap),
        ResizeCorner.BOTTOM_START to score(leftGap, bottomGap),
        ResizeCorner.BOTTOM_END to score(rightGap, bottomGap),
    ).maxByOrNull { it.second }?.first ?: ResizeCorner.BOTTOM_END
}
