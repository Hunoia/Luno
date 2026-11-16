package hunoia.luno.config.model

import hunoia.luno.core.domain.StoredAction
import hunoia.luno.core.domain.withLongPressVariant
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 库条目引用统计：动作库列表的「引用 x」必须和运行时「谁指着我」一致，
 * 所以面板项、网格面板项、触钮槽、子手势槽、全局手势触发、悬浮球、以及长按变体都要算到。
 */
class LibraryReferencesTest {

    private fun libraryRef(id: String) =
        StoredAction.of("internal.libraryRef", "entryId" to id)

    private val other = StoredAction.of("app.launch")

    @Test
    fun `counts library refs across all action slots`() {
        val settings = ActionSettings(
            actionPanels = ActionPanelSettings(
                panels = listOf(
                    ActionPanel(id = "a", items = listOf(libraryRef("L"), other)),
                ),
            ),
            actionGridPanels = GridPanelSettings(
                panels = listOf(
                    GridPanel(
                        id = "g",
                        portrait = GridPanelOrientation(
                            items = listOf(
                                GridPanelItem(id = "i1", kind = GridPanelItem.Kind.Action(listOf(libraryRef("L")))),
                                GridPanelItem(id = "i2", kind = GridPanelItem.Kind.Action(listOf(other))),
                            ),
                        ),
                    ),
                ),
            ),
            floatBall = FloatBallSettings(tapAction = libraryRef("L")),
        )
        val buttons = listOf(
            GestureButton(id = "1", tapActions = libraryRef("L")),
            GestureButton(id = "2", longPressActions = other),
        )
        val subGestures = listOf(
            SubGesture(id = "s", slideActions = DirectionActions(mapOf(0 to libraryRef("L")))),
        )
        val global = GlobalGestureSettings(
            zones = listOf(
                GlobalGestureZone(
                    id = "z",
                    triggers = listOf(
                        GlobalGestureTrigger(id = "t1", action = libraryRef("L")),
                        GlobalGestureTrigger(
                            id = "t2",
                            directionActions = DirectionActions(mapOf(0 to libraryRef("L"), 1 to other)),
                        ),
                    ),
                ),
            ),
        )
        // 面板项 1 + 网格项 1 + 悬浮球 1 + 按钮 1 + 子手势 1 + 全局手势单动作 1 + 全局手势方向 1
        val counts = LibraryReferences.count(settings, buttons, subGestures, global)
        assertEquals(7, counts["L"])
    }

    @Test
    fun `counts long press variants separately`() {
        val item = other.withLongPressVariant(libraryRef("L"))
        val settings = ActionSettings(
            actionPanels = ActionPanelSettings(panels = listOf(ActionPanel(id = "a", items = listOf(item)))),
        )
        val counts = LibraryReferences.count(settings, emptyList(), emptyList(), GlobalGestureSettings())
        assertEquals(1, counts["L"])
    }

    @Test
    fun `ignores refs to other entries`() {
        val settings = ActionSettings(
            actionPanels = ActionPanelSettings(
                panels = listOf(ActionPanel(id = "a", items = listOf(libraryRef("another")))),
            ),
        )
        val counts = LibraryReferences.count(settings, emptyList(), emptyList(), GlobalGestureSettings())
        assertEquals(null, counts["L"])
    }
}
