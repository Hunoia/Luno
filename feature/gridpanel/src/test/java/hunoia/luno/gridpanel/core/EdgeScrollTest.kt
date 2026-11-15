package hunoia.luno.gridpanel.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 拖拽边缘自动滚动的方向判定（面板缩到只容一行时仍要能往下滚）。 */
class EdgeScrollTest {

    @Test
    fun `tall viewport uses the normal band`() {
        // 400 高、带宽 56：上下各一条，中间大段不滚。
        assertTrue(edgeScrollAt(400f, 10f, 56f)!! < 0f)
        assertNull(edgeScrollAt(400f, 200f, 56f))
        assertTrue(edgeScrollAt(400f, 390f, 56f)!! > 0f)
    }

    @Test
    fun `short viewport still scrolls down near the bottom`() {
        // 可视区只有 80（约一行）：以前 80 < 2×56，上带吃掉整个可视区，
        // 拖到底部也只会上滚，底部两行永远够不到。
        val nearBottom = edgeScrollAt(viewport = 80f, pointerY = 70f, bandPx = 56f)
        assertTrue(nearBottom != null && nearBottom > 0f)
    }

    @Test
    fun `short viewport keeps a dead zone in the middle`() {
        // 带宽收窄到 80×0.45 = 36：36..44 之间两侧都不触发。
        assertNull(edgeScrollAt(viewport = 80f, pointerY = 40f, bandPx = 56f))
        assertTrue(edgeScrollAt(80f, 10f, 56f)!! < 0f)
        assertTrue(edgeScrollAt(80f, 60f, 56f)!! > 0f)
    }

    @Test
    fun `strength is clamped to a usable range`() {
        // 刚进带 25%，压到边缘 100%。
        assertEquals(-0.25f, edgeScrollAt(400f, 55.5f, 56f)!!, 0.001f)
        assertEquals(-1f, edgeScrollAt(400f, 0f, 56f)!!, 0.001f)
    }

    @Test
    fun `invalid sizes do nothing`() {
        assertNull(edgeScrollAt(0f, 10f, 56f))
        assertNull(edgeScrollAt(400f, 10f, 0f))
    }
}
