package hunoia.luno.runtime.floatball

import hunoia.luno.config.model.FloatBallDefaults
import hunoia.luno.config.model.WindowBounds
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 判定区判定：归一化矩形按屏幕换算后做边界包含；默认 = 居中 1/3 × 1/4。 */
class BallDropZoneTest {

    private val w = 1080
    private val h = 2400

    /** 默认判定区：居中，半宽 180、半高 300。 */
    private val default = FloatBallDefaults.defaultDropZone()

    @Test
    fun `default zone keeps the screen center inside`() {
        assertTrue(isBallInDropZone(w / 2, h / 2, w, h, default))
        // 区半宽 = 1080/3/2 = 180，半高 = 2400/4/2 = 300：恰好到边也算入区。
        assertTrue(isBallInDropZone(w / 2 + 180, h / 2, w, h, default))
        assertTrue(isBallInDropZone(w / 2, h / 2 + 300, w, h, default))
    }

    @Test
    fun `default zone boundary rejects one pixel beyond`() {
        assertFalse(isBallInDropZone(w / 2 + 181, h / 2, w, h, default))
        assertFalse(isBallInDropZone(w / 2, h / 2 + 301, w, h, default))
        assertFalse(isBallInDropZone(0, h / 2, w, h, default))
        assertFalse(isBallInDropZone(w, h, w, h, default))
    }

    @Test
    fun `custom zone follows its own bounds`() {
        // 左上角 20% × 10% 的小区：左上角点入区，中心不在。
        val corner = WindowBounds(x = 0f, y = 0f, width = 0.2f, height = 0.1f)
        assertTrue(isBallInDropZone(108, 120, w, h, corner))
        assertFalse(isBallInDropZone(w / 2, h / 2, w, h, corner))
    }

    @Test
    fun `full screen zone accepts every point`() {
        val full = WindowBounds(x = 0f, y = 0f, width = 1f, height = 1f)
        assertTrue(isBallInDropZone(0, 0, w, h, full))
        assertTrue(isBallInDropZone(w, h, w, h, full))
    }

    @Test
    fun `degenerate screen size never matches`() {
        assertFalse(isBallInDropZone(0, 0, 0, 0, default))
        assertFalse(isBallInDropZone(w / 2, h / 2, 0, h, default))
    }
}
