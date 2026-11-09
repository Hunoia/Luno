package hunoia.luno.runtime.surface

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SurfaceVisibilityStateTest {

    private var now = 1_000L
    private val state = SurfaceVisibilityState(now = { now })

    @Test
    fun `panel count hides until dismissed`() {
        state.onPanelShown()
        state.onPanelShown()
        assertEquals(2, state.panelCount)
        assertTrue(state.isHidden)

        state.onPanelDismissed()
        assertTrue(state.isPanelHidden)
        state.onPanelDismissed()
        assertFalse(state.isPanelHidden)
        assertFalse(state.isHidden)
    }

    @Test
    fun `dismissed never goes negative`() {
        state.onPanelDismissed()
        assertEquals(0, state.panelCount)
    }

    @Test
    fun `temp hide extends to the later deadline and expires`() {
        assertTrue(state.applyTempHide(500L))
        now += 100L
        assertFalse(state.applyTempHide(200L))
        assertEquals(400L, state.tempHideRemainingMs())

        now += 400L
        assertFalse(state.isTempHidden())
        assertTrue(state.hasTempHide)
        state.cancelTempHide()
        assertFalse(state.hasTempHide)
    }

    @Test
    fun `reset clears both hidden sources`() {
        state.onPanelShown()
        state.applyTempHide(1_000L)
        assertTrue(state.isHidden)

        state.reset()
        assertEquals(0, state.panelCount)
        assertFalse(state.hasTempHide)
        assertFalse(state.isHidden)
    }
}
