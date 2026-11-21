package hunoia.luno.annotate.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** P2 功能测试：直线吸附、图层调整、复制元素、模糊/贴纸元素几何。 */
class AnnotateOpsFeatureTest {

    // ---- snapShapeEnd（P2b 直线吸附） ----

    @Test
    fun `snap pulls near-horizontal ends to exactly horizontal`() {
        val start = Pt(100f, 100f)
        // 3° 偏差（< 5° 容差）：吸附到水平，长度不变。
        val near = Pt(203f, 105.4f)
        val snapped = snapShapeEnd(start, near, ElementKind.LINE)
        assertEquals(start.y, snapped.y, 0.01f)
        assertEquals(start.distanceTo(near), snapped.x - start.x, 0.5f)
    }

    @Test
    fun `snap pulls near-diagonal to 45 degrees`() {
        val start = Pt(0f, 0f)
        val near = Pt(140f, 150f) // ~47°，偏差 2° 内吸附。
        val snapped = snapShapeEnd(start, near, ElementKind.ARROW)
        val length = start.distanceTo(near)
        assertEquals(length, start.distanceTo(snapped), 0.5f)
        assertEquals(snapped.y, snapped.x, 1.0f)
    }

    @Test
    fun `no snap outside tolerance`() {
        val start = Pt(0f, 0f)
        val free = Pt(180f, 100f) // ~29°，离任何 45° 倍数都远。
        assertEquals(free, snapShapeEnd(start, free, ElementKind.LINE))
    }

    @Test
    fun `snap only applies to straight kinds`() {
        val start = Pt(0f, 0f)
        val near = Pt(203f, 110.6f)
        assertEquals(near, snapShapeEnd(start, near, ElementKind.RECT))
        assertEquals(near, snapShapeEnd(start, near, ElementKind.CROSS))
    }

    private fun Pt.distanceTo(other: Pt): Float {
        val dx = other.x - x
        val dy = other.y - y
        return kotlin.math.sqrt(dx * dx + dy * dy)
    }

    // ---- raiseElement / duplicateElement（P2c 图层调整 + 复制） ----

    private fun stateOf(vararg ids: Long): AnnotateState =
        AnnotateState.of(1000, 1000).copy(
            elements = ids.map { id ->
                ShapeElement(id, Pt(0f, 0f), Pt(100f, 100f), 0xFF000000, 4f, ElementKind.RECT)
            },
        )

    @Test
    fun `raise moves element up one layer and to top`() {
        val state = stateOf(1, 2, 3)
        val up = AnnotateOps.raiseElement(state, 1, toTop = false)
        assertEquals(listOf(2L, 1L, 3L), up.elements.map { it.id })
        val top = AnnotateOps.raiseElement(state, 1, toTop = true)
        assertEquals(listOf(2L, 3L, 1L), top.elements.map { it.id })
    }

    @Test
    fun `raise is one undo step and no-op at top`() {
        val state = stateOf(1, 2, 3)
        val raised = AnnotateOps.raiseElement(state, 1, toTop = false)
        val undone = AnnotateOps.undo(raised)
        assertEquals(state.elements, undone.elements)

        // 已在顶层：原样返回，不产生空历史。
        val topState = stateOf(1, 2, 3)
        val noop = AnnotateOps.raiseElement(topState, 3, toTop = false)
        assertEquals(topState, noop)
        val noopTop = AnnotateOps.raiseElement(topState, 3, toTop = true)
        assertEquals(topState, noopTop)
    }

    @Test
    fun `duplicate copies element with new id and offset`() {
        val state = stateOf(1, 2)
        val duplicated = AnnotateOps.duplicateElement(state, 2, newId = 99, offset = Pt(16f, 16f))
        assertEquals(3, duplicated.elements.size)
        val copy = duplicated.elements.last() as ShapeElement
        assertEquals(99L, copy.id)
        assertEquals(Pt(16f, 16f), copy.start)
        // 一步撤销回到原状。
        assertEquals(state.elements, AnnotateOps.undo(duplicated).elements)
        // 复制不存在的元素：原样返回。
        assertEquals(state, AnnotateOps.duplicateElement(state, 42, 99, Pt(1f, 1f)))
    }

    // ---- BlurElement（P2d） ----

    @Test
    fun `blur trail hit and rect hit`() {
        val trail = BlurElement(1, points = listOf(Pt(0f, 100f), Pt(300f, 100f)), width = 40f, radius = 16f)
        assertTrue(trail.hitBody(Pt(150f, 110f), ContainFit.identity(1000, 1000), { it.bounds() }))
        assertFalse(trail.hitBody(Pt(150f, 300f), ContainFit.identity(1000, 1000), { it.bounds() }))
        val fill = BlurElement(2, radius = 16f, rect = PxRect(0f, 400f, 200f, 500f))
        assertTrue(fill.hitBody(Pt(100f, 450f), ContainFit.identity(1000, 1000), { it.bounds() }))
    }

    @Test
    fun `blur sanitizes radius and transforms with image`() {
        val blur = BlurElement(1, points = listOf(Pt(0f, 0f), Pt(100f, 0f)), width = 40f, radius = 500f)
        assertEquals(96f, blur.sanitized().radius, 0.01f)

        val scaled = blur.transformed(PxRect.full(1000, 1000), PxRect.full(2000, 2000)) as BlurElement
        assertEquals(80f, scaled.width, 0.01f)
        assertEquals(96f, scaled.radius, 0.01f)

        val moved = blur.translated(5f, 7f) as BlurElement
        assertEquals(Pt(5f, 7f), moved.points.first())
    }

    @Test
    fun `blur is not erased by rect eraser`() {
        val blur = BlurElement(1, radius = 16f, rect = PxRect(0f, 0f, 100f, 100f))
        assertFalse(blur.erasedBy(PxRect.full(1000, 1000)))
    }

    // ---- StickerElement（P2e） ----

    /**
     * JVM 单测拿不到真实 Bitmap（android.jar 桩的 createBitmap 返回 null）。
     * 贴纸几何只依赖 [rect]，用 Unsafe 分配一个不跑构造器的 Bitmap 壳
     * 通过 Kotlin 非空检查；测试路径不调用它的任何方法。
     */
    private fun fakeBitmap(): android.graphics.Bitmap {
        val unsafeField = sun.misc.Unsafe::class.java.getDeclaredField("theUnsafe")
        unsafeField.isAccessible = true
        val unsafe = unsafeField.get(null) as sun.misc.Unsafe
        return unsafe.allocateInstance(android.graphics.Bitmap::class.java)
            as android.graphics.Bitmap
    }

    private fun stickerForTest(rect: PxRect): StickerElement =
        StickerElement(1, rect = rect, bitmap = fakeBitmap())

    @Test
    fun `sticker bounds hit translate and uniform scale`() {
        val sticker = stickerForTest(PxRect(100f, 100f, 300f, 200f))
        assertEquals(PxRect(100f, 100f, 300f, 200f), sticker.bounds())
        assertTrue(sticker.hitBody(Pt(200f, 150f), ContainFit.identity(1000, 1000), { it.bounds() }))
        assertFalse(sticker.hitBody(Pt(50f, 50f), ContainFit.identity(1000, 1000), { it.bounds() }))
        assertFalse(sticker.erasedBy(PxRect.full(1000, 1000)))

        val moved = sticker.translated(10f, -10f) as StickerElement
        assertEquals(PxRect(110f, 90f, 310f, 190f), moved.rect)

        // 等比缩放：宽高比不变。
        val scaled = sticker.transformed(PxRect.full(1000, 1000), PxRect.full(2000, 2000)) as StickerElement
        val scaledRect = scaled.rect
        val ratioBefore = sticker.rect.width / sticker.rect.height
        val ratioAfter = scaledRect.width / scaledRect.height
        assertEquals(ratioBefore, ratioAfter, 0.001f)
        assertNotEquals(sticker.rect, scaledRect)
    }
}
