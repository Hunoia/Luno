package hunoia.luno.annotate.render

import android.graphics.Canvas as AndroidCanvas
import android.graphics.DashPathEffect
import android.graphics.Paint as AndroidPaint
import android.graphics.Path as AndroidPath
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF as AndroidRectF
import android.graphics.Typeface
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import hunoia.luno.annotate.model.AnnotateState
import hunoia.luno.annotate.model.AnnotationElement
import hunoia.luno.annotate.model.ContainFit
import hunoia.luno.annotate.model.ElementKind
import hunoia.luno.annotate.model.MosaicElement
import hunoia.luno.annotate.model.NumberElement
import hunoia.luno.annotate.model.Pt
import hunoia.luno.annotate.model.PxRect
import hunoia.luno.annotate.model.ShapeElement
import hunoia.luno.annotate.model.StrokeElement
import hunoia.luno.annotate.model.TextElement
import hunoia.luno.annotate.model.bounds
import hunoia.luno.annotate.model.shapeCrossSegments
import hunoia.luno.annotate.model.shapeOutlinePoints
import java.util.IdentityHashMap
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * 标注渲染的唯一实现（计划 D4）。
 *
 * 预览：Compose `Canvas` 里调 [drawAnnotate]；
 * 导出：`CanvasDrawScope` 包一层 `android.graphics.Canvas`，调**同一个** [drawAnnotate]。
 *
 * 所有元素几何都是图片像素坐标，[fit] 负责映射到当前画布坐标；
 * 导出时 `fit = ContainFit.identity(...)`，scale 恒为 1。
 *
 * 笔迹/形状/马赛克几何在**图片坐标**缓存（[AnnotateGeometryCache]），
 * 绘制时对 native canvas 做一次 translate+scale，避免每帧把所有点重算到视图坐标；
 * 双指缩放/平移的成本因此与笔迹总点数无关。
 */
/** 常量集中在渲染层，宿主与导出共用。 */
object AnnotateVisuals {
    /** 图片四周的留白（预览特有，导出不会出现）。 */
    val CanvasGutter: Color = Color.Black

    /** 选区外遮罩（只在预览出现，导出不含选区）。 */
    val SelectionScrim: Color = Color.Black.copy(alpha = 0.35f)
    val SelectionStroke: Color = Color.White
    val SelectionShadow: Color = Color.Black.copy(alpha = 0.5f)
    val SelectionHandleFill: Color = Color.White

    const val SelectionStrokeWidthPx = 1.5f
    const val SelectionHandleSizePx = 12f
    const val SelectionDashPx = 8f
    const val SelectionGapPx = 6f

    /** 元素（图形/文字/序号）选中框双色：深色外描边 + 白内芯，白底/深底都可见。 */
    val ElementSelectionOuter: Color = Color.Black.copy(alpha = 0.55f)
    val ElementSelectionInner: Color = Color.White
    /** 角柄/边中柄/旋转柄：白方块 + 深色描边（与选区框同一对比策略）。 */
    val ElementHandleFill: Color = Color.White
    val ElementHandleStroke: Color = Color.Black.copy(alpha = 0.62f)

    /** 单点笔迹的最小可视化直径（图片像素）。 */
    const val MinDotDiameterPx = 2f
}

/**
 * 元素几何缓存（图片坐标）。
 *
 * 键是元素对象引用（`IdentityHashMap`）：元素不可变，撤销/替换会产生新实例，
 * 旧条目自然成为垃圾；草稿元素引用不变但点数在涨，只追加新增点。
 * 条目数有上限，超了整体清空（极端场景退化为每帧重建，安全）。
 */
class AnnotateGeometryCache {

    private class StrokeEntry(val path: AndroidPath, var builtPoints: Int)
    private class ShapeEntry(val path: AndroidPath)
    private class MosaicEntry(
        val mask: AndroidPath,
        var builtPoints: Int,
        var builtRect: PxRect?,
    )

    private val strokes = IdentityHashMap<StrokeElement, StrokeEntry>()
    private val shapes = IdentityHashMap<ShapeElement, ShapeEntry>()
    private val mosaics = IdentityHashMap<MosaicElement, MosaicEntry>()

    /** 元素列表引用不变时，图层标志位/包围盒可以复用（缩放时列表引用不变）。 */
    private var flagsElements: List<AnnotationElement>? = null
    private var flags: Int = 0
    private var boundsElements: List<AnnotationElement>? = null
    private var bounds: PxRect? = null

    fun layerFlags(elements: List<AnnotationElement>): Int {
        if (flagsElements === elements) return flags
        var value = 0
        for (element in elements) {
            when {
                element is StrokeElement && element.erase -> value = value or FLAG_ERASER
                element is TextElement -> value = value or FLAG_TEXT
            }
        }
        flagsElements = elements
        flags = value
        return value
    }

    /** 所有元素的图片坐标包围盒并集；无元素时 null。 */
    fun layerBounds(elements: List<AnnotationElement>): PxRect? {
        if (boundsElements === elements) return bounds
        val union = elements
            .map { it.bounds() }
            .reduceOrNull { a, b -> a.union(b) }
        boundsElements = elements
        bounds = union
        return union
    }

    fun strokePath(element: StrokeElement): AndroidPath {
        val size = element.points.size
        val existing = strokes[element]
        if (existing != null && existing.builtPoints == size) return existing.path
        // 草稿只追加点：沿已有路径继续 lineTo，避免整条重建成 O(n²)。
        if (existing != null && size > existing.builtPoints) {
            val points = element.points
            for (i in existing.builtPoints until size) {
                existing.path.lineTo(points[i].x, points[i].y)
            }
            existing.builtPoints = size
            return existing.path
        }
        ensureCapacity(strokes)
        val path = AndroidPath()
        val points = element.points
        if (points.isNotEmpty()) {
            path.moveTo(points[0].x, points[0].y)
            for (i in 1 until points.size) path.lineTo(points[i].x, points[i].y)
        }
        strokes[element] = StrokeEntry(path, size)
        return path
    }

    fun shapePath(element: ShapeElement): AndroidPath {
        shapes[element]?.let { return it.path }
        ensureCapacity(shapes)
        val path = AndroidPath()
        when (element.kind) {
            ElementKind.RECT -> {
                val r = PxRect.fromPoints(element.start, element.end)
                path.addRect(r.left, r.top, r.right, r.bottom, AndroidPath.Direction.CW)
            }

            ElementKind.LINE, ElementKind.ARROW, ElementKind.DASHED_LINE -> {
                path.moveTo(element.start.x, element.start.y)
                path.lineTo(element.end.x, element.end.y)
                if (element.kind == ElementKind.ARROW) {
                    addArrowWings(path, element.start, element.end, element.width)
                }
            }

            ElementKind.CROSS -> {
                // 叉：两条对角线，各自一段子路径。
                shapeCrossSegments(element.start, element.end).forEach { (a, b) ->
                    path.moveTo(a.x, a.y)
                    path.lineTo(b.x, b.y)
                }
            }

            ElementKind.CHECK -> {
                // 对勾：开放折线（3 点）。
                val pts = shapeOutlinePoints(element.kind, element.start, element.end)
                if (pts.isNotEmpty()) {
                    path.moveTo(pts[0].x, pts[0].y)
                    for (i in 1 until pts.size) path.lineTo(pts[i].x, pts[i].y)
                }
            }

            // 闭合形状：轮廓顶点连成闭合路径。
            ElementKind.ELLIPSE, ElementKind.TRIANGLE,
            ElementKind.STAR, ElementKind.ROUND_RECT, ElementKind.HEART,
            -> {
                val pts = shapeOutlinePoints(element.kind, element.start, element.end)
                if (pts.isNotEmpty()) {
                    path.moveTo(pts[0].x, pts[0].y)
                    for (i in 1 until pts.size) path.lineTo(pts[i].x, pts[i].y)
                    path.close()
                }
            }

            else -> Unit
        }
        shapes[element] = ShapeEntry(path)
        return path
    }

    fun mosaicMask(element: MosaicElement, sanitized: MosaicElement): AndroidPath {
        val pointsSize = sanitized.points.size
        val rect = sanitized.rect?.normalized()
        var existing = mosaics[element]
        // 矩形变了或点数回退：无法增量修补，整体重建。
        if (existing != null && (existing.builtRect != rect || pointsSize < existing.builtPoints)) {
            existing = null
        }
        if (existing != null && existing.builtPoints == pointsSize) return existing.mask
        ensureCapacity(mosaics)
        if (existing == null) {
            val mask = AndroidPath()
            appendMosaicPoints(mask, sanitized, from = 0)
            rect?.let { r ->
                val fill = AndroidPath().apply {
                    addRect(r.left, r.top, r.right, r.bottom, AndroidPath.Direction.CW)
                }
                mask.op(fill, AndroidPath.Op.UNION)
            }
            mosaics[element] = MosaicEntry(mask, pointsSize, rect)
            return mask
        }
        // 同一矩形、只追加轨迹点：每段单独描边后并进 mask。
        appendMosaicPoints(existing.mask, sanitized, from = existing.builtPoints)
        existing.builtPoints = pointsSize
        return existing.mask
    }

    /** 把 [from] 之后的新轨迹段并进 [mask]；首点用圆点补 round cap。 */
    private fun appendMosaicPoints(mask: AndroidPath, element: MosaicElement, from: Int) {
        val points = element.points
        if (points.isEmpty()) return
        val width = element.width
        if (from == 0) {
            val first = points.first()
            mask.addCircle(first.x, first.y, width / 2f, AndroidPath.Direction.CW)
        }
        if (points.size < 2) return
        val paint = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply {
            style = AndroidPaint.Style.STROKE
            strokeCap = AndroidPaint.Cap.ROUND
            strokeJoin = AndroidPaint.Join.ROUND
            strokeWidth = width
        }
        val segment = AndroidPath()
        val filled = AndroidPath()
        for (i in maxOf(1, from) until points.size) {
            val a = points[i - 1]
            val b = points[i]
            segment.reset()
            segment.moveTo(a.x, a.y)
            segment.lineTo(b.x, b.y)
            filled.reset()
            paint.getFillPath(segment, filled)
            mask.op(filled, AndroidPath.Op.UNION)
        }
    }

    fun clear() {
        strokes.clear()
        shapes.clear()
        mosaics.clear()
        flagsElements = null
        boundsElements = null
        bounds = null
    }

    private fun <T> ensureCapacity(map: IdentityHashMap<T, *>) {
        if (map.size >= MAX_ENTRIES) map.clear()
    }

    companion object {
        private const val MAX_ENTRIES = 512
        const val FLAG_ERASER = 1
        const val FLAG_TEXT = 2
    }
}

fun DrawScope.drawAnnotate(
    state: AnnotateState,
    base: BaseImage?,
    fit: ContainFit,
    showSelection: Boolean = false,
    selectionOverride: PxRect? = null,
    scrimAroundImage: Boolean = false,
    geometryCache: AnnotateGeometryCache? = null,
    /** 导出时限制可见区域（图片坐标），让 `saveLayer` 只分配裁剪区。 */
    visibleImageRect: PxRect? = null,
    /** SELECT 拖动中的元素：不复制 points，绘制时临时平移。 */
    draftId: Long? = null,
    draftOffset: Pt? = null,
) {
    val imageRect = fit.imageRectToView(PxRect.full(state.imageWidth, state.imageHeight))
    if (scrimAroundImage) {
        drawRect(color = AnnotateVisuals.CanvasGutter, size = size)
    }
    drawBaseImage(base, fit, imageRect)
    drawAnnotationLayer(
        state = state,
        base = base,
        fit = fit,
        imageRect = imageRect,
        cache = geometryCache ?: AnnotateGeometryCache(),
        visibleImageRect = visibleImageRect,
        draftId = draftId,
        draftOffset = draftOffset,
    )
    if (showSelection) {
        val selection = selectionOverride ?: state.selection
        if (selection != null) drawSelectionOverlay(selection, fit, imageRect)
    }
}

/**
 * 预览专用：选区遮罩 + 手柄。放在世界层之外绘制，
 * 这样 `graphicsLayer` 缩放不会把描边/手柄一起放大。
 */
fun DrawScope.drawAnnotateSelection(
    state: AnnotateState,
    fit: ContainFit,
    selectionOverride: PxRect? = null,
) {
    val selection = selectionOverride ?: state.selection ?: return
    val imageRect = fit.imageRectToView(PxRect.full(state.imageWidth, state.imageHeight))
    drawSelectionOverlay(selection, fit, imageRect)
}

private fun DrawScope.drawBaseImage(
    base: BaseImage?,
    fit: ContainFit,
    imageRect: PxRect,
) {
    if (base == null) return
    drawImage(
        image = base.image,
        srcOffset = IntOffset.Zero,
        srcSize = IntSize(base.width, base.height),
        dstOffset = IntOffset(imageRect.left.roundToInt(), imageRect.top.roundToInt()),
        dstSize = IntSize(
            (imageRect.width).roundToInt().coerceAtLeast(1),
            (imageRect.height).roundToInt().coerceAtLeast(1),
        ),
        filterQuality = FilterQuality.Medium,
    )
}

/**
 * 标注层渲染。
 *
 * 橡皮用 `PorterDuff.Mode.CLEAR`，必须隔离层才不会把底图擦成透明（计划 D4）；
 * 但**没有橡皮时省掉整帧的 `saveLayer`**。有橡皮时图层范围取所有元素包围盒的并集
 * （夹到可见区），元素集中在局部时能省下可观的缓冲区分配。
 */
private fun DrawScope.drawAnnotationLayer(
    state: AnnotateState,
    base: BaseImage?,
    fit: ContainFit,
    imageRect: PxRect,
    cache: AnnotateGeometryCache,
    visibleImageRect: PxRect?,
    draftId: Long?,
    draftOffset: Pt?,
) {
    val elements = state.elements
    if (elements.isEmpty()) return
    val flags = cache.layerFlags(elements)
    val needsLayer = flags and AnnotateGeometryCache.FLAG_ERASER != 0
    val hasText = flags and AnnotateGeometryCache.FLAG_TEXT != 0
    // 文字用真实字宽绘制，模型层包围盒只是估算（多行高度可能超出），
    // 有文字时退回避险：图层边界取可见区，避免文字被 saveLayer 裁剪。
    val layerBounds = if (needsLayer) {
        annotationLayerBounds(state, fit, imageRect, cache, visibleImageRect, hasText, draftOffset)
    } else {
        null
    }
    val paints = RenderPaints()
    if (layerBounds != null) drawContext.canvas.saveLayer(layerBounds, Paint())
    try {
        clipRect(
            left = imageRect.left,
            top = imageRect.top,
            right = imageRect.right,
            bottom = imageRect.bottom,
        ) {
            var numberCount = 0
            for (element in elements) {
                when (element) {
                    // 文字保持视图坐标绘制（量字宽依赖 fit.scale），不进几何缓存。
                    is TextElement -> drawTextElement(element, fit, paints.text)
                    else -> drawWithImageTransform(fit) { canvas ->
                        val checkpoint = if (draftOffset != null && element.id == draftId) {
                            canvas.save().also { canvas.translate(draftOffset.x, draftOffset.y) }
                        } else {
                            -1
                        }
                        try {
                            when (element) {
                                is StrokeElement -> drawStrokeNative(canvas, element, cache, paints.stroke)
                                is ShapeElement ->
                                    drawShapeNative(canvas, element, cache, paints.stroke, paints.fill)
                                is MosaicElement ->
                                    drawMosaicNative(canvas, element, base, cache, paints.mosaic)
                                // 序号编号按列表顺序推导：删除/撤销后自动 1,2,3… 连续。
                                is NumberElement ->
                                    drawNumberNative(canvas, element, numberCount + 1, paints.fill, paints.text)
                                is TextElement -> Unit
                            }
                        } finally {
                            if (checkpoint >= 0) canvas.restoreToCount(checkpoint)
                        }
                    }
                }
                if (element is NumberElement) numberCount++
            }
        }
    } finally {
        if (layerBounds != null) drawContext.canvas.restore()
    }
}

/** 绘制期内复用的原生 Paint：避免每元素每帧分配。 */
private class RenderPaints {
    val stroke = AndroidPaint()
    val fill = AndroidPaint()
    val text = AndroidPaint()
    val mosaic = AndroidPaint()
}

/** 把所有元素包围盒合成一个视图坐标矩形，夹到可见区，作为 saveLayer 的边界。 */
private fun annotationLayerBounds(
    state: AnnotateState,
    fit: ContainFit,
    imageRect: PxRect,
    cache: AnnotateGeometryCache,
    visibleImageRect: PxRect?,
    hasText: Boolean,
    draftOffset: Pt?,
): Rect {
    val visible = visibleImageRect ?: PxRect.full(state.imageWidth, state.imageHeight)
    var imageBounds = if (hasText) {
        visible
    } else {
        cache.layerBounds(state.elements)?.let { intersect(it, visible) } ?: visible
    }
    if (draftOffset != null) {
        val shifted = PxRect(
            left = imageBounds.left + draftOffset.x,
            top = imageBounds.top + draftOffset.y,
            right = imageBounds.right + draftOffset.x,
            bottom = imageBounds.bottom + draftOffset.y,
        )
        imageBounds = union(imageBounds, intersect(shifted, visible))
    }
    if (imageBounds.right <= imageBounds.left || imageBounds.bottom <= imageBounds.top) {
        val fallback = fit.imageRectToView(visible)
        return Rect(fallback.left, fallback.top, fallback.right, fallback.bottom)
    }
    val view = fit.imageRectToView(imageBounds)
    // 夹到图片在视图里的范围，避免 layer 超出画布。
    val clamped = PxRect(
        left = maxOf(view.left, imageRect.left),
        top = maxOf(view.top, imageRect.top),
        right = minOf(view.right, imageRect.right),
        bottom = minOf(view.bottom, imageRect.bottom),
    )
    return if (clamped.right <= clamped.left || clamped.bottom <= clamped.top) {
        Rect(imageRect.left, imageRect.top, imageRect.right, imageRect.bottom)
    } else {
        Rect(clamped.left, clamped.top, clamped.right, clamped.bottom)
    }
}

private fun intersect(a: PxRect, b: PxRect): PxRect = PxRect(
    left = maxOf(a.left, b.left),
    top = maxOf(a.top, b.top),
    right = minOf(a.right, b.right),
    bottom = minOf(a.bottom, b.bottom),
)

private fun union(a: PxRect, b: PxRect): PxRect = PxRect(
    left = minOf(a.left, b.left),
    top = minOf(a.top, b.top),
    right = maxOf(a.right, b.right),
    bottom = maxOf(a.bottom, b.bottom),
)

/** 进入图片坐标系绘制，画完恢复（保证后续 Compose 绘制不受影响）。 */
private inline fun DrawScope.drawWithImageTransform(fit: ContainFit, block: (AndroidCanvas) -> Unit) {
    val canvas = drawContext.canvas.nativeCanvas
    val checkpoint = canvas.save()
    canvas.translate(fit.offsetX, fit.offsetY)
    canvas.scale(fit.scale, fit.scale)
    try {
        block(canvas)
    } finally {
        canvas.restoreToCount(checkpoint)
    }
}

private fun drawStrokeNative(
    canvas: AndroidCanvas,
    element: StrokeElement,
    cache: AnnotateGeometryCache,
    paint: AndroidPaint,
) {
    if (element.points.isEmpty()) return
    val width = element.width * if (element.highlight) 1.6f else 1f
    paint.reset()
    paint.isAntiAlias = true
    paint.style = AndroidPaint.Style.STROKE
    paint.strokeCap = AndroidPaint.Cap.ROUND
    paint.strokeJoin = AndroidPaint.Join.ROUND
    paint.strokeWidth = width
    if (element.erase) {
        paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
    } else {
        paint.color = element.colorArgb.toInt()
        if (element.highlight) paint.alpha = (255 * 0.38f).toInt()
    }
    if (element.points.size == 1) {
        if (element.erase) return
        val p = element.points.first()
        canvas.drawCircle(p.x, p.y, max(width / 2f, AnnotateVisuals.MinDotDiameterPx / 2f), paint)
        return
    }
    canvas.drawPath(cache.strokePath(element), paint)
}

private fun drawShapeNative(
    canvas: AndroidCanvas,
    element: ShapeElement,
    cache: AnnotateGeometryCache,
    strokePaint: AndroidPaint,
    fillPaint: AndroidPaint,
) {
    val path = cache.shapePath(element)
    val center = element.bounds.center
    val checkpoint = canvas.save()
    if (element.rotationRad != 0f) {
        canvas.rotate(rotationRadToDegrees(element.rotationRad), center.x, center.y)
    }
    // 虚线（DASHED_LINE）：用虚线描边。
    if (element.kind == ElementKind.DASHED_LINE) {
        strokePaint.reset()
        strokePaint.isAntiAlias = true
        strokePaint.style = AndroidPaint.Style.STROKE
        strokePaint.strokeCap = AndroidPaint.Cap.ROUND
        strokePaint.strokeWidth = element.width
        strokePaint.color = element.colorArgb.toInt()
        // 间距 = 5×线宽，间隙 = 线宽（与 moting 视觉一致）。
        strokePaint.pathEffect = DashPathEffect(
            floatArrayOf(element.width * 5f, element.width),
            0f,
        )
        canvas.drawPath(path, strokePaint)
        canvas.restoreToCount(checkpoint)
        return
    }
    // 半透明填充（alpha ≈ 14%）：仅闭合形状（开放路径填充会闭合产生错误图形）。
    if (element.filled && element.kind.isClosedShape()) {
        fillPaint.reset()
        fillPaint.isAntiAlias = true
        fillPaint.style = AndroidPaint.Style.FILL
        fillPaint.color = element.colorArgb.toInt()
        fillPaint.alpha = (255 * 0.14f).toInt()
        canvas.drawPath(path, fillPaint)
    }
    strokePaint.reset()
    strokePaint.isAntiAlias = true
    strokePaint.style = AndroidPaint.Style.STROKE
    strokePaint.strokeCap = AndroidPaint.Cap.ROUND
    strokePaint.strokeJoin = AndroidPaint.Join.ROUND
    strokePaint.strokeWidth = element.width
    strokePaint.color = element.colorArgb.toInt()
    canvas.drawPath(path, strokePaint)
    canvas.restoreToCount(checkpoint)
}

/** 序号：填充圆底（元素色）+ 白色居中数字；旋转绕圆底中心。 */
private fun drawNumberNative(
    canvas: AndroidCanvas,
    element: NumberElement,
    number: Int,
    badgePaint: AndroidPaint,
    textPaint: AndroidPaint,
) {
    if (number <= 0) return
    val radius = (element.textSize / 2f).coerceAtLeast(1f)
    badgePaint.reset()
    badgePaint.isAntiAlias = true
    badgePaint.style = AndroidPaint.Style.FILL
    badgePaint.color = element.colorArgb.toInt()
    textPaint.reset()
    textPaint.isAntiAlias = true
    textPaint.style = AndroidPaint.Style.FILL
    // 浅色底用深字、深色底用白字：调色板首位是白，白底白字会看不见。
    textPaint.color = if (isLightColor(element.colorArgb)) android.graphics.Color.BLACK
    else android.graphics.Color.WHITE
    // 数字字号占圆底约 60%，保持高对比。
    textPaint.textSize = (element.textSize * 0.6f).coerceAtLeast(1f)
    textPaint.typeface = Typeface.DEFAULT_BOLD
    textPaint.textAlign = AndroidPaint.Align.CENTER
    val checkpoint = canvas.save()
    if (element.rotationRad != 0f) {
        canvas.rotate(rotationRadToDegrees(element.rotationRad), element.position.x, element.position.y)
    }
    canvas.drawCircle(element.position.x, element.position.y, radius, badgePaint)
    val baseline = element.position.y - (textPaint.ascent() + textPaint.descent()) / 2f
    canvas.drawText(number.toString(), element.position.x, baseline, textPaint)
    canvas.restoreToCount(checkpoint)
}

private fun rotationRadToDegrees(rad: Float): Float = Math.toDegrees(rad.toDouble()).toFloat()

/** ARGB 亮度（Rec.601）：浅色返回 true（数字用深字），深色返回 false（用白字）。 */
private fun isLightColor(argb: Long): Boolean {
    val r = (argb shr 16) and 0xFF
    val g = (argb shr 8) and 0xFF
    val b = argb and 0xFF
    val lum = 0.299 * r + 0.587 * g + 0.114 * b
    return lum > 150.0
}

private fun drawMosaicNative(
    canvas: AndroidCanvas,
    element: MosaicElement,
    base: BaseImage?,
    cache: AnnotateGeometryCache,
    paint: AndroidPaint,
) {
    if (base == null) return
    val sanitized = element.sanitized()
    val mask = cache.mosaicMask(element, sanitized)
    if (mask.isEmpty) return
    val mosaic = base.mosaicImage(sanitized.cellSize)
    paint.reset()
    paint.isAntiAlias = false
    paint.isFilterBitmap = false
    paint.isDither = false
    canvas.clipPath(mask)
    canvas.drawBitmap(mosaic, null, AndroidRectF(0f, 0f, base.width.toFloat(), base.height.toFloat()), paint)
}

/** 闭合形状（可填充）：矩形/椭圆/圆角矩形/三角/星/心。 */
private fun ElementKind.isClosedShape(): Boolean = when (this) {
    ElementKind.RECT, ElementKind.ELLIPSE, ElementKind.ROUND_RECT,
    ElementKind.TRIANGLE, ElementKind.STAR, ElementKind.HEART,
    -> true
    else -> false
}

/** 箭头两翼（图片坐标）：长度随线宽收敛，终点方向为轴向。 */
private fun addArrowWings(path: AndroidPath, start: Pt, end: Pt, width: Float) {
    val dx = end.x - start.x
    val dy = end.y - start.y
    val length = sqrt(dx * dx + dy * dy)
    if (length < 0.01f) return
    val ux = dx / length
    val uy = dy / length
    val head = max(width * 3.5f, 8f).coerceAtMost(length * 0.6f)
    val spread = 0.45f
    fun wing(sign: Float): Pt {
        val cos = cos(spread * sign)
        val sin = sin(spread * sign)
        val wx = (ux * cos - uy * sin) * head
        val wy = (ux * sin + uy * cos) * head
        return Pt(end.x - wx, end.y - wy)
    }
    val a = wing(1f)
    val b = wing(-1f)
    path.moveTo(a.x, a.y)
    path.lineTo(end.x, end.y)
    path.lineTo(b.x, b.y)
}

private fun DrawScope.drawTextElement(
    element: TextElement,
    fit: ContainFit,
    paint: AndroidPaint,
) {
    if (element.text.isEmpty()) return
    val p = fit.imageToView(element.position)
    paint.reset()
    paint.isAntiAlias = true
    paint.color = element.colorArgb.toInt()
    paint.textSize = max(element.textSize * fit.scale, 1f)
    paint.typeface = Typeface.DEFAULT
    paint.style = AndroidPaint.Style.FILL
    val canvas = drawContext.canvas.nativeCanvas
    val lines = element.text.split('\n')
    val metrics = paint.fontMetrics
    val lineHeight = metrics.descent - metrics.ascent
    // 旋转绕真实文本框中心（与画布选中框 `realHitBounds` 同一枢轴），
    // 避免全角/半角混排时模型估算中心偏移导致视觉旋转轴错位。
    val checkpoint = canvas.save()
    if (element.rotationRad != 0f) {
        val width = lines.maxOfOrNull { paint.measureText(it) } ?: 0f
        canvas.rotate(
            rotationRadToDegrees(element.rotationRad),
            p.x + width / 2f,
            p.y + lineHeight * lines.size / 2f,
        )
    }
    val baselineOffset = -metrics.ascent
    lines.forEachIndexed { index, line ->
        canvas.drawText(line, p.x, p.y + baselineOffset + index * lineHeight, paint)
    }
    canvas.restoreToCount(checkpoint)
}

/** 只出现在预览：选区裁剪范围提示 + 四角手柄；导出不调这个函数。 */
private fun DrawScope.drawSelectionOverlay(
    selection: PxRect?,
    fit: ContainFit,
    imageRect: PxRect,
) {
    if (selection == null) return
    val raw = selection.normalized()
    val sel = raw.clampTo(fit.imageWidth, fit.imageHeight)
    if (sel.isEmpty(minSize = 0.5f)) return
    val view = fit.imageRectToView(sel)

    // 选区外压暗：用「整图 - 选区」的四个矩形拼出来，避免引入 BlendMode 依赖。
    val scrim = AnnotateVisuals.SelectionScrim
    fun scrimRect(l: Float, t: Float, r: Float, b: Float) {
        if (r <= l || b <= t) return
        drawRect(color = scrim, topLeft = Offset(l, t), size = Size(r - l, b - t))
    }
    scrimRect(imageRect.left, imageRect.top, imageRect.right, view.top)
    scrimRect(imageRect.left, view.bottom, imageRect.right, imageRect.bottom)
    scrimRect(imageRect.left, view.top, view.left, view.bottom)
    scrimRect(view.right, view.top, imageRect.right, view.bottom)

    val dash = PathEffect.dashPathEffect(
        floatArrayOf(AnnotateVisuals.SelectionDashPx, AnnotateVisuals.SelectionGapPx),
    )
    drawRect(
        color = AnnotateVisuals.SelectionShadow,
        topLeft = Offset(view.left, view.top),
        size = Size(view.width, view.height),
        style = Stroke(width = AnnotateVisuals.SelectionStrokeWidthPx * 2f),
    )
    drawRect(
        color = AnnotateVisuals.SelectionStroke,
        topLeft = Offset(view.left, view.top),
        size = Size(view.width, view.height),
        style = Stroke(width = AnnotateVisuals.SelectionStrokeWidthPx, pathEffect = dash),
    )

    val handle = AnnotateVisuals.SelectionHandleSizePx
    val corners = listOf(
        Offset(view.left, view.top),
        Offset(view.right, view.top),
        Offset(view.left, view.bottom),
        Offset(view.right, view.bottom),
    )
    for (corner in corners) {
        drawRect(
            color = AnnotateVisuals.SelectionHandleFill,
            topLeft = Offset(corner.x - handle / 2f, corner.y - handle / 2f),
            size = Size(handle, handle),
        )
        drawRect(
            color = AnnotateVisuals.SelectionShadow,
            topLeft = Offset(corner.x - handle / 2f, corner.y - handle / 2f),
            size = Size(handle, handle),
            style = Stroke(width = 1f),
        )
    }
}

/** 导出时按选区裁剪的像素尺寸，供宿主预分配位图 / 工具条显示。 */
fun AnnotateState.exportPixelSize(): IntSize {
    val crop = hunoia.luno.annotate.model.AnnotateOps.exportCropRect(this)
    return IntSize(
        width = ceil(crop.width).toInt().coerceAtLeast(1),
        height = ceil(crop.height).toInt().coerceAtLeast(1),
    )
}
