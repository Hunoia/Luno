package hunoia.luno.runtime.surface

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SurfaceVisibilityStateTest {

    private val state = SurfaceVisibilityState()

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
    fun `screen hide persists while staying on the same screen`() {
        assertTrue(state.enterScreenHide("app.a"))
        assertTrue(state.isScreenHidden)
        assertTrue(state.isHidden)
        // 停留期间重复进入无状态变化（toggle 路径由调用方先清再进）。
        assertFalse(state.enterScreenHide("app.a"))
        // 前台未变（含窗口内跳页）不清槽。
        assertFalse(state.clearScreenHideIfLeft("app.a", locked = false))
        assertTrue(state.isScreenHidden)
    }

    @Test
    fun `leaving the screen clears the hide`() {
        state.enterScreenHide("app.a")
        assertTrue(state.clearScreenHideIfLeft("app.b", locked = false))
        assertFalse(state.isScreenHidden)
        assertFalse(state.isHidden)
    }

    @Test
    fun `lock screen clears the hide`() {
        state.enterScreenHide("app.a")
        // 锁屏视为离开：即使前台包名未变（keyguard 盖在上面）也清槽。
        assertTrue(state.clearScreenHideIfLeft("app.a", locked = true))
        assertFalse(state.isScreenHidden)
    }

    @Test
    fun `returning after leaving does not re-hide`() {
        state.enterScreenHide("app.a")
        state.clearScreenHideIfLeft("app.b", locked = false)
        // 回到当初隐藏它的界面：槽已清，不重新隐藏；显式再触发动作才会再进。
        assertFalse(state.clearScreenHideIfLeft("app.a", locked = false))
        assertFalse(state.isScreenHidden)
    }

    @Test
    fun `clear without hide is a no-op`() {
        assertFalse(state.clearScreenHide())
        assertFalse(state.clearScreenHideIfLeft("app.b", locked = false))
        assertFalse(state.isScreenHidden)
    }

    @Test
    fun `blank package cannot enter hide`() {
        assertFalse(state.enterScreenHide(""))
        assertFalse(state.isScreenHidden)
    }

    @Test
    fun `reset clears screen hide`() {
        state.onPanelShown()
        state.enterScreenHide("app.a")
        assertTrue(state.isHidden)

        state.reset()
        assertEquals(0, state.panelCount)
        assertFalse(state.isScreenHidden)
        assertFalse(state.isHidden)
    }
}
