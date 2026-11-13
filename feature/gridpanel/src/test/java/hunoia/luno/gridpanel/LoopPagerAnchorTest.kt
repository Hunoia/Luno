package hunoia.luno.gridpanel

import hunoia.luno.gridpanel.geometry.loopAnchorIndex
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 循环 pager 的首页锚点：首开与「切换面板」必须用同一个解。
 *
 * 这里钉住的是回归：切换面板后 pager 若沿用上一块面板的绝对索引，
 * 真实页 = index % pageCount 会落在任意页（表现为「从底栏切换后不在首页」）。
 */
class LoopPagerAnchorTest {

    @Test
    fun `锚点落在真实第 0 页`() {
        for (pages in 1..7) {
            assertEquals(0, loopAnchorIndex(pages) % pages)
        }
    }

    @Test
    fun `锚点两侧都留出翻页空间`() {
        for (pages in 2..7) {
            assertTrue(loopAnchorIndex(pages) >= 100_000)
        }
    }

    @Test
    fun `与首开 initialPage 同解`() {
        for (pages in 1..7) {
            assertEquals(1_000_000 - 1_000_000 % pages, loopAnchorIndex(pages))
        }
    }

    @Test
    fun `非法页数退化为真实页`() {
        assertEquals(0, loopAnchorIndex(0))
        assertEquals(2, loopAnchorIndex(-1, real = 2))
    }
}
