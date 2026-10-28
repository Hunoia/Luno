package hunoia.luno.gridpanel.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 顶部指示条与拾取浮层共用的页点窗口：页数多时不能超宽、虚页入口不能丢。 */
class PageIndicatorLayoutTest {

    private fun pages(slots: List<PageDotSlot>): List<Int> =
        slots.filterIsInstance<PageDotSlot.Page>().map { it.index }

    @Test
    fun `all pages when they fit`() {
        val slots = pageDotSlots(pageCount = 3, includeVirtual = true, centerPage = 0, maxSlots = 9)
        assertEquals(listOf(0, 1, 2, 3), pages(slots))
        assertTrue(slots.none { it is PageDotSlot.Ellipsis })
    }

    @Test
    fun `window keeps virtual page and stays within budget`() {
        val slots = pageDotSlots(pageCount = 20, includeVirtual = true, centerPage = 0, maxSlots = 9)
        assertTrue("slots=${slots.size}", slots.size <= 9)
        // 虚页点（20）永远保留在最后。
        assertEquals(20, (slots.last() as PageDotSlot.Page).index)
        // 以当前页 0 为中心：从 0 开始，右侧有省略号。
        assertTrue(slots.contains(PageDotSlot.Page(0)))
        assertTrue(slots.contains(PageDotSlot.Ellipsis))
    }

    @Test
    fun `window follows active page and keeps both ellipses`() {
        val slots = pageDotSlots(pageCount = 20, includeVirtual = false, centerPage = 10, maxSlots = 9)
        assertTrue("slots=${slots.size}", slots.size <= 9)
        assertEquals(PageDotSlot.Ellipsis, slots.first())
        assertEquals(PageDotSlot.Ellipsis, slots.last())
        assertTrue(pages(slots).contains(10))
        // 以 10 为中心，窗口内页码连续。
        val real = pages(slots)
        assertTrue(real.zipWithNext().all { (a, b) -> b == a + 1 })
    }

    @Test
    fun `narrow window keeps virtual page`() {
        val slots = pageDotSlots(pageCount = 20, includeVirtual = true, centerPage = 5, maxSlots = 3)
        assertTrue("slots=${slots.size}", slots.size <= 3)
        assertEquals(20, (slots.last() as PageDotSlot.Page).index)
        assertTrue(pages(slots).contains(5))
    }

    @Test
    fun `centers stay inside card width`() {
        val slots = pageDotSlots(pageCount = 20, includeVirtual = true, centerPage = 10, maxSlots = 9)
        val centers = pageDotCentersPx(
            slots = slots,
            pageCount = 20,
            cardWidthPx = 360f,
            density = 1f,
        )
        assertTrue(centers.all { it > 0f && it < 360f })
    }
}
