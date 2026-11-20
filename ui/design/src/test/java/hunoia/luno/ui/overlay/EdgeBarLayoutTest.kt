package hunoia.luno.ui.overlay

import androidx.compose.ui.geometry.Offset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 边缘动作条的几何 / 命中共用同一份纯函数：识屏工具条与剪贴板拖拽动作条都读它，
 * 渲染与松手判定一旦分叉，就会出现「看着拖到「复制」上了、松手却没执行」。
 * 这里把坐标口径、四边取向、沿边锚定、屏内夹紧与命中钉死。
 */
class EdgeBarLayoutTest {

    private val density = 2f
    private val w = 1000f
    private val h = 2000f
    private val insets = EdgeBarLayout.Insets(top = 60f, bottom = 40f)

    private fun bar(
        finger: Offset,
        count: Int = 3,
        anchor: Float? = null,
    ) = EdgeBarLayout.bar(
        finger = finger,
        screenWidth = w,
        screenHeight = h,
        count = count,
        density = density,
        anchorAxis = anchor,
        insets = insets,
    )

    // ---- 边缘取向 + 带宽 ----

    @Test
    fun `finger near bottom shows the bottom bar`() {
        val b = bar(Offset(500f, 1940f))
        assertNotNull(b)
        assertEquals(EdgeBarEdge.BOTTOM, b!!.edge)
        assertTrue(!b.isVertical)
        // 贴边距 = 12dp = 24px，inset 40px -> 条底边在 2000-40-24
        assertEquals(1936f, b.bounds.bottom, 0.01f)
        // 条厚 = 单格 48dp + 上下 padding 6dp
        val depth = (EdgeBarLayout.SlotDp + EdgeBarLayout.PaddingDp * 2) * density
        assertEquals(1936f - depth, b.bounds.top, 0.01f)
    }

    @Test
    fun `finger near left edge shows the vertical left bar`() {
        val b = bar(Offset(30f, 900f))
        assertNotNull(b)
        assertEquals(EdgeBarEdge.LEFT, b!!.edge)
        assertTrue(b.isVertical)
        assertEquals(24f, b.bounds.left, 0.01f)
    }

    @Test
    fun `finger near right edge shows the vertical right bar`() {
        val b = bar(Offset(980f, 900f))
        assertEquals(EdgeBarEdge.RIGHT, b!!.edge)
        assertEquals(w - 24f, b.bounds.right, 0.01f)
    }

    @Test
    fun `finger near top edge respects the top inset`() {
        val b = bar(Offset(500f, 100f))
        assertNotNull(b)
        assertEquals(EdgeBarEdge.TOP, b!!.edge)
        assertEquals(60f + 24f, b.bounds.top, 0.01f)
    }

    @Test
    fun `finger in the middle shows no bar`() {
        assertNull(bar(Offset(500f, 1000f)))
    }

    @Test
    fun `screen corners resolve deterministically`() {
        // 左下角：底边与左边都在带宽内，取更近的一条；这里底边更近（10 vs 20）。
        assertEquals(EdgeBarEdge.BOTTOM, bar(Offset(20f, 1990f))!!.edge)
        // 左上角：左边距离 20，顶边距离 40 -> 选左边。
        assertEquals(EdgeBarEdge.LEFT, bar(Offset(20f, 100f))!!.edge)
    }

    @Test
    fun `vertical bar keeps its axis while the finger slides along the edge`() {
        val b = EdgeBarLayout.bar(
            finger = Offset(30f, 1500f),
            screenWidth = w,
            screenHeight = h,
            density = density,
            count = 3,
            insets = insets,
            anchorAxis = 900f,
        )!!
        assertEquals(EdgeBarEdge.LEFT, b.edge)
        // 锚点 900f 是条中心；条高 = 3 格 + 2 缝 + 2 padding。
        assertEquals(900f - b.bounds.height / 2f, b.bounds.top, 0.01f)
    }

    @Test
    fun `zero screen size has no geometry`() {
        assertNull(
            EdgeBarLayout.bar(
                finger = Offset.Zero,
                screenWidth = 0f,
                screenHeight = 0f,
                count = 4,
                density = density,
            ),
        )
        assertNull(EdgeBarLayout.edgeFor(Offset.Zero, 0f, 0f, density))
    }

    // ---- 沿边锚定 + 命中 ----

    @Test
    fun `bar is anchored once and does not follow the finger`() {
        val anchor = Offset(500f, 1940f)
        val locked = bar(anchor, anchor = anchor.x)!!
        // 手指沿底边滑动：整条位置不变，只有指到哪一格会变（否则永远选不中同一个动作）。
        val slid = bar(Offset(800f, 1940f), anchor = anchor.x)!!
        assertEquals(locked.bounds.left, slid.bounds.left, 0.01f)
        assertEquals(locked.bounds.width, slid.bounds.width, 0.01f)
    }

    @Test
    fun `hit picks the nearest slot and misses outside the bar`() {
        val b = bar(Offset(500f, 1940f))!!
        b.slots.forEachIndexed { index, rect ->
            assertEquals(index, EdgeBarLayout.hit(b, Offset(rect.center.x, rect.center.y)))
        }
        // 条体范围外：未命中（落空回退默认）。
        assertNull(EdgeBarLayout.hit(b, Offset(500f, 500f)))
        // 格间缝隙也算最近的一格，不逼用户压线。
        val between = Offset(
            (b.slots[0].right + b.slots[1].left) / 2f,
            b.slots[0].center.y,
        )
        assertTrue(EdgeBarLayout.hit(b, between) in 0..1)
    }

    // ---- 屏内夹紧 ----

    @Test
    fun `too many slots shrink to stay on screen`() {
        val many = bar(Offset(500f, 1990f), count = 40)!!
        assertTrue(many.bounds.left >= 0f)
        assertTrue(many.bounds.right <= w)
        // 整条等比缩小后每格仍然命中得到。
        assertEquals(0, EdgeBarLayout.hit(many, many.slots.first().center))
        assertEquals(39, EdgeBarLayout.hit(many, many.slots.last().center))
    }

    @Test
    fun `anchor near the corner is clamped to the safe area`() {
        val nearStart = bar(Offset(500f, 1940f), anchor = 0f)!!
        val margin = EdgeBarLayout.EdgeMarginDp * density
        assertEquals(margin, nearStart.bounds.left, 0.01f)
        val nearEnd = bar(Offset(500f, 1940f), anchor = w)!!
        assertEquals(w - margin, nearEnd.bounds.right, 0.01f)
    }

    @Test
    fun `translated keeps shape and moves bounds`() {
        val b = bar(Offset(500f, 1940f))!!
        val moved = b.translated(10f, -20f)
        assertEquals(b.edge, moved.edge)
        assertEquals(b.bounds.left + 10f, moved.bounds.left, 0.01f)
        assertEquals(b.bounds.top - 20f, moved.bounds.top, 0.01f)
        assertEquals(b.slots.size, moved.slots.size)
    }
}
