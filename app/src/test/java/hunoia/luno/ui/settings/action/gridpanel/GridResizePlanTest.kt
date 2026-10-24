package hunoia.luno.ui.settings.action.gridpanel

import hunoia.luno.config.model.GridPanelItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 列数收敛的落位预演：弹窗告知的「会移除哪些」必须就是实际移除的。 */
class GridResizePlanTest {

    private fun item(id: String, x: Int, y: Int, spanX: Int = 1, spanY: Int = 1) =
        GridPanelItem(id = id, x = x, y = y, spanX = spanX, spanY = spanY)

    // 极小屏幕 + 小卡高 → 可见行数压到 1，便于构造「真的没地方放」的场景。
    private fun plan(items: List<GridPanelItem>, columns: Int) = planGridResize(
        items = items,
        columns = columns,
        cardWidthFraction = 0.9f,
        cardHeightFraction = 0.30f,
        screenWdp = 100f,
        screenHdp = 100f,
    )

    @Test
    fun `narrowing columns pushes items left without dropping`() {
        val result = plan(
            listOf(item("a", 0, 0), item("b", 2, 0), item("c", 3, 1)),
            columns = 2,
        )
        assertTrue(result.dropped.isEmpty())
        assertEquals(3, result.items.size)
        result.items.forEach { assertTrue("x=${it.x} out of 2 cols", it.x + it.spanX <= 2) }
        // 重排后不允许重叠。
        for (i in result.items.indices) {
            for (j in i + 1 until result.items.size) {
                assertTrue(
                    "${result.items[i]} overlaps ${result.items[j]}",
                    !result.items[i].overlaps(result.items[j]),
                )
            }
        }
    }

    @Test
    fun `items with no free slot are reported as dropped`() {
        // 4 列里同一行放 3 项 → 收成 2 列、行数 1 时只有一个位置能放第三项都放不下。
        val result = plan(
            listOf(item("a", 0, 0), item("b", 1, 0), item("c", 2, 0)),
            columns = 2,
        )
        assertEquals(2, result.items.size)
        assertEquals(1, result.dropped.size)
        assertEquals("c", result.dropped.single().id)
    }

    @Test
    fun `widening columns keeps every item`() {
        val result = plan(
            listOf(item("a", 0, 0), item("b", 1, 0), item("c", 0, 1, spanX = 2)),
            columns = 4,
        )
        assertTrue(result.dropped.isEmpty())
        assertEquals(3, result.items.size)
    }
}
