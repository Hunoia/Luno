package hunoia.luno.picker

import hunoia.luno.config.model.ActionPanel
import hunoia.luno.config.model.GridPanel
import hunoia.luno.config.model.SubGesture
import hunoia.luno.core.domain.ACTION_PANEL_PARAM_PANEL_ID
import hunoia.luno.core.domain.ACTION_PANEL_TYPE_ID
import hunoia.luno.core.domain.GRID_PANEL_PARAM_PANEL_ID
import hunoia.luno.core.domain.GRID_PANEL_TYPE_ID
import hunoia.luno.core.domain.StoredAction
import hunoia.luno.action.model.Action
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 面板项模式的合成行：两种面板项模式（动作面板 / 网格面板）给同一套行，只差「排除自己」；
 * 子手势不按启用过滤（禁用仍可选、触发时提示），面板项模式另有「无」占位行。
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

    @Test
    fun `panel item mode lists disabled sub gestures and a none placeholder`() {
        val state = baseState().copy(
            currentActionPanelId = "a1",
            subGestures = listOf(
                SubGesture(id = "s1", name = "OK", enabled = true),
                SubGesture(id = "s2", name = "已关", enabled = false),
            ),
        )
        val rows = assembleDataTransform(state).actions
        assertEquals(
            listOf("s1", "s2"),
            rows.filter { it.typeId == "internal.subGesture" }.map { it.params.getValue("id").toString().trim('"') },
        )
        assertTrue(rows.any { it.typeId == Action.NONE_TYPE_ID })
    }

    @Test
    fun `copy deep link mode hides the none row`() {
        val rows = assembleDataTransform(
            baseState().copy(isPanelItemMode = false, copyDeepLink = true),
        ).actions
        assertTrue(rows.none { it.typeId == Action.NONE_TYPE_ID })
    }

    @Test
    fun `plain slot mode lists all sub gestures including disabled ones`() {
        val state = baseState().copy(
            isPanelItemMode = false,
            subGestures = listOf(SubGesture(id = "s1", enabled = false)),
        )
        val rows = assembleDataTransform(state).actions
        assertTrue(rows.any { it.typeId == "internal.subGesture" })
        assertTrue(rows.any { it.typeId == Action.NONE_TYPE_ID })
    }
}
