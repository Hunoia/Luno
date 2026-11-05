package hunoia.luno.annotate.model

/**
 * 画布里一个可撤销的标注元素。
 *
 * 所有几何量都是**图片像素坐标**；渲染时由 `ContainFit` 统一映射到视图。
 * 列表顺序即图层顺序（后画的在上）。
 */
sealed interface AnnotationElement {
    val id: Long
    val kind: ElementKind
}

enum class ElementKind {
    STROKE, ERASER, RECT, LINE, ARROW, TEXT, MOSAIC,
    // 快速标注器扩展形状 + 序号。
    ELLIPSE, TRIANGLE, CHECK, CROSS, STAR, ROUND_RECT, HEART, NUMBER,
}

/** 自由笔迹。 [erase] 为 true 时是橡皮：只擦标注层，不擦底图（导出时 saveLayer + CLEAR）。 */
data class StrokeElement(
    override val id: Long,
    val points: List<Pt>,
    val colorArgb: Long,
    val width: Float,
    val erase: Boolean = false,
    /** 荧光笔：半透明宽笔迹，不参与橡皮命中（与普通笔迹同层）。 */
    val highlight: Boolean = false,
) : AnnotationElement {
    override val kind: ElementKind = if (erase) ElementKind.ERASER else ElementKind.STROKE
}

data class ShapeElement(
    override val id: Long,
    val start: Pt,
    val end: Pt,
    val colorArgb: Long,
    val width: Float,
    override val kind: ElementKind,
    /** 绕元素中心旋转（弧度，绕 [bounds] 中心）。默认 0 = 不旋转。 */
    val rotationRad: Float = 0f,
) : AnnotationElement {
    val bounds: PxRect get() = PxRect.fromPoints(start, end)
}

data class TextElement(
    override val id: Long,
    /** 文本框左上角（图片像素）。 */
    val position: Pt,
    val text: String,
    val colorArgb: Long,
    val textSize: Float,
    /** 绕文本框中心旋转（弧度）。默认 0 = 不旋转。 */
    val rotationRad: Float = 0f,
) : AnnotationElement {
    override val kind: ElementKind = ElementKind.TEXT
}

/**
 * 序号标注（快速标注器）：自动递增数字 + 圆形底。
 *
 * 编号**不存**在元素里，渲染/导出时按元素列表顺序推导（本元素之前有多少个
 * [NumberElement] + 1），删除 / 撤销 / 重做后自动保持 1,2,3… 连续。
 * [position] 是圆形底的中心（图片像素）。
 */
data class NumberElement(
    override val id: Long,
    val position: Pt,
    val colorArgb: Long,
    /** 圆形底直径（图片像素）。 */
    val textSize: Float,
    /** 绕圆形底中心旋转（弧度）。默认 0。 */
    val rotationRad: Float = 0f,
) : AnnotationElement {
    override val kind: ElementKind = ElementKind.NUMBER

    /** 圆形底外接包围盒（图片像素），用于命中/选中/移动。 */
    val badgeBounds: PxRect get() = PxRect.squareAround(position, textSize)
}

/**
 * 马赛克：涂抹轨迹（[points] + [width]）或选区矩形填充（[rect]），二者可并存。
 * 渲染时都从冻结底图的预栅格化小块取样，预览与导出一致。
 */
data class MosaicElement(
    override val id: Long,
    /** 涂抹轨迹，图片像素。 */
    val points: List<Pt> = emptyList(),
    /** 笔刷直径，图片像素。 */
    val width: Float = 1f,
    val cellSize: Float,
    /** 选区填充（B6）：整块矩形也铺马赛克。 */
    val rect: PxRect? = null,
) : AnnotationElement {
    override val kind: ElementKind = ElementKind.MOSAIC

    /** 归一化并限制尺寸，避免 0 或负数把绘制/命中测试卡死。 */
    fun sanitized(maxCell: Float = 96f): MosaicElement = copy(
        cellSize = cellSize.coerceIn(4f, maxCell),
        width = width.coerceIn(2f, 256f),
        rect = rect?.normalized(),
    )
}

/**
 * 把元素几何包成图片坐标矩形（命中测试 / 重排 / 调试用）。
 *
 * 注意：文字只是 `textSize * text.length` 的**估算**，全角/半角混排不准；
 * 画布上点选已有文字走 `ui/AnnotateTextMetrics.realHitBounds`（真实测量）。
 */
fun AnnotationElement.bounds(): PxRect = when (this) {
    is StrokeElement -> pointsBounds(points, width / 2f)
    is ShapeElement -> bounds.let { it.inset(-width / 2f) }
    is TextElement -> PxRect(position.x, position.y, position.x + textSize * text.length, position.y + textSize)
    is NumberElement -> badgeBounds
    is MosaicElement -> {
        val stroke = pointsBounds(points, width / 2f)
        val fill = rect?.normalized()
        when {
            fill == null -> stroke
            stroke.isEmptyRaw() -> fill
            else -> PxRect(
                left = minOf(stroke.left, fill.left),
                top = minOf(stroke.top, fill.top),
                right = maxOf(stroke.right, fill.right),
                bottom = maxOf(stroke.bottom, fill.bottom),
            )
        }
    }
}

/** 点集包围盒外扩 [pad]；空点集返回退化矩形。 */
private fun pointsBounds(points: List<Pt>, pad: Float): PxRect {
    if (points.isEmpty()) return PxRect(0f, 0f, 0f, 0f)
    var l = points[0].x
    var t = points[0].y
    var r = points[0].x
    var b = points[0].y
    for (p in points) {
        if (p.x < l) l = p.x
        if (p.y < t) t = p.y
        if (p.x > r) r = p.x
        if (p.y > b) b = p.y
    }
    return PxRect(l - pad, t - pad, r + pad, b + pad)
}

private fun PxRect.isEmptyRaw(): Boolean = width <= 0f && height <= 0f

/**
 * 矩形橡皮是否该清掉这个元素（审计 C1）。
 *
 * 只清**笔迹与形状**（橡皮笔迹本身不清，否则会把已擦区域又露出来）；
 * 文字 / 马赛克不动——它们有各自的选中与删除语义，大面积矩形误伤面太大。
 * 笔迹按逐段线段相交判（只看 bbox 会把「框碰到大 U 形空白」的笔迹误删）。
 */
fun AnnotationElement.erasedBy(rect: PxRect): Boolean = when (this) {
    is StrokeElement -> {
        if (erase) {
            false
        } else if (points.size == 1) {
            rect.contains(points[0])
        } else {
            points.zipWithNext().any { (a, b) -> segmentIntersectsRect(a, b, rect) }
        }
    }

    is ShapeElement -> {
        // 按「实际画出来的边段」判：矩形/圆/三角等闭合形状用轮廓边段，直线/箭头用主干，
        // 箭头翼尺寸随线宽走忽略不计。旋转元素先绕中心旋转回实际位置再判。
        val center = bounds.center
        val rot = rotationRad
        fun seg(a: Pt, b: Pt): Boolean =
            segmentIntersectsRect(rotateAround(a, center, rot), rotateAround(b, center, rot), rect)
        when (kind) {
            ElementKind.RECT -> {
                val b = bounds
                val tl = Pt(b.left, b.top)
                val tr = Pt(b.right, b.top)
                val br = Pt(b.right, b.bottom)
                val bl = Pt(b.left, b.bottom)
                seg(tl, tr) || seg(tr, br) || seg(br, bl) || seg(bl, tl)
            }

            ElementKind.LINE, ElementKind.ARROW -> seg(start, end)

            ElementKind.CROSS -> shapeCrossSegments(start, end).any { seg(it.first, it.second) }

            ElementKind.CHECK -> {
                val pts = shapeOutlinePoints(kind, start, end)
                pts.zipWithNext().any { seg(it.first, it.second) }
            }

            else -> {
                val pts = shapeOutlinePoints(kind, start, end)
                pts.size >= 2 &&
                    (pts.zipWithNext().any { seg(it.first, it.second) } || seg(pts.last(), pts.first()))
            }
        }
    }
    is TextElement -> false
    is NumberElement -> false
    is MosaicElement -> false
}

/**
 * 元素仿射变换：把 [from] 包围盒映射到 [to]（元素调整 / 缩放）。
 * 线宽、文字大小、马赛克单元按两轴平均缩放；文字位置按左上角映射。
 */
fun AnnotationElement.transformed(from: PxRect, to: PxRect): AnnotationElement {
    val f = from.normalized()
    val t = to.normalized()
    val sx = if (f.width > 0.01f) t.width / f.width else 1f
    val sy = if (f.height > 0.01f) t.height / f.height else 1f
    val s = (sx + sy) / 2f
    fun map(p: Pt): Pt = Pt(
        x = t.left + (p.x - f.left) * sx,
        y = t.top + (p.y - f.top) * sy,
    )
    return when (this) {
        is StrokeElement -> copy(points = points.map(::map), width = (width * s).coerceAtLeast(0.5f))
        is ShapeElement -> copy(start = map(start), end = map(end), width = (width * s).coerceAtLeast(0.5f))
        is TextElement -> copy(position = map(position), textSize = (textSize * s).coerceAtLeast(4f))
        is NumberElement -> copy(position = map(position), textSize = (textSize * s).coerceAtLeast(4f))
        is MosaicElement -> copy(
            points = points.map(::map),
            width = (width * s).coerceAtLeast(2f),
            cellSize = (cellSize * s).coerceIn(4f, 96f),
            rect = rect?.let { r ->
                val a = map(Pt(r.left, r.top))
                val b = map(Pt(r.right, r.bottom))
                PxRect(a.x, a.y, b.x, b.y).normalized()
            },
        )
    }
}

/** 平移元素（元素拖动）。 */
fun AnnotationElement.translated(dx: Float, dy: Float): AnnotationElement =
    when (this) {
        is StrokeElement -> copy(points = points.map { Pt(it.x + dx, it.y + dy) })
        is ShapeElement -> copy(start = Pt(start.x + dx, start.y + dy), end = Pt(end.x + dx, end.y + dy))
        is TextElement -> copy(position = Pt(position.x + dx, position.y + dy))
        is NumberElement -> copy(position = Pt(position.x + dx, position.y + dy))
        is MosaicElement -> copy(
            points = points.map { Pt(it.x + dx, it.y + dy) },
            rect = rect?.let { PxRect(it.left + dx, it.top + dy, it.right + dx, it.bottom + dy) },
        )
    }

/** 元素当前旋转角（弧度）。不可旋转的元素恒为 0。 */
fun AnnotationElement.rotationRadOf(): Float = when (this) {
    is ShapeElement -> rotationRad
    is TextElement -> rotationRad
    is NumberElement -> rotationRad
    else -> 0f
}

/** 设置元素旋转角（弧度）。不可旋转的元素原样返回。 */
fun AnnotationElement.withRotation(rad: Float): AnnotationElement = when (this) {
    is ShapeElement -> copy(rotationRad = rad)
    is TextElement -> copy(rotationRad = rad)
    is NumberElement -> copy(rotationRad = rad)
    else -> this
}
