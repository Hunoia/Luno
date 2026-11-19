package hunoia.luno.picker

import hunoia.luno.core.domain.StoredAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 多选选中态：toggle 增删、multiActions 收敛、isSelected 成员判定；单选行为不变。 */
class SelectedRecordTest {

    private val actionA = StoredAction.of("test.a")
    private val actionB = StoredAction.of("test.b")

    @Test
    fun `toggle adds and removes and keeps multiSelect flag`() {
        var record = UiState.SelectedRecord(multiSelect = true)
        record = record.toggle(actionA)
        record = record.toggle(actionB)
        assertEquals(2, record.size)
        assertTrue(record.multiSelect)
        assertTrue(record.isSelected(actionA))
        assertTrue(record.isSelected(actionB))

        record = record.toggle(actionA)
        assertEquals(1, record.size)
        assertFalse(record.isSelected(actionA))
        assertTrue(record.isSelected(actionB))
    }

    @Test
    fun `multi actions converge stored entries`() {
        val record = UiState.SelectedRecord(
            list = listOf(actionA, actionB),
            multiSelect = true,
        )
        assertEquals(listOf(actionA, actionB), record.multiActions)
    }

    @Test
    fun `isSelected is false for non members in multi mode`() {
        val record = UiState.SelectedRecord(list = listOf(actionA), multiSelect = true)
        assertTrue(record.isSelected(actionA))
        assertFalse(record.isSelected(actionB))
    }

    @Test
    fun `single select keeps one-value semantics`() {
        val record = UiState.SelectedRecord().selectAction(actionA)
        assertEquals(actionA, record.action)
        assertTrue(record.isSelected(actionA))
        assertFalse(record.isSelected(actionB))
        // 单选再选一个顶掉前一个。
        val replaced = record.selectAction(actionB)
        assertEquals(actionB, replaced.action)
        assertFalse(replaced.isSelected(actionA))
    }

    @Test
    fun `none selection in single mode is reported as selected none`() {
        val record = UiState.SelectedRecord().selectAction(StoredAction.of("internal.none"))
        // action 归一成 null（空槽），isSelected 对「无动作」行返回 true。
        assertEquals(null, record.action)
        assertTrue(record.isSelected(StoredAction.of("internal.none")))
    }

    @Test
    fun `supports multi select only for panel and grid add`() {
        // 动作面板新增
        assertTrue(ActionSelect(actionPanelId = "p").supportsMultiSelect)
        // 动作面板长按变体编辑
        assertFalse(ActionSelect(actionPanelId = "p", actionPanelItemIndex = 2).supportsMultiSelect)
        // 网格面板新增
        assertTrue(ActionSelect(gridPanelId = "g").supportsMultiSelect)
        // 网格面板原位替换
        assertFalse(ActionSelect(gridPanelId = "g", gridPanelReplaceItemId = "x").supportsMultiSelect)
        // 手势槽
        assertFalse(ActionSelect(gestureButtonId = "b").supportsMultiSelect)
        // 复制链接 / pick token
        assertFalse(ActionSelect(copyDeepLink = true).supportsMultiSelect)
        assertFalse(ActionSelect(pickActionToken = "t").supportsMultiSelect)
    }
}
