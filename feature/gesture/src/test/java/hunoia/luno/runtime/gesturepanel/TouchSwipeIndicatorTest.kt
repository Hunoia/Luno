package hunoia.luno.runtime.gesturepanel

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import hunoia.luno.config.model.SwipeIndicatorStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 滑动指示条：按钮边 / 拖尾跨度 / 凸起量 / 安全区 / 阈值。 */
class TouchSwipeIndicatorTest {

    private val screenWidth = 1080f
    private val screenHeight = 2400f

    @Test
    fun `nearest edge follows the closest screen side`() {
        assertEquals(
            SwipeIndicatorEdge.Left,
            nearestSwipeEdge(Offset(40f, 1200f), screenWidth, screenHeight),
        )
        assertEquals(
            SwipeIndicatorEdge.Right,
            nearestSwipeEdge(Offset(1020f, 1200f), screenWidth, screenHeight),
        )
        assertEquals(
            SwipeIndicatorEdge.Top,
            nearestSwipeEdge(Offset(540f, 60f), screenWidth, screenHeight),
        )
        assertEquals(
            SwipeIndicatorEdge.Bottom,
            nearestSwipeEdge(Offset(540f, 2360f), screenWidth, screenHeight),
        )
    }

    @Test
    fun `edge follows the button side not the finger`() {
        assertEquals(
            SwipeIndicatorEdge.Left,
            buttonSwipeEdge(Rect(0f, 600f, 60f, 1200f), screenWidth, screenHeight),
        )
        assertEquals(
            SwipeIndicatorEdge.Right,
            buttonSwipeEdge(Rect(1020f, 600f, 1080f, 1200f), screenWidth, screenHeight),
        )
        assertEquals(
            SwipeIndicatorEdge.Top,
            buttonSwipeEdge(Rect(400f, 0f, 600f, 100f), screenWidth, screenHeight),
        )
        assertEquals(
            SwipeIndicatorEdge.Bottom,
            buttonSwipeEdge(Rect(400f, 2300f, 600f, 2400f), screenWidth, screenHeight),
        )
    }

    @Test
    fun `edge frame maps tangent and depth to screen coordinates`() {
        val frame = { edge: SwipeIndicatorEdge ->
            edgeFrame(edge, 0f, 80f, 0f, 60f, screenWidth, screenHeight)
        }
        // 左边：t 是 y，深度向右；安全区从状态栏下沿开始。
        assertEquals(Offset(20f, 300f), frame(SwipeIndicatorEdge.Left).point(300f, 20f))
        assertEquals(80f, frame(SwipeIndicatorEdge.Left).tangentStart, 0.001f)
        assertEquals(2340f, frame(SwipeIndicatorEdge.Left).tangentEnd, 0.001f)

        // 右边：深度向左。
        assertEquals(Offset(1060f, 300f), frame(SwipeIndicatorEdge.Right).point(300f, 20f))

        // 上边：t 是 x，深度向下，贴着状态栏下沿。
        assertEquals(Offset(300f, 100f), frame(SwipeIndicatorEdge.Top).point(300f, 20f))

        // 下边：深度向上，贴着导航栏上沿。
        assertEquals(Offset(300f, 2320f), frame(SwipeIndicatorEdge.Bottom).point(300f, 20f))
    }

    @Test
    fun `tangent reads the axis parallel to the edge`() {
        val point = Offset(120f, 900f)
        assertEquals(900f, SwipeIndicatorEdge.Left.tangentOf(point), 0.001f)
        assertEquals(900f, SwipeIndicatorEdge.Right.tangentOf(point), 0.001f)
        assertEquals(120f, SwipeIndicatorEdge.Top.tangentOf(point), 0.001f)
        assertEquals(120f, SwipeIndicatorEdge.Bottom.tangentOf(point), 0.001f)
    }

    @Test
    fun `bulge is driven by total displacement and clamped`() {
        // 沿边滑动同样要鼓起来：垂直距离为 0 也有 100px 位移。
        assertEquals(
            100f,
            dragDistance(Offset(0f, 600f), Offset(0f, 700f), 200f),
            0.001f,
        )
        assertEquals(
            200f,
            dragDistance(Offset(0f, 600f), Offset(300f, 1000f), 200f),
            0.001f,
        )
        assertEquals(0f, dragDistance(Offset.Unspecified, Offset(10f, 10f), 200f), 0.001f)
        assertEquals(0f, dragDistance(Offset(0f, 0f), Offset.Unspecified, 200f), 0.001f)
    }

    @Test
    fun `peek grows with the bulge and keeps every style positive`() {
        assertEquals(8f, indicatorPeek(8f, 0f, 0.6f), 0.001f)
        assertEquals(38f, indicatorPeek(8f, 50f, 0.6f), 0.001f)
        assertEquals(18f, indicatorPeek(18f, 0f, 0.5f), 0.001f)
        assertEquals(0f, indicatorPeek(0f, 0f, 0.5f), 0.001f)
        SwipeIndicatorStyle.entries.forEach { style ->
            assertTrue(style.baseRadiusDp() > 0f)
            assertTrue(style.basePeekDp() >= 0f)
            assertTrue(style.peekFactor() in 0.4f..0.8f)
        }
    }

    @Test
    fun `capsule uses a shorter trailing span than the classic profiles`() {
        val radius = 100f
        assertEquals(55f, SwipeIndicatorStyle.Pill.spanRadius(radius), 0.001f)
        assertEquals(100f, SwipeIndicatorStyle.Droplet.spanRadius(radius), 0.001f)
        assertEquals(100f, SwipeIndicatorStyle.Ripple.spanRadius(radius), 0.001f)
    }

    @Test
    fun `drag span covers both points plus the base radius`() {
        val still = dragSpan(
            originTangent = 1000f,
            fingerTangent = 1000f,
            radius = 80f,
            maxSpan = 2000f,
            safeStart = 80f,
            safeEnd = 2340f,
        )
        assertEquals(920f, still.start, 0.001f)
        assertEquals(1080f, still.end, 0.001f)

        val trailing = dragSpan(
            originTangent = 1000f,
            fingerTangent = 1500f,
            radius = 80f,
            maxSpan = 2000f,
            safeStart = 80f,
            safeEnd = 2340f,
        )
        assertEquals(920f, trailing.start, 0.001f)
        assertEquals(1580f, trailing.end, 0.001f)
    }

    @Test
    fun `drag span caps the stretch and stays inside the safe area`() {
        val capped = dragSpan(
            originTangent = 100f,
            fingerTangent = 2000f,
            radius = 80f,
            maxSpan = 500f,
            safeStart = 80f,
            safeEnd = 2340f,
        )
        assertEquals(500f, capped.length, 0.001f)
        assertEquals(800f, capped.start, 0.001f)
        assertEquals(1300f, capped.end, 0.001f)

        val nearStart = dragSpan(
            originTangent = 100f,
            fingerTangent = 200f,
            radius = 80f,
            maxSpan = 2000f,
            safeStart = 200f,
            safeEnd = 2300f,
        )
        assertEquals(200f, nearStart.start, 0.001f)
        assertEquals(460f, nearStart.end, 0.001f)

        val nearEnd = dragSpan(
            originTangent = 2300f,
            fingerTangent = 2200f,
            radius = 80f,
            maxSpan = 2000f,
            safeStart = 200f,
            safeEnd = 2300f,
        )
        assertEquals(2040f, nearEnd.start, 0.001f)
        assertEquals(2300f, nearEnd.end, 0.001f)
    }

    @Test
    fun `wave profile returns to the edge at both ends`() {
        val start = waveDepth(
            tangent = 500f,
            fingerTangent = 900f,
            spanStart = 500f,
            spanEnd = 1300f,
            peek = 40f,
            amplitude = 6f,
            wavelength = 100f,
            phase = 0.3f,
        )
        val end = waveDepth(
            tangent = 1300f,
            fingerTangent = 900f,
            spanStart = 500f,
            spanEnd = 1300f,
            peek = 40f,
            amplitude = 6f,
            wavelength = 100f,
            phase = 0.3f,
        )
        assertEquals(0f, start, 0.001f)
        assertEquals(0f, end, 0.001f)

        val peak = waveDepth(
            tangent = 900f,
            fingerTangent = 900f,
            spanStart = 500f,
            spanEnd = 1300f,
            peek = 40f,
            amplitude = 0f,
            wavelength = 100f,
            phase = 0f,
        )
        assertEquals(40f, peak, 0.001f)
    }

    @Test
    fun `wave profile never dips below the edge or overshoots the cap`() {
        for (step in 0..40) {
            val tangent = 500f + step * 20f
            val depth = waveDepth(
                tangent = tangent,
                fingerTangent = 900f,
                spanStart = 500f,
                spanEnd = 1300f,
                peek = 40f,
                amplitude = 60f,
                wavelength = 70f,
                phase = step * 0.37f,
            )
            assertTrue(depth >= 0f)
            assertTrue(depth <= 40f * 1.15f + 0.001f)
        }
    }

    @Test
    fun `threshold only crosses after the button slide distance`() {
        val origin = Offset(100f, 100f)
        assertFalse(crossedSwipeThreshold(origin, Offset(110f, 100f), 50f))
        assertTrue(crossedSwipeThreshold(origin, Offset(150f, 100f), 50f))
        // 未开始的坐标（NaN）绝不算跨越，避免 1×1 窗口里误亮。
        assertFalse(crossedSwipeThreshold(Offset.Unspecified, Offset(150f, 100f), 50f))
        assertFalse(crossedSwipeThreshold(origin, Offset.Unspecified, 50f))
    }
}
