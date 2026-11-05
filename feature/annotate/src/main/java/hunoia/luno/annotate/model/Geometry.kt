package hunoia.luno.annotate.model

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/** 图片像素坐标系里的一个点。整个标注子系统只认这一套坐标，不存屏幕坐标。 */
data class Pt(val x: Float, val y: Float) {
    operator fun plus(other: Pt) = Pt(x + other.x, y + other.y)
    operator fun minus(other: Pt) = Pt(x - other.x, y - other.y)
}

/**
 * 图片像素坐标系里的矩形。构造时允许反向拖动（right < left），
 * 使用前统一走 [normalized]，避免每个调用点各写一遍 swap。
 */
data class PxRect(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top

    val center: Pt get() = Pt((left + right) / 2f, (top + bottom) / 2f)

    val isReversed: Boolean get() = right < left || bottom < top

    fun normalized(): PxRect = PxRect(
        left = min(left, right),
        top = min(top, bottom),
        right = max(left, right),
        bottom = max(top, bottom),
    )

    /** 裁到 [0,width]×[0,height] 内；完全出界时返回退化矩形（宽高为 0）。 */
    fun clampTo(width: Float, height: Float): PxRect {
        val n = normalized()
        val l = n.left.coerceIn(0f, width)
        val t = n.top.coerceIn(0f, height)
        val r = n.right.coerceIn(0f, width)
        val b = n.bottom.coerceIn(0f, height)
        return PxRect(l, t, r, b)
    }

    /** 选区小于 [minSize] 视为「没有选区」，导出全图。 */
    fun isEmpty(minSize: Float = MIN_SELECTION_PX): Boolean =
        width < minSize || height < minSize

    fun contains(pt: Pt): Boolean =
        pt.x in left..right && pt.y in top..bottom

    fun inset(dx: Float, dy: Float = dx): PxRect =
        PxRect(left + dx, top + dy, right - dx, bottom - dy)

    /** 两矩形并集（用于图层/选中态合成）。 */
    fun union(other: PxRect): PxRect = PxRect(
        left = minOf(left, other.left),
        top = minOf(top, other.top),
        right = maxOf(right, other.right),
        bottom = maxOf(bottom, other.bottom),
    )

    companion object {
        /** 小于这个像素尺寸的拖动不当作选区（防误触，同计划 §8）。 */
        const val MIN_SELECTION_PX = 8f

        fun fromPoints(a: Pt, b: Pt): PxRect =
            PxRect(min(a.x, b.x), min(a.y, b.y), max(a.x, b.x), max(a.y, b.y))

        fun full(width: Int, height: Int): PxRect =
            PxRect(0f, 0f, width.toFloat(), height.toFloat())

        /** 以 [center] 为中心、边长 [size] 的正方形（马赛克单元 / 命中测试用）。 */
        fun squareAround(center: Pt, size: Float): PxRect {
            val half = abs(size) / 2f
            return PxRect(center.x - half, center.y - half, center.x + half, center.y + half)
        }
    }
}

/** 绕 [center] 旋转 [angle] 弧度（图片像素坐标，正角为顺时针屏幕方向）。 */
fun rotateAround(p: Pt, center: Pt, angle: Float): Pt {
    if (angle == 0f) return p
    val c = cos(angle)
    val s = sin(angle)
    val dx = p.x - center.x
    val dy = p.y - center.y
    return Pt(center.x + dx * c - dy * s, center.y + dx * s + dy * c)
}

/** 线段 [a]→[b] 是否与矩形 [r] 相交（任一端点在内，或与任一边相交）。 */
fun segmentIntersectsRect(a: Pt, b: Pt, r: PxRect): Boolean {
    val rect = r.normalized()
    if (rect.contains(a) || rect.contains(b)) return true
    val tl = Pt(rect.left, rect.top)
    val tr = Pt(rect.right, rect.top)
    val br = Pt(rect.right, rect.bottom)
    val bl = Pt(rect.left, rect.bottom)
    return segmentsIntersect(a, b, tl, tr) ||
        segmentsIntersect(a, b, tr, br) ||
        segmentsIntersect(a, b, br, bl) ||
        segmentsIntersect(a, b, bl, tl)
}

/** 两线段是否相交（方向测试 + 跨立判断；共线重叠按端点落在另一线段上算）。 */
fun segmentsIntersect(p1: Pt, p2: Pt, p3: Pt, p4: Pt): Boolean {
    fun orient(a: Pt, b: Pt, c: Pt): Float =
        (b.x - a.x) * (c.y - a.y) - (b.y - a.y) * (c.x - a.x)

    fun onSegment(a: Pt, b: Pt, c: Pt): Boolean =
        c.x in min(a.x, b.x)..max(a.x, b.x) && c.y in min(a.y, b.y)..max(a.y, b.y)

    val o1 = orient(p1, p2, p3)
    val o2 = orient(p1, p2, p4)
    val o3 = orient(p3, p4, p1)
    val o4 = orient(p3, p4, p2)
    if (o1 * o2 < 0f && o3 * o4 < 0f) return true
    if (o1 == 0f && onSegment(p1, p2, p3)) return true
    if (o2 == 0f && onSegment(p1, p2, p4)) return true
    if (o3 == 0f && onSegment(p3, p4, p1)) return true
    if (o4 == 0f && onSegment(p3, p4, p2)) return true
    return false
}

/**
 * 点是否在闭合多边形内（偶-奇规则；凹多边形如五角星/心形也正确）。
 * [pts] 为多边形顶点，首尾不要求闭合。
 */
fun pointInPolygon(p: Pt, pts: List<Pt>): Boolean {
    if (pts.size < 3) return false
    var inside = false
    var j = pts.size - 1
    for (i in pts.indices) {
        val a = pts[i]
        val b = pts[j]
        if ((a.y > p.y) != (b.y > p.y)) {
            val xCross = a.x + (p.y - a.y) * (b.x - a.x) / (b.y - a.y)
            if (p.x < xCross) inside = !inside
        }
        j = i
    }
    return inside
}

/**
 * 点 [p] 是否落在以 [radius] 为半径的线段 a→b 邻域内（单点橡皮的命中测试）。
 * a == b 时退化为「到点的距离」，正好覆盖单点笔迹。
 */
fun pointNearSegment(p: Pt, a: Pt, b: Pt, radius: Float): Boolean {
    val dx = b.x - a.x
    val dy = b.y - a.y
    val len2 = dx * dx + dy * dy
    val t = if (len2 <= 0f) 0f else ((p.x - a.x) * dx + (p.y - a.y) * dy) / len2
    val clamped = t.coerceIn(0f, 1f)
    val nearX = p.x - (a.x + dx * clamped)
    val nearY = p.y - (a.y + dy * clamped)
    return nearX * nearX + nearY * nearY <= radius * radius
}

/**
 * contain 映射：图片完整显示、等比缩放、居中。
 *
 * 预览与导出共用同一个模型，区别只是导出时 view 尺寸 == 图片尺寸（scale 恒为 1）。
 */
data class ContainFit(
    val imageWidth: Float,
    val imageHeight: Float,
    val viewWidth: Float,
    val viewHeight: Float,
    val scale: Float,
    val offsetX: Float,
    val offsetY: Float,
) {
    val drawnWidth: Float get() = imageWidth * scale
    val drawnHeight: Float get() = imageHeight * scale

    /** 图片像素 → 视图坐标。 */
    fun imageToView(pt: Pt): Pt =
        Pt(pt.x * scale + offsetX, pt.y * scale + offsetY)

    /** 视图坐标 → 图片像素。视图外的点会落到图片外（负值 / 超界），调用方自行 clamp。 */
    fun viewToImage(pt: Pt): Pt =
        Pt((pt.x - offsetX) / scale, (pt.y - offsetY) / scale)

    fun imageRectToView(rect: PxRect): PxRect {
        val a = imageToView(Pt(rect.left, rect.top))
        val b = imageToView(Pt(rect.right, rect.bottom))
        return PxRect(a.x, a.y, b.x, b.y)
    }

    fun viewRectToImage(rect: PxRect): PxRect {
        val a = viewToImage(Pt(rect.left, rect.top))
        val b = viewToImage(Pt(rect.right, rect.bottom))
        return PxRect(a.x, a.y, b.x, b.y)
    }

    /**
     * 叠加双指视图变换（B7）：先按 contain 居中，再乘 [zoom]，最后加视图平移 [pan]。
     * [pan] 是相对居中位置的偏移，调用方先用 [clampPan] 夹取。
     */
    fun withViewTransform(zoom: Float, pan: Pt): ContainFit {
        val z = zoom.coerceAtLeast(0.01f)
        val s = scale * z
        val dw = imageWidth * s
        val dh = imageHeight * s
        return copy(
            scale = s,
            offsetX = (viewWidth - dw) / 2f + pan.x,
            offsetY = (viewHeight - dh) / 2f + pan.y,
        )
    }

    /**
     * 平移夹取：放大后最多移到图片边缘贴住视图边缘；
     * 图片在该轴上仍小于视图时锁定为 0（不允许把整图拖出屏幕）。
     */
    fun clampPan(zoom: Float, pan: Pt): Pt {
        val s = scale * zoom.coerceAtLeast(0.01f)
        val maxX = max(0f, (imageWidth * s - viewWidth) / 2f)
        val maxY = max(0f, (imageHeight * s - viewHeight) / 2f)
        return Pt(
            x = pan.x.coerceIn(-maxX, maxX),
            y = pan.y.coerceIn(-maxY, maxY),
        )
    }

    companion object {
        fun of(imageWidth: Float, imageHeight: Float, viewWidth: Float, viewHeight: Float): ContainFit {
            require(imageWidth > 0f && imageHeight > 0f) { "image size must be positive" }
            require(viewWidth > 0f && viewHeight > 0f) { "view size must be positive" }
            val scale = min(viewWidth / imageWidth, viewHeight / imageHeight)
            val drawnW = imageWidth * scale
            val drawnH = imageHeight * scale
            return ContainFit(
                imageWidth = imageWidth,
                imageHeight = imageHeight,
                viewWidth = viewWidth,
                viewHeight = viewHeight,
                scale = scale,
                offsetX = (viewWidth - drawnW) / 2f,
                offsetY = (viewHeight - drawnH) / 2f,
            )
        }

        /** 导出场景：1:1，无偏移。 */
        fun identity(width: Int, height: Int): ContainFit =
            ContainFit(
                imageWidth = width.toFloat(),
                imageHeight = height.toFloat(),
                viewWidth = width.toFloat(),
                viewHeight = height.toFloat(),
                scale = 1f,
                offsetX = 0f,
                offsetY = 0f,
            )
    }
}
