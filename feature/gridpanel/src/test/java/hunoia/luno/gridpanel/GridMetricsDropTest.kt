package hunoia.luno.gridpanel

import hunoia.luno.gridpanel.geometry.GridMetrics
import org.junit.Assert.assertEquals
import org.junit.Test

/** 拖拽落格映射：左上格中心 + 合法范围夹取。 */
class GridMetricsDropTest {

    // cellW = 100, gap = 10 → step = 110；4 列 5 行。
    private val metrics = GridMetrics(
        columns = 4,
        rows = 5,
        screenW = 0f,
        screenH = 0f,
        cellW = 100f,
        cellH = 100f,
        gap = 10f,
        gridPad = 0f,
        indicator = 0f,
        cardW = 0f,
        cardH = 0f,
    )

    @Test
    fun `single span switches after half a step`() {
        assertEquals(0, metrics.dropCellX(0f, 1))
        assertEquals(0, metrics.dropCellX(59f, 1))
        assertEquals(1, metrics.dropCellX(60f, 1))
        assertEquals(3, metrics.dropCellX(1000f, 1))
    }

    @Test
    fun `multi span can reach both edges`() {
        // 2 格宽：最左夹到 0，最右夹到 columns - span = 2。
        assertEquals(0, metrics.dropCellX(-500f, 2))
        assertEquals(0, metrics.dropCellX(0f, 2))
        assertEquals(2, metrics.dropCellX(220f, 2))
        assertEquals(2, metrics.dropCellX(9999f, 2))
    }

    @Test
    fun `vertical mapping clamps to rows minus span`() {
        assertEquals(0, metrics.dropCellY(0f, 1))
        assertEquals(1, metrics.dropCellY(60f, 1))
        assertEquals(4, metrics.dropCellY(9999f, 1))
        assertEquals(3, metrics.dropCellY(9999f, 2))
    }
}
