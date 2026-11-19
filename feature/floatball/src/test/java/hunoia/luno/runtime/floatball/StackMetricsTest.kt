package hunoia.luno.runtime.floatball

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 通知胶囊滚动栈的几何口径：视口高、可滚动距离、夹取与「按下点 → 行号」。
 *
 * 窗口高与触摸命中必须共用这一份计算，否则滚过几行后点按会落到错误的通知上。
 * 每颗胶囊之间有透明间距，布局与命中都按 row + gap 前进。
 */
class StackMetricsTest {

    private val row = 140
    private val gap = 12

    private fun metrics(items: Int, visible: Int, rowGap: Int = 0) =
        StackMetrics(
            rowHeightPx = row,
            itemCount = items,
            visibleRows = visible,
            gapPx = rowGap,
        )

    @Test
    fun `single item does not scroll`() {
        val m = metrics(items = 1, visible = 3, rowGap = gap)

        assertEquals(row, m.viewportHeightPx)
        assertEquals(0, m.maxScrollPx)
        assertEquals(0f, m.clamp(-500f), 0.001f)
        assertEquals(0f, m.clamp(500f), 0.001f)
    }

    @Test
    fun `viewport caps at visible rows`() {
        val m = metrics(items = 10, visible = 3, rowGap = gap)

        // 3 颗 + 之间 2 段间距。
        assertEquals(row * 3 + gap * 2, m.viewportHeightPx)
        assertEquals(row * 10 + gap * 9, m.contentHeightPx)
        assertEquals((row + gap) * 7, m.maxScrollPx)
    }

    @Test
    fun `fewer items than visible rows shows all of them`() {
        val m = metrics(items = 2, visible = 5, rowGap = gap)

        assertEquals(row * 2 + gap, m.viewportHeightPx)
        assertEquals(0, m.maxScrollPx)
    }

    @Test
    fun `clamp keeps offset inside scroll range`() {
        val m = metrics(items = 10, visible = 3, rowGap = gap)
        val maxScroll = (row + gap) * 7

        assertEquals(0f, m.clamp(120f), 0.001f)
        assertEquals(-maxScroll.toFloat(), m.clamp(-10_000f), 0.001f)
        assertEquals(-(maxScroll / 2).toFloat(), m.clamp(-(maxScroll / 2).toFloat()), 0.001f)
    }

    @Test
    fun `row hit testing accounts for scroll offset`() {
        val m = metrics(items = 10, visible = 3, rowGap = gap)
        val pitch = row + gap

        // 顶部：第 0 颗覆盖 [0, row)，间距仍归第 0 颗。
        assertEquals(0, m.rowAt(0f, offset = 0f))
        assertEquals(0, m.rowAt(row - 1f, offset = 0f))
        assertEquals(0, m.rowAt(row.toFloat(), offset = 0f))
        assertEquals(1, m.rowAt(pitch.toFloat(), offset = 0f))
        // 向下滚 2 颗：窗口顶部现在对应第 2 条。
        assertEquals(2, m.rowAt(0f, offset = -(pitch * 2).toFloat()))
        assertEquals(4, m.rowAt((pitch * 2).toFloat(), offset = -(pitch * 2).toFloat()))
    }

    @Test
    fun `row hit testing clamps to the last item`() {
        val m = metrics(items = 3, visible = 3, rowGap = gap)

        assertEquals(2, m.rowAt(row * 10f, offset = 0f))
        assertEquals(0, m.rowAt(-row.toFloat(), offset = 0f))
    }

    @Test
    fun `empty box does not crash hit testing`() {
        val m = metrics(items = 0, visible = 3, rowGap = gap)

        assertEquals(0, m.rowAt(0f, offset = 0f))
        assertEquals(0, m.viewportHeightPx)
        assertEquals(0, m.maxScrollPx)
    }

    @Test
    fun `negative gap is treated as no gap`() {
        val m = metrics(items = 3, visible = 2, rowGap = -20)

        assertEquals(row * 2, m.viewportHeightPx)
        assertEquals(row * 3, m.contentHeightPx)
        assertEquals(row, m.pitchPx)
    }
}
