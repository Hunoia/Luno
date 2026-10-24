package hunoia.luno.config.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 固定网格落位求解回归：拖拽预览与落盘共用这套规则——
 * 预览说放得下、落下却重叠就是事故，所以这里锁死求解语义。
 */
class GridPanelItemLayoutTest {

    private fun item(id: String, x: Int, y: Int, w: Int = 1, h: Int = 1) =
        GridPanelItem(id = id, x = x, y = y, spanX = w, spanY = h)

    /** 两两不相交且全部在界内（planReorder 的成功判据）。 */
    private fun assertNoOverlap(plan: List<GridPanelItem>, columns: Int, rows: Int) {
        for (i in plan.indices) {
            val a = plan[i]
            assertTrue("${a.id} 越界", a.x >= 0 && a.right <= columns && a.y >= 0 && a.bottom <= rows)
            for (j in i + 1 until plan.size) {
                val b = plan[j]
                assertTrue("${a.id} 与 ${b.id} 重叠", !a.overlaps(b))
            }
        }
    }

    @Test
    fun `first free slot scans row major`() {
        val items = listOf(item("a", 0, 0), item("b", 1, 0))
        assertEquals(
            GridCell(2, 0),
            GridPanelItemLayout.firstFreeSlot(items, columns = 3, rows = 2, spanX = 1, spanY = 1),
        )
        assertEquals(
            GridCell(0, 1),
            GridPanelItemLayout.firstFreeSlot(items, columns = 2, rows = 2, spanX = 1, spanY = 1),
        )
    }

    @Test
    fun `first free slot skips cells that cannot fit the whole span`() {
        val items = listOf(item("a", 0, 0))
        assertEquals(
            GridCell(1, 0),
            GridPanelItemLayout.firstFreeSlot(items, columns = 3, rows = 3, spanX = 2, spanY = 2),
        )
    }

    @Test
    fun `first free slot clamps oversized span to the grid`() {
        assertEquals(
            GridCell(0, 0),
            GridPanelItemLayout.firstFreeSlot(emptyList(), columns = 4, rows = 1, spanX = 5, spanY = 1),
        )
    }

    @Test
    fun `no free slot returns null`() {
        val items = listOf(item("a", 0, 0), item("b", 1, 0))
        assertNull(GridPanelItemLayout.firstFreeSlot(items, columns = 2, rows = 1, spanX = 1, spanY = 1))
    }

    @Test
    fun `is free ignores the moving item itself`() {
        val items = listOf(item("a", 0, 0))
        assertTrue(GridPanelItemLayout.isFree(items, 0, 0, 1, 1, ignoreId = "a"))
        assertTrue(!GridPanelItemLayout.isFree(items, 0, 0, 1, 1))
    }

    @Test
    fun `plan reorder pins the moved item and pushes the blocker away`() {
        val items = listOf(item("blocker", 1, 0), item("moved", 0, 0))
        val plan = GridPanelItemLayout.planReorder(
            items, movingId = "moved", targetX = 1, targetY = 0, columns = 2, rows = 2,
        )!!
        assertEquals(1 to 0, plan.first { it.id == "moved" }.let { it.x to it.y })
        assertNoOverlap(plan, columns = 2, rows = 2)
    }

    @Test
    fun `plan reorder keeps untouched items in place and order`() {
        val items = listOf(
            item("moved", 0, 0),
            item("far", 0, 1, w = 2),
            item("blocker", 1, 0),
        )
        val plan = GridPanelItemLayout.planReorder(
            items, movingId = "moved", targetX = 0, targetY = 0, columns = 2, rows = 2,
        )!!
        assertEquals(listOf("moved", "far", "blocker"), plan.map { it.id })
        assertEquals(0 to 1, plan.first { it.id == "far" }.let { it.x to it.y })
        assertNoOverlap(plan, columns = 2, rows = 2)
    }

    @Test
    fun `plan reorder rejects target that forces items out of bounds`() {
        // 2 宽的项钉进 1 列网格：收敛后出界 → 整案判负，调用方维持上一个有效目标。
        val items = listOf(item("a", 0, 0, w = 2))
        assertNull(
            GridPanelItemLayout.planReorder(
                items, movingId = "a", targetX = 0, targetY = 0, columns = 1, rows = 1,
            ),
        )
    }

    @Test
    fun `plan reorder returns null for unknown moving id`() {
        assertNull(
            GridPanelItemLayout.planReorder(
                listOf(item("a", 0, 0)), movingId = "ghost", targetX = 0, targetY = 0, columns = 2, rows = 2,
            ),
        )
    }
}
