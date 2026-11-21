package hunoia.luno.annotate.model

import kotlin.math.abs

/**
 * 裁剪（CROP 工具）的纯几何：窗口手柄命中 / 调整 / 缩放 / 平移夹取 / 安全区域。
 *
 * 从 `ui/AnnotateCanvas` 下沉到 model 层（审计 P1a）：全部是图片像素与视图像素的
 * 纯计算，不依赖 Compose（坐标入参用 [Pt]），可 JVM 单测。
 * 画布层只负责把 Compose 的 Offset / IntSize 换算成 Pt / 像素宽高再调进来。
 */

/** 裁剪窗口的可拖拽部位：四边 + 四角。 */
internal enum class CropHandle {
    LEFT, RIGHT, TOP, BOTTOM,
    TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT,
}

/** 视图坐标 [point] 命中裁剪窗 [rect] 的哪个手柄（[radius] 为命中半径，视图像素）。 */
internal fun hitCropHandle(rect: PxRect, point: Pt, radius: Float): CropHandle? {
    val x = point.x
    val y = point.y
    fun near(a: Float, b: Float): Boolean = abs(a - b) <= radius
    return when {
        near(x, rect.left) && near(y, rect.top) -> CropHandle.TOP_LEFT
        near(x, rect.right) && near(y, rect.top) -> CropHandle.TOP_RIGHT
        near(x, rect.left) && near(y, rect.bottom) -> CropHandle.BOTTOM_LEFT
        near(x, rect.right) && near(y, rect.bottom) -> CropHandle.BOTTOM_RIGHT
        near(x, rect.left) && y in rect.top..rect.bottom -> CropHandle.LEFT
        near(x, rect.right) && y in rect.top..rect.bottom -> CropHandle.RIGHT
        near(y, rect.top) && x in rect.left..rect.right -> CropHandle.TOP
        near(y, rect.bottom) && x in rect.left..rect.right -> CropHandle.BOTTOM
        else -> null
    }
}

/** 按角/边柄把 [original] 窗口调整到 [point]，夹取在 [bounds] 内且不小于 [minSize]。 */
internal fun adjustCropWindow(
    original: PxRect,
    handle: CropHandle,
    point: Pt,
    bounds: PxRect,
    minSize: Float,
): PxRect {
    var left = original.left
    var top = original.top
    var right = original.right
    var bottom = original.bottom
    val minWidth = minSize.coerceAtMost(bounds.width)
    val minHeight = minSize.coerceAtMost(bounds.height)
    when (handle) {
        CropHandle.LEFT, CropHandle.TOP_LEFT, CropHandle.BOTTOM_LEFT ->
            left = point.x.coerceIn(bounds.left, right - minWidth)
        CropHandle.RIGHT, CropHandle.TOP_RIGHT, CropHandle.BOTTOM_RIGHT ->
            right = point.x.coerceIn(left + minWidth, bounds.right)
        else -> Unit
    }
    when (handle) {
        CropHandle.TOP, CropHandle.TOP_LEFT, CropHandle.TOP_RIGHT ->
            top = point.y.coerceIn(bounds.top, bottom - minHeight)
        CropHandle.BOTTOM, CropHandle.BOTTOM_LEFT, CropHandle.BOTTOM_RIGHT ->
            bottom = point.y.coerceIn(top + minHeight, bounds.bottom)
        else -> Unit
    }
    return PxRect(left, top, right, bottom)
}

/** 按同一比例放大裁剪框，并以 [anchor] 为中心。 */
internal fun scaleCropWindow(rect: PxRect, anchor: Pt, factor: Float): PxRect {
    val width = rect.width * factor
    val height = rect.height * factor
    return PxRect(
        left = anchor.x - width / 2f,
        top = anchor.y - height / 2f,
        right = anchor.x + width / 2f,
        bottom = anchor.y + height / 2f,
    )
}

/** 计算以固定锚点放大后仍落在安全区域内的最大倍数。 */
internal fun maxCropScale(rect: PxRect, anchor: Pt, bounds: PxRect): Float {
    var result = Float.POSITIVE_INFINITY
    if (rect.left < anchor.x) {
        result = minOf(result, (anchor.x - bounds.left) / (anchor.x - rect.left))
    }
    if (rect.right > anchor.x) {
        result = minOf(result, (bounds.right - anchor.x) / (rect.right - anchor.x))
    }
    if (rect.top < anchor.y) {
        result = minOf(result, (anchor.y - bounds.top) / (anchor.y - rect.top))
    }
    if (rect.bottom > anchor.y) {
        result = minOf(result, (bounds.bottom - anchor.y) / (rect.bottom - anchor.y))
    }
    return result.takeIf { it.isFinite() }?.coerceAtLeast(1f) ?: 1f
}

/**
 * 裁剪框的固定安全区域（视图像素）：左右/顶部 48dp，底部 96dp。
 * [viewWidth] / [viewHeight] 是画布视图尺寸（已乘 density 的像素）。
 */
internal fun cropSafeBounds(viewWidth: Float, viewHeight: Float, density: Float): PxRect {
    val horizontal = 48f * density
    val top = 48f * density
    val bottom = 96f * density
    return PxRect(
        left = horizontal,
        top = top,
        right = (viewWidth - horizontal).coerceAtLeast(horizontal),
        bottom = (viewHeight - bottom).coerceAtLeast(top),
    )
}

/** 计算让整张图片落入 [bounds] 安全区域所需的 contain 缩放倍数。 */
internal fun zoomToFitBounds(base: ContainFit, bounds: PxRect): Float = minOf(
    bounds.width / (base.imageWidth * base.scale).coerceAtLeast(1f),
    bounds.height / (base.imageHeight * base.scale).coerceAtLeast(1f),
).coerceIn(AnnotateOps.MIN_VIEW_ZOOM, 1f)

/** 使图片始终覆盖裁剪框；裁剪态允许图片移出屏幕。 */
internal fun clampPanToCropWindow(
    base: ContainFit,
    zoom: Float,
    pan: Pt,
    window: PxRect?,
): Pt {
    if (window == null) return pan
    val centered = base.withViewTransform(zoom, Pt(0f, 0f))
    val lowX = window.right - (centered.offsetX + centered.imageWidth * centered.scale)
    val highX = window.left - centered.offsetX
    val lowY = window.bottom - (centered.offsetY + centered.imageHeight * centered.scale)
    val highY = window.top - centered.offsetY
    return Pt(
        x = if (lowX <= highX) pan.x.coerceIn(lowX, highX) else (lowX + highX) / 2f,
        y = if (lowY <= highY) pan.y.coerceIn(lowY, highY) else (lowY + highY) / 2f,
    )
}
