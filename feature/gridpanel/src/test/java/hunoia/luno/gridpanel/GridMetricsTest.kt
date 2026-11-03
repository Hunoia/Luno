package hunoia.luno.gridpanel

import hunoia.luno.gridpanel.geometry.gridPanelCardHeight
import hunoia.luno.gridpanel.geometry.rowsForCardHeight
import org.junit.Assert.assertEquals
import org.junit.Test

/** 连续卡高 → 行数派生的纯函数回归。 */
class GridMetricsTest {

    @Test
    fun `small remainder does not add a row`() {
        // available=32, cellH=10 → 3.2 行；余数 0.2 < 0.25 → 3 行。
        assertEquals(3, rowsForCardHeight(cardH = 32f, cellH = 10f, gap = 0f, gridPad = 0f, indicator = 0f))
    }

    @Test
    fun `large remainder adds a row`() {
        // available=35 → 3.5 行；余数 0.5 ≥ 0.25 → 4 行。
        assertEquals(4, rowsForCardHeight(cardH = 35f, cellH = 10f, gap = 0f, gridPad = 0f, indicator = 0f))
    }

    @Test
    fun `exact multiple keeps its row count`() {
        assertEquals(3, rowsForCardHeight(cardH = 30f, cellH = 10f, gap = 0f, gridPad = 0f, indicator = 0f))
    }

    @Test
    fun `never drops below one row`() {
        assertEquals(1, rowsForCardHeight(cardH = 0f, cellH = 10f, gap = 0f, gridPad = 0f, indicator = 0f))
        assertEquals(1, rowsForCardHeight(cardH = 5f, cellH = 10f, gap = 0f, gridPad = 0f, indicator = 0f))
    }

    @Test
    fun `gap is counted so the last row is not clipped`() {
        // cellH=10, gap=2, cardH=34：可用 34 恰好装 3 行（3*10 + 2*2 = 34）。
        assertEquals(3, rowsForCardHeight(cardH = 34f, cellH = 10f, gap = 2f, gridPad = 0f, indicator = 0f))
        assertEquals(2, rowsForCardHeight(cardH = 22f, cellH = 10f, gap = 2f, gridPad = 0f, indicator = 0f))
    }

    @Test
    fun `card height follows fraction continuously with a one row floor`() {
        // 4 列、卡宽 324、gap 8、pad 10、indicator 24：cellW=70，最低一行 = 20+24+70。
        val continuous = gridPanelCardHeight(
            columns = 4,
            cardW = 324f,
            cardHeightFraction = 0.9f,
            screenH = 800f,
            gridPad = 10f,
            indicator = 24f,
            gap = 8f,
        )
        assertEquals(720f, continuous, 0.01f)

        val floored = gridPanelCardHeight(
            columns = 4,
            cardW = 324f,
            cardHeightFraction = 0.1f,
            screenH = 800f,
            gridPad = 10f,
            indicator = 24f,
            gap = 8f,
        )
        assertEquals(114f, floored, 0.01f)
    }
}
