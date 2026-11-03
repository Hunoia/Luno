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

enum class ElementKind { STROKE, ERASER, RECT, ARROW, TEXT, MOSAIC }

/** 自由笔迹。 [erase] 为 true 时是橡皮：只擦标注层，不擦底图（导出时 saveLayer + CLEAR）。 */
data class StrokeElement(
    override val id: Long,
    val points: List<Pt>,
    val colorArgb: Long,
    val width: Float,
    val erase: Boolean = false,
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
) : AnnotationElement {
    override val kind: ElementKind = ElementKind.TEXT
}

data class MosaicElement(
    override val id: Long,
    val rect: PxRect,
    val cellSize: Float,
) : AnnotationElement {
    override val kind: ElementKind = ElementKind.MOSAIC

    /** 归一化并限制单元尺寸，避免 0 或负数把采样循环卡死。 */
    fun sanitized(maxCell: Float = 96f): MosaicElement {
        val n = rect.normalized()
        return copy(rect = n, cellSize = cellSize.coerceIn(4f, maxCell))
    }
}

/** 把元素几何包成图片坐标矩形（命中测试 / 重排 / 调试用）。 */
fun AnnotationElement.bounds(): PxRect = when (this) {
    is StrokeElement -> {
        if (points.isEmpty()) PxRect(0f, 0f, 0f, 0f)
        else {
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
            val pad = width / 2f
            PxRect(l - pad, t - pad, r + pad, b + pad)
        }
    }
    is ShapeElement -> bounds.let { it.inset(-width / 2f) }
    is TextElement -> PxRect(position.x, position.y, position.x + textSize * text.length, position.y + textSize)
    is MosaicElement -> rect
}
