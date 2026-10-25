package hunoia.luno.gridpanel

import hunoia.luno.config.model.GridPanelItem
import hunoia.luno.config.model.GridPanelItemLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 落位求解（拖拽预览与落盘共用）的纯函数测试。 */
class GridPanelLayoutTest {

    private fun item(id: String, x: Int, y: Int, spanX: Int = 1, spanY: Int = 1) =
        GridPanelItem(id = id, x = x, y = y, spanX = spanX, spanY = spanY)

    @Test
    fun `moves item to target cell`() {
        val plan = GridPanelItemLayout.planReorder(
            items = listOf(item("a", 0, 0), item("b", 1, 0)),
            movingId = "a",
            targetX = 3,
            targetY = 2,
            columns = 4,
            rows = 5,
        )
        assertNotNull(plan)
        val a = plan!!.first { it.id == "a" }
        assertEquals(3, a.x)
        assertEquals(2, a.y)
    }

    @Test
    fun `multi span item can land on first column`() {
        // 2 格宽项从第 2 列拖到最左：目标被夹成 0，而不是因为整项中心进不了第 0 格。
        val plan = GridPanelItemLayout.planReorder(
            items = listOf(item("wide", 2, 0, spanX = 2)),
            movingId = "wide",
            targetX = 0,
            targetY = 0,
            columns = 4,
            rows = 4,
        )
        assertNotNull(plan)
        assertEquals(0, plan!!.first().x)
    }

    @Test
    fun `pushed items never overlap`() {
        val items = listOf(
            item("a", 0, 0),
            item("b", 2, 0),
            item("c", 2, 1),
        )
        val plan = GridPanelItemLayout.planReorder(
            items = items,
            movingId = "a",
            targetX = 2,
            targetY = 0,
            columns = 4,
            rows = 4,
        )
        assertNotNull(plan)
        assertEquals(items.size, plan!!.size)
        for (i in plan.indices) {
            for (j in i + 1 until plan.size) {
                assertTrue(
                    "${plan[i]} overlaps ${plan[j]}",
                    !plan[i].overlaps(plan[j]),
                )
            }
        }
    }

    @Test
    fun `first free slot scans row major`() {
        val items = listOf(item("a", 0, 0), item("b", 1, 0))
        val slot = GridPanelItemLayout.firstFreeSlot(items, columns = 4, rows = 3, spanX = 2, spanY = 1)
        assertNotNull(slot)
        assertEquals(2, slot!!.x)
        assertEquals(0, slot.y)
    }
}
