package hunoia.luno.config

import hunoia.luno.config.model.Action
import hunoia.luno.config.model.DirectionActions
import hunoia.luno.config.model.GestureButton
import hunoia.luno.config.model.GestureDirection
import hunoia.luno.config.model.SubGesture
import org.junit.Assert.assertEquals
import org.junit.Test

class ActionReferenceCleanerTest {

    private val shouldRemove: (Action) -> Boolean = { it.value == "remove_me" }

    @Test
    fun cleanList_removesMatchingAction() {
        val actions = listOf(Action("keep"), Action("remove_me"), Action("keep"))
        val result = actions.cleanList(shouldRemove)
        assertEquals(listOf(Action("keep"), Action("keep")), result)
    }

    @Test
    fun cleanList_cleansNestedLongPressAction() {
        val innerRemove = Action("remove_me")
        val outer = Action("keep", longPressAction = innerRemove)
        val result = listOf(outer).cleanList(shouldRemove)
        assertEquals(listOf(Action("keep")), result)
        assertEquals(null, result[0].longPressAction)
    }

    @Test
    fun cleanList_keepsNonMatchingNestedLongPressAction() {
        val innerKeep = Action("keep_inner")
        val outer = Action("keep", longPressAction = innerKeep)
        val result = listOf(outer).cleanList(shouldRemove)
        assertEquals(innerKeep, result[0].longPressAction)
    }

    @Test
    fun cleanList_removesOuterWhenMatchingAndRecursesInner() {
        val innerRemove = Action("remove_me")
        val outer = Action("remove_me", longPressAction = innerRemove)
        val result = listOf(outer).cleanList(shouldRemove)
        assertEquals(emptyList<Action>(), result)
    }

    @Test
    fun cleanList_emptyList_returnsEmpty() {
        val result = emptyList<Action>().cleanList(shouldRemove)
        assertEquals(emptyList<Action>(), result)
    }

    @Test
    fun cleanList_noMatch_returnsSame() {
        val actions = listOf(Action("a"), Action("b"))
        val result = actions.cleanList { false }
        assertEquals(actions, result)
    }

    @Test
    fun directionActions_cleanActions_cleansAllDirections() {
        val actions = DirectionActions(mapOf(
            GestureDirection.Right to listOf(Action("keep"), Action("remove_me")),
            GestureDirection.Left to listOf(Action("remove_me")),
        ))
        val result = actions.cleanActions(shouldRemove)
        assertEquals(listOf(Action("keep")), result.actionsBy(GestureDirection.Right))
        assertEquals(emptyList<Action>(), result.actionsBy(GestureDirection.Left))
    }

    @Test
    fun gestureButton_cleanActions_cleansAllFields() {
        val button = GestureButton(
            id = "test",
            slideActions = DirectionActions(mapOf(GestureDirection.Right to listOf(Action("remove_me")))),
            slideHoldActions = DirectionActions(mapOf(GestureDirection.Left to listOf(Action("remove_me")))),
            longSlideActions = DirectionActions(mapOf(GestureDirection.Up to listOf(Action("remove_me")))),
            longSlideHoldActions = DirectionActions(mapOf(GestureDirection.Down to listOf(Action("remove_me")))),
            tapActions = listOf(Action("remove_me"), Action("keep_tap")),
            doubleTapActions = listOf(Action("remove_me")),
            longPressActions = listOf(Action("keep_long")),
        )
        val result = button.cleanActions(shouldRemove)
        assertEquals(emptyList<Action>(), result.slideActions.actionsBy(GestureDirection.Right))
        assertEquals(emptyList<Action>(), result.slideHoldActions.actionsBy(GestureDirection.Left))
        assertEquals(emptyList<Action>(), result.longSlideActions.actionsBy(GestureDirection.Up))
        assertEquals(emptyList<Action>(), result.longSlideHoldActions.actionsBy(GestureDirection.Down))
        assertEquals(listOf(Action("keep_tap")), result.tapActions)
        assertEquals(emptyList<Action>(), result.doubleTapActions)
        assertEquals(listOf(Action("keep_long")), result.longPressActions)
    }

    @Test
    fun gestureButton_cleanActions_preservesNonMatching() {
        val orig = GestureButton(id = "test", tapActions = listOf(Action("a"), Action("b")))
        val result = orig.cleanActions { false }
        assertEquals(orig, result)
    }

    @Test
    fun subGesture_cleanActions_cleansAllFields() {
        val gesture = SubGesture(
            id = "sg",
            slideActions = DirectionActions(mapOf(GestureDirection.Right to listOf(Action("remove_me")))),
            slideHoldActions = DirectionActions(mapOf(GestureDirection.Left to listOf(Action("remove_me")))),
            longSlideActions = DirectionActions(mapOf(GestureDirection.Up to listOf(Action("keep_up")))),
            longSlideHoldActions = DirectionActions(mapOf(GestureDirection.Down to listOf(Action("remove_me")))),
        )
        val result = gesture.cleanActions(shouldRemove)
        assertEquals(emptyList<Action>(), result.slideActions.actionsBy(GestureDirection.Right))
        assertEquals(emptyList<Action>(), result.slideHoldActions.actionsBy(GestureDirection.Left))
        assertEquals(listOf(Action("keep_up")), result.longSlideActions.actionsBy(GestureDirection.Up))
        assertEquals(emptyList<Action>(), result.longSlideHoldActions.actionsBy(GestureDirection.Down))
    }

    @Test
    fun subGesture_cleanActions_preservesNonMatching() {
        val orig = SubGesture(id = "sg",
            slideActions = DirectionActions(mapOf(GestureDirection.Right to listOf(Action("a"))))
        )
        val result = orig.cleanActions { false }
        assertEquals(orig, result)
    }
}
