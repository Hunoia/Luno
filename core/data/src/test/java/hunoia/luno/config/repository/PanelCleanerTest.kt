package hunoia.luno.config.repository

import hunoia.luno.config.model.ActionPanel
import hunoia.luno.config.model.ActionPanelSettings
import hunoia.luno.config.model.ActionSettings
import hunoia.luno.config.model.DirectionActions
import hunoia.luno.config.model.FloatBallSettings
import hunoia.luno.config.model.GestureButton
import hunoia.luno.config.model.GridPanel
import hunoia.luno.config.model.GridPanelItem
import hunoia.luno.config.model.GridPanelOrientation
import hunoia.luno.config.model.GridPanelSettings
import hunoia.luno.config.model.SubGesture
import hunoia.luno.config.model.SubGestureSettings
import hunoia.luno.core.domain.ACTION_PANEL_PARAM_PANEL_ID
import hunoia.luno.core.domain.ACTION_PANEL_TYPE_ID
import hunoia.luno.core.domain.GRID_PANEL_PARAM_PANEL_ID
import hunoia.luno.core.domain.GRID_PANEL_TYPE_ID
import hunoia.luno.core.domain.StoredAction
import hunoia.luno.core.domain.longPressVariant
import hunoia.luno.core.domain.withLongPressVariant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 删面板后的引用清理：删了什么就清什么，且**变体一起清**——
 * 只清本体会留下「短按正常、长按静默无反应」的悬空引用。
 */
class PanelCleanerTest {

    private fun actionPanelRef(id: String) =
        StoredAction.of(ACTION_PANEL_TYPE_ID, ACTION_PANEL_PARAM_PANEL_ID to id)

    private fun gridPanelRef(id: String) =
        StoredAction.of(GRID_PANEL_TYPE_ID, GRID_PANEL_PARAM_PANEL_ID to id)

    private val other = StoredAction.of("app.launch")

    @Test
    fun `drops matching panel items and keeps the rest`() {
        val settings = ActionSettings(
            actionPanels = ActionPanelSettings(
                panels = listOf(
                    ActionPanel(id = "a", items = listOf(actionPanelRef("target"), other)),
                    ActionPanel(id = "b", items = listOf(gridPanelRef("target"))),
                ),
            ),
        )
        val cleaned = PanelCleaner.clean(settings, "target")
        assertEquals(listOf(other), cleaned.actionPanels.panels[0].items)
        assertTrue(cleaned.actionPanels.panels[1].items.isEmpty())
    }

    @Test
    fun `clears variants pointing at the deleted panel`() {
        val kept = other.withLongPressVariant(actionPanelRef("target"))
        val settings = ActionSettings(
            actionPanels = ActionPanelSettings(panels = listOf(ActionPanel(id = "a", items = listOf(kept)))),
        )
        val cleaned = PanelCleaner.clean(settings, "target")
        val item = cleaned.actionPanels.panels[0].items.single()
        assertEquals(other, item)
        assertNull(item.longPressVariant())
    }

    @Test
    fun `grid items emptied by cleanup are removed entirely`() {
        val settings = ActionSettings(
            actionGridPanels = GridPanelSettings(
                panels = listOf(
                    GridPanel(
                        id = "g",
                        portrait = GridPanelOrientation(
                            items = listOf(
                                GridPanelItem(id = "a", kind = GridPanelItem.Kind.Action(listOf(actionPanelRef("target")))),
                                GridPanelItem(id = "b", kind = GridPanelItem.Kind.Action(listOf(other))),
                            ),
                        ),
                        landscape = GridPanelOrientation(
                            items = listOf(
                                GridPanelItem(id = "c", kind = GridPanelItem.Kind.Action(listOf(actionPanelRef("target"), other))),
                            ),
                        ),
                    ),
                ),
            ),
        )
        val cleaned = PanelCleaner.clean(settings, "target")
        val panel = cleaned.actionGridPanels.panels.single()
        assertEquals(listOf("b"), panel.portrait.items.map { it.id })
        // 同一项里还有别的动作：只摘失效那一条，项保留。
        assertEquals(listOf("c"), panel.landscape.items.map { it.id })
        assertEquals(
            listOf(other),
            (panel.landscape.items.single().kind as GridPanelItem.Kind.Action).items,
        )
    }

    @Test
    fun `cleans gesture slots sub gestures and float ball`() {
        val buttons = listOf(
            GestureButton(id = "1", tapActions = actionPanelRef("target")),
            GestureButton(id = "2", longPressActions = other),
        )
        val cleanedButtons = PanelCleaner.clean(buttons, "target")
        assertNull(cleanedButtons[0].tapActions)
        assertEquals(other, cleanedButtons[1].longPressActions)

        val subGestures = SubGestureSettings(
            subGestures = listOf(
                SubGesture(id = "s", slideActions = DirectionActions(mapOf(0 to gridPanelRef("target"), 1 to other))),
            ),
        )
        val cleanedSub = PanelCleaner.clean(subGestures, "target")
        assertEquals(mapOf(1 to other), cleanedSub.subGestures.single().slideActions.actions)

        val ball = PanelCleaner.clean(
            ActionSettings(floatBall = FloatBallSettings(tapAction = actionPanelRef("target"))),
            "target",
        )
        assertNull(ball.floatBall.tapAction)
    }
}
