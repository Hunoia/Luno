package hunoia.luno.runtime.floatball.inbox

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class BallInboxReducerTest {

    private val config = BallInboxConfig(
        notificationTtlMs = 2_000L,
        clipboardTtlMs = 5_000L,
        maxItems = 3,
    )

    private fun reduce(
        state: BallInboxState,
        action: BallInboxAction,
        nowMs: Long = 0L,
    ): BallInboxChange = BallInboxReducer.reduce(state, action, nowMs, config)

    private fun add(
        state: BallInboxState,
        item: BallItem,
        nowMs: Long = item.receivedAtElapsedMs,
        makeCurrent: Boolean = true,
    ): BallInboxChange = reduce(state, BallInboxAction.Add(item, makeCurrent), nowMs)

    private fun notification(
        key: String,
        at: Long = 0L,
        title: String = "title-$key",
        text: String = "body-$key",
    ) = NotificationItem(
        notificationKey = key,
        packageName = "com.test",
        title = title,
        text = text,
        receivedAtElapsedMs = at,
    )

    private fun clip(text: String, at: Long = 0L) = ClipboardTextItem(
        contentHash = "hash-$text",
        text = text,
        receivedAtElapsedMs = at,
    )

    private fun ids(state: BallInboxState): List<String> = state.items.map { it.id }

    @Test
    fun `add puts newest first and selects it`() {
        val item = notification("a", at = 1_000)

        val change = add(BallInboxState(), item, nowMs = 1_000)

        assertEquals(listOf(item.id), ids(change.state))
        assertEquals(item.id, change.state.currentId)
        assertEquals(item, change.added)
        assertNull(change.updated)
        assertFalse(change.duplicate)
        assertTrue(change.changed)
        assertEquals(1, change.state.revision)
        assertEquals(1, change.state.badge.unpinnedCount)
    }

    @Test
    fun `add keeps older items behind the newest one`() {
        val first = clip("a", at = 1)
        val second = clip("b", at = 2)

        val state = add(BallInboxState(), first, nowMs = 1).state
        val change = add(state, second, nowMs = 2)

        assertEquals(listOf(second.id, first.id), ids(change.state))
        assertEquals(second.id, change.state.currentId)
    }

    @Test
    fun `same key notification updates in place and moves to front`() {
        val old = notification("a", at = 1_000, title = "old")
        val newer = notification("b", at = 1_500)
        val updated = notification("a", at = 2_000, title = "new")

        var state = add(BallInboxState(), old, nowMs = 1_000).state
        state = add(state, newer, nowMs = 1_500).state
        val change = add(state, updated, nowMs = 2_000)

        assertEquals(listOf(updated.id, newer.id), ids(change.state))
        assertEquals(updated.id, change.state.currentId)
        assertEquals(updated, change.updated)
        assertTrue(change.duplicate)
        assertEquals(2, change.state.items.size)
    }

    @Test
    fun `same key update can keep the current item while browsing`() {
        val a = notification("a", at = 1)
        val b = notification("b", at = 2)
        val updatedA = notification("a", at = 3, title = "updated")

        var state = add(BallInboxState(), a, nowMs = 1).state
        state = add(state, b, nowMs = 2).state
        val change = add(state, updatedA, nowMs = 3, makeCurrent = false)

        assertEquals(listOf(updatedA.id, b.id), ids(change.state))
        assertEquals(b.id, change.state.currentId)
        assertTrue(change.duplicate)
        assertFalse(change.currentChanged)
    }

    @Test
    fun `duplicate clipboard refreshes timestamp without duplicating`() {
        val first = clip("x", at = 100)
        val second = clip("x", at = 200)

        val state = add(BallInboxState(), first, nowMs = 100).state
        val change = add(state, second, nowMs = 200)

        assertEquals(1, change.state.items.size)
        assertEquals(second, change.updated)
        assertTrue(change.duplicate)
        assertEquals(200L, change.state.current?.receivedAtElapsedMs)
    }

    @Test
    fun `capacity evicts the oldest unpinned item`() {
        val a = clip("a", at = 1)
        val b = clip("b", at = 2)
        val c = clip("c", at = 3)

        var state = BallInboxState()
        state = add(state, a, nowMs = 1).state
        state = add(state, b, nowMs = 2).state
        state = add(state, c, nowMs = 3).state

        val change = add(state, clip("d", at = 4), nowMs = 4)

        assertEquals(listOf("clip:hash-d", "clip:hash-c", "clip:hash-b"), ids(change.state))
        assertEquals(listOf(a.id), change.removed.map { it.id })
        assertEquals("clip:hash-d", change.state.currentId)
        assertEquals(3, change.state.items.size)
    }

    @Test
    fun `capacity protects the current item when not switching`() {
        val a = clip("a", at = 1)
        val b = clip("b", at = 2)
        val c = clip("c", at = 3)

        var state = BallInboxState()
        state = add(state, a, nowMs = 1).state
        state = add(state, b, nowMs = 2).state
        state = add(state, c, nowMs = 3).state
        state = reduce(state, BallInboxAction.Select(a.id), nowMs = 3).state

        val change = add(state, clip("d", at = 4), nowMs = 4, makeCurrent = false)

        assertEquals(listOf("clip:hash-d", "clip:hash-c", "clip:hash-a"), ids(change.state))
        assertEquals(listOf(b.id), change.removed.map { it.id })
        assertEquals(a.id, change.state.currentId)
        assertFalse(change.currentChanged)
    }

    @Test
    fun `capacity can evict the current item when switching to a new one`() {
        val tightConfig = BallInboxConfig(
            notificationTtlMs = 2_000L,
            clipboardTtlMs = 5_000L,
            maxItems = 1,
        )
        val a = clip("a", at = 1)
        val b = clip("b", at = 2)
        val state = BallInboxReducer.reduce(
            state = BallInboxState(),
            action = BallInboxAction.Add(a),
            nowMs = 1,
            config = tightConfig,
        ).state

        val change = BallInboxReducer.reduce(
            state = state,
            action = BallInboxAction.Add(b),
            nowMs = 2,
            config = tightConfig,
        )

        assertEquals(listOf(b.id), ids(change.state))
        assertEquals(b.id, change.state.currentId)
        assertEquals(listOf(a.id), change.removed.map { it.id })
    }

    @Test
    fun `all pinned items make a new item drop`() {
        val a = clip("a", at = 1)
        val b = clip("b", at = 2)
        val c = clip("c", at = 3)

        var state = BallInboxState()
        state = add(state, a, nowMs = 1).state
        state = add(state, b, nowMs = 2).state
        state = add(state, c, nowMs = 3).state
        state = reduce(state, BallInboxAction.SetPinned(a.id, true), nowMs = 1).state
        state = reduce(state, BallInboxAction.SetPinned(b.id, true), nowMs = 2).state
        state = reduce(state, BallInboxAction.SetPinned(c.id, true), nowMs = 3).state
        val before = state

        val dropped = clip("d", at = 4)
        val change = add(state, dropped, nowMs = 4)

        assertEquals(dropped, change.dropped)
        assertEquals(before.items, change.state.items)
        assertEquals(before.currentId, change.state.currentId)
        assertEquals(before.revision, change.state.revision)
        assertFalse(change.changed)
    }

    @Test
    fun `pinning a clipboard item updates badge and keeps current`() {
        val item = clip("x", at = 1)
        val state = add(BallInboxState(), item, nowMs = 1).state

        val change = reduce(state, BallInboxAction.SetPinned(item.id, true), nowMs = 1)

        assertEquals(true, change.updated?.pinned)
        assertEquals(item.id, change.state.currentId)
        assertEquals(0, change.state.badge.unpinnedCount)
        assertEquals(1, change.state.badge.pinnedCount)
        assertTrue(change.state.badge.showPinOnly)
    }

    @Test
    fun `notification pin is a no-op`() {
        val item = notification("n", at = 1)
        val state = add(BallInboxState(), item, nowMs = 1).state

        val change = reduce(state, BallInboxAction.SetPinned(item.id, true), nowMs = 1)

        assertSame(state, change.state)
        assertNull(change.updated)
        assertFalse(change.changed)
    }

    @Test
    fun `unpinning an expired item removes it immediately`() {
        val item = clip("x", at = 0)
        val state = add(BallInboxState(), item, nowMs = 0).state
        val pinned = reduce(state, BallInboxAction.SetPinned(item.id, true), nowMs = 0).state

        val change = reduce(pinned, BallInboxAction.SetPinned(item.id, false), nowMs = 6_000)

        assertTrue(change.state.items.isEmpty())
        assertNull(change.state.currentId)
        assertEquals(listOf(item.id), change.removed.map { it.id })
        assertTrue(change.currentChanged)
    }

    @Test
    fun `pinning an expired item rescues it`() {
        val item = clip("x", at = 0)
        val state = add(BallInboxState(), item, nowMs = 0).state

        val change = reduce(state, BallInboxAction.SetPinned(item.id, true), nowMs = 6_000)

        assertEquals(1, change.state.items.size)
        assertEquals(true, change.state.current?.pinned)
    }

    @Test
    fun `remove current selects the next item at the same position`() {
        val a = clip("a", at = 1)
        val b = clip("b", at = 2)
        val c = clip("c", at = 3)

        var state = BallInboxState()
        state = add(state, a, nowMs = 1).state
        state = add(state, b, nowMs = 2).state
        state = add(state, c, nowMs = 3).state
        state = reduce(state, BallInboxAction.Select(b.id), nowMs = 3).state

        val change = reduce(state, BallInboxAction.Remove(b.id), nowMs = 3)

        assertEquals(listOf(c.id, a.id), ids(change.state))
        assertEquals(a.id, change.state.currentId)
        assertEquals(listOf(b.id), change.removed.map { it.id })
        assertTrue(change.currentChanged)
    }

    @Test
    fun `remove non-current keeps the current item`() {
        val a = clip("a", at = 1)
        val b = clip("b", at = 2)
        val c = clip("c", at = 3)

        var state = BallInboxState()
        state = add(state, a, nowMs = 1).state
        state = add(state, b, nowMs = 2).state
        state = add(state, c, nowMs = 3).state
        state = reduce(state, BallInboxAction.Select(b.id), nowMs = 3).state

        val change = reduce(state, BallInboxAction.Remove(a.id), nowMs = 3)

        assertEquals(listOf(c.id, b.id), ids(change.state))
        assertEquals(b.id, change.state.currentId)
        assertFalse(change.currentChanged)
    }

    @Test
    fun `removing the last current item clears the state`() {
        val item = clip("a", at = 1)
        val state = add(BallInboxState(), item, nowMs = 1).state

        val change = reduce(state, BallInboxAction.Remove(item.id), nowMs = 1)

        assertTrue(change.state.items.isEmpty())
        assertNull(change.state.currentId)
        assertTrue(change.currentChanged)
    }

    @Test
    fun `clear source removes all items of that source including pinned`() {
        val n1 = notification("n1", at = 1)
        val c = clip("c", at = 2)
        val n2 = notification("n2", at = 3)

        var state = BallInboxState()
        state = add(state, n1, nowMs = 1).state
        state = add(state, c, nowMs = 2).state
        state = add(state, n2, nowMs = 3).state
        state = reduce(state, BallInboxAction.SetPinned(c.id, true), nowMs = 3).state

        val change = reduce(state, BallInboxAction.ClearSource(BallSource.NOTIFICATION), nowMs = 3)

        assertEquals(listOf(c.id), ids(change.state))
        assertEquals(c.id, change.state.currentId)
        assertEquals(setOf(n1.id, n2.id), change.removed.map { it.id }.toSet())
        assertTrue(change.currentChanged)
    }

    @Test
    fun `clear source keeps the current item when it belongs to the other source`() {
        val n = notification("n", at = 1)
        val c = clip("c", at = 2)

        var state = BallInboxState()
        state = add(state, n, nowMs = 1).state
        state = add(state, c, nowMs = 2).state

        val change = reduce(state, BallInboxAction.ClearSource(BallSource.NOTIFICATION), nowMs = 2)

        assertEquals(listOf(c.id), ids(change.state))
        assertEquals(c.id, change.state.currentId)
        assertFalse(change.currentChanged)
    }

    @Test
    fun `cycle wraps in both directions`() {
        val a = clip("a", at = 1)
        val b = clip("b", at = 2)
        val c = clip("c", at = 3)

        var state = BallInboxState()
        state = add(state, a, nowMs = 1).state
        state = add(state, b, nowMs = 2).state
        state = add(state, c, nowMs = 3).state

        state = reduce(state, BallInboxAction.Next, nowMs = 3).state
        assertEquals(b.id, state.currentId)
        state = reduce(state, BallInboxAction.Next, nowMs = 3).state
        assertEquals(a.id, state.currentId)
        state = reduce(state, BallInboxAction.Next, nowMs = 3).state
        assertEquals(c.id, state.currentId)
        state = reduce(state, BallInboxAction.Previous, nowMs = 3).state
        assertEquals(a.id, state.currentId)
    }

    @Test
    fun `cycle and select are no-ops when there is nothing to change`() {
        val item = clip("a", at = 1)
        val state = add(BallInboxState(), item, nowMs = 1).state

        assertSame(state, reduce(state, BallInboxAction.Next, nowMs = 1).state)
        assertSame(state, reduce(state, BallInboxAction.Previous, nowMs = 1).state)
        assertSame(state, reduce(state, BallInboxAction.Select("missing"), nowMs = 1).state)
    }

    @Test
    fun `add with makeCurrent false still selects the first item of an empty queue`() {
        val item = clip("a", at = 1)

        val change = add(BallInboxState(), item, nowMs = 1, makeCurrent = false)

        assertEquals(item.id, change.state.currentId)
    }

    @Test
    fun `an already expired new item is dropped`() {
        val item = clip("a", at = 0)

        val change = add(BallInboxState(), item, nowMs = 6_000)

        assertEquals(item, change.dropped)
        assertTrue(change.state.items.isEmpty())
    }

    @Test
    fun `count label follows the current index`() {
        val a = clip("a", at = 1)
        val b = clip("b", at = 2)
        val c = clip("c", at = 3)

        var state = BallInboxState()
        state = add(state, a, nowMs = 1).state
        state = add(state, b, nowMs = 2).state
        state = add(state, c, nowMs = 3).state
        state = reduce(state, BallInboxAction.Select(b.id), nowMs = 3).state

        assertEquals("2/3", state.countLabel)
    }
}
