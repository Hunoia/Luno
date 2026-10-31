package hunoia.luno.runtime.floatball.inbox

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class BallInboxTtlTest {

    private val config = BallInboxConfig(
        notificationTtlMs = 2_000L,
        clipboardTtlMs = 5_000L,
        maxItems = 10,
    )

    private fun reduce(
        state: BallInboxState,
        action: BallInboxAction,
        nowMs: Long,
    ): BallInboxChange = BallInboxReducer.reduce(state, action, nowMs, config)

    private fun add(
        state: BallInboxState,
        item: BallItem,
        nowMs: Long = item.receivedAtElapsedMs,
        makeCurrent: Boolean = true,
    ): BallInboxChange = reduce(state, BallInboxAction.Add(item, makeCurrent), nowMs)

    private fun notification(key: String, at: Long) = NotificationItem(
        notificationKey = key,
        packageName = "com.test",
        title = "title-$key",
        text = "body-$key",
        receivedAtElapsedMs = at,
    )

    private fun clip(text: String, at: Long) = ClipboardTextItem(
        contentHash = "hash-$text",
        text = text,
        receivedAtElapsedMs = at,
    )

    private fun ids(state: BallInboxState): List<String> = state.items.map { it.id }

    @Test
    fun `notification and clipboard use their own ttl`() {
        val n = notification("n", at = 0)
        val c = clip("c", at = 0)

        var state = add(BallInboxState(), n, nowMs = 0).state
        state = add(state, c, nowMs = 0).state

        val change = reduce(state, BallInboxAction.Prune(), nowMs = 2_500)

        assertEquals(listOf(c.id), ids(change.state))
        assertEquals(listOf(n.id), change.expired.map { it.id })
        assertEquals(c.id, change.state.currentId)
    }

    @Test
    fun `prune keeps the visible current item even after ttl`() {
        val n = notification("n", at = 0)
        val state = add(BallInboxState(), n, nowMs = 0).state

        val kept = reduce(state, BallInboxAction.Prune(keepId = n.id), nowMs = 2_000)

        assertSame(state, kept.state)
        assertTrue(kept.expired.isEmpty())

        val removed = reduce(state, BallInboxAction.Prune(), nowMs = 2_000)

        assertTrue(removed.state.items.isEmpty())
        assertEquals(listOf(n.id), removed.expired.map { it.id })
    }

    @Test
    fun `add prunes expired items before inserting`() {
        val n = notification("n", at = 0)
        val state = add(BallInboxState(), n, nowMs = 0).state

        val change = add(state, clip("c", at = 3_000), nowMs = 3_000)

        assertEquals(listOf("clip:hash-c"), ids(change.state))
        assertEquals(listOf(n.id), change.expired.map { it.id })
        assertEquals("clip:hash-c", change.state.currentId)
    }

    @Test
    fun `add with makeCurrent false keeps an expired current item until hide`() {
        val n = notification("n", at = 0)
        val state = add(BallInboxState(), n, nowMs = 0).state

        val change = add(
            state = state,
            item = clip("c", at = 3_000),
            nowMs = 3_000,
            makeCurrent = false,
        )

        assertEquals(listOf("clip:hash-c", n.id), ids(change.state))
        assertEquals(n.id, change.state.currentId)
        assertTrue(change.expired.isEmpty())
    }

    @Test
    fun `pinned item survives pruning indefinitely`() {
        val c = clip("c", at = 0)
        var state = add(BallInboxState(), c, nowMs = 0).state
        state = reduce(state, BallInboxAction.SetPinned(c.id, true), nowMs = 0).state

        val change = reduce(state, BallInboxAction.Prune(), nowMs = 100_000)

        assertSame(state, change.state)
        assertTrue(change.expired.isEmpty())
    }

    @Test
    fun `prune selects the next item after the current one expires`() {
        val a = notification("a", at = 0)
        val b = notification("b", at = 1_000)
        val c = notification("c", at = 1_900)

        var state = BallInboxState()
        state = add(state, a, nowMs = 0).state
        state = add(state, b, nowMs = 1_000).state
        state = add(state, c, nowMs = 1_900).state
        state = reduce(state, BallInboxAction.Select(a.id), nowMs = 1_900).state

        val change = reduce(state, BallInboxAction.Prune(), nowMs = 2_000)

        assertEquals(listOf(a.id), change.expired.map { it.id })
        assertEquals(listOf(c.id, b.id), ids(change.state))
        assertEquals(b.id, change.state.currentId)
        assertTrue(change.currentChanged)
    }
}
