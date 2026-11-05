package hunoia.luno.annotate.model

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

/**
 * 快速标注器扩展形状的轮廓顶点（图片像素坐标，由 [start]/[end] 定义的外接盒派生）。
 *
 * 渲染（[AnnotateGeometryCache.shapePath]）与矩形擦（[erasedBy]）共用同一份顶点，
 * 保证「画出来的形状」和「擦得掉的形状」一致。顶点不含闭合重复点。
 *
 * - 闭合形状（圆/三角/五角星/圆角矩形/心形）：首尾由调用方闭合。
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

        ElementKind.STAR -> {
            // 五角星：10 个顶点，外接/内接半径交替，内半径 = 外半径 × 0.382。
            val a = w / 2f
            val bb = h / 2f
            val ro = min(a, bb)
            val ri = ro * 0.382f
            (0 until 10).map { i ->
                val ang = -PI / 2.0 + PI * i / 5.0
                val rad = if (i % 2 == 0) ro else ri
                Pt(cx + (rad * cos(ang)).toFloat(), cy + (rad * sin(ang)).toFloat())
            }
        }

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

        ElementKind.HEART -> {
            // 心形：经典参数方程采样 32 点。
            val a = w / 2f
            val bb = h / 2f
            (0 until 32).map { i ->
                val tAng = 2.0 * PI * i / 32.0
                val x = 16.0 * sin(tAng).pow(3)
                val y = 13.0 * cos(tAng) - 5.0 * cos(2.0 * tAng) - 2.0 * cos(3.0 * tAng) - cos(4.0 * tAng)
                Pt(cx + (x / 17.0 * a).toFloat(), cy - (y / 17.0 * bb).toFloat())
            }
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
