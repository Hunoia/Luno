package hunoia.luno.gesture

import androidx.compose.ui.geometry.Offset
import hunoia.luno.config.model.SubGesture
import hunoia.luno.config.model.SubGestureSettings
import hunoia.luno.core.domain.StoredAction
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 进入子手势会话的三种非成功结果必须是**可分辨**的：禁用与悬空引用以前都返回 false，
 * 于是执行层把它们当 `Action.SubGesture` 空操作，表现为「点了没反应」。
 */
class SubGestureEntryTest {

    private fun ref(id: String) = StoredAction.of("internal.subGesture", "id" to id)

    private fun state(vararg gestures: SubGesture) = SubGestureState(
        scope = CoroutineScope(Dispatchers.Unconfined),
        subGestureSettings = SubGestureSettings(gestures.toList()),
        onModeChanged = { _, _, _ -> },
    )

    @Test
    fun `enabled sub gesture enters the session`() {
        val state = state(SubGesture(id = "sg"))
        assertEquals(SubGestureEntry.Entered, state.tryEnterSubGesture(ref("sg"), Offset.Zero))
        assertTrue(state.isActive)
    }

    @Test
    fun `disabled sub gesture is reported instead of silently ignored`() {
        val state = state(SubGesture(id = "sg", enabled = false))
        assertEquals(SubGestureEntry.Disabled, state.tryEnterSubGesture(ref("sg"), Offset.Zero))
        assertFalse(state.isActive)
    }

    @Test
    fun `dangling reference is reported as missing`() {
        val state = state(SubGesture(id = "sg"))
        assertEquals(SubGestureEntry.Missing, state.tryEnterSubGesture(ref("gone"), Offset.Zero))
        assertFalse(state.isActive)
    }

    @Test
    fun `other actions fall through`() {
        val state = state(SubGesture(id = "sg"))
        assertEquals(
            SubGestureEntry.Ignored,
            state.tryEnterSubGesture(StoredAction.of("accessibility.back"), Offset.Zero),
        )
        assertFalse(state.isActive)
    }
}
