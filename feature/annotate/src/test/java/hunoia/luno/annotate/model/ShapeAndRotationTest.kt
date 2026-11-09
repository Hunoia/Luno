package hunoia.luno.annotate.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI

class ShapeAndRotationTest {

    private val start = Pt(10f, 20f)
    private val end = Pt(110f, 120f)

    @Test
    fun `shape outlines stay inside defining box and are finite`() {
        val kinds = listOf(
            ElementKind.ELLIPSE, ElementKind.TRIANGLE, ElementKind.CHECK,
            ElementKind.STAR, ElementKind.ROUND_RECT, ElementKind.HEART,
        )
        val box = PxRect.fromPoints(start, end)
        for (kind in kinds) {
            val pts = shapeOutlinePoints(kind, start, end)
            assertTrue("$kind has points", pts.isNotEmpty())
            for (p in pts) {
                assertTrue("$kind x finite", p.x.isFinite())
                assertTrue("$kind y finite", p.y.isFinite())
                assertTrue("$kind x in box", p.x in box.left - 1f..box.right + 1f)
                assertTrue("$kind y in box", p.y in box.top - 1f..box.bottom + 1f)
            }
        }
    }

    @Test
    fun `cross is two diagonals`() {
        val segs = shapeCrossSegments(start, end)
        assertEquals(2, segs.size)
        assertEquals(segs[0].first, Pt(start.x, start.y))
        assertEquals(segs[0].second, Pt(end.x, end.y))
        assertEquals(segs[1].first, Pt(end.x, start.y))
        assertEquals(segs[1].second, Pt(start.x, end.y))
    }

    @Test
    fun `point in polygon even odd rule`() {
        val square = listOf(Pt(0f, 0f), Pt(10f, 0f), Pt(10f, 10f), Pt(0f, 10f))
        assertTrue(pointInPolygon(Pt(5f, 5f), square))
        assertTrue(pointInPolygon(Pt(0f, 0f), square))
        assertFalse(pointInPolygon(Pt(15f, 5f), square))
        // 凹五角星：中心在内，星角间的凹陷在外。
        val star = shapeOutlinePoints(ElementKind.STAR, Pt(0f, 0f), Pt(10f, 10f))
        assertTrue(pointInPolygon(Pt(5f, 5f), star))
    }

    @Test
    fun `rotate around center`() {
        val c = Pt(5f, 5f)
        // +90°（屏幕顺时针）：左上 → 右上。
        val r = rotateAround(Pt(0f, 0f), c, (PI / 2).toFloat())
        assertEquals(10f, r.x, 0.01f)
        assertEquals(0f, r.y, 0.01f)
        // 0° 不变。
        assertEquals(Pt(0f, 0f), rotateAround(Pt(0f, 0f), c, 0f))
    }

    @Test
    fun `oriented geometry rotates corners`() {
        val box = PxRect(0f, 0f, 10f, 10f)
        val g0 = orientedGeometry(box, 0f, 20f)
        assertEquals(Pt(0f, 0f), g0.corners[0])
        assertEquals(Pt(10f, 0f), g0.corners[1])
        assertEquals(Pt(5f, -20f), g0.rotate)

        val g90 = orientedGeometry(box, (PI / 2).toFloat(), 20f)
        // 绕中心 (5,5) +90°：TL(0,0) → (10,0)。
        assertEquals(10f, g90.corners[0].x, 0.01f)
        assertEquals(0f, g90.corners[0].y, 0.01f)
    }

    @Test
    fun `handle hit detects corner and rotate`() {
        val fit = ContainFit.identity(100, 100)
        val box = PxRect(50f, 50f, 100f, 100f)
        val geom = orientedGeometry(box, 0f, 20f)
        val t = 5f
        // 角：TL(50,50)。
        assertEquals(ElementHandle.TOP_LEFT, hitElementHandle(geom, fit, Pt(52f, 52f), t))
        // 角：BR(100,100)。
        assertEquals(ElementHandle.BOTTOM_RIGHT, hitElementHandle(geom, fit, Pt(99f, 99f), t))
        // 旋转柄：TOP 外 20 → (75,30)。
        assertEquals(ElementHandle.ROTATE, hitElementHandle(geom, fit, Pt(75f, 30f), t))
        // 空白（原来的边中点现在不算柄）。
        assertNull(hitElementHandle(geom, fit, Pt(75f, 51f), t))
        assertNull(hitElementHandle(geom, fit, Pt(200f, 200f), t))
    }

    @Test
    fun `min selection box expands tiny element around its center`() {
        val fit = ContainFit.identity(100, 100)
        val tiny = PxRect(48f, 48f, 52f, 52f)
        val box = minSelectionBox(tiny, fit, 48f)
        assertEquals(48f, box.width, 0.01f)
        assertEquals(48f, box.height, 0.01f)
        assertEquals(tiny.center.x, box.center.x, 0.01f)
        assertEquals(tiny.center.y, box.center.y, 0.01f)
    }

    @Test
    fun `min selection box only expands the short side of a long line`() {
        val fit = ContainFit.identity(300, 100)
        val line = PxRect(20f, 48f, 220f, 52f)
        val box = minSelectionBox(line, fit, 48f)
        assertEquals(200f, box.width, 0.01f)
        assertEquals(48f, box.height, 0.01f)
        assertEquals(line.center.x, box.center.x, 0.01f)
        assertEquals(line.center.y, box.center.y, 0.01f)
    }

    @Test
    fun `min selection box stays constant in view space when zoomed`() {
        val fit = ContainFit.identity(100, 100).withViewTransform(2f, Pt(0f, 0f))
        val tiny = PxRect(50f, 50f, 52f, 52f)
        val box = minSelectionBox(tiny, fit, 48f)
        val view = fit.imageRectToView(box)
        assertEquals(48f, view.width, 0.01f)
        assertEquals(48f, view.height, 0.01f)
        assertEquals(tiny.center.x, box.center.x, 0.01f)
        assertEquals(tiny.center.y, box.center.y, 0.01f)
    }

    @Test
    fun `handle hit leaves center of min box free for body drag`() {
        val fit = ContainFit.identity(100, 100)
        val box = PxRect(26f, 26f, 74f, 74f)
        val geom = orientedGeometry(box, 0f, 20f)
        val threshold = 18f
        // 48dp 框中心距四角 34dp，18dp 命中半径不会吞掉本体拖动区。
        assertNull(hitElementHandle(geom, fit, Pt(50f, 50f), threshold))
        assertEquals(ElementHandle.TOP_LEFT, hitElementHandle(geom, fit, Pt(30f, 30f), threshold))
        assertEquals(ElementHandle.ROTATE, hitElementHandle(geom, fit, Pt(50f, 6f), threshold))
    }

    @Test
    fun `scaling from min box maps proportionally back to real element`() {
        val fit = ContainFit.identity(100, 100)
        val element = ShapeElement(
            id = 1L,
            start = Pt(48f, 48f),
            end = Pt(52f, 52f),
            colorArgb = 0xFFFF3B30,
            width = 4f,
            kind = ElementKind.RECT,
        )
        val real = element.bounds()
        val visual = minSelectionBox(real, fit, 48f)
        val to = adjustByHandle(visual, ElementHandle.BOTTOM_RIGHT, Pt(96f, 96f))
        val scaled = element.transformed(visual, to)
        val ratio = to.width / visual.width
        assertEquals(real.width * ratio, scaled.bounds().width, 0.02f)
        assertEquals(real.height * ratio, scaled.bounds().height, 0.02f)
    }

    @Test
    fun `adjust by corner handle`() {
        val box = PxRect(0f, 0f, 10f, 10f)
        assertEquals(PxRect(0f, 3f, 12f, 10f), adjustByHandle(box, ElementHandle.TOP_RIGHT, Pt(12f, 3f)))
        assertEquals(PxRect(-2f, 0f, 10f, 14f), adjustByHandle(box, ElementHandle.BOTTOM_LEFT, Pt(-2f, 14f)))
        assertEquals(PxRect(0f, 0f, 14f, 14f), adjustByHandle(box, ElementHandle.BOTTOM_RIGHT, Pt(14f, 14f)))
    }

    @Test
    fun `number element bounds transform and rotate`() {
        val n = NumberElement(id = 1L, position = Pt(50f, 50f), colorArgb = 0xFFFF3B30, textSize = 40f)
        assertEquals(PxRect(30f, 30f, 70f, 70f), n.badgeBounds)
        assertEquals(PxRect(30f, 30f, 70f, 70f), n.bounds())
        assertEquals(ElementKind.NUMBER, n.kind)
        assertEquals(0f, n.rotationRadOf())

        val from = PxRect.full(100, 100)
        val to = PxRect.full(200, 200)
        val scaled = n.transformed(from, to) as NumberElement
        assertEquals(100f, scaled.position.x, 0.01f)
        assertEquals(100f, scaled.position.y, 0.01f)
        assertEquals(80f, scaled.textSize, 0.01f)

        val moved = n.translated(5f, -3f) as NumberElement
        assertEquals(Pt(55f, 47f), moved.position)

        val rotated = n.withRotation((PI / 4).toFloat()) as NumberElement
        assertEquals((PI / 4).toFloat(), rotated.rotationRad, 0.0001f)
    }

    @Test
    fun `shape erase detects new shapes and respects rotation`() {
        // 椭圆被擦框横穿。
        val ellipse = ShapeElement(id = 1L, start = Pt(0f, 0f), end = Pt(100f, 100f), colorArgb = 0xFFFF3B30, width = 4f, kind = ElementKind.ELLIPSE)
        assertTrue(ellipse.erasedBy(PxRect(40f, -10f, 60f, 110f)))
        assertFalse(ellipse.erasedBy(PxRect(200f, 200f, 220f, 220f)))

        // 叉：擦框只碰一条对角线。
        val cross = ShapeElement(id = 2L, start = Pt(0f, 0f), end = Pt(100f, 100f), colorArgb = 0xFFFF3B30, width = 4f, kind = ElementKind.CROSS)
        assertTrue(cross.erasedBy(PxRect(-10f, -10f, 50f, 50f)))

        // 旋转 90° 的矩形：竖直擦框仍应命中（边已转到竖直）。
        val rect = ShapeElement(id = 3L, start = Pt(0f, 0f), end = Pt(100f, 100f), colorArgb = 0xFFFF3B30, width = 4f, kind = ElementKind.RECT, rotationRad = (PI / 2).toFloat())
        assertTrue(rect.erasedBy(PxRect(40f, -10f, 60f, 110f)))

        // 序号不被矩形擦（同文字语义，走选中删除）。
        val num = NumberElement(id = 4L, position = Pt(50f, 50f), colorArgb = 0xFFFF3B30, textSize = 40f)
        assertFalse(num.erasedBy(PxRect.full(100, 100)))
    }
}
