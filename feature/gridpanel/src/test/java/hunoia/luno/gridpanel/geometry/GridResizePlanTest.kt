package hunoia.luno.gridpanel.geometry

import hunoia.luno.config.model.GridPanelItem
import hunoia.luno.gridpanel.geometry.planGridResize
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 列数收敛的落位预演：弹窗告知的「会移除哪些」必须就是实际移除的。 */
class GridResizePlanTest {

    private fun item(
        id: String,
        x: Int,
        y: Int,
        spanX: Int = 1,
        spanY: Int = 1,
        pageIndex: Int = 0,
    ) = GridPanelItem(
        id = id, x = x, y = y, spanX = spanX, spanY = spanY, pageIndex = pageIndex,
    )

    // 极小屏幕 + 小卡高 → 可见行数压到 1，便于构造「真的没地方放」的场景。
    private fun plan(items: List<GridPanelItem>, columns: Int, expandRows: Boolean = false) = planGridResize(
        items = items,
        columns = columns,
        cardWidthFraction = 0.9f,
        cardHeightFraction = 0.30f,
        screenWdp = 100f,
        screenHdp = 100f,
        expandRows = expandRows,
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
    fun `expanding rows keeps items that would otherwise drop`() {
        val items = listOf(item("a", 0, 0), item("b", 1, 0), item("c", 2, 0))
        val destructive = plan(items, columns = 2)
        assertEquals(1, destructive.dropped.size)

        val growing = plan(items, columns = 2, expandRows = true)
        assertTrue(growing.dropped.isEmpty())
        assertEquals(3, growing.items.size)
    }

    @Test
    fun `expanding rows compacts items to top-left by row-major`() {
        // 4 列：b 在 (2,0) 留出 (0,1)(1,1) 空洞，c 更在 (3,2)。缩到 2 列后
        // expandRows=true 按 (y,x) 排序从左上角按行优先填充，上方不留空洞。
        val result = plan(
            listOf(item("a", 0, 0), item("b", 2, 0), item("c", 3, 2)),
            columns = 2,
            expandRows = true,
        )
        assertTrue(result.dropped.isEmpty())
        assertEquals(3, result.items.size)
        val positions = result.items.map { it.x to it.y }.toSet()
        assertEquals(setOf(0 to 0, 0 to 1, 1 to 0), positions)
        result.items.forEach { assertTrue("x=${it.x}", it.x + it.spanX <= 2) }
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

    @Test
    fun `each page compacts independently`() {
        // 第 0 页占住 (0,0)(1,0)，第 1 页仍应从 (0,0) 开始：占位集合不能跨页复用。
        val result = plan(
            listOf(
                item("a", 0, 0),
                item("b", 1, 0),
                item("c", 3, 2, pageIndex = 1),
            ),
            columns = 4,
            expandRows = true,
        )
        assertTrue(result.dropped.isEmpty())
        val c = result.items.single { it.id == "c" }
        assertEquals(0, c.x)
        assertEquals(0, c.y)
    }

    @Test
    fun `non expanding path also compacts away holes above`() {
        // 两项都停在 y=2，上方全空：调列后应被压回左上，而不是原地保留空洞。
        val result = plan(
            listOf(item("a", 0, 2), item("b", 1, 2)),
            columns = 4,
        )
        assertTrue(result.dropped.isEmpty())
        assertEquals(
            setOf(0 to 0, 1 to 0),
            result.items.map { it.x to it.y }.toSet(),
        )
    }
}
