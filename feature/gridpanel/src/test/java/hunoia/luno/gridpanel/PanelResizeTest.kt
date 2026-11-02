package hunoia.luno.gridpanel

import hunoia.luno.gridpanel.geometry.ResizeCorner
import hunoia.luno.gridpanel.geometry.choosePanelResizeCorner
import org.junit.Assert.assertEquals
import org.junit.Test

class PanelResizeTest {

    private fun cornerFor(left: Float, top: Float) = choosePanelResizeCorner(
        cardLeft = left,
        cardTop = top,
        cardWidth = 200f,
        cardHeight = 200f,
        screenWidth = 1000f,
        screenHeight = 1000f,
        insetsTop = 0,
        insetsBottom = 0,
        marginPx = 10f,
    )

    @Test
    fun `panel at right bottom resizes from top start`() {
        assertEquals(ResizeCorner.TOP_START, cornerFor(left = 790f, top = 790f))
    }

    @Test
    fun `panel at left top resizes from bottom end`() {
        assertEquals(ResizeCorner.BOTTOM_END, cornerFor(left = 10f, top = 10f))
    }

    @Test
    fun `panel centered uses first farthest corner deterministically`() {
        // 四角余量相同：按 TOP_START、TOP_END、BOTTOM_START、BOTTOM_END 的顺序取首项。
        assertEquals(ResizeCorner.TOP_START, cornerFor(left = 400f, top = 400f))
    }
}
