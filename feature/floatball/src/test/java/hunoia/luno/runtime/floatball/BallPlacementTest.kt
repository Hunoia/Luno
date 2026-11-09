package hunoia.luno.runtime.floatball

import hunoia.luno.bridge.ui.SystemBarsSafeArea
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 转屏侧别回归：屏宽变化时，落点 x 必须按各自朝向的归一值判侧，
 * 不能拿旧朝向像素坐标比新屏宽（否则竖屏贴右会在横屏翻到左边）。
 */
class BallPlacementTest {

    private val ballPx = 132

    private fun rightRatio(width: Int): Float = (width - ballPx).toFloat() / width

    @Test
    fun `portrait right stays right after rotating to wider landscape`() {
        val portraitWidth = 1080
        val landscapeWidth = 2400
        val ratio = rightRatio(portraitWidth)

        assertFalse(BallPlacement.isLeftEdge(ratio, portraitWidth, ballPx))
        // 旧实现用 portrait 的 posX 比 landscape 屏宽，会误判为左侧。
        assertFalse(BallPlacement.isLeftEdge(ratio, landscapeWidth, ballPx))
        assertEquals(
            (landscapeWidth - ballPx).toFloat(),
            BallPlacement.edgeX(ratio, landscapeWidth, ballPx),
            0.001f,
        )
    }

    @Test
    fun `landscape left stays left after rotating to narrower portrait`() {
        val ratio = 0f
        assertTrue(BallPlacement.isLeftEdge(ratio, 2400, ballPx))
        assertTrue(BallPlacement.isLeftEdge(ratio, 1080, ballPx))
        assertEquals(0f, BallPlacement.edgeX(ratio, 1080, ballPx), 0.001f)
    }

    @Test
    fun `edgeX snaps to full ball inside screen`() {
        assertEquals(0f, BallPlacement.edgeX(0.1f, 1080, ballPx), 0.001f)
        assertEquals(948f, BallPlacement.edgeX(0.9f, 1080, ballPx), 0.001f)
    }

    @Test
    fun `idle shift points outward on both edges`() {
        assertTrue(BallPlacement.idleShiftPx(left = true, ballPx = ballPx, outRatio = 0.5f) < 0f)
        assertTrue(BallPlacement.idleShiftPx(left = false, ballPx = ballPx, outRatio = 0.5f) > 0f)
    }

    @Test
    fun `isOnLeft matches edge classification`() {
        val width = 2400
        assertTrue(BallPlacement.isOnLeft(0f, width, ballPx))
        assertFalse(BallPlacement.isOnLeft((width - ballPx).toFloat(), width, ballPx))
    }

    // ---- 系统栏安全区（只约束上下，左右贴物理边）----

    @Test
    fun `landscape status bar keeps ball below it even when lp origin is zero`() {
        // 横屏下状态栏可能只作覆盖层（不占窗口布局原点）：LP 原点 0 时不能停在 y=0。
        val safe = SystemBarsSafeArea(top = 90)
        val bounds = BallPlacement.bounds(
            screenWidthPx = 2400,
            screenHeightPx = 1080,
            ballPx = ballPx,
            safe = safe,
            lpOriginY = 0,
        )
        assertEquals(90f, bounds.minY, 0.001f)
        // 本来停在 y=0（状态栏下面）的球会被夹回安全区。
        assertEquals(90f, 0f.coerceIn(bounds.minY, bounds.maxY), 0.001f)
    }

    @Test
    fun `fullscreen without status bar leaves no top gap`() {
        // 全屏游戏：没有可见系统栏就不留空，球可以到屏幕顶。
        val bounds = BallPlacement.bounds(2400, 1080, ballPx, SystemBarsSafeArea(), lpOriginY = 0)
        assertEquals(0f, bounds.minY, 0.001f)
        assertEquals((1080 - ballPx).toFloat(), bounds.maxY, 0.001f)
    }

    @Test
    fun `portrait lp origin still wins over a smaller safe top`() {
        // 竖屏 LP 原点就是状态栏下沿；安全区不能把球再往下推一截，否则出现缝隙。
        val bounds = BallPlacement.bounds(
            screenWidthPx = 1080,
            screenHeightPx = 2400,
            ballPx = ballPx,
            safe = SystemBarsSafeArea(top = 90),
            lpOriginY = 108,
        )
        assertEquals(108f, bounds.minY, 0.001f)
    }

    @Test
    fun `bottom nav bar keeps ball above it`() {
        val bounds = BallPlacement.bounds(
            screenWidthPx = 2400,
            screenHeightPx = 1080,
            ballPx = ballPx,
            safe = SystemBarsSafeArea(bottom = 72),
            lpOriginY = 0,
        )
        assertEquals((1080 - ballPx - 72).toFloat(), bounds.maxY, 0.001f)
    }

    @Test
    fun `horizontal edges stay physical regardless of cutout side`() {
        // 产品口径：横屏挖孔 / 侧边系统区不参与避让，左右贴物理屏边。
        assertEquals(0f, BallPlacement.edgeX(left = true, 2400, ballPx), 0.001f)
        assertEquals((2400 - ballPx).toFloat(), BallPlacement.edgeX(left = false, 2400, ballPx), 0.001f)
        assertEquals(0f, BallPlacement.edgeX(0.1f, 2400, ballPx), 0.001f)
        assertEquals((2400 - ballPx).toFloat(), BallPlacement.edgeX(0.9f, 2400, ballPx), 0.001f)
    }

    @Test
    fun `bounds never invert when bars exceed screen`() {
        val bounds = BallPlacement.bounds(
            screenWidthPx = 100,
            screenHeightPx = 100,
            ballPx = ballPx,
            safe = SystemBarsSafeArea(top = 80, bottom = 80),
            lpOriginY = 0,
        )
        assertTrue(bounds.minX <= bounds.maxX)
        assertTrue(bounds.minY <= bounds.maxY)
    }
}
