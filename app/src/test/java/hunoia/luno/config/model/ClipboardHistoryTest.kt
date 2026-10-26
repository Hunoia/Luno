package hunoia.luno.config.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ClipboardHistoryTest {

    @Test
    fun `records newest first`() {
        val out = ClipboardHistory()
            .record("a", now = 1_000L)
            .record("b", now = 2_000L)
        assertEquals(listOf("b", "a"), out.entries.map { it.text })
    }

    @Test
    fun `blank text is ignored`() {
        val out = ClipboardHistory().record("   ", now = 1_000L)
        assertTrue(out.entries.isEmpty())
    }

    @Test
    fun `duplicate text is moved to front instead of duplicated`() {
        val out = ClipboardHistory()
            .record("a", now = 1_000L)
            .record("b", now = 2_000L)
            .record("a", now = 3_000L)
        assertEquals(listOf("a", "b"), out.entries.map { it.text })
        assertEquals(3_000L, out.entries.first().time)
        assertEquals(1, out.entries.count { it.text == "a" })
    }

    @Test
    fun `trims to max entries`() {
        val out = (1..5).fold(ClipboardHistory()) { acc, i ->
            acc.record("t$i", now = i.toLong(), maxEntries = 3)
        }
        assertEquals(listOf("t5", "t4", "t3"), out.entries.map { it.text })
    }

    @Test
    fun `remove deletes one entry and is idempotent`() {
        val out = ClipboardHistory().record("a", now = 1_000L).record("b", now = 2_000L)
        val id = out.entries.first { it.text == "a" }.id
        val removed = out.remove(id)
        assertEquals(listOf("b"), removed.entries.map { it.text })
        assertEquals(removed, removed.remove(id))
    }

    @Test
    fun `cleared empties history and is idempotent`() {
        val out = ClipboardHistory().record("a", now = 1_000L)
        assertTrue(out.cleared().entries.isEmpty())
        assertTrue(ClipboardHistory().cleared().entries.isEmpty())
    }
}
