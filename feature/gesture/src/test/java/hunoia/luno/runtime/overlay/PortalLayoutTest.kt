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

    private val density = 2f
    private val maxW = PortalLayout.CardMaxWidthDp * density // 560
    private val minW = PortalLayout.CardMinWidthDp * density // 320
    private val estH = PortalLayout.CardEstimatedHeightDp * density // 192

    private fun card(
        finger: Offset,
        screenWidth: Float = 1000f,
        screenHeight: Float = 2000f,
        cardWidthPx: Float = maxW,
        cardHeightPx: Float = estH,
    ) = PortalLayout.cardRect(finger, screenWidth, screenHeight, density, cardWidthPx, cardHeightPx)

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

    /** 卡片中心对齐手指（跟手）；屏内时 center == finger，尺寸用给定的测量值。 */
    @Test
    fun `card is centered on finger and uses given size`() {
        val c = card(Offset(500f, 300f), cardWidthPx = 400f, cardHeightPx = 150f)
        assertEquals(300f, c.left, 0.01f)
        assertEquals(225f, c.top, 0.01f)
        assertEquals(400f, c.width, 0.01f)
        assertEquals(150f, c.height, 0.01f)
        assertEquals(500f, c.center.x, 0.01f)
        assertEquals(300f, c.center.y, 0.01f)
    }

    /** 内容测量的宽度在 [min,max] 区间内时原样采用，不强制封顶/保底。 */
    @Test
    fun `card width honors measured content width within bounds`() {
        val narrow = card(Offset(100f, 100f), cardWidthPx = minW + 40f)
        assertEquals(minW + 40f, narrow.width, 0.01f)
    }

    /** 未测量（0）时回退到封顶宽。 */
    @Test
    fun `card width falls back to max when unmeasured`() {
        val c = card(Offset(100f, 100f), cardWidthPx = 0f)
        assertEquals(maxW, c.width, 0.01f)
        assertEquals(estH, c.height, 0.01f)
    }

    /** 贴边时整卡回推到屏内，不超出右/下边界（中心不再严格等于手指）。 */
    @Test
    fun `card is clamped inside the screen at edges`() {
        val c = card(Offset(990f, 1990f))
        assertEquals(maxW, c.width, 0.01f)
        assertEquals(1000f - maxW, c.left, 0.01f) // 440
        assertEquals(2000f - estH, c.top, 0.01f) // 1808
        assertEquals(1000f, c.right, 0.01f)
        assertEquals(2000f, c.bottom, 0.01f)
    }

    /** 手指在负坐标（理论上不会，但兜底）也夹到 0。 */
    @Test
    fun `card clamps negative finger to origin`() {
        val c = card(Offset(-50f, -30f))
        assertEquals(0f, c.left, 0.01f)
        assertEquals(0f, c.top, 0.01f)
    }
}
