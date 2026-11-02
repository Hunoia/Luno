package hunoia.luno.gridpanel.geometry

import androidx.compose.ui.geometry.Offset
import hunoia.luno.config.model.GridPanelDefaults

/** 面板缩放的解析结果：新的卡宽高比例 + 面板左上角位置（锚点固定后派生）。 */
data class PanelResizeResult(
    val cardWidthFraction: Float,
    val cardHeightFraction: Float,
    val left: Float,
    val top: Float,
)

/**
 * 面板缩放的锚点固定解析（纯函数）：
 * 对侧角固定，手柄角随拖拽移动。尺寸夹在
 * [minWidthFraction, minOf(maxWidthFraction, 锚点边界)]，**只 clamp 尺寸、不 clamp 位置**
 * —— 位置 = 锚点 − 渲染尺寸，锚点永不被 coerceIn 推走。
 *
 * 高度用 [gridPanelCardHeight] 按可见行数量化（渲染是贴齐格子的），不能按连续
 * [newH] 反推：否则渲染锚点 = top + 量化卡高 ≠ 锚点，拖动时对侧角会被行数取整推走。
 * 宽度不量化（cardW = screenW × fraction 精确）。
 */
fun resolvePanelResize(
    corner: ResizeCorner,
    startSize: Offset,
    startPos: Offset,
    total: Offset,
    screenW: Float,
    screenH: Float,
    insetsTop: Int,
    insetsBottom: Int,
    screenMarginPx: Float,
    /** 渲染几何（列数与内边距/指示条/格间距 px）：算量化卡高用。 */
    columns: Int,
    gridPad: Float,
    indicator: Float,
    gap: Float,
    minWidthFraction: Float = GridPanelDefaults.MinCardWidthFraction,
    maxWidthFraction: Float = GridPanelDefaults.MaxCardWidthFraction,
    minHeightFraction: Float = GridPanelDefaults.MinCardHeightFraction,
    maxHeightFraction: Float = GridPanelDefaults.MaxCardHeightFraction,
): PanelResizeResult {
    val startW = startSize.x
    val startH = startSize.y
    val widthDelta = if (corner.isLeft) -total.x else total.x
    val heightDelta = if (corner.isTop) -total.y else total.y
    // 锚点 = 对侧角（固定）：面板生长到屏幕边缘即停，锚点不被 clamp 推走。
    val anchorRight = startPos.x + startW
    val anchorBottom = startPos.y + startH
    val maxW = (if (corner.isLeft) {
        anchorRight - screenMarginPx
    } else {
        (screenW - screenMarginPx) - startPos.x
    }).coerceAtLeast(screenW * minWidthFraction)
    val maxH = (if (corner.isTop) {
        anchorBottom - insetsTop - screenMarginPx
    } else {
        (screenH - insetsBottom - screenMarginPx) - startPos.y
    }).coerceAtLeast(screenH * minHeightFraction)
    val newW = (startW + widthDelta).coerceIn(
        screenW * minWidthFraction,
        minOf(screenW * maxWidthFraction, maxW),
    )
    val newH = (startH + heightDelta).coerceIn(
        screenH * minHeightFraction,
        minOf(screenH * maxHeightFraction, maxH),
    )
    // 实际渲染卡高（行数量化）：锚点按渲染尺寸反推，对侧角才真正固定。
    val renderH = gridPanelCardHeight(
        columns = columns,
        cardW = newW,
        cardHeightFraction = newH / screenH,
        screenH = screenH,
        gridPad = gridPad,
        indicator = indicator,
        gap = gap,
    )
    // 位置 = 锚点 − 渲染尺寸：锚点固定，手柄角随拖拽移动。
    val left = if (corner.isLeft) anchorRight - newW else startPos.x
    val top = if (corner.isTop) anchorBottom - renderH else startPos.y
    return PanelResizeResult(
        cardWidthFraction = newW / screenW,
        cardHeightFraction = newH / screenH,
        left = left,
        top = top,
    )
}

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
