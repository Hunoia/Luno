package hunoia.luno.runtime.overlay.text

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class TextSnapshotOpsTest {

    private fun leaf(
        text: String,
        left: Int,
        top: Int,
        charWidth: Int = 10,
        charHeight: Int = 20,
    ): TextLeaf {
        val chars = text.mapIndexed { index, c ->
            TextChar(
                text = c.toString(),
                bounds = SnapshotRect(
                    left + index * charWidth,
                    top,
                    left + (index + 1) * charWidth,
                    top + charHeight,
                ),
            )
        }
        return TextLeaf(
            text = text,
            bounds = SnapshotRect(left, top, left + text.length * charWidth, top + charHeight),
            chars = chars,
        )
    }

    @Test
    fun `hit prefers the smallest leaf under the point`() {
        val big = leaf("whole paragraph", 0, 0, charWidth = 20)
        val small = leaf("word", 40, 0, charWidth = 10)
        val snapshot = TextSnapshot(leaves = listOf(big, small))

        val hit = TextSnapshotOps.hitText(snapshot, 45, 10)

        assertSame(small, hit)
    }

    @Test
    fun `hit falls back to bounds when there is no char geometry`() {
        val noChars = TextLeaf("hello", SnapshotRect(0, 0, 100, 30))
        val snapshot = TextSnapshot(leaves = listOf(noChars))

        assertSame(noChars, TextSnapshotOps.hitText(snapshot, 20, 10))
    }

    @Test
    fun `hit returns null on blank area`() {
        val snapshot = TextSnapshot(leaves = listOf(leaf("hello", 0, 0)))
        assertNull(TextSnapshotOps.hitText(snapshot, 500, 500))
    }

    @Test
    fun `selectAll returns the whole short leaf`() {
        val hit = leaf("hello world", 0, 0)
        assertEquals("hello world", TextSnapshotOps.selectAll(hit, 25, 10))
    }

    @Test
    fun `selectAll over the cap returns the line under the point`() {
        val longText = "a".repeat(1_200) + "\n" + "b".repeat(1_200)
        val chars = longText.mapIndexed { index, c ->
            val line = if (index < 1_200) 0 else 1
            val column = if (line == 0) index else index - 1_200
            TextChar(
                text = c.toString(),
                bounds = SnapshotRect(column * 4, line * 20, column * 4 + 4, line * 20 + 18),
            )
        }
        val hit = TextLeaf(
            text = longText,
            bounds = SnapshotRect(0, 0, 4_800, 40),
            chars = chars,
        )

        val selected = TextSnapshotOps.selectAll(hit, x = 40, y = 10)

        assertEquals("a".repeat(1_200), selected)
    }

    @Test
    fun `clipRegion keeps only characters intersecting the selection`() {
        val hit = leaf("abcdef", 0, 0, charWidth = 10)
        val snapshot = TextSnapshot(leaves = listOf(hit))

        val clipped = TextSnapshotOps.clipRegion(snapshot, 15, 0, 40, 20)

        assertEquals(1, clipped.size)
        assertEquals("bcd", clipped.single().text)
        assertEquals(SnapshotRect(10, 0, 40, 20), clipped.single().bounds)
    }

    @Test
    fun `clipRegion splits disjoint character runs`() {
        val hit = leaf("abcdef", 0, 0, charWidth = 10)
        val snapshot = TextSnapshot(leaves = listOf(hit))

        val clipped = TextSnapshotOps.clipRegion(snapshot, 5, 0, 65, 20)
            .filter { it.text.isNotBlank() }

        // 5..65 覆盖 a..f；这里主要钉住不会把字符顺序打乱。
        assertEquals("abcdef", clipped.joinToString("") { it.text })
    }

    @Test
    fun `clipRegion falls back to leaf bounds without char geometry`() {
        val noChars = TextLeaf("hello", SnapshotRect(0, 0, 100, 30))
        val snapshot = TextSnapshot(leaves = listOf(noChars))

        val clipped = TextSnapshotOps.clipRegion(snapshot, 10, 10, 80, 20)

        assertEquals(1, clipped.size)
        assertEquals("hello", clipped.single().text)
    }

    @Test
    fun `dedupeAndOrder removes duplicate text and sorts by reading order`() {
        val a = leaf("a", 0, 0)
        val b = leaf("b", 200, 0)
        val aDup = a.copy(bounds = a.bounds)
        val c = leaf("c", 0, 40)

        val ordered = TextSnapshotOps.dedupeAndOrder(listOf(c, b, aDup, a))

        assertEquals(listOf("a", "b", "c"), ordered.map { it.text })
    }

    @Test
    fun `imageAt returns the smallest image under the point`() {
        val big = ImageLeaf(SnapshotRect(0, 0, 200, 200))
        val small = ImageLeaf(SnapshotRect(40, 40, 60, 60))
        val snapshot = TextSnapshot(images = listOf(big, small))

        assertEquals(small, snapshot.imageAt(45, 45))
    }

    @Test
    fun `empty snapshot has no content`() {
        assertTrue(TextSnapshot.Empty.isEmpty)
    }
}
