package hunoia.luno.annotate.model

import kotlin.math.abs
import kotlin.math.max

/**
 * 画布手势的纯几何逻辑（B5/B6/B9）：与 Compose/UI 解耦，可 JVM 单测。
 *
 * 命中测试的**文字**部分依赖 `android.graphics.Paint` 量字宽，留在 `AnnotateCanvas`
 * 的 `realHitBounds` 里；其余几何全部集中在这里。
 */
internal enum class Corner { TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT }

/** 视图坐标点是否落在 [rect] 的某个角柄阈值邻域内。 */
internal fun cornerAt(
    rect: PxRect,
    fit: ContainFit,
    viewPoint: Pt,
    threshold: Float,
): Corner? {
    val view = fit.imageRectToView(rect.normalized())
    val corners = listOf(
        Corner.TOP_LEFT to Pt(view.left, view.top),
        Corner.TOP_RIGHT to Pt(view.right, view.top),
        Corner.BOTTOM_LEFT to Pt(view.left, view.bottom),
        Corner.BOTTOM_RIGHT to Pt(view.right, view.bottom),
    )
    return corners.firstOrNull { (_, p) ->
        abs(p.x - viewPoint.x) <= threshold && abs(p.y - viewPoint.y) <= threshold
    }?.first
}

internal fun adjustCorner(rect: PxRect, corner: Corner, p: Pt): PxRect = when (corner) {
    Corner.TOP_LEFT -> PxRect(p.x, p.y, rect.right, rect.bottom)
    Corner.TOP_RIGHT -> PxRect(rect.left, p.y, p.x, rect.bottom)
    Corner.BOTTOM_LEFT -> PxRect(p.x, rect.top, rect.right, p.y)
    Corner.BOTTOM_RIGHT -> PxRect(rect.left, rect.top, p.x, p.y)
}

/** 视图坐标点是否落在选区内部（选区整体拖动命中）。 */
internal fun selectionHolds(rect: PxRect, fit: ContainFit, viewPoint: Pt): Boolean {
    val view = fit.imageRectToView(rect.normalized())
    return viewPoint.x in view.left..view.right && viewPoint.y in view.top..view.bottom
}

/** 选区整体平移，保持尺寸不缩放：位移按图边夹取。 */
internal fun translateSelection(
    rect: PxRect,
    dx: Float,
    dy: Float,
    imageWidth: Float,
    imageHeight: Float,
): PxRect {
    val clampedDx = dx.coerceIn(-rect.left, (imageWidth - rect.right).coerceAtLeast(-rect.left))
    val clampedDy = dy.coerceIn(-rect.top, (imageHeight - rect.bottom).coerceAtLeast(-rect.top))
    return PxRect(
        left = rect.left + clampedDx,
        top = rect.top + clampedDy,
        right = rect.right + clampedDx,
        bottom = rect.bottom + clampedDy,
    )
}

/**
 * 屏幕 dp 线宽 → 图片像素（B9）。落笔瞬间换算，元素里只存图片像素。
 */
internal fun strokeWidthInImagePx(
    styleDp: Float,
    density: Float,
    fit: ContainFit,
): Float = (styleDp * density / fit.scale).coerceIn(0.5f, 512f)

/**
 * 马赛克涂抹笔刷直径（图片像素）。
 *
 * 不单独给笔刷滑杆：颗粒越大笔刷越粗，视觉上「一把刷过去」的效果稳定；
 * 下限 40 图片像素保证高分辨率截图上也有可见笔触。
 */
internal fun mosaicBrushWidth(cellSize: Float): Float =
    max(cellSize * 5f, 40f).coerceAtMost(256f)
