package hunoia.luno.runtime.floatball.inbox

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BadgeStateTest {

    private val config = BallInboxConfig(
        notificationTtlMs = 2_000L,
        clipboardTtlMs = 5_000L,
        maxItems = 10,
    )

    private fun add(state: BallInboxState, item: BallItem): BallInboxState =
        BallInboxReducer.reduce(
            state = state,
            action = BallInboxAction.Add(item),
            nowMs = item.receivedAtElapsedMs,
            config = config,
        ).state

    private fun pin(state: BallInboxState, item: BallItem): BallInboxState =
        BallInboxReducer.reduce(
            state = state,
            action = BallInboxAction.SetPinned(item.id, true),
            nowMs = item.receivedAtElapsedMs,
            config = config,
        ).state

    private fun clip(text: String, at: Long = 1L) = ClipboardTextItem(
        contentHash = "hash-$text",
        text = text,
        receivedAtElapsedMs = at,
    )

    @Test
    fun `empty inbox has no badge`() {
        val state = BallInboxState()

        assertEquals(BadgeState(0, 0), state.badge)
        assertFalse(state.badge.visible)
        assertFalse(state.badge.hasPinned)
        assertFalse(state.badge.showPinOnly)
        assertNull(state.badge.countLabel)
        assertNull(state.countLabel)
    }

    @Test
    fun `unpinned items drive the numeric badge`() {
        val a = clip("a", at = 1)
        val b = clip("b", at = 2)

        var state = BallInboxState()
        state = add(state, a)
        state = add(state, b)

        assertEquals(2, state.badge.unpinnedCount)
        assertEquals(0, state.badge.pinnedCount)
        assertEquals("2", state.badge.countLabel)
        assertTrue(state.badge.visible)
        assertFalse(state.badge.showPinOnly)
        assertEquals("1/2", state.countLabel)
    }

    @Test
    fun `pinned-only inbox shows the pin badge instead of a number`() {
        val item = clip("x")

        var state = add(BallInboxState(), item)
        state = pin(state, item)

        assertEquals(0, state.badge.unpinnedCount)
        assertEquals(1, state.badge.pinnedCount)
        assertTrue(state.badge.hasPinned)
        assertTrue(state.badge.visible)
        assertTrue(state.badge.showPinOnly)
        assertNull(state.badge.countLabel)
    }

    @Test
    fun `mixed inbox counts only unpinned items`() {
        val pinned = clip("pinned", at = 1)
        val a = clip("a", at = 2)
        val b = clip("b", at = 3)

        var state = BallInboxState()
        state = add(state, pinned)
        state = pin(state, pinned)
        state = add(state, a)
        state = add(state, b)

        assertEquals(2, state.badge.unpinnedCount)
        assertEquals(1, state.badge.pinnedCount)
        assertEquals("2", state.badge.countLabel)
        assertTrue(state.badge.visible)
        assertFalse(state.badge.showPinOnly)
        assertEquals("1/3", state.countLabel)
    }
}
