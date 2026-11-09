package hunoia.luno.annotate.model

import org.junit.Assert.assertEquals
import org.junit.Test

class AnnotateGestureMathTest {

    // contain：1000×2000 图放入 1000×1000 视图 → scale 0.5，offsetX 250。
    private val fit = ContainFit.of(
        imageWidth = 1000f,
        imageHeight = 2000f,
        viewWidth = 1000f,
        viewHeight = 1000f,
    )

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
