package hunoia.luno.runtime.floatball

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
}
