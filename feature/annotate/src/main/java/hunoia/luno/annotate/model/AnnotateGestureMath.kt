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

/** 元素选中柄：四角（双向缩放）、四边中点（单向缩放）、旋转柄。 */
internal enum class ElementHandle {
    TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT,
    TOP, RIGHT, BOTTOM, LEFT,
    ROTATE,
}

/**
 * 旋转元素的有向选中几何（图片像素坐标）。
 * [corners] 顺序 = TL, TR, BR, BL；[edges] 顺序 = TOP, RIGHT, BOTTOM, LEFT；
 * [rotate] 在顶边中点外法线方向 [rotateGap] 处。
 */
internal data class OrientedGeometry(
    val center: Pt,
    val rot: Float,
    val corners: List<Pt>,
    val edges: List<Pt>,
    val rotate: Pt,
)

/** 由未旋转包围盒 [box] + 旋转角 [rot] 生成有向几何。 */
internal fun orientedGeometry(box: PxRect, rot: Float, rotateGap: Float): OrientedGeometry {
    val c = box.center
    fun r(p: Pt) = rotateAround(p, c, rot)
    return OrientedGeometry(
        center = c,
        rot = rot,
        corners = listOf(
            r(Pt(box.left, box.top)), r(Pt(box.right, box.top)),
            r(Pt(box.right, box.bottom)), r(Pt(box.left, box.bottom)),
        ),
        edges = listOf(
            r(Pt(c.x, box.top)), r(Pt(box.right, c.y)),
            r(Pt(c.x, box.bottom)), r(Pt(box.left, c.y)),
        ),
        rotate = r(Pt(c.x, box.top - rotateGap)),
    )
}

/** 视图坐标点命中的选中柄（角 > 边 > 旋转），未命中返回 null。 */
internal fun hitElementHandle(
    geom: OrientedGeometry,
    fit: ContainFit,
    viewPoint: Pt,
    threshold: Float,
): ElementHandle? {
    fun hit(p: Pt): Boolean {
        val v = fit.imageToView(p)
        return abs(v.x - viewPoint.x) <= threshold && abs(v.y - viewPoint.y) <= threshold
    }
    val corners = listOf(
        ElementHandle.TOP_LEFT, ElementHandle.TOP_RIGHT,
        ElementHandle.BOTTOM_LEFT, ElementHandle.BOTTOM_RIGHT,
    )
    corners.forEachIndexed { i, h -> if (hit(geom.corners[i])) return h }
    val edges = listOf(
        ElementHandle.TOP, ElementHandle.RIGHT,
        ElementHandle.BOTTOM, ElementHandle.LEFT,
    )
    edges.forEachIndexed { i, h -> if (hit(geom.edges[i])) return h }
    if (hit(geom.rotate)) return ElementHandle.ROTATE
    return null
}

/** 按角/边柄把 [box] 调整到 [p]（[p] 已转进局部未旋转坐标系）。 */
internal fun adjustByHandle(box: PxRect, handle: ElementHandle, p: Pt): PxRect = when (handle) {
    ElementHandle.TOP_LEFT -> PxRect(p.x, p.y, box.right, box.bottom)
    ElementHandle.TOP_RIGHT -> PxRect(box.left, p.y, p.x, box.bottom)
    ElementHandle.BOTTOM_LEFT -> PxRect(p.x, box.top, box.right, p.y)
    ElementHandle.BOTTOM_RIGHT -> PxRect(box.left, box.top, p.x, p.y)
    ElementHandle.TOP -> PxRect(box.left, p.y, box.right, box.bottom)
    ElementHandle.BOTTOM -> PxRect(box.left, box.top, box.right, p.y)
    ElementHandle.LEFT -> PxRect(p.x, box.top, box.right, box.bottom)
    ElementHandle.RIGHT -> PxRect(box.left, box.top, p.x, box.bottom)
    ElementHandle.ROTATE -> box
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
