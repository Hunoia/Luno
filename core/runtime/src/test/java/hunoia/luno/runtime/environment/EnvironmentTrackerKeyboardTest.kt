package hunoia.luno.runtime.environment

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 键盘状态源回归：状态必须来自真实 IME 窗口（注入的 imeWindowVisible），
 * 不再由输入焦点推断；焦点仅作为窗口列表不可用（null）时的兜底。
 */
class EnvironmentTrackerKeyboardTest {

    private val changes = mutableListOf<Boolean>()

    private fun tracker(imeVisible: () -> Boolean?) = EnvironmentTracker(
        getCurrentPackageName = { "" },
        nowInLauncher = { false },
        imeWindowVisible = imeVisible,
        nowMs = { 0L },
        onKeyboardStateChanged = { changes += it },
    )

    @Test
    fun `seed uses ime window without emitting a pulse`() {
        val tracker = tracker { true }
        tracker.seedKeyboardInputActive()
        assertTrue(tracker.isKeyboardInputActive)
        assertTrue("seed 不应该发边沿脉冲", changes.isEmpty())
    }

    @Test
    fun `poll emits hide even when the input field keeps focus`() {
        var imeVisible = true
        val tracker = tracker { imeVisible }
        tracker.seedKeyboardInputActive()
        assertTrue(tracker.isKeyboardInputActive)

        // 模拟「输入框仍有焦点、键盘被返回键收起」：只有 IME 窗口消失。
        imeVisible = false
        tracker.pollKeyboardVisibility()

        assertFalse(tracker.isKeyboardInputActive)
        assertEquals(listOf(false), changes)
    }

    @Test
    fun `poll emits show and ignores duplicate samples`() {
        var imeVisible = false
        val tracker = tracker { imeVisible }
        tracker.seedKeyboardInputActive()

        imeVisible = true
        tracker.pollKeyboardVisibility()
        tracker.pollKeyboardVisibility()
        imeVisible = false
        tracker.pollKeyboardVisibility()
        tracker.pollKeyboardVisibility()

        assertFalse(tracker.isKeyboardInputActive)
        assertEquals(listOf(true, false), changes)
    }

    @Test
    fun `falls back to focus heuristic only when windows are unavailable`() {
        // findFocusedNode 默认返回 null：兜底结果为「键盘未弹出」，而不是沿用上次状态。
        val tracker = tracker { null }
        tracker.seedKeyboardInputActive()
        assertFalse(tracker.isKeyboardInputActive)
    }
}
