package hunoia.luno.annotate.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AnnotateOpsTest {

    private fun state() = AnnotateState.of(imageWidth = 1000, imageHeight = 2000)

    private fun stroke(id: Long) = StrokeElement(
        id = id,
        points = listOf(Pt(10f, 10f), Pt(20f, 20f)),
        colorArgb = 0xFFFF0000,
        width = 6f,
    )

    @Test
    fun `elements keep insertion order so later strokes draw on top`() {
        var s = state()
        s = AnnotateOps.addElement(s, stroke(1))
        s = AnnotateOps.addElement(s, stroke(2))
        assertEquals(listOf(1L, 2L), s.elements.map { it.id })
    }

    @Test
    fun `undo and redo walk element snapshots`() {
        var s = state()
        s = AnnotateOps.addElement(s, stroke(1))
        s = AnnotateOps.addElement(s, stroke(2))

        s = AnnotateOps.undo(s)
        assertEquals(listOf(1L), s.elements.map { it.id })
        s = AnnotateOps.undo(s)
        assertTrue(s.elements.isEmpty())
        s = AnnotateOps.undo(s)
        assertTrue(s.elements.isEmpty())

        s = AnnotateOps.redo(s)
        assertEquals(listOf(1L), s.elements.map { it.id })
        s = AnnotateOps.redo(s)
        assertEquals(listOf(1L, 2L), s.elements.map { it.id })
        s = AnnotateOps.redo(s)
        assertEquals(listOf(1L, 2L), s.elements.map { it.id })
    }

    @Test
    fun `new element clears redo stack`() {
        var s = state()
        s = AnnotateOps.addElement(s, stroke(1))
        s = AnnotateOps.addElement(s, stroke(2))
        s = AnnotateOps.undo(s)
        s = AnnotateOps.addElement(s, stroke(3))
        assertTrue(s.redoStack.isEmpty())
        assertEquals(listOf(1L, 3L), s.elements.map { it.id })
    }

    @Test
    fun `undo history is capped`() {
        var s = state()
        for (i in 1..(AnnotateOps.UNDO_LIMIT + 5)) {
            s = AnnotateOps.addElement(s, stroke(i.toLong()))
        }
        assertEquals(AnnotateOps.UNDO_LIMIT, s.undoStack.size)
        repeat(AnnotateOps.UNDO_LIMIT) { s = AnnotateOps.undo(s) }
        // 只保留最近 30 步：最早停在第 5 个元素。
        assertEquals(5, s.elements.size)
    }

    @Test
    fun `destructive clear drops history`() {
        var s = state()
        s = AnnotateOps.addElement(s, stroke(1))
        s = AnnotateOps.clearElements(s)
        assertTrue(s.elements.isEmpty())
        assertTrue(s.undoStack.isEmpty())
        assertTrue(s.redoStack.isEmpty())
        s = AnnotateOps.undo(s)
        assertTrue(s.elements.isEmpty())
    }

    @Test
    fun `selection is normalized clamped and ignores tiny drags`() {
        var s = AnnotateOps.setSelection(state(), PxRect(800f, 1500f, 200f, 100f))
        assertEquals(PxRect(200f, 100f, 800f, 1500f), s.selection)

        s = AnnotateOps.setSelection(s, PxRect(-50f, -50f, 2000f, 3000f))
        assertEquals(PxRect(0f, 0f, 1000f, 2000f), s.selection)

        s = AnnotateOps.setSelection(s, PxRect(10f, 10f, 14f, 14f))
        assertNull(s.selection)
    }

    @Test
    fun `export crop falls back to full image`() {
        assertEquals(PxRect(0f, 0f, 1000f, 2000f), AnnotateOps.exportCropRect(state()))

        val withSelection = AnnotateOps.setSelection(state(), PxRect(100f, 200f, 300f, 400f))
        assertEquals(PxRect(100f, 200f, 300f, 400f), AnnotateOps.exportCropRect(withSelection))
    }

    @Test
    fun `replace image keeps strokes but re-clamps selection`() {
        var s = state()
        s = AnnotateOps.addElement(s, stroke(1))
        s = AnnotateOps.setSelection(s, PxRect(100f, 100f, 900f, 1900f))

        val kept = AnnotateOps.replaceImage(s, imageWidth = 500, imageHeight = 800, keepElements = true)
        assertEquals(listOf(1L), kept.elements.map { it.id })
        assertEquals(PxRect(100f, 100f, 500f, 800f), kept.selection)
        assertEquals(1, kept.undoStack.size)
    }

    @Test
    fun `replace image can drop strokes and history`() {
        var s = state()
        s = AnnotateOps.addElement(s, stroke(1))
        s = AnnotateOps.setSelection(s, PxRect(100f, 100f, 900f, 1900f))

        val cleared = AnnotateOps.replaceImage(s, 500, 800, keepElements = false)
        assertTrue(cleared.elements.isEmpty())
        assertTrue(cleared.undoStack.isEmpty())
        assertTrue(cleared.redoStack.isEmpty())
        assertNull(cleared.selection)
    }

    @Test
    fun `toolbar anchor is clamped to screen fractions`() {
        val moved = AnnotateOps.moveToolbar(state(), ToolbarAnchor(1.4f, -0.2f))
        assertEquals(ToolbarAnchor(1f, 0f), moved.toolbarAnchor)
    }

    @Test
    fun `element bounds include stroke width padding`() {
        val bounds = stroke(1).bounds()
        assertEquals(7f, bounds.left, 0.01f)
        assertEquals(7f, bounds.top, 0.01f)
        assertEquals(23f, bounds.right, 0.01f)
        assertEquals(23f, bounds.bottom, 0.01f)
    }
}
