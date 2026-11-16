package hunoia.luno.config.model

import hunoia.luno.core.domain.ACTION_PANEL_TYPE_ID
import hunoia.luno.core.domain.ACTION_PANEL_PARAM_PANEL_ID
import hunoia.luno.core.domain.GRID_PANEL_TYPE_ID
import hunoia.luno.core.domain.GRID_PANEL_PARAM_PANEL_ID
import hunoia.luno.core.domain.StoredAction
import hunoia.luno.core.domain.withLongPressVariant
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 面板引用统计：删面板前的提示数必须和运行时「谁指着我」一致，
 * 所以面板项、触钮槽、子手势槽、悬浮球、以及长按变体都要算到。
 */
class PanelReferencesTest {

    private fun actionPanelRef(id: String) =
        StoredAction.of(ACTION_PANEL_TYPE_ID, ACTION_PANEL_PARAM_PANEL_ID to id)

    private fun gridPanelRef(id: String) =
        StoredAction.of(GRID_PANEL_TYPE_ID, GRID_PANEL_PARAM_PANEL_ID to id)

    private val other = StoredAction.of("app.launch")

    @Test
    fun `counts panel items in both panel kinds`() {
        val settings = ActionSettings(
            actionPanels = ActionPanelSettings(
                panels = listOf(
                    ActionPanel(id = "a", items = listOf(actionPanelRef("target"), other)),
                    ActionPanel(id = "b", items = listOf(gridPanelRef("target"))),
                ),
            ),
            actionGridPanels = GridPanelSettings(
                panels = listOf(
                    GridPanel(
                        id = "g",
                        portrait = GridPanelOrientation(
                            items = listOf(
                                GridPanelItem(id = "i1", kind = GridPanelItem.Kind.Action(listOf(actionPanelRef("target")))),
                                GridPanelItem(id = "i2", kind = GridPanelItem.Kind.Action(listOf(other))),
                            ),
                        ),
                    ),
                ),
            ),
        )
        assertEquals(3, PanelReferences.count("target", settings, emptyList(), emptyList(), GlobalGestureSettings()))
    }

    @Test
    fun `counts long press variants separately`() {
        val item = other.withLongPressVariant(actionPanelRef("target"))
        val settings = ActionSettings(
            actionPanels = ActionPanelSettings(panels = listOf(ActionPanel(id = "a", items = listOf(item)))),
        )
        assertEquals(1, PanelReferences.count("target", settings, emptyList(), emptyList(), GlobalGestureSettings()))
    }

    @Test
    fun `counts gesture slots sub gestures and float ball`() {
        val buttons = listOf(
            GestureButton(
                id = "1",
                tapActions = actionPanelRef("target"),
                longPressActions = other.withLongPressVariant(gridPanelRef("target")),
            ),
        )
        val subGestures = listOf(
            SubGesture(
                id = "s",
                slideActions = DirectionActions(mapOf(0 to actionPanelRef("target"))),
            ),
        )
        val settings = ActionSettings(floatBall = FloatBallSettings(tapAction = gridPanelRef("target")))
        assertEquals(4, PanelReferences.count("target", settings, buttons, subGestures, GlobalGestureSettings()))
    }

    @Test
    fun `counts global gesture trigger slots`() {
        val global = GlobalGestureSettings(
            zones = listOf(
                GlobalGestureZone(
                    id = "z",
                    triggers = listOf(
                        GlobalGestureTrigger(id = "t1", action = actionPanelRef("target")),
                        GlobalGestureTrigger(
                            id = "t2",
                            directionActions = DirectionActions(mapOf(0 to gridPanelRef("target"), 1 to other)),
                        ),
                    ),
                ),
            ),
        )
        assertEquals(2, PanelReferences.count("target", ActionSettings(), emptyList(), emptyList(), global))
    }

    @Test
    fun `ignores references to other panels`() {
        val settings = ActionSettings(
            actionPanels = ActionPanelSettings(
                panels = listOf(ActionPanel(id = "a", items = listOf(actionPanelRef("another"), gridPanelRef("another")))),
            ),
        )
        assertEquals(0, PanelReferences.count("target", settings, emptyList(), emptyList(), GlobalGestureSettings()))
    }
}
