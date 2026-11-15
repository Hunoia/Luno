package hunoia.luno.gridpanel

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 面板浮层的返回键落点（ui-guidelines §7：回调里现读状态，不用组合期快照）。
 * 编辑态必须退编辑态而不是关面板；在途拖拽 / 缩放优先于编辑态。
 */
class PanelBackActionTest {

    @Test
    fun `dragging wins over edit mode`() {
        assertEquals(
            PanelBackAction.CancelDrag,
            panelBackAction(draggingId = "a1", resizingId = null, inEditMode = true),
        )
    }

    @Test
    fun `resizing wins over edit mode`() {
        assertEquals(
            PanelBackAction.CancelResize,
            panelBackAction(draggingId = null, resizingId = "a2", inEditMode = true),
        )
    }

    @Test
    fun `edit mode exits edit instead of closing the panel`() {
        assertEquals(
            PanelBackAction.ExitEdit,
            panelBackAction(draggingId = null, resizingId = null, inEditMode = true),
        )
    }

    @Test
    fun `non edit mode closes the panel`() {
        assertEquals(
            PanelBackAction.Close,
            panelBackAction(draggingId = null, resizingId = null, inEditMode = false),
        )
    }
}