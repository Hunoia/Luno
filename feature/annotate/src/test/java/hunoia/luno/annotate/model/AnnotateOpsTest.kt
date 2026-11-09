package hunoia.luno.annotate.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
    fun `element bounds include stroke width padding`() {
        val bounds = stroke(1).bounds()
        assertEquals(7f, bounds.left, 0.01f)
        assertEquals(7f, bounds.top, 0.01f)
        assertEquals(23f, bounds.right, 0.01f)
        assertEquals(23f, bounds.bottom, 0.01f)
    }

    // ---- 矩形橡皮（C1）----

    private fun shape(id: Long) = ShapeElement(
        id = id,
        start = Pt(100f, 100f),
        end = Pt(200f, 200f),
        colorArgb = 0xFF00FF00,
        width = 6f,
        kind = ElementKind.ARROW,
    )

    @Test
    fun `rect eraser removes strokes and shapes crossing the rect but not texts or mosaic`() {
        var s = state()
        s = AnnotateOps.addElement(s, stroke(1)) // (10,10)-(20,20)
        s = AnnotateOps.addElement(s, shape(2))
        s = AnnotateOps.addElement(
            s,
            TextElement(id = 3, position = Pt(15f, 15f), text = "hi", colorArgb = 0xFF000000, textSize = 20f),
        )
        s = AnnotateOps.addElement(
            s,
            MosaicElement(
                id = 4,
                points = listOf(Pt(5f, 5f), Pt(30f, 30f)),
                width = 10f,
                cellSize = 8f,
            ),
        )
        s = AnnotateOps.addElement(
            s,
            // 橡皮笔迹本身不能被矩形擦删掉（否则已擦区域会露回来）。
            StrokeElement(id = 5, points = listOf(Pt(12f, 12f), Pt(18f, 18f)), colorArgb = 0, width = 6f, erase = true),
        )

        val erased = AnnotateOps.eraseInRect(s, PxRect(0f, 0f, 50f, 50f))
        // 箭头 (100,100)-(200,200) 离框远，保留。
        assertEquals(listOf(2L, 3L, 4L, 5L), erased.elements.map { it.id })
        // 擦除只追加一步历史（前面的 addElement 各自带一步，只比增量）。
        assertEquals(s.undoStack.size + 1, erased.undoStack.size)
    }

    @Test
    fun `rect eraser cuts a stroke passing through the rect without touching its endpoints`() {
        val crossing = StrokeElement(
            id = 9,
            points = listOf(Pt(0f, 100f), Pt(300f, 100f)),
            colorArgb = 0xFFFF0000,
            width = 6f,
        )
        var s = AnnotateOps.addElement(state(), crossing)
        s = AnnotateOps.eraseInRect(s, PxRect(140f, 90f, 160f, 110f))
        assertTrue(s.elements.isEmpty())
    }

    @Test
    fun `rect eraser miss returns state without pushing undo`() {
        val s = AnnotateOps.addElement(state(), stroke(1))
        val missed = AnnotateOps.eraseInRect(s, PxRect(500f, 500f, 600f, 600f))
        assertEquals(s, missed)
    }

    @Test
    fun `rect eraser is one undo step`() {
        var s = state()
        s = AnnotateOps.addElement(s, stroke(1))
        s = AnnotateOps.addElement(s, stroke(2))
        s = AnnotateOps.eraseInRect(s, PxRect(0f, 0f, 50f, 50f))
        assertTrue(s.elements.isEmpty())
        s = AnnotateOps.undo(s)
        assertEquals(listOf(1L, 2L), s.elements.map { it.id })
    }

    // ---- 清除全部（可撤销）与单点删除 ----    @Test
    fun `clear all is undoable unlike destructive clear`() {
        var s = state()
        s = AnnotateOps.addElement(s, stroke(1))
        s = AnnotateOps.addElement(s, stroke(2))

        val before = s.undoStack.size
        s = AnnotateOps.clearAll(s)
        assertTrue(s.elements.isEmpty())
        assertEquals(before + 1, s.undoStack.size)
        s = AnnotateOps.undo(s)
        assertEquals(listOf(1L, 2L), s.elements.map { it.id })
    }

    @Test
    fun `clear all on empty document is a no-op`() {
        val s = state()
        assertEquals(s, AnnotateOps.clearAll(s))
    }

    @Test
    fun `remove element pushes one undo step and ignores unknown id`() {
        var s = state()
        s = AnnotateOps.addElement(s, stroke(1))
        val removed = AnnotateOps.removeElement(s, 1L)
        assertTrue(removed.elements.isEmpty())
        assertEquals(s, AnnotateOps.removeElement(s, 42L))
        s = AnnotateOps.undo(removed)
        assertEquals(listOf(1L), s.elements.map { it.id })
    }

    // ---- 矩形擦模式开关 ----

    @Test
    fun `switching tool exits rect eraser mode`() {
        var s = AnnotateOps.selectTool(state(), ToolKind.ERASER)
        s = AnnotateOps.setEraserRect(s, true)
        assertTrue(s.eraserRect)
        s = AnnotateOps.selectTool(s, ToolKind.PEN)
        assertFalse(s.eraserRect)
        // 同一工具重复点击也复位（不残留框选模式）。
        s = AnnotateOps.setEraserRect(s, true)
        s = AnnotateOps.selectTool(s, ToolKind.ERASER)
        assertFalse(s.eraserRect)
    }

    // ---- 持久化偏好应用 ----

    @Test
    fun `apply settings merges styles and keeps built-in defaults`() {
        val s = AnnotateOps.applySettings(
            state(),
            styles = mapOf(ToolKind.PEN to ToolStyle(colorArgb = 0xFF0000FF, strokeWidth = 12f)),
        )

        assertEquals(0xFF0000FF, s.styleFor(ToolKind.PEN).colorArgb)
        assertEquals(12f, s.styleFor(ToolKind.PEN).strokeWidth)
        // 没存过的工具仍用内置默认（不是被空 map 清掉）。
        assertEquals(AnnotateState.DEFAULT_STYLES.getValue(ToolKind.MOSAIC), s.styleFor(ToolKind.MOSAIC))
        assertTrue(s.elements.isEmpty())
        assertTrue(s.undoStack.isEmpty())
    }

    @Test
    fun `apply settings does not touch content or undo history`() {
        var s = AnnotateOps.addElement(state(), stroke(1))
        val before = s.undoStack.size
        s = AnnotateOps.applySettings(
            s,
            styles = mapOf(ToolKind.ERASER to ToolStyle(strokeWidth = 24f)),
        )
        assertEquals(listOf(1L), s.elements.map { it.id })
        assertEquals(before, s.undoStack.size)
        assertEquals(24f, s.styleFor(ToolKind.ERASER).strokeWidth)
    }

    // ---- 马赛克涂抹笔刷（B4）----

    private fun mosaic(id: Long, vararg points: Pt) = MosaicElement(
        id = id,
        points = points.toList(),
        width = 40f,
        cellSize = 24f,
    )

    @Test
    fun `mosaic trail bounds follow points plus brush radius`() {
        val bounds = mosaic(1, Pt(10f, 20f), Pt(110f, 120f)).bounds()
        assertEquals(-10f, bounds.left, 0.01f)
        assertEquals(0f, bounds.top, 0.01f)
        assertEquals(130f, bounds.right, 0.01f)
        assertEquals(140f, bounds.bottom, 0.01f)
    }

    @Test
    fun `mosaic single point has a visible hit box`() {
        val bounds = mosaic(1, Pt(100f, 100f)).bounds()
        assertEquals(80f, bounds.left, 0.01f)
        assertEquals(120f, bounds.right, 0.01f)
        assertTrue(bounds.contains(Pt(107f, 93f)))
        assertFalse(bounds.contains(Pt(160f, 160f)))
    }

    @Test
    fun `mosaic bounds union trail and rect fill`() {
        val element = MosaicElement(
            id = 1,
            points = listOf(Pt(300f, 300f)),
            width = 20f,
            cellSize = 16f,
            rect = PxRect(10f, 20f, 50f, 60f),
        )
        val bounds = element.bounds()
        assertEquals(10f, bounds.left, 0.01f)
        assertEquals(20f, bounds.top, 0.01f)
        assertEquals(310f, bounds.right, 0.01f)
        assertEquals(310f, bounds.bottom, 0.01f)
    }

    @Test
    fun `mosaic sanitized clamps cell and width`() {
        val tooSmall = MosaicElement(id = 1, points = listOf(Pt(1f, 1f)), width = 0f, cellSize = 0f).sanitized()
        assertEquals(2f, tooSmall.width)
        assertEquals(4f, tooSmall.cellSize)

        val tooBig = MosaicElement(id = 1, points = listOf(Pt(1f, 1f)), width = 999f, cellSize = 999f).sanitized()
        assertEquals(256f, tooBig.width)
        assertEquals(96f, tooBig.cellSize)
    }

    @Test
    fun `mosaic sanitized normalizes reversed rect`() {
        val element = MosaicElement(
            id = 1,
            width = 10f,
            cellSize = 8f,
            rect = PxRect(80f, 90f, 20f, 30f),
        ).sanitized()
        assertEquals(PxRect(20f, 30f, 80f, 90f), element.rect)
    }

    @Test
    fun `mosaic is transformed and translated as a whole`() {
        val element = MosaicElement(
            id = 1,
            points = listOf(Pt(10f, 10f), Pt(20f, 10f)),
            width = 10f,
            cellSize = 10f,
            rect = PxRect(0f, 0f, 30f, 30f),
        )

        val moved = element.translated(5f, 7f) as MosaicElement
        assertEquals(listOf(Pt(15f, 17f), Pt(25f, 17f)), moved.points)
        assertEquals(PxRect(5f, 7f, 35f, 37f), moved.rect)

        val scaled = element.transformed(
            from = PxRect(0f, 0f, 30f, 30f),
            to = PxRect(0f, 0f, 60f, 60f),
        ) as MosaicElement
        assertEquals(listOf(Pt(20f, 20f), Pt(40f, 20f)), scaled.points)
        assertEquals(PxRect(0f, 0f, 60f, 60f), scaled.rect)
        assertEquals(20f, scaled.width)
        assertEquals(20f, scaled.cellSize)
    }

    @Test
    fun `mosaic element is one undo step like other elements`() {
        var s = AnnotateOps.addElement(state(), mosaic(1, Pt(10f, 10f), Pt(30f, 30f)))
        s = AnnotateOps.addElement(s, mosaic(2, Pt(40f, 40f)))
        s = AnnotateOps.undo(s)
        assertEquals(listOf(1L), s.elements.map { it.id })
    }

    // ---- 元素选中 / 删除（B5）----

    @Test
    fun `select element keeps only ids present in document`() {
        var s = AnnotateOps.addElement(state(), stroke(1))
        s = AnnotateOps.selectElement(s, 1L)
        assertEquals(1L, s.selectedElementId)
        s = AnnotateOps.selectElement(s, 42L)
        assertNull(s.selectedElementId)
        // 选中不是内容，不产生撤销步。
        assertTrue(s.undoStack.size == 1)
    }

    @Test
    fun `remove selected pushes one undo step and clears selection`() {
        var s = AnnotateOps.addElement(state(), stroke(1))
        s = AnnotateOps.selectElement(s, 1L)
        val before = s.undoStack.size
        s = AnnotateOps.removeSelected(s)
        assertTrue(s.elements.isEmpty())
        assertNull(s.selectedElementId)
        assertEquals(before + 1, s.undoStack.size)
        s = AnnotateOps.undo(s)
        assertEquals(listOf(1L), s.elements.map { it.id })
        // 撤销回来的元素不再保持选中（撤销前已删除）。
        assertNull(s.selectedElementId)
    }

    @Test
    fun `remove selected without selection is a no-op`() {
        val s = state()
        assertEquals(s, AnnotateOps.removeSelected(s))
    }

    @Test
    fun `undo drops selection for elements that no longer exist`() {
        var s = AnnotateOps.addElement(state(), stroke(1))
        s = AnnotateOps.selectElement(s, 1L)
        s = AnnotateOps.undo(s)
        assertTrue(s.elements.isEmpty())
        assertNull(s.selectedElementId)
    }

    // ---- 选区填充马赛克（B6）----

    @Test
    fun `fill selection with mosaic adds one undoable element`() {
        var s = AnnotateOps.setSelection(state(), PxRect(100f, 200f, 300f, 400f))
        s = AnnotateOps.fillSelectionMosaic(s, id = 9L, cellSize = 24f)
        val mosaic = s.elements.single() as MosaicElement
        assertEquals(PxRect(100f, 200f, 300f, 400f), mosaic.rect)
        assertTrue(mosaic.points.isEmpty())
        assertEquals(24f, mosaic.cellSize)
        s = AnnotateOps.undo(s)
        assertTrue(s.elements.isEmpty())
    }

    @Test
    fun `fill selection without selection is a no-op`() {
        val s = state()
        assertEquals(s, AnnotateOps.fillSelectionMosaic(s, id = 9L, cellSize = 24f))
    }

    // ---- 双指缩放 / 平移（B7）----

    @Test
    fun `contain fit applies zoom and clamps pan to image edges`() {
        val fit = ContainFit.of(imageWidth = 1000f, imageHeight = 2000f, viewWidth = 1000f, viewHeight = 1000f)
        // 基准 contain：scale=0.5，图片 500×1000 居中。
        assertEquals(0.5f, fit.scale, 0.001f)
        assertEquals(250f, fit.offsetX, 0.001f)
        assertEquals(0f, fit.offsetY, 0.001f)

        val zoomed = fit.withViewTransform(2f, Pt(0f, 0f))
        assertEquals(1f, zoomed.scale, 0.001f)
        assertEquals(0f, zoomed.offsetX, 0.001f)
        assertEquals(-500f, zoomed.offsetY, 0.001f)

        // 放大 2 倍后图片高 2000 > 视图 1000，最多上下各移 500。
        assertEquals(Pt(0f, 500f), fit.clampPan(2f, Pt(0f, 999f)))
        assertEquals(Pt(0f, -500f), fit.clampPan(2f, Pt(0f, -999f)))
        // 宽度方向没超出，锁定为 0。
        assertEquals(0f, fit.clampPan(2f, Pt(300f, 0f)).x, 0.001f)
    }

    // ---- 原分辨率导出映射（B8）----

    @Test
    fun `scale image maps elements and selection to full resolution`() {
        var s = AnnotateOps.addElement(state(), stroke(1)) // 1000×2000
        s = AnnotateOps.setSelection(s, PxRect(100f, 200f, 300f, 400f))

        val scaled = AnnotateOps.scaleImage(s, newWidth = 2000, newHeight = 4000)
        assertEquals(2000, scaled.imageWidth)
        assertEquals(4000, scaled.imageHeight)
        val scaledStroke = scaled.elements.single() as StrokeElement
        assertEquals(listOf(Pt(20f, 20f), Pt(40f, 40f)), scaledStroke.points)
        assertEquals(12f, scaledStroke.width, 0.01f)
        assertEquals(PxRect(200f, 400f, 600f, 800f), scaled.selection)

        // 宿主原文档不能被就地改写。
        assertEquals(1000, s.imageWidth)
        assertEquals(listOf(Pt(10f, 10f), Pt(20f, 20f)), (s.elements.single() as StrokeElement).points)
    }

    @Test
    fun `scale image keeps state when size is unchanged or invalid`() {
        val s = AnnotateOps.addElement(state(), stroke(1))
        assertEquals(s, AnnotateOps.scaleImage(s, 1000, 2000))
        assertEquals(s, AnnotateOps.scaleImage(s, 0, 2000))
    }

    @Test
    fun `mosaic cell is sanitized when filling a selection`() {
        var s = AnnotateOps.setSelection(state(), PxRect(10f, 10f, 500f, 500f))
        s = AnnotateOps.fillSelectionMosaic(s, id = 9L, cellSize = 999f)
        assertEquals(96f, (s.elements.single() as MosaicElement).cellSize)
    }
}
