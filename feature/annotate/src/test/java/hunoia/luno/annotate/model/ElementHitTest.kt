package hunoia.luno.annotate.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ElementHitTest {

    private val fit = ContainFit.identity(1000, 1000)
    private val textBounds: (TextElement) -> PxRect = { el ->
        // 与模型层估算一致的单行框，命中测试不关心真实量宽。
        PxRect(el.position.x, el.position.y, el.position.x + el.textSize * el.text.length, el.position.y + el.textSize)
    }

    // ---- shapeSegments ----

    @Test
    fun `rect segments are the four edges`() {
        fun canon(a: Pt, b: Pt) = if (a.x < b.x || (a.x == b.x && a.y < b.y)) a to b else b to a
        val segs = shapeSegments(ElementKind.RECT, Pt(100f, 100f), Pt(300f, 200f))
            .map { (a, b) -> canon(a, b) }.toSet()
        assertEquals(4, segs.size)
        assertTrue(Pt(100f, 100f) to Pt(300f, 100f) in segs) // 顶边
        assertTrue(Pt(100f, 200f) to Pt(300f, 200f) in segs) // 底边
        assertTrue(Pt(100f, 100f) to Pt(100f, 200f) in segs) // 左边
        assertTrue(Pt(300f, 100f) to Pt(300f, 200f) in segs) // 右边
    }

    @Test
    fun `straight shapes are single trunk segment`() {
        for (kind in listOf(ElementKind.LINE, ElementKind.ARROW, ElementKind.DASHED_LINE)) {
            val segs = shapeSegments(kind, Pt(0f, 0f), Pt(100f, 50f))
            assertEquals(listOf(Pt(0f, 0f) to Pt(100f, 50f)), segs)
        }
    }

    @Test
    fun `check is open polyline without closing segment`() {
        val segs = shapeSegments(ElementKind.CHECK, Pt(0f, 0f), Pt(100f, 100f))
        assertEquals(2, segs.size)
        // 对勾不闭合：最后一段终点不回到起点。
        assertFalse(segs.last().second == segs.first().first)
    }

    @Test
    fun `closed shapes include closing segment`() {
        for (kind in listOf(
            ElementKind.ELLIPSE, ElementKind.TRIANGLE, ElementKind.ROUND_RECT,
        )) {
            val segs = shapeSegments(kind, Pt(0f, 0f), Pt(100f, 100f))
            assertTrue("$kind 至少 3 段", segs.size >= 3)
            assertEquals("$kind 首尾闭合", segs.first().first, segs.last().second)
        }
    }

    @Test
    fun `cross is two diagonals`() {
        val segs = shapeSegments(ElementKind.CROSS, Pt(0f, 0f), Pt(100f, 100f))
        assertEquals(2, segs.size)
    }

    // ---- erasedBy（矩形擦与点选共用线段表） ----

    @Test
    fun `eraser removes stroke crossing rect but keeps text and mosaic`() {
        val stroke = StrokeElement(1, listOf(Pt(0f, 50f), Pt(200f, 50f)), 0xFF000000, 4f)
        val text = TextElement(2, Pt(10f, 300f), "文字", 0xFF000000, 40f)
        val mosaic = MosaicElement(3, cellSize = 24f, rect = PxRect(0f, 400f, 200f, 500f))
        val state = AnnotateState.of(1000, 1000).copy(elements = listOf(stroke, text, mosaic))

        val erased = AnnotateOps.eraseInRect(state, PxRect(50f, 0f, 150f, 100f))
        assertTrue(erased.elements.none { it is StrokeElement })
        assertTrue(erased.elements.any { it is TextElement })
        assertTrue(erased.elements.any { it is MosaicElement })
    }

    @Test
    fun `eraser removes rotated rect shape via actual segments`() {
        // 旋转 90° 的正方形自对称（绕中心），边段仍在外接盒四条边上：
        // 擅自用外接盒判断的话，完全在盒内部的窄带也会被误判相交。
        val shape = ShapeElement(1, Pt(400f, 400f), Pt(500f, 500f), 0xFF000000, 4f, ElementKind.RECT)
        val rotated = shape.copy(rotationRad = (Math.PI / 2).toFloat())
        val state = AnnotateState.of(1000, 1000).copy(elements = listOf(rotated))
        // 完全落在正方形内部（不碰四条边）的擦除区：不该删。
        val miss = AnnotateOps.eraseInRect(state, PxRect(430f, 430f, 470f, 470f))
        assertEquals(1, miss.elements.size)
        // 跨过顶边（y=400）的窄擦除区：命中。
        val hit = AnnotateOps.eraseInRect(state, PxRect(440f, 390f, 460f, 410f))
        assertTrue(hit.elements.isEmpty())

        // 水平线旋转 90° 后变成过 (500,440) 的竖线；擦左侧横带不该命中。
        val line = ShapeElement(2, Pt(400f, 440f), Pt(600f, 440f), 0xFF000000, 4f, ElementKind.LINE)
        val rotatedLine = line.copy(rotationRad = (Math.PI / 2).toFloat())
        val lineState = AnnotateState.of(1000, 1000).copy(elements = listOf(rotatedLine))
        val missLine = AnnotateOps.eraseInRect(lineState, PxRect(0f, 400f, 300f, 480f))
        assertEquals(1, missLine.elements.size)
        val hitLine = AnnotateOps.eraseInRect(lineState, PxRect(480f, 400f, 520f, 480f))
        assertTrue(hitLine.elements.isEmpty())
    }

    // ---- hitBody / hitTopElement ----

    @Test
    fun `hit picks topmost element first`() {
        val bottom = StrokeElement(1, listOf(Pt(0f, 100f), Pt(500f, 100f)), 0xFF000000, 8f)
        val top = StrokeElement(2, listOf(Pt(400f, 100f), Pt(800f, 100f)), 0xFFFF0000, 8f)
        val hit = hitTopElement(listOf(bottom, top), Pt(450f, 100f), fit, textBounds)
        assertEquals(2L, hit?.id)
        val onlyBottom = hitTopElement(listOf(bottom, top), Pt(100f, 100f), fit, textBounds)
        assertEquals(1L, onlyBottom?.id)
    }

    @Test
    fun `hollow shape hit inside but dash line only on trunk`() {
        val circle = ShapeElement(1, Pt(0f, 0f), Pt(200f, 200f), 0xFF000000, 4f, ElementKind.ELLIPSE)
        assertTrue(circle.hitBody(Pt(100f, 100f), fit, textBounds))

        val line = ShapeElement(2, Pt(0f, 0f), Pt(200f, 200f), 0xFF000000, 4f, ElementKind.LINE)
        assertTrue(line.hitBody(Pt(100f, 100f), fit, textBounds))
        assertFalse(line.hitBody(Pt(150f, 50f), fit, textBounds))
    }

    @Test
    fun `eraser stroke itself is not hittable`() {
        val eraser = StrokeElement(1, listOf(Pt(0f, 100f), Pt(500f, 100f)), 0xFF000000, 12f, erase = true)
        assertFalse(eraser.hitBody(Pt(250f, 100f), fit, textBounds))
    }

    @Test
    fun `mosaic hit by trail or fill rect`() {
        val trail = MosaicElement(1, points = listOf(Pt(0f, 100f), Pt(300f, 100f)), width = 40f, cellSize = 24f)
        assertTrue(trail.hitBody(Pt(150f, 110f), fit, textBounds))
        assertFalse(trail.hitBody(Pt(150f, 300f), fit, textBounds))

        val fill = MosaicElement(2, cellSize = 24f, rect = PxRect(0f, 400f, 200f, 500f))
        assertTrue(fill.hitBody(Pt(100f, 450f), fit, textBounds))
        assertFalse(fill.hitBody(Pt(100f, 550f), fit, textBounds))
    }

    @Test
    fun `number hit by badge bounds`() {
        val number = NumberElement(1, Pt(500f, 500f), 0xFFFF3B30, textSize = 40f)
        assertTrue(number.hitBody(Pt(500f, 500f), fit, textBounds))
        assertTrue(number.hitBody(Pt(518f, 518f), fit, textBounds))
        assertFalse(number.hitBody(Pt(525f, 525f), fit, textBounds))
    }

    @Test
    fun `text hit uses injected real bounds`() {
        val text = TextElement(1, Pt(0f, 0f), "hi", 0xFF000000, 40f)
        assertTrue(text.hitBody(Pt(10f, 10f), fit, textBounds))
        assertFalse(text.hitBody(Pt(90f, 10f), fit, textBounds))
    }
}
