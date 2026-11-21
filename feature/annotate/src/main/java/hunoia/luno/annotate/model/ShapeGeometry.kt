package hunoia.luno.annotate.model

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.round
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.cos
import kotlin.math.sin

/**
 * 快速标注器扩展形状的轮廓顶点（图片像素坐标，由 [start]/[end] 定义的外接盒派生）。
 *
 * 渲染（[AnnotateGeometryCache.shapePath]）与命中/擦除（[shapeSegments]）共用
 * 同一份顶点，保证「画出来的形状」和「擦得掉的形状」一致。顶点不含闭合重复点。
 *
 * - 闭合形状（圆/三角/圆角矩形）：首尾由调用方闭合。
 * - 对勾：开放折线（3 点）。
 * - 叉：两条对角线，不在这里（[shapeCrossSegments] 单独给）。
 */
fun shapeOutlinePoints(kind: ElementKind, start: Pt, end: Pt): List<Pt> {
    val l = minOf(start.x, end.x)
    val t = minOf(start.y, end.y)
    val r = maxOf(start.x, end.x)
    val b = maxOf(start.y, end.y)
    val w = (r - l).coerceAtLeast(0f)
    val h = (b - t).coerceAtLeast(0f)
    val cx = (l + r) / 2f
    val cy = (t + b) / 2f
    return when (kind) {
        ElementKind.ELLIPSE -> {
            // 椭圆：32 段采样（足够圆滑，命中/擦除近似一致）。
            val a = w / 2f
            val bb = h / 2f
            (0 until 32).map { i ->
                val ang = 2.0 * PI * i / 32.0
                Pt(cx + a * cos(ang).toFloat(), cy + bb * sin(ang).toFloat())
            }
        }

        ElementKind.TRIANGLE -> listOf(
            Pt(l, b),
            Pt(r, b),
            Pt(cx, t),
        )

        ElementKind.ROUND_RECT -> {
            // 圆角矩形：圆角半径 = min(w,h) × 0.25，每角 4 段采样。
            val rCorner = (minOf(w, h) * 0.25f).coerceAtLeast(0f)
            val pts = ArrayList<Pt>(16)
            // 4 个圆角：TR(0→90°) BR(90→180) BL(180→270) TL(270→360)。
            fun arc(ccx: Float, ccy: Float, startDeg: Double) {
                for (k in 0..3) {
                    val ang = startDeg + 90.0 * k / 4.0
                    pts += Pt(ccx + rCorner * cos(ang * PI / 180.0).toFloat(), ccy + rCorner * sin(ang * PI / 180.0).toFloat())
                }
            }
            arc(r - rCorner, t + rCorner, 270.0)
            arc(r - rCorner, b - rCorner, 0.0)
            arc(l + rCorner, b - rCorner, 90.0)
            arc(l + rCorner, t + rCorner, 180.0)
            pts
        }

        ElementKind.CHECK -> listOf(
            Pt(l + 0.20f * w, t + 0.52f * h),
            Pt(l + 0.42f * w, t + 0.76f * h),
            Pt(r - 0.18f * w, t + 0.24f * h),
        )

        // 其余形状（矩形/直线/箭头）不走这里，保留既有实现。
        else -> emptyList()
    }
}

/** 叉（×）：两条对角线。 */
fun shapeCrossSegments(start: Pt, end: Pt): List<Pair<Pt, Pt>> {
    val l = minOf(start.x, end.x)
    val t = minOf(start.y, end.y)
    val r = maxOf(start.x, end.x)
    val b = maxOf(start.y, end.y)
    return listOf(
        Pt(l, t) to Pt(r, b),
        Pt(r, t) to Pt(l, b),
    )
}

/** 闭合形状（点进内部也算命中 / 可填充）：矩形/椭圆/圆角矩形/三角。 */
fun ElementKind.isClosedShape(): Boolean = when (this) {
    ElementKind.RECT, ElementKind.ELLIPSE, ElementKind.ROUND_RECT,
    ElementKind.TRIANGLE,
    -> true

    else -> false
}

/**
 * 形状「实际画出来的边段」（图片像素坐标）。
 *
 * 点选命中（ElementHit）与矩形擦（erasedBy）共用这一份线段表，保证「画出来的
 * 形状」和「擦得掉 / 点得中的形状」一致；渲染侧 [shapeOutlinePoints] 与这里
 * 同源（闭合形状的边段 = 轮廓顶点顺次相连 + 首尾闭合）。
 *
 * - 矩形：外接盒四条边；
 * - 直线/箭头/虚线：主干一段；
 * - 叉：两条对角线；
 * - 对勾：开放折线（3 点，不闭合）；
 * - 其余闭合形状：轮廓顶点顺次相连 + 首尾闭合段。
 */
fun shapeSegments(kind: ElementKind, start: Pt, end: Pt): List<Pair<Pt, Pt>> {
    val outline = shapeOutlinePoints(kind, start, end)
    return when (kind) {
        ElementKind.RECT -> {
            val b = PxRect.fromPoints(start, end)
            val tl = Pt(b.left, b.top)
            val tr = Pt(b.right, b.top)
            val br = Pt(b.right, b.bottom)
            val bl = Pt(b.left, b.bottom)
            listOf(tl to tr, tr to br, br to bl, bl to tl)
        }

        ElementKind.LINE, ElementKind.ARROW, ElementKind.DASHED_LINE -> listOf(start to end)

        ElementKind.CROSS -> shapeCrossSegments(start, end)

        ElementKind.CHECK -> outline.zipWithNext().map { (a, b) -> a to b }

        else ->
            if (outline.size >= 2) {
                outline.zipWithNext().map { (a, b) -> a to b } + (outline.last() to outline.first())
            } else {
                emptyList()
            }
    }
}

/**
 * 直线类形状（LINE / DASHED_LINE / ARROW）拖拽终点的方向吸附（P2b）：
 * 指针方向距 0°/45°/90°/135°（含对侧）不足 5° 时吸附到该方向，
 * 长度保持指针到起点的距离自由变化。其余形状原样返回 [end]。
 */
internal fun snapShapeEnd(start: Pt, end: Pt, kind: ElementKind): Pt {
    val isStraight = kind == ElementKind.LINE ||
        kind == ElementKind.DASHED_LINE ||
        kind == ElementKind.ARROW
    if (!isStraight) return end
    val dx = end.x - start.x
    val dy = end.y - start.y
    val length = sqrt(dx * dx + dy * dy)
    if (length < 0.01f) return end
    val angle = atan2(dy, dx)
    val step = (PI / 4).toFloat()
    val nearest = round(angle / step) * step
    if (abs(angle - nearest) >= SNAP_TOLERANCE_RAD) return end
    return Pt(start.x + cos(nearest) * length, start.y + sin(nearest) * length)
}

/** 直线吸附容差（弧度）：5°，与旋转吸附 [snapRotation] 一致。 */
private const val SNAP_TOLERANCE_RAD = (5.0 * PI / 180.0).toFloat()
