package hunoia.luno.annotate.model

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

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
