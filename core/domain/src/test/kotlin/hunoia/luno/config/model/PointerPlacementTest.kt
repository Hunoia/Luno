package hunoia.luno.config.model

import org.junit.Assert.assertEquals
import org.junit.Test

/** 指针起始点与跟随偏移：以左下分区为基准，按触发点所在半屏镜像，再夹进屏内。 */
class PointerPlacementTest {

    private val settings = GestureSettings.Pointer(
        initialXRatio = 0.5f,
        initialYRatio = 0.35f,
        followTrigger = true,
        followOffsetXRatio = 0.05f,
        followOffsetYRatio = -0.10f,
    )

    private val width = 1000
    private val height = 2000

    private fun follow(x: Float, y: Float) =
        settings.followPoint(
            triggerX = x,
            triggerY = y,
            screenWidthPx = width,
            screenHeightPx = height,
        )

    @Test
    fun `initial point maps ratios to screen`() {
        val point = settings.initialPoint(width, height)
        assertEquals(500f, point.x, 0.001f)
        assertEquals(700f, point.y, 0.001f)
    }

    @Test
    fun `default offset sits above the finger in the lower half`() {
        val defaults = GestureSettings.Pointer()
        val point = defaults.followPoint(300f, 1500f, width, height)
        assertEquals(300f, point.x, 0.001f)
        assertEquals(1300f, point.y, 0.001f)
    }

    @Test
    fun `offset mirrors per screen half`() {
        // 左下：+5% / −10%。
        val bottomLeft = follow(300f, 1500f)
        assertEquals(350f, bottomLeft.x, 0.001f)
        assertEquals(1300f, bottomLeft.y, 0.001f)

        // 右下：X 翻转。
        val bottomRight = follow(700f, 1500f)
        assertEquals(650f, bottomRight.x, 0.001f)
        assertEquals(1300f, bottomRight.y, 0.001f)

        // 左上：Y 翻转。
        val topLeft = follow(300f, 500f)
        assertEquals(350f, topLeft.x, 0.001f)
        assertEquals(700f, topLeft.y, 0.001f)

        // 右上：两轴都翻转。
        val topRight = follow(700f, 500f)
        assertEquals(650f, topRight.x, 0.001f)
        assertEquals(700f, topRight.y, 0.001f)
    }

    @Test
    fun `mirror sign flips on the middle line`() {
        val bottomLeft = pointerMirrorSign(499f, 1001f, width, height)
        assertEquals(1f, bottomLeft.x, 0.001f)
        assertEquals(1f, bottomLeft.y, 0.001f)

        val topRight = pointerMirrorSign(500f, 999f, width, height)
        assertEquals(-1f, topRight.x, 0.001f)
        assertEquals(-1f, topRight.y, 0.001f)
    }

    @Test
    fun `result is clamped into the screen`() {
        val nearLeftTop = follow(0f, 0f)
        // 左上：X +50、Y +200 → 仍可显示。
        assertEquals(50f, nearLeftTop.x, 0.001f)
        assertEquals(200f, nearLeftTop.y, 0.001f)

        val offBottom = GestureSettings.Pointer(followOffsetYRatio = 0.5f)
            .followPoint(500f, 1900f, width, height)
        assertEquals(2000f, offBottom.y, 0.001f)
    }

    @Test
    fun `out of range offsets and ratios are coerced`() {
        val weird = settings.copy(
            initialXRatio = -1f,
            initialYRatio = 2f,
            followOffsetXRatio = 3f,
            followOffsetYRatio = -3f,
        )
        val initial = weird.initialPoint(width, height)
        assertEquals(0f, initial.x, 0.001f)
        assertEquals(2000f, initial.y, 0.001f)

        // 偏移上限 100%：左下的 Y 翻转后在屏内。
        val point = weird.followPoint(300f, 1500f, width, height)
        assertEquals(1000f, point.x, 0.001f)
        assertEquals(0f, point.y, 0.001f)
    }

    @Test
    fun `canonical offset inverts follow point`() {
        val trigger = PointerPoint(700f, 1500f)
        val display = settings.followPoint(trigger.x, trigger.y, width, height)
        val offset = pointerCanonicalOffset(trigger, display, width, height)
        assertEquals(0.05f, offset.x, 0.0001f)
        assertEquals(-0.10f, offset.y, 0.0001f)

        val topLeft = PointerPoint(300f, 500f)
        val topDisplay = settings.followPoint(topLeft.x, topLeft.y, width, height)
        val topOffset = pointerCanonicalOffset(topLeft, topDisplay, width, height)
        assertEquals(0.05f, topOffset.x, 0.0001f)
        assertEquals(-0.10f, topOffset.y, 0.0001f)
    }
}
