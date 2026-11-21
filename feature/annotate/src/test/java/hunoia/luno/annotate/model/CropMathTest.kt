package hunoia.luno.annotate.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CropMathTest {

    private val density = 2f

    private fun view(w: Float, h: Float) = PxRect(0f, 0f, w, h)

    // ---- cropSafeBounds / zoomToFitBounds ----

    @Test
    fun `safe bounds inset by 48dp sides and top, 96dp bottom`() {
        val bounds = cropSafeBounds(viewWidth = 1000f, viewHeight = 2000f, density = density)
        assertEquals(96f, bounds.left, 0.01f)
        assertEquals(96f, bounds.top, 0.01f)
        assertEquals(904f, bounds.right, 0.01f)
        assertEquals(1808f, bounds.bottom, 0.01f)
    }

    @Test
    fun `safe bounds survives tiny view`() {
        val bounds = cropSafeBounds(viewWidth = 10f, viewHeight = 10f, density = density)
        // 画布比边距还小：左右/顶保持在 96，底边不低于顶边（退化矩形不反转）。
        assertEquals(96f, bounds.left, 0.01f)
        assertEquals(96f, bounds.right, 0.01f)
        assertEquals(96f, bounds.bottom, 0.01f)
    }

    @Test
    fun `zoom to fit shrinks for bottom inset`() {
        val image = PxRect(0f, 0f, 1000f, 2000f)
        val full = ContainFit.of(1000f, 2000f, 1000f, 2000f)
        val bounds = cropSafeBounds(1000f, 2000f, density)
        val zoom = zoomToFitBounds(full, bounds)
        // 宽度方向是瓶颈：可用宽 808 / 图片绘制宽 1000（高度 1712/2000 = 0.856 更宽）。
        assertEquals(808f / 1000f, zoom, 0.001f)
    }

    // ---- hitCropHandle ----

    @Test
    fun `handle hit prefers corners over edges`() {
        val window = PxRect(100f, 100f, 500f, 500f)
        assertEquals(CropHandle.TOP_LEFT, hitCropHandle(window, Pt(102f, 101f), 20f))
        assertEquals(CropHandle.LEFT, hitCropHandle(window, Pt(104f, 300f), 20f))
        assertEquals(CropHandle.BOTTOM, hitCropHandle(window, Pt(300f, 496f), 20f))
        assertNull(hitCropHandle(window, Pt(300f, 300f), 20f))
        assertNull(hitCropHandle(window, Pt(90f, 90f), 8f))
    }

    // ---- adjustCropWindow ----

    @Test
    fun `adjust honors bounds and min size`() {
        val bounds = cropSafeBounds(1000f, 2000f, density)
        val window = PxRect(200f, 200f, 800f, 800f)
        val minSize = 96f

        val shrunk = adjustCropWindow(window, CropHandle.BOTTOM_RIGHT, Pt(400f, 400f), bounds, minSize)
        assertEquals(400f, shrunk.right, 0.01f)
        assertEquals(400f, shrunk.bottom, 0.01f)

        // 拖出安全区域：夹到边界。
        val clamped = adjustCropWindow(window, CropHandle.TOP_LEFT, Pt(-500f, -500f), bounds, minSize)
        assertEquals(bounds.left, clamped.left, 0.01f)
        assertEquals(bounds.top, clamped.top, 0.01f)

        // 拖过头（越过对边）：夹到最小尺寸。
        val inverted = adjustCropWindow(window, CropHandle.RIGHT, Pt(150f, 0f), bounds, minSize)
        assertEquals(window.left + minSize, inverted.right, 0.01f)
    }

    // ---- scaleCropWindow / maxCropScale ----

    @Test
    fun `scale keeps anchor fixed`() {
        val window = PxRect(100f, 100f, 300f, 300f)
        val anchor = Pt(300f, 200f)
        val scaled = scaleCropWindow(window, anchor, 2f)
        assertEquals(anchor.x - 200f, scaled.left, 0.01f)
        assertEquals(anchor.x + 200f, scaled.right, 0.01f)
        assertEquals(anchor.y - 200f, scaled.top, 0.01f)
        assertEquals(anchor.y + 200f, scaled.bottom, 0.01f)
    }

    @Test
    fun `max crop scale limited by nearest bound`() {
        val bounds = view(1000f, 1000f)
        val window = PxRect(400f, 400f, 600f, 600f)
        val anchor = Pt(500f, 500f)
        // 右侧到边界 500，窗口右边缘到锚点 100 → 最多 5 倍。
        assertEquals(5f, maxCropScale(window, anchor, bounds), 0.001f)
        // 锚点偏上：底边到边界 550 / 窗口底到锚点 150 = 3.667 是最近的限制。
        assertEquals(3.6666667f, maxCropScale(window, Pt(500f, 450f), bounds), 0.001f)
    }

    // ---- clampPanToCropWindow ----

    @Test
    fun `pan clamps so crop window stays covered by image`() {
        val base = ContainFit.of(1000f, 1000f, 1000f, 1000f)
        val window = PxRect(250f, 250f, 750f, 750f)
        val zoom = 2f
        val centered = base.withViewTransform(zoom, Pt(0f, 0f))
        // 图片放大后占满屏幕并溢出，平移量被限制在「窗口仍被图片覆盖」的范围内。
        val tooFar = clampPanToCropWindow(base, zoom, Pt(-9999f, 0f), window)
        val lowX = window.right - (centered.offsetX + centered.imageWidth * centered.scale)
        assertEquals(lowX, tooFar.x, 0.01f)
        val back = clampPanToCropWindow(base, zoom, Pt(9999f, 0f), window)
        val highX = window.left - centered.offsetX
        assertEquals(highX, back.x, 0.01f)
    }

    @Test
    fun `pan midpoints when image smaller than window on an axis`() {
        // 图片 zoom 后某轴比窗口还小：该轴不可平移，取区间中点（不抖动）。
        val base = ContainFit.of(100f, 100f, 1000f, 1000f)
        val window = PxRect(100f, 100f, 900f, 900f)
        val zoom = 0.5f // 图片绘制 500×500，小于窗口 800×800。
        val pan = clampPanToCropWindow(base, zoom, Pt(50f, 50f), window)
        val centered = base.withViewTransform(zoom, Pt(0f, 0f))
        val lowX = window.right - (centered.offsetX + centered.imageWidth * centered.scale)
        val highX = window.left - centered.offsetX
        assertEquals((lowX + highX) / 2f, pan.x, 0.01f)
        assertEquals((lowX + highX) / 2f, pan.y, 0.01f)
    }
}
