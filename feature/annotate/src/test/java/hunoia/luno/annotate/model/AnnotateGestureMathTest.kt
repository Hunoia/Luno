package hunoia.luno.annotate.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AnnotateGestureMathTest {

    // contain：1000×2000 图放入 1000×1000 视图 → scale 0.5，offsetX 250。
    private val fit = ContainFit.of(
        imageWidth = 1000f,
        imageHeight = 2000f,
        viewWidth = 1000f,
        viewHeight = 1000f,
    )

    private val selection = PxRect(100f, 200f, 300f, 400f)

    @Test
    fun `cornerAt finds the nearest corner within threshold`() {
        // 选区视图矩形：300,100 → 400,200。
        assertEquals(Corner.TOP_LEFT, cornerAt(selection, fit, Pt(305f, 105f), 28f))
        assertEquals(Corner.TOP_RIGHT, cornerAt(selection, fit, Pt(395f, 105f), 28f))
        assertEquals(Corner.BOTTOM_LEFT, cornerAt(selection, fit, Pt(305f, 195f), 28f))
        assertEquals(Corner.BOTTOM_RIGHT, cornerAt(selection, fit, Pt(395f, 195f), 28f))
        // 离四个角都远 → 命中不了。
        assertNull(cornerAt(selection, fit, Pt(350f, 150f), 28f))
    }

    @Test
    fun `adjustCorner moves only the requested corner`() {
        assertEquals(PxRect(100f, 300f, 500f, 400f), adjustCorner(selection, Corner.TOP_RIGHT, Pt(500f, 300f)))
        assertEquals(PxRect(50f, 200f, 300f, 400f), adjustCorner(selection, Corner.TOP_LEFT, Pt(50f, 200f)))
        assertEquals(PxRect(100f, 200f, 300f, 50f), adjustCorner(selection, Corner.BOTTOM_LEFT, Pt(100f, 50f)))
        assertEquals(PxRect(100f, 200f, 300f, 900f), adjustCorner(selection, Corner.BOTTOM_RIGHT, Pt(300f, 900f)))
    }

    @Test
    fun `translateSelection keeps size and clamps to image edges`() {
        val moved = translateSelection(selection, 50f, 0f, 1000f, 2000f)
        assertEquals(PxRect(150f, 200f, 350f, 400f), moved)

        val clampedLeft = translateSelection(selection, -1000f, 0f, 1000f, 2000f)
        assertEquals(PxRect(0f, 200f, 200f, 400f), clampedLeft)

        val clampedBottom = translateSelection(selection, 0f, 100000f, 1000f, 2000f)
        assertEquals(PxRect(100f, 1800f, 300f, 2000f), clampedBottom)
    }

    @Test
    fun `selectionHolds only true inside the selection view rect`() {
        assertTrue(selectionHolds(selection, fit, Pt(350f, 150f)))
        assertFalse(selectionHolds(selection, fit, Pt(250f, 50f)))
        assertFalse(selectionHolds(selection, fit, Pt(450f, 250f)))
    }

    @Test
    fun `strokeWidth converts screen dp to image px`() {
        assertEquals(18f, strokeWidthInImagePx(styleDp = 3f, density = 3f, fit = fit), 0.01f)
        assertEquals(0.6f, strokeWidthInImagePx(styleDp = 0.1f, density = 3f, fit = fit), 0.01f)
    }

    @Test
    fun `mosaic brush width follows cell size with bounds`() {
        assertEquals(40f, mosaicBrushWidth(8f), 0.01f)
        assertEquals(120f, mosaicBrushWidth(24f), 0.01f)
        assertEquals(256f, mosaicBrushWidth(999f), 0.01f)
    }
}
