package hunoia.luno.annotate.model

/**
 * 元素本体命中测试的几何部分（审计 P1a 从 `ui/AnnotateCanvas` 下沉）。
 *
 * 文字的真实占位量宽依赖 `android.graphics.Paint`，留在 UI 层
 * （`AnnotateTextMetrics.realHitBounds`），经 [hitTopElement] / [hitBody] 的
 * [textBounds] 参数注入；其余几何全部在这里，可 JVM 单测。
 */

/**
 * 从最上层往下找第一个被 [at] 点中的元素（单点橡皮 / SELECT 点选）。
 *
 * 笔迹按线宽半径逐段判；矩形形状含内部（点进框里就中），直线/箭头只判主干；
 * 文字用 [textBounds] 传入的真实测量框（多行按 `\n` 分行）；马赛克按轨迹包围盒。
 * 橡皮笔迹自身不参与命中——点在已擦区域上不该把橡皮轨迹删掉（内容回不来）。
 */
internal fun hitTopElement(
    elements: List<AnnotationElement>,
    at: Pt,
    fit: ContainFit,
    textBounds: (TextElement) -> PxRect,
): AnnotationElement? = elements.asReversed().firstOrNull { el ->
    el.hitBody(at, fit, textBounds)
}

/** 单个元素的本体命中（已处理旋转；旋转是刚体变换，把命中点逆旋回局部坐标再判）。 */
internal fun AnnotationElement.hitBody(
    at: Pt,
    fit: ContainFit,
    textBounds: (TextElement) -> PxRect,
): Boolean {
    val rot = rotationRadOf()
    val local = if (rot == 0f) at else {
        val box = hitBounds(this, fit, textBounds)
        rotateAround(at, box.center, -rot)
    }
    return hitLocal(this, local, fit, textBounds)
}

/**
 * 元素命中框（图片像素）。文字用 [textBounds] 注入的真实测量框；
 * 序号用圆底外接盒；其余走模型层包围盒。
 */
internal fun hitBounds(
    element: AnnotationElement,
    fit: ContainFit,
    textBounds: (TextElement) -> PxRect,
): PxRect = when (element) {
    is TextElement -> textBounds(element)
    is NumberElement -> element.badgeBounds
    else -> element.bounds()
}

/** 未旋转元素的命中（[at] 已在元素局部坐标系）。 */
private fun hitLocal(
    el: AnnotationElement,
    at: Pt,
    fit: ContainFit,
    textBounds: (TextElement) -> PxRect,
): Boolean = when (el) {
    is StrokeElement -> !el.erase && (
        if (el.points.size == 1) {
            pointNearSegment(at, el.points[0], el.points[0], el.width / 2f)
        } else {
            el.points.zipWithNext().any { (a, b) -> pointNearSegment(at, a, b, el.width / 2f) }
        }
        )

    is ShapeElement -> {
        val nearEdge = shapeSegments(el.kind, el.start, el.end)
            .any { (a, b) -> pointNearSegment(at, a, b, el.width / 2f) }
        // 点进形状内部也能选中（空心形状点中心是常见操作）；矩形直接判外接盒。
        val inside = when {
            el.kind == ElementKind.RECT -> el.bounds.contains(at)
            el.kind.isClosedShape() -> pointInPolygon(at, shapeOutlinePoints(el.kind, el.start, el.end))
            else -> false
        }
        nearEdge || inside
    }

    is TextElement -> textBounds(el).contains(at)
    is NumberElement -> el.badgeBounds.contains(at)
    is MosaicElement -> trailOrRectHit(el.points, el.width, el.rect, at)
    is BlurElement -> trailOrRectHit(el.points, el.width, el.rect, at)
    is StickerElement -> el.rect.normalized().contains(at)
}

/** 轨迹型元素（马赛克/模糊）命中：涂抹轨迹线宽邻域 ∪ 选区填充矩形。 */
private fun trailOrRectHit(points: List<Pt>, width: Float, rect: PxRect?, at: Pt): Boolean {
    val nearTrail = when {
        points.isEmpty() -> false
        points.size == 1 -> pointNearSegment(at, points[0], points[0], width / 2f)
        else -> points.zipWithNext().any { (a, b) ->
            pointNearSegment(at, a, b, width / 2f)
        }
    }
    return nearTrail || rect?.normalized()?.contains(at) == true
}
