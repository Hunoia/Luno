package hunoia.luno.config.repository

import hunoia.luno.config.model.ActionPanel
import hunoia.luno.config.model.ActionPanelSettings
import hunoia.luno.config.model.ActionSettings
import hunoia.luno.config.model.DirectionActions
import hunoia.luno.config.model.FloatBallSettings
import hunoia.luno.config.model.GestureButton
import hunoia.luno.config.model.GlobalGestureSettings
import hunoia.luno.config.model.GlobalGestureTrigger
import hunoia.luno.config.model.GlobalGestureZone
import hunoia.luno.config.model.GridPanel
import hunoia.luno.config.model.GridPanelItem
import hunoia.luno.config.model.GridPanelOrientation
import hunoia.luno.config.model.GridPanelSettings
import hunoia.luno.config.model.SubGestureSettings
import hunoia.luno.core.domain.StoredAction
import hunoia.luno.core.domain.libraryEntryId
import hunoia.luno.core.domain.longPressVariant
import hunoia.luno.core.domain.withLongPressVariant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 删库条目后的引用清理：删了什么就清什么，且**变体一起清**——
 * 只清本体会留下「短按正常、长按静默无反应」的悬空引用。
 */
class LibraryCleanerTest {

    private fun libraryRef(id: String) =
        StoredAction.of("internal.libraryRef", "entryId" to id)

    private val other = StoredAction.of("app.launch")

    private val shouldRemove: (StoredAction) -> Boolean = { it.libraryEntryId() == "L" }

    @Test
    fun `cleans panels grid and float ball`() {
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
                                GridPanelItem(id = "a", kind = GridPanelItem.Kind.Action(listOf(libraryRef("L")))),
                                GridPanelItem(id = "b", kind = GridPanelItem.Kind.Action(listOf(other))),
                            ),
                        ),
                        landscape = GridPanelOrientation(
                            items = listOf(
                                GridPanelItem(id = "c", kind = GridPanelItem.Kind.Action(listOf(libraryRef("L"), other))),
                            ),
                        ),
                    ),
                ),
            ),
            floatBall = FloatBallSettings(tapAction = libraryRef("L")),
        )
        val cleaned = LibraryCleaner.clean(settings, shouldRemove)
        assertEquals(listOf(other), cleaned.actionPanels.panels[0].items)
        assertEquals(listOf("b"), cleaned.actionGridPanels.panels[0].portrait.items.map { it.id })
        // 同一项里还有别的动作：只摘失效那一条，项保留。
        assertEquals(listOf("c"), cleaned.actionGridPanels.panels[0].landscape.items.map { it.id })
        assertEquals(
            listOf(other),
            (cleaned.actionGridPanels.panels[0].landscape.items.single().kind as GridPanelItem.Kind.Action).items,
        )
        assertNull(cleaned.floatBall.tapAction)
    }

    @Test
    fun `clears variants pointing at the deleted entry`() {
        val kept = other.withLongPressVariant(libraryRef("L"))
        val settings = ActionSettings(
            actionPanels = ActionPanelSettings(panels = listOf(ActionPanel(id = "a", items = listOf(kept)))),
        )
        val cleaned = LibraryCleaner.clean(settings, shouldRemove)
        val item = cleaned.actionPanels.panels[0].items.single()
        assertEquals(other, item)
        assertNull(item.longPressVariant())
    }

    @Test
    fun `cleans gesture slots sub gestures and global gesture`() {
        val buttons = listOf(
            GestureButton(id = "1", tapActions = libraryRef("L")),
            GestureButton(id = "2", longPressActions = other),
        )
        val cleanedButtons = LibraryCleaner.clean(buttons, shouldRemove)
        assertNull(cleanedButtons[0].tapActions)
        assertEquals(other, cleanedButtons[1].longPressActions)

        val subGestures = SubGestureSettings(
            subGestures = listOf(
                hunoia.luno.config.model.SubGesture(
                    id = "s",
                    slideActions = DirectionActions(mapOf(0 to libraryRef("L"), 1 to other)),
                ),
            ),
        )
        val cleanedSub = LibraryCleaner.clean(subGestures, shouldRemove)
        assertEquals(mapOf(1 to other), cleanedSub.subGestures.single().slideActions.actions)

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
        val cleanedGlobal = LibraryCleaner.clean(global, shouldRemove)
        val triggers = cleanedGlobal.zones.single().triggers
        assertNull(triggers[0].action)
        assertEquals(mapOf(1 to other), triggers[1].directionActions.actions)
    }
}
