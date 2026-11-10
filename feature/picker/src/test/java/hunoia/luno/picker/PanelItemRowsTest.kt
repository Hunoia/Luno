package hunoia.luno.picker

import hunoia.luno.config.model.ActionPanel
import hunoia.luno.config.model.GridPanel
import hunoia.luno.core.domain.ACTION_PANEL_PARAM_PANEL_ID
import hunoia.luno.core.domain.ACTION_PANEL_TYPE_ID
import hunoia.luno.core.domain.GRID_PANEL_PARAM_PANEL_ID
import hunoia.luno.core.domain.GRID_PANEL_TYPE_ID
import hunoia.luno.core.domain.StoredAction
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 面板项模式的面板引用行：两种面板项模式（动作面板 / 网格面板）必须给同一套行，
 * 只差「排除自己」——选择器不分叉，动作面板里才选得到别的面板。
 */
class PanelItemRowsTest {

    private fun UiState.panelRows(): List<StoredAction> = assembleDataTransform(this).actions
        .filter { it.typeId == ACTION_PANEL_TYPE_ID || it.typeId == GRID_PANEL_TYPE_ID }

    private fun baseState() = UiState(
        // 排除集必须是真实那一份（空集会让过滤失效，整张定义表都变成可选）。
        excludedTypeIds = hunoia.luno.action.definitions.ActionDefinitions.pickerExcludedTypeIds(),
        isPanelItemMode = true,
        actionPanels = listOf(ActionPanel(id = "a1"), ActionPanel(id = "a2")),
        gridPanels = listOf(GridPanel(id = "g1")),
    )

    @Test
    fun `action panel item mode offers other panels and excludes itself`() {
        val rows = baseState().copy(currentActionPanelId = "a1").panelRows()
        assertEquals(
            listOf(
                StoredAction.of(GRID_PANEL_TYPE_ID, GRID_PANEL_PARAM_PANEL_ID to "g1"),
                StoredAction.of(ACTION_PANEL_TYPE_ID, ACTION_PANEL_PARAM_PANEL_ID to "a2"),
            ),
            rows,
        )
    }

    @Test
    fun `grid panel item mode keeps the same rows and excludes itself`() {
        val rows = baseState().copy(currentGridPanelId = "g1").panelRows()
        assertEquals(
            listOf(
                StoredAction.of(ACTION_PANEL_TYPE_ID, ACTION_PANEL_PARAM_PANEL_ID to "a1"),
                StoredAction.of(ACTION_PANEL_TYPE_ID, ACTION_PANEL_PARAM_PANEL_ID to "a2"),
            ),
            rows,
        )
    }
}
