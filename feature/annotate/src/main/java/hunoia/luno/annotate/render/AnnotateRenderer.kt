package hunoia.luno.annotate.render

import android.graphics.Paint as AndroidPaint
import android.graphics.Typeface
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import hunoia.luno.annotate.model.AnnotateState
import hunoia.luno.annotate.model.AnnotationElement
import hunoia.luno.annotate.model.ContainFit
import hunoia.luno.annotate.model.MosaicElement
import hunoia.luno.annotate.model.Pt
import hunoia.luno.annotate.model.PxRect
import hunoia.luno.annotate.model.ShapeElement
import hunoia.luno.annotate.model.StrokeElement
import hunoia.luno.annotate.model.TextElement
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * 标注渲染的唯一实现（计划 D4）。
 *
 * 预览：Compose `Canvas` 里调 [drawAnnotate]；
 * 导出：`CanvasDrawScope` 包一层 `android.graphics.Canvas`，调**同一个** [drawAnnotate]。
 *
 * 所有元素几何都是图片像素坐标，[fit] 负责映射到当前画布坐标；
 * 导出时 `fit = ContainFit.identity(...)`，scale 恒为 1。
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

    /** 单个马赛克元素最多绘制的格子数，超过则自动放大单元。 */
    const val MosaicMaxCells = 12_000

    /** 单点笔迹的最小可视化直径（图片像素）。 */
    const val MinDotDiameterPx = 2f
}

fun DrawScope.drawAnnotate(
    state: AnnotateState,
    base: BaseImage?,
    fit: ContainFit,
    showSelection: Boolean = false,
    selectionOverride: PxRect? = null,
    scrimAroundImage: Boolean = false,
) {
    val imageRect = fit.imageRectToView(PxRect.full(state.imageWidth, state.imageHeight))
    if (scrimAroundImage) {
        drawRect(color = AnnotateVisuals.CanvasGutter, size = size)
    }
    drawBaseImage(base, fit, imageRect)
    drawAnnotationLayer(state, base, fit, imageRect)
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
 * 标注层单独 `saveLayer`：橡皮用 [BlendMode.Clear] 只清这一层，
 * 不会把底图擦成透明（计划 D4 / 验收「橡皮只擦笔迹」）。
 */
private fun DrawScope.drawAnnotationLayer(
    state: AnnotateState,
    base: BaseImage?,
    fit: ContainFit,
    imageRect: PxRect,
) {
    if (state.elements.isEmpty()) return
    val bounds = Rect(
        left = imageRect.left,
        top = imageRect.top,
        right = imageRect.right,
        bottom = imageRect.bottom,
    )
    drawContext.canvas.saveLayer(bounds, Paint())
    try {
        clipRect(
            left = imageRect.left,
            top = imageRect.top,
            right = imageRect.right,
            bottom = imageRect.bottom,
        ) {
            for (element in state.elements) {
                when (element) {
                    is StrokeElement -> drawStrokeElement(element, fit)
                    is ShapeElement -> drawShapeElement(element, fit)
                    is TextElement -> drawTextElement(element, fit)
                    is MosaicElement -> drawMosaicElement(element, base, fit)
                }
            }
        }
    } finally {
        drawContext.canvas.restore()
    }
}

private fun DrawScope.drawStrokeElement(element: StrokeElement, fit: ContainFit) {
    val width = max(element.width * fit.scale, 0.75f)
    val color = Color(element.colorArgb)
    if (element.points.isEmpty()) return
    if (element.points.size == 1) {
        if (element.erase) return
        val p = fit.imageToView(element.points[0])
        drawCircle(color = color, radius = max(width / 2f, AnnotateVisuals.MinDotDiameterPx / 2f), center = p.asOffset())
        return
    }
    val path = Path().apply {
        val first = fit.imageToView(element.points[0])
        moveTo(first.x, first.y)
        for (i in 1 until element.points.size) {
            val p = fit.imageToView(element.points[i])
            lineTo(p.x, p.y)
        }
    }
    drawPath(
        path = path,
        color = color,
        style = Stroke(
            width = width,
            cap = StrokeCap.Round,
            join = StrokeJoin.Round,
        ),
        blendMode = if (element.erase) BlendMode.Clear else BlendMode.SrcOver,
    )
}

private fun DrawScope.drawShapeElement(element: ShapeElement, fit: ContainFit) {
    val width = max(element.width * fit.scale, 0.75f)
    val color = Color(element.colorArgb)
    val start = fit.imageToView(element.start)
    val end = fit.imageToView(element.end)

    when (element.kind) {
        hunoia.luno.annotate.model.ElementKind.RECT -> {
            val rect = PxRect.fromPoints(start, end)
            drawRect(
                color = color,
                topLeft = Offset(rect.left, rect.top),
                size = Size(rect.width, rect.height),
                style = Stroke(width = width, join = StrokeJoin.Round),
            )
        }

        hunoia.luno.annotate.model.ElementKind.ARROW -> {
            drawLine(
                color = color,
                start = Offset(start.x, start.y),
                end = Offset(end.x, end.y),
                strokeWidth = width,
                cap = StrokeCap.Round,
            )
            drawArrowHead(start, end, color, width)
        }

        else -> Unit
    }
}

/** 箭头两翼：长度随线宽收敛，终点方向为轴向。 */
private fun DrawScope.drawArrowHead(start: Pt, end: Pt, color: Color, width: Float) {
    val dx = end.x - start.x
    val dy = end.y - start.y
    val length = sqrt(dx * dx + dy * dy)
    if (length < 0.01f) return
    val ux = dx / length
    val uy = dy / length
    val head = max(width * 3.5f, 8f).coerceAtMost(length * 0.6f)
    val spread = 0.45f
    fun wing(sign: Float): Offset {
        val cos = kotlin.math.cos(spread * sign)
        val sin = kotlin.math.sin(spread * sign)
        val wx = (ux * cos - uy * sin) * head
        val wy = (ux * sin + uy * cos) * head
        return Offset(end.x - wx, end.y - wy)
    }
    val a = wing(1f)
    val b = wing(-1f)
    drawLine(color = color, start = Offset(end.x, end.y), end = a, strokeWidth = width, cap = StrokeCap.Round)
    drawLine(color = color, start = Offset(end.x, end.y), end = b, strokeWidth = width, cap = StrokeCap.Round)
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
    val baselineOffset = -paint.fontMetrics.ascent
    val lineHeight = (paint.fontMetrics.descent - paint.fontMetrics.ascent)
    element.text.split('\n').forEachIndexed { index, line ->
        canvas.drawText(line, p.x, p.y + baselineOffset + index * lineHeight, paint)
    }
}

/**
 * 马赛克：按单元采样**冻结底图**的真实像素，预览与导出一致。
 * 单元数超过上限时自动放大单元，避免大图卡死。
 */
private fun DrawScope.drawMosaicElement(
    element: MosaicElement,
    base: BaseImage?,
    fit: ContainFit,
) {
    if (base == null) return
    val sanitized = element.sanitized()
    val rect = sanitized.rect
        .normalized()
        .clampTo(fit.imageWidth, fit.imageHeight)
    if (rect.isEmpty(minSize = 0.5f)) return

    val requestedCell = sanitized.cellSize
    val cells = (rect.width / requestedCell) * (rect.height / requestedCell)
    val cell = if (cells > AnnotateVisuals.MosaicMaxCells) {
        sqrt((rect.width * rect.height) / AnnotateVisuals.MosaicMaxCells)
    } else {
        requestedCell
    }.coerceAtLeast(1f)

    clipRect(
        left = fit.offsetX,
        top = fit.offsetY,
        right = fit.offsetX + fit.drawnWidth,
        bottom = fit.offsetY + fit.drawnHeight,
    ) {
        var y = rect.top
        while (y < rect.bottom) {
            val cellBottom = (y + cell).coerceAtMost(rect.bottom)
            var x = rect.left
            while (x < rect.right) {
                val cellRight = (x + cell).coerceAtMost(rect.right)
                val sampleX = ((x + cellRight) / 2f).roundToInt()
                val sampleY = ((y + cellBottom) / 2f).roundToInt()
                val sampled = base.sampleColor(sampleX, sampleY)
                val viewLeft = fit.imageToView(Pt(x, y))
                val viewRight = fit.imageToView(Pt(cellRight, cellBottom))
                drawRect(
                    color = Color(sampled),
                    topLeft = Offset(viewLeft.x, viewLeft.y),
                    // +0.5px 消除相邻格之间的缝。
                    size = Size(
                        width = (viewRight.x - viewLeft.x) + 0.5f,
                        height = (viewRight.y - viewLeft.y) + 0.5f,
                    ),
                )
                x = cellRight
            }
            y = cellBottom
        }
    }
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

/** 导出时按选区裁剪的像素尺寸，供宿主预分配位图。 */
fun AnnotateState.exportPixelSize(): IntSize {
    val crop = hunoia.luno.annotate.model.AnnotateOps.exportCropRect(this)
    return IntSize(
        width = ceil(crop.width).toInt().coerceAtLeast(1),
        height = ceil(crop.height).toInt().coerceAtLeast(1),
    )
}
