package hunoia.luno.runtime.recentspicker

import androidx.compose.ui.geometry.Offset
import hunoia.luno.action.model.RecentsPickerLayout
import hunoia.luno.core.domain.RecentApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RecentsPickerStateTest {

    private fun state() = RecentsPickerState(CoroutineScope(Dispatchers.Unconfined))

    @Test
    fun `activate centers on the most recent app`() {
        val state = state()
        val apps = listOf(RecentApp("a"), RecentApp("b"), RecentApp("c"))
        state.activate(apps, RecentsPickerLayout.VERTICAL, 100, 60, Offset(10f, 20f))
        assertTrue(state.isActive)
        assertEquals(apps, state.apps)
        assertEquals(RecentsPickerLayout.VERTICAL, state.layout)
        assertEquals(100, state.blankScrollSensitivityPercent)
        assertEquals(60, state.iconSizeDp)
        assertEquals(Offset(10f, 20f), state.anchor)
        // 历史旧→新，末尾最新：初始滚动到 lastIndex，配合 contentPadding 居中。
        assertEquals(2, state.listState.firstVisibleItemIndex)
    }

    @Test
    fun `activate stores sensitivity and icon size`() {
        val state = state()
        state.activate(listOf(RecentApp("a")), RecentsPickerLayout.HORIZONTAL, 250, 84, Offset.Zero)
        assertTrue(state.isActive)
        assertEquals(250, state.blankScrollSensitivityPercent)
        assertEquals(84, state.iconSizeDp)
    }

    @Test
    fun `activate with empty list keeps first visible at zero`() {
        val state = state()
        state.activate(emptyList(), RecentsPickerLayout.HORIZONTAL, 100, 60, Offset.Zero)
        assertTrue(state.isActive)
        assertTrue(state.apps.isEmpty())
        assertEquals(0, state.listState.firstVisibleItemIndex)
    }

    @Test
    fun `drag with zero delta is a no-op`() {
        val state = state()
        state.activate(listOf(RecentApp("a"), RecentApp("b")), RecentsPickerLayout.VERTICAL, 100, 60, Offset.Zero)
        // 零位移不累计、不启动滚动协程，也不崩。
        state.onDrag(Offset.Zero)
        assertTrue(state.isActive)
    }

    @Test
    fun `centered app is null when list empty`() {
        val state = state()
        state.activate(emptyList(), RecentsPickerLayout.VERTICAL, 100, 60, Offset.Zero)
        // 空列表视为「不在图标上」：抬手进预览而非打开。
        assertEquals(null, state.centeredApp())
    }

    @Test
    fun `clear resets session`() {
        val state = state()
        state.activate(listOf(RecentApp("a")), RecentsPickerLayout.HORIZONTAL, 100, 60, Offset(5f, 5f))
        state.clear()
        assertFalse(state.isActive)
        assertTrue(state.apps.isEmpty())
    }
}
