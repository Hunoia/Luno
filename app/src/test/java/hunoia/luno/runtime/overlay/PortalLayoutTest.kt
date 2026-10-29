package hunoia.luno.runtime.overlay

import androidx.compose.ui.geometry.Offset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 幽灵卡动作槽的几何/命中共用同一份纯函数：渲染与松手判定一旦分叉，就会出现
 * “看着拖到复制上了、松手却没执行”。这里把坐标口径钉死。
 */
class PortalLayoutTest {

    private fun slots(count: Int) = PortalLayout.slotRects(
        screenWidth = 1000f,
        screenHeight = 2000f,
        insetBottom = 50f,
        count = count,
        density = 2f,
    )

    @Test
    fun `slots sit bottom-centered above the inset`() {
        val rects = slots(3)
        assertEquals(3, rects.size)
        // slot=112, gap=24, row=384 -> left=308; bottom=2000-50-56=1894
        assertEquals(308f, rects[0].left, 0.01f)
        assertEquals(420f, rects[0].right, 0.01f)
        assertEquals(1782f, rects[0].top, 0.01f)
        assertEquals(1894f, rects[0].bottom, 0.01f)
        assertEquals(444f, rects[1].left, 0.01f)
        assertEquals(580f, rects[2].left, 0.01f)
    }

    @Test
    fun `hit returns the slot under the finger`() {
        val rects = slots(3)
        assertEquals(0, PortalLayout.hit(rects, Offset(350f, 1800f)))
        assertEquals(1, PortalLayout.hit(rects, Offset(500f, 1850f)))
        assertEquals(2, PortalLayout.hit(rects, Offset(650f, 1880f)))
    }

    @Test
    fun `points outside slots and empty layouts miss`() {
        val rects = slots(3)
        assertNull(PortalLayout.hit(rects, Offset(500f, 900f)))
        assertNull(PortalLayout.hit(emptyList(), Offset(500f, 1850f)))
        assertNull(PortalLayout.hit(slots(0), Offset(500f, 1850f)))
        assertTrue(slots(0).isEmpty())
    }
}
