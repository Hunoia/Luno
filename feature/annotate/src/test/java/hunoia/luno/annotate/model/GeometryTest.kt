package hunoia.luno.annotate.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GeometryTest {

    @Test
    fun `contain fit letterboxes and centers`() {
        val fit = ContainFit.of(
            imageWidth = 100f,
            imageHeight = 50f,
            viewWidth = 200f,
            viewHeight = 200f,
        )
        assertEquals(2f, fit.scale, 0.001f)
        assertEquals(0f, fit.offsetX, 0.001f)
        assertEquals(50f, fit.offsetY, 0.001f)
        assertEquals(200f, fit.drawnWidth, 0.001f)
        assertEquals(100f, fit.drawnHeight, 0.001f)
    }

    @Test
    fun `image and view coordinates round trip`() {
        val fit = ContainFit.of(1440f, 3200f, 1080f, 2400f)
        val imagePoint = Pt(321.5f, 1234.25f)
        val viewPoint = fit.imageToView(imagePoint)
        val back = fit.viewToImage(viewPoint)
        assertEquals(imagePoint.x, back.x, 0.01f)
        assertEquals(imagePoint.y, back.y, 0.01f)

        val rect = PxRect(10f, 20f, 300f, 400f)
        val roundTrip = fit.viewRectToImage(fit.imageRectToView(rect))
        assertEquals(rect.left, roundTrip.left, 0.01f)
        assertEquals(rect.bottom, roundTrip.bottom, 0.01f)
    }

    @Test
    fun `identity fit maps one to one`() {
        val fit = ContainFit.identity(1440, 3200)
        assertEquals(1f, fit.scale, 0f)
        assertEquals(0f, fit.offsetX, 0f)
        assertEquals(0f, fit.offsetY, 0f)
        assertEquals(Pt(42f, 84f), fit.imageToView(Pt(42f, 84f)))
    }

    @Test
    fun `rect normalizes reversed drags`() {
        val reversed = PxRect(100f, 200f, 40f, 50f)
        assertTrue(reversed.isReversed)
        assertEquals(PxRect(40f, 50f, 100f, 200f), reversed.normalized())
    }

    @Test
    fun `rect clamps into image bounds`() {
        val clamped = PxRect(-20f, -5f, 120f, 90f).clampTo(100f, 80f)
        assertEquals(PxRect(0f, 0f, 100f, 80f), clamped)
    }

    @Test
    fun `tiny rect counts as no selection`() {
        assertTrue(PxRect(0f, 0f, 4f, 400f).isEmpty())
        assertFalse(PxRect(0f, 0f, 8f, 400f).isEmpty())
    }

    @Test
    fun `segment intersects rect when crossing, containing or disjoint`() {
        // 横穿：端点都在框外。
        assertTrue(segmentIntersectsRect(Pt(0f, 50f), Pt(100f, 50f), PxRect(40f, 40f, 60f, 60f)))
        // 端点在框内。
        assertTrue(segmentIntersectsRect(Pt(45f, 45f), Pt(200f, 200f), PxRect(40f, 40f, 60f, 60f)))
        // 完全错开。
        assertFalse(segmentIntersectsRect(Pt(0f, 0f), Pt(10f, 10f), PxRect(40f, 40f, 60f, 60f)))
        // 反向矩形也能判（先 normalize）。
        assertTrue(segmentIntersectsRect(Pt(0f, 50f), Pt(100f, 50f), PxRect(60f, 60f, 40f, 40f)))
    }

    @Test
    fun `collinear overlap counts as intersection`() {
        assertTrue(segmentsIntersect(Pt(0f, 0f), Pt(100f, 0f), Pt(50f, 0f), Pt(150f, 0f)))
        assertFalse(segmentsIntersect(Pt(0f, 0f), Pt(40f, 0f), Pt(50f, 0f), Pt(150f, 0f)))
    }
}
