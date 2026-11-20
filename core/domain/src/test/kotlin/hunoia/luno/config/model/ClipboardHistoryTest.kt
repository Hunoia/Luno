package hunoia.luno.config.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ClipboardHistoryTest {

    private fun text(value: String, pinned: Boolean = false) =
        ClipboardEntry(kind = ClipKind.TEXT, text = value, pinned = pinned)

    private fun image(name: String) =
        ClipboardEntry(kind = ClipKind.IMAGE, imagePath = name, mime = "image/png")

    @Test
    fun `records newest first`() {
        val out = ClipboardHistory()
            .record(text("a"), now = 1_000L)
            .record(text("b"), now = 2_000L)
        assertEquals(listOf("b", "a"), out.entries.map { it.text })
    }

    @Test
    fun `blank text and image without file are ignored`() {
        assertTrue(ClipboardHistory().record(text("   "), now = 1_000L).entries.isEmpty())
        assertTrue(
            ClipboardHistory()
                .record(ClipboardEntry(kind = ClipKind.IMAGE, imagePath = null), now = 1_000L)
                .entries.isEmpty(),
        )
    }

    @Test
    fun `duplicate text is moved to front and keeps id and pinned`() {
        val out = ClipboardHistory()
            .record(text("a"), now = 1_000L)
            .record(text("b"), now = 2_000L)
        val id = out.entries.first { it.text == "a" }.id
        val pinned = out.setPinned(id, true)
        val again = pinned.record(text("a"), now = 3_000L)

        assertEquals(listOf("a", "b"), again.entries.map { it.text })
        assertEquals(3_000L, again.entries.first().time)
        assertEquals(id, again.entries.first().id)
        assertTrue(again.entries.first().pinned)
        assertEquals(1, again.entries.count { it.text == "a" })
    }

    @Test
    fun `trim only drops oldest unpinned entries`() {
        var out = ClipboardHistory().record(text("pinned", pinned = true), now = 0L)
        repeat(12) { i -> out = out.record(text("u$i"), now = 1L + i) }
        out = out.trimmed(maxEntries = 10)

        assertEquals(1, out.pinned.size)
        assertEquals(10, out.unpinned.size)
        assertEquals("u11", out.unpinned.first().text)
        assertEquals("u2", out.unpinned.last().text)
    }

    @Test
    fun `pinned entries do not consume the unpinned quota`() {
        var out = ClipboardHistory()
        repeat(3) { out = out.record(text("p$it", pinned = true), now = it.toLong()) }
        repeat(15) { out = out.record(text("u$it"), now = 10L + it, maxEntries = 10) }

        assertEquals(3, out.pinned.size)
        assertEquals(10, out.unpinned.size)
    }

    @Test
    fun `text over 100KB is dropped instead of truncated`() {
        val huge = "x".repeat(ClipboardMaxTextChars + 1)
        val out = ClipboardHistory().record(text(huge), now = 1L)
        assertTrue(out.entries.isEmpty())
    }

    @Test
    fun `remove deletes one entry and is idempotent`() {
        val out = ClipboardHistory().record(text("a"), now = 1_000L).record(text("b"), now = 2_000L)
        val id = out.entries.first { it.text == "a" }.id
        val removed = out.remove(id)
        assertEquals(listOf("b"), removed.entries.map { it.text })
        assertEquals(removed, removed.remove(id))
    }

    @Test
    fun `cleared empties history and is idempotent`() {
        val out = ClipboardHistory().record(text("a"), now = 1_000L)
        assertTrue(out.cleared().entries.isEmpty())
        assertTrue(ClipboardHistory().cleared().entries.isEmpty())
    }

    @Test
    fun `cleared keeping pinned removes only unpinned and is idempotent`() {
        val out = ClipboardHistory()
            .record(text("fixed", pinned = true), now = 1_000L)
            .record(text("a"), now = 2_000L)
            .record(text("b"), now = 3_000L)

        val cleared = out.clearedKeepingPinned()
        assertEquals(listOf("fixed"), cleared.entries.map { it.text })
        assertTrue(cleared.entries.first().pinned)
        // 没有未固定项时原样返回，不写盘。
        assertEquals(cleared, cleared.clearedKeepingPinned())
        assertEquals(ClipboardHistory(), ClipboardHistory().clearedKeepingPinned())
    }

    @Test
    fun `cleared keeping pinned by kind clears only that kinds unpinned`() {
        val out = ClipboardHistory()
            .record(text("fixed", pinned = true), now = 1_000L)
            .record(text("a"), now = 2_000L)
            .record(image("p1.png"), now = 3_000L)

        // 文本页清空：清未固定文本，保留文本固定 + 全部图片。
        val clearedText = out.clearedKeepingPinned(ClipKind.TEXT)
        assertEquals(listOf("fixed"), clearedText.entries.filter { it.kind == ClipKind.TEXT }.map { it.text })
        assertEquals(listOf("p1.png"), clearedText.entries.filter { it.kind == ClipKind.IMAGE }.map { it.imagePath })

        // 图片页清空：清未固定图片，保留文本（含未固定）与图片固定；图片无固定，全清。
        val clearedImage = out.clearedKeepingPinned(ClipKind.IMAGE)
        assertEquals(listOf("a", "fixed"), clearedImage.entries.map { it.text })
        assertTrue(clearedImage.entries.none { it.kind == ClipKind.IMAGE })
        // 无图片后再清图片页 → 原样返回（幂等）。
        assertEquals(clearedImage, clearedImage.clearedKeepingPinned(ClipKind.IMAGE))
    }

    @Test
    fun `cleared keeping pinned by kind keeps other kinds even when this kind has none`() {
        val out = ClipboardHistory()
            .record(image("p1.png"), now = 1_000L)
            .record(text("t1"), now = 2_000L)

        // 图片页清空：保留全部文本（含未固定），只清未固定图片。
        val clearedImage = out.clearedKeepingPinned(ClipKind.IMAGE)
        assertEquals(listOf("t1"), clearedImage.entries.map { it.text })
        assertEquals(1, clearedImage.entries.size)
        // 幂等。
        assertEquals(clearedImage, clearedImage.clearedKeepingPinned(ClipKind.IMAGE))
    }

    @Test
    fun `pin moves entry to front and unpin keeps it there`() {
        val out = ClipboardHistory()
            .record(text("a"), now = 1_000L)
            .record(text("b"), now = 2_000L)
        val id = out.entries.first { it.text == "a" }.id

        val pinned = out.setPinned(id, true)
        assertEquals("a", pinned.entries.first().text)
        assertTrue(pinned.entries.first().pinned)

        val unpinned = pinned.setPinned(id, false)
        assertEquals("a", unpinned.entries.first().text)
        assertFalse(unpinned.entries.first().pinned)
    }

    @Test
    fun `shrinking max entries trims immediately`() {
        val out = (1..15).fold(ClipboardHistory()) { acc, i ->
            acc.record(text("t$i"), now = i.toLong())
        }.withMaxEntries(10)
        assertEquals(10, out.entries.size)
        assertEquals("t15", out.entries.first().text)
        assertEquals("t6", out.entries.last().text)
    }

    @Test
    fun `export excludes images but keeps every text entry`() {
        val out = ClipboardHistory()
            .record(image("a.jpg"), now = 1L)
            .record(text("code", pinned = true), now = 2L)
            .record(text("fixed", pinned = true), now = 3L)
            .record(text("plain"), now = 4L)

        val exported = out.exportableEntries()
        assertEquals(listOf("plain", "fixed", "code"), exported.map { it.text })
        assertFalse(exported.first().pinned)
        assertTrue(exported.last().pinned)
    }

    @Test
    fun `search pool includes pinned and history text alike`() {
        val out = ClipboardHistory()
            .record(text("visible", pinned = true), now = 1L)
            .record(text("code", pinned = true), now = 2L)
            .record(text("plain"), now = 3L)

        val (pinned, history) = out.searchPool()
        assertEquals(listOf("code", "visible"), pinned)
        assertEquals(listOf("plain"), history)
    }

    @Test
    fun `merge imported keeps pinned flag and dedupes without overwriting`() {
        val current = ClipboardHistory()
            .record(text("local"), now = 1L)
            .record(text("dup"), now = 2L)

        val merged = current.mergeImported(
            listOf(
                ClipboardExportEntry("imported-fixed", pinned = true),
                ClipboardExportEntry("dup", pinned = true),
            ),
            now = 3L,
            maxEntries = 50,
        )

        assertEquals(listOf("imported-fixed", "dup", "local"), merged.entries.map { it.text })
        assertTrue(merged.entries.first().pinned)
        assertTrue(merged.entries.first { it.text == "dup" }.pinned)
        assertEquals(1, merged.entries.count { it.text == "dup" })
    }

    @Test
    fun `of kind keeps pinned first and filters other kinds`() {
        val out = ClipboardHistory()
            .record(text("plain-new"), now = 1L)
            .record(image("a.png"), now = 2L)
            .record(text("fixed", pinned = true), now = 3L)
            .record(image("b.png"), now = 4L)

        assertEquals(listOf("fixed", "plain-new"), out.ofKind(ClipKind.TEXT).map { it.text })
        assertEquals(listOf("b.png", "a.png"), out.ofKind(ClipKind.IMAGE).map { it.imagePath })
    }

    @Test
    fun `merge imported respects max entries`() {
        val merged = ClipboardHistory().mergeImported(
            (20 downTo 1).map { ClipboardExportEntry("t$it") },
            now = 1L,
            maxEntries = 10,
        )
        assertEquals(10, merged.entries.size)
        assertEquals("t20", merged.entries.first().text)
    }
}
