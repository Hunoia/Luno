package hunoia.luno.gesture

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import hunoia.luno.config.model.Action
import hunoia.luno.config.model.GestureButton
import hunoia.luno.config.model.GestureTriggerType
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GestureStateTest {

    private val thresholds = GestureThresholds(
        touchSlop = 10f,
        longPressDelayMs = 250L,
        doubleTapDelayMs = 20L,
        slideHoldDelayMs = 120L,
        longSlideHoldDelayMs = 120L,
        slideTriggerDistance = 30f,
        longSlideTriggerDistance = 100f,
    )

    @Test
    fun `pending tap falls back after double tap timeout`() = runBlocking {
        val button = button("A", tap = "tapA", doubleTap = "doubleA")
        val resolved = mutableListOf<GestureResolvedActions>()
        val state = state(listOf(button), resolved)

        state.onDragStart(Offset(10f, 0f), 0)
        assertEquals(null, state.onDragEnd())
        delay(50L)

        assertResolved(resolved.single(), "A", GestureTriggerType.Tap, "tapA", Offset(10f, 0f))
    }

    @Test
    fun `same button second tap resolves double tap and clears pending tap`() = runBlocking {
        val button = button("A", tap = "tapA", doubleTap = "doubleA")
        val resolved = mutableListOf<GestureResolvedActions>()
        val state = state(listOf(button), resolved)

        state.onDragStart(Offset(10f, 0f), 0)
        state.onDragEnd()

        state.onDragStart(Offset(10f, 0f), 0)
        val second = state.onDragEnd()
        delay(50L)

        assertTrue(resolved.isEmpty())
        assertResolved(second!!, "A", GestureTriggerType.DoubleTap, "doubleA", Offset(10f, 0f))
    }

    @Test
    fun `same button failed second touch does not restore first tap`() = runBlocking {
        val button = button("A", tap = "tapA", doubleTap = "doubleA")
        val resolved = mutableListOf<GestureResolvedActions>()
        val state = state(listOf(button), resolved)

        state.onDragStart(Offset(10f, 0f), 0)
        state.onDragEnd()

        state.onDragStart(Offset(10f, 0f), 0)
        state.onDrag(Offset(8f, 8f))
        assertEquals(null, state.onDragEnd())
        delay(50L)

        assertTrue(resolved.isEmpty())
    }

    @Test
    fun `different button down immediately settles previous pending tap and continues new tap`() = runBlocking {
        val buttonA = button("A", tap = "tapA", doubleTap = "doubleA")
        val buttonB = button("B", tap = "tapB")
        val resolved = mutableListOf<GestureResolvedActions>()
        val state = state(listOf(buttonA, buttonB), resolved)

        state.onDragStart(Offset(10f, 0f), 0)
        state.onDragEnd()

        state.onDragStart(Offset(110f, 0f), 0)
        val bTap = state.onDragEnd()

        assertResolved(resolved.single(), "A", GestureTriggerType.Tap, "tapA", Offset(10f, 0f))
        assertResolved(bTap!!, "B", GestureTriggerType.Tap, "tapB", Offset(110f, 0f))
    }

    @Test
    fun `blank area down immediately settles previous pending tap`() = runBlocking {
        val button = button("A", tap = "tapA", doubleTap = "doubleA")
        val resolved = mutableListOf<GestureResolvedActions>()
        val state = state(listOf(button), resolved)

        state.onDragStart(Offset(10f, 0f), 0)
        state.onDragEnd()

        state.onDragStart(Offset(300f, 0f), 0)

        assertResolved(resolved.single(), "A", GestureTriggerType.Tap, "tapA", Offset(10f, 0f))
    }

    @Test
    fun `only double tap pending is cleared by different button down without fallback`() = runBlocking {
        val buttonA = button("A", doubleTap = "doubleA")
        val buttonB = button("B", tap = "tapB")
        val resolved = mutableListOf<GestureResolvedActions>()
        val state = state(listOf(buttonA, buttonB), resolved)

        state.onDragStart(Offset(10f, 0f), 0)
        state.onDragEnd()
        state.onDragStart(Offset(110f, 0f), 0)

        assertTrue(resolved.isEmpty())
    }

    @Test
    fun `cancel pending tap clears it without fallback`() = runBlocking {
        val button = button("A", tap = "tapA", doubleTap = "doubleA")
        val resolved = mutableListOf<GestureResolvedActions>()
        val state = state(listOf(button), resolved)

        state.onDragStart(Offset(10f, 0f), 0)
        state.onDragEnd()
        state.cancelPendingTap()
        delay(50L)

        assertTrue(resolved.isEmpty())
    }

    @Test
    fun `drag cancel clears pending tap without fallback`() = runBlocking {
        val button = button("A", tap = "tapA", doubleTap = "doubleA")
        val resolved = mutableListOf<GestureResolvedActions>()
        val state = state(listOf(button), resolved)

        state.onDragStart(Offset(10f, 0f), 0)
        state.onDragEnd()
        state.onDragCancel()
        delay(50L)

        assertTrue(resolved.isEmpty())
    }

    private fun state(
        buttons: List<GestureButton>,
        resolved: MutableList<GestureResolvedActions>,
    ): GestureState {
        return GestureState(
            coroutineScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Default),
            buttons = buttons,
            touchTargetProvider = { allButtons, offset, _ ->
                val button = when {
                    offset.x < 100f -> allButtons.firstOrNull { it.id == "A" }
                    offset.x < 200f -> allButtons.firstOrNull { it.id == "B" }
                    else -> null
                }
                button?.let {
                    GestureTouchTarget(
                        sourceButton = it,
                        effectiveButton = it,
                        bounds = Rect(Offset.Zero, Size.Zero),
                        isMirror = false,
                    )
                }
            },
            thresholdsProvider = { thresholds },
            timeProvider = { System.currentTimeMillis() },
        ).also { it.onResolved = resolved::add }
    }

    private fun button(
        id: String,
        tap: String? = null,
        doubleTap: String? = null,
    ): GestureButton {
        return GestureButton(
            id = id,
            tapActions = actionList(tap),
            doubleTapActions = actionList(doubleTap),
            slideActions = hunoia.luno.config.model.DirectionActions(),
        )
    }

    private fun actionList(value: String?): List<Action> {
        return if (value == null) listOf(Action.NONE) else listOf(Action(value))
    }

    private fun assertResolved(
        result: GestureResolvedActions,
        buttonId: String,
        triggerType: GestureTriggerType,
        action: String,
        touchPosition: Offset,
    ) {
        assertEquals(buttonId, result.button.id)
        assertEquals(triggerType, result.triggerType)
        assertEquals(action, result.actions.single().value)
        assertEquals(touchPosition, result.touchPosition)
    }
}
