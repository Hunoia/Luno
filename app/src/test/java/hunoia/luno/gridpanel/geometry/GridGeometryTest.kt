package hunoia.luno.gridpanel.geometry

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 网格几何公式回归：渲染（rememberGridMetrics）与落盘（DataLoader）共用
 * [gridPanelVisibleRows]，两边行数不一致就是「看着放得下、落盘却挤出去」。
 * 纯函数锁定公式语义；改公式时这里是有意要更新的锚点。
 */
class GridGeometryTest {

    @Test
    fun `gap shrinks as columns grow`() {
        assertEquals(10f, gapFor(2).value, 0f)
        assertEquals(10f, gapFor(3).value, 0f)
        assertEquals(8f, gapFor(4).value, 0f)
        assertEquals(6f, gapFor(5).value, 0f)
        assertEquals(4f, gapFor(6).value, 0f)
    }

    @Test
    fun `visible rows computes the shared formula`() {
        // 360x800 屏、4 列、卡宽 0.9 / 卡高 0.9：
        // gap=8, cellW=(324-20-24)/4=70, visibleH=720-20-24=676, stepH=78
        // (676+8)/78 = 8.77 → round = 9
        assertEquals(
            9,
            gridPanelVisibleRows(
                screenWdp = 360f, screenHdp = 800f, columns = 4,
                cardWidthFraction = 0.9f, cardHeightFraction = 0.9f,
            ),
        )
    }

    @Test
    fun `visible rows never drops below one`() {
        assertTrue(
            gridPanelVisibleRows(
                screenWdp = 100f, screenHdp = 100f, columns = 6,
                cardWidthFraction = 0.35f, cardHeightFraction = 0.3f,
            ) >= 1,
        )
    }

    @Test
    fun `visible rows grows monotonically with card height`() {
        var last = 0
        for (tenth in 3..9) {
            val rows = gridPanelVisibleRows(
                screenWdp = 360f, screenHdp = 800f, columns = 4,
                cardWidthFraction = 0.9f, cardHeightFraction = tenth / 10f,
            )
            assertTrue("卡高 $tenth/10 行数应不回退", rows >= last)
            last = rows
        }
    }

    @Test
    fun `fractions outside defaults are clamped before computing`() {
        // 宽 0.1 → 夹到 0.35；高 5 → 夹到 0.9。与显式传夹取值结果一致。
        val clamped = gridPanelVisibleRows(360f, 800f, 4, 0.1f, 5f)
        val explicit = gridPanelVisibleRows(360f, 800f, 4, 0.35f, 0.9f)
        assertEquals(explicit, clamped)
    }

    @Test
    fun `cell coordinates floor toward negative infinity`() {
        // cellW=70, gap=8 → step=78。floor 是拖拽手感的锚：往左/上拖时 -0.5 格不能截成 0。
        val metrics = GridMetrics(
            columns = 4, rows = 3, screenW = 360f, screenH = 800f,
            cellW = 70f, cellH = 70f, gap = 8f, gridPad = 10f,
            indicator = 24f, cardW = 324f, cardH = 700f,
        )
        assertEquals(78f, metrics.stepW, 0f)
        assertEquals(0, metrics.cellX(0f))
        assertEquals(0, metrics.cellX(77.9f))
        assertEquals(1, metrics.cellX(78f))
        assertEquals(-1, metrics.cellX(-39f))
        assertEquals(-1, metrics.cellY(-39f))
        assertEquals(148f, metrics.itemW(2f), 0f)
        assertEquals(304f, metrics.contentW, 0f)
    }
}
