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
 * 旧条目自然成为垃圾；草稿元素引用不变但点数在涨，按 `点数` 判失效重建。
 * 条目数有上限，超了整体清空（极端场景退化为每帧重建，安全）。
 */
class AnnotateGeometryCache {

    private class StrokeEntry(val path: AndroidPath, val builtPoints: Int)
    private class ShapeEntry(val path: AndroidPath)
    private class MosaicEntry(val mask: AndroidPath, val builtPoints: Int, val builtRect: PxRect?)

    private val strokes = IdentityHashMap<StrokeElement, StrokeEntry>()
    private val shapes = IdentityHashMap<ShapeElement, ShapeEntry>()
    private val mosaics = IdentityHashMap<MosaicElement, MosaicEntry>()

    fun strokePath(element: StrokeElement): AndroidPath {
        val size = element.points.size
        val existing = strokes[element]
        if (existing != null && existing.builtPoints == size) return existing.path
        ensureCapacity(strokes)
        val path = AndroidPath()
        if (size > 0) {
            val first = element.points.first()
            path.moveTo(first.x, first.y)
            for (i in 1 until size) {
                val p = element.points[i]
                path.lineTo(p.x, p.y)
            }
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
        val existing = mosaics[element]
        if (existing != null && existing.builtPoints == pointsSize && existing.builtRect == rect) {
            return existing.mask
        }
        ensureCapacity(mosaics)
        val mask = AndroidPath()
        if (sanitized.points.isNotEmpty()) {
            val width = sanitized.width
            if (sanitized.points.size == 1) {
                val p = sanitized.points.first()
                mask.addCircle(p.x, p.y, width / 2f, AndroidPath.Direction.CW)
            } else {
                val trail = AndroidPath().apply {
                    val first = sanitized.points.first()
                    moveTo(first.x, first.y)
                    for (i in 1 until sanitized.points.size) {
                        val p = sanitized.points[i]
                        lineTo(p.x, p.y)
                    }
                }
                val stroke = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply {
                    style = AndroidPaint.Style.STROKE
                    strokeCap = AndroidPaint.Cap.ROUND
                    strokeJoin = AndroidPaint.Join.ROUND
                    strokeWidth = width
                }
                val filled = AndroidPath()
                stroke.getFillPath(trail, filled)
                mask.op(filled, AndroidPath.Op.UNION)
            }
        }
        rect?.let { r ->
            val fill = AndroidPath().apply {
                addRect(r.left, r.top, r.right, r.bottom, AndroidPath.Direction.CW)
            }
            mask.op(fill, AndroidPath.Op.UNION)
        }
        mosaics[element] = MosaicEntry(mask, pointsSize, rect)
        return mask
    }

    fun clear() {
        strokes.clear()
        shapes.clear()
        mosaics.clear()
    }

    private fun <T> ensureCapacity(map: IdentityHashMap<T, *>) {
        if (map.size >= MAX_ENTRIES) map.clear()
    }

    private companion object {
        const val MAX_ENTRIES = 512
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
) {
    val imageRect = fit.imageRectToView(PxRect.full(state.imageWidth, state.imageHeight))
    if (scrimAroundImage) {
        drawRect(color = AnnotateVisuals.CanvasGutter, size = size)
    }
    drawBaseImage(base, fit, imageRect)
    drawAnnotationLayer(state, base, fit, imageRect, geometryCache ?: AnnotateGeometryCache())
    if (showSelection) {
        val selection = selectionOverride ?: state.selection
        if (selection != null) drawSelectionOverlay(selection, fit, imageRect)
    }
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
 * （夹到图片内），比整图小；元素集中在局部时能省下可观的缓冲区分配。
 */
private fun DrawScope.drawAnnotationLayer(
    state: AnnotateState,
    base: BaseImage?,
    fit: ContainFit,
    imageRect: PxRect,
    cache: AnnotateGeometryCache,
) {
    if (state.elements.isEmpty()) return
    val needsLayer = state.elements.any { it is StrokeElement && it.erase }
    // 文字用真实字宽绘制，模型层包围盒只是估算（多行高度可能超出），
    // 有文字时退回避险：图层边界取整图，避免文字被 saveLayer 裁剪。
    val layerBounds = if (needsLayer) {
        if (state.elements.any { it is TextElement }) {
            Rect(imageRect.left, imageRect.top, imageRect.right, imageRect.bottom)
        } else {
            annotationLayerBounds(state, fit, imageRect)
        }
    } else {
        null
    }
    if (layerBounds != null) drawContext.canvas.saveLayer(layerBounds, Paint())
    try {
        clipRect(
            left = imageRect.left,
            top = imageRect.top,
            right = imageRect.right,
            bottom = imageRect.bottom,
        ) {
            var numberCount = 0
            for (element in state.elements) {
                when (element) {
                    // 文字保持视图坐标绘制（量字宽依赖 fit.scale），不进几何缓存。
                    is TextElement -> drawTextElement(element, fit)
                    else -> drawWithImageTransform(fit) { canvas ->
                        when (element) {
                            is StrokeElement -> drawStrokeNative(canvas, element, cache)
                            is ShapeElement -> drawShapeNative(canvas, element, cache)
                            is MosaicElement -> drawMosaicNative(canvas, element, base, cache)
                            // 序号编号按列表顺序推导：删除/撤销后自动 1,2,3… 连续。
                            is NumberElement -> drawNumberNative(canvas, element, numberCount + 1)
                            is TextElement -> Unit
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

/** 把所有元素包围盒合成一个视图坐标矩形，夹到图片范围内，作为 saveLayer 的边界。 */
private fun annotationLayerBounds(
    state: AnnotateState,
    fit: ContainFit,
    imageRect: PxRect,
): Rect {
    val union = state.elements
        .map { it.bounds() }
        .reduceOrNull { a, b -> a.union(b) }
    val clamped = union?.let { u ->
        PxRect(
            left = maxOf(u.left, imageRect.left),
            top = maxOf(u.top, imageRect.top),
            right = minOf(u.right, imageRect.right),
            bottom = minOf(u.bottom, imageRect.bottom),
        )
    }
    if (clamped == null || clamped.right <= clamped.left || clamped.bottom <= clamped.top) {
        return Rect(imageRect.left, imageRect.top, imageRect.right, imageRect.bottom)
    }
    val view = fit.imageRectToView(clamped)
    return Rect(view.left, view.top, view.right, view.bottom)
}

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
) {
    if (element.points.isEmpty()) return
    val width = element.width * if (element.highlight) 1.6f else 1f
    val paint = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply {
        style = AndroidPaint.Style.STROKE
        strokeCap = AndroidPaint.Cap.ROUND
        strokeJoin = AndroidPaint.Join.ROUND
        strokeWidth = width
        if (element.erase) {
            xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
        } else {
            color = element.colorArgb.toInt()
            if (element.highlight) alpha = (255 * 0.38f).toInt()
        }
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
) {
    val path = cache.shapePath(element)
    val center = element.bounds.center
    val checkpoint = canvas.save()
    if (element.rotationRad != 0f) {
        canvas.rotate(rotationRadToDegrees(element.rotationRad), center.x, center.y)
    }
    // 虚线（DASHED_LINE）：用虚线描边。
    if (element.kind == ElementKind.DASHED_LINE) {
        val dashed = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply {
            style = AndroidPaint.Style.STROKE
            strokeCap = AndroidPaint.Cap.ROUND
            strokeWidth = element.width
            color = element.colorArgb.toInt()
            // 间距 = 5×线宽，间隙 = 线宽（与 moting 视觉一致）。
            pathEffect = DashPathEffect(
                floatArrayOf(element.width * 5f, element.width),
                0f,
            )
        }
        canvas.drawPath(path, dashed)
        canvas.restoreToCount(checkpoint)
        return
    }
    // 半透明填充（alpha ≈ 14%）：仅闭合形状（开放路径填充会闭合产生错误图形）。
    if (element.filled && element.kind.isClosedShape()) {
        val fill = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply {
            style = AndroidPaint.Style.FILL
            color = element.colorArgb.toInt()
            alpha = (255 * 0.14f).toInt()
        }
        canvas.drawPath(path, fill)
    }
    val paint = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply {
        style = AndroidPaint.Style.STROKE
        strokeCap = AndroidPaint.Cap.ROUND
        strokeJoin = AndroidPaint.Join.ROUND
        strokeWidth = element.width
        color = element.colorArgb.toInt()
    }
    canvas.drawPath(path, paint)
    canvas.restoreToCount(checkpoint)
}

/** 序号：填充圆底（元素色）+ 白色居中数字；旋转绕圆底中心。 */
private fun drawNumberNative(
    canvas: AndroidCanvas,
    element: NumberElement,
    number: Int,
) {
    if (number <= 0) return
    val radius = (element.textSize / 2f).coerceAtLeast(1f)
    val badge = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply {
        style = AndroidPaint.Style.FILL
        color = element.colorArgb.toInt()
    }
    val textPaint = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply {
        style = AndroidPaint.Style.FILL
        // 浅色底用深字、深色底用白字：调色板首位是白，白底白字会看不见。
        color = if (isLightColor(element.colorArgb)) android.graphics.Color.BLACK
        else android.graphics.Color.WHITE
        // 数字字号占圆底约 60%，保持高对比。
        textSize = (element.textSize * 0.6f).coerceAtLeast(1f)
        typeface = Typeface.DEFAULT_BOLD
        textAlign = AndroidPaint.Align.CENTER
    }
    val checkpoint = canvas.save()
    if (element.rotationRad != 0f) {
        canvas.rotate(rotationRadToDegrees(element.rotationRad), element.position.x, element.position.y)
    }
    canvas.drawCircle(element.position.x, element.position.y, radius, badge)
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
) {
    if (base == null) return
    val sanitized = element.sanitized()
    val mask = cache.mosaicMask(element, sanitized)
    if (mask.isEmpty) return
    val mosaic = base.mosaicImage(sanitized.cellSize)
    val paint = AndroidPaint().apply {
        isAntiAlias = false
        isFilterBitmap = false
        isDither = false
    }
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

private fun DrawScope.drawTextElement(element: TextElement, fit: ContainFit) {
    if (element.text.isEmpty()) return
    val p = fit.imageToView(element.position)
    val paint = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply {
        color = element.colorArgb.toInt()
        textSize = max(element.textSize * fit.scale, 1f)
        typeface = Typeface.DEFAULT
        style = AndroidPaint.Style.FILL
    }
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

private fun Pt.asOffset(): Offset = Offset(x, y)

/** 导出时按选区裁剪的像素尺寸，供宿主预分配位图 / 工具条显示。 */
fun AnnotateState.exportPixelSize(): IntSize {
    val crop = hunoia.luno.annotate.model.AnnotateOps.exportCropRect(this)
    return IntSize(
        width = ceil(crop.width).toInt().coerceAtLeast(1),
        height = ceil(crop.height).toInt().coerceAtLeast(1),
    )
}
