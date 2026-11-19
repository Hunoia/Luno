package hunoia.luno.runtime.overlay.text

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 情景识别「只识别全屏窗口」的窗口判定（[isSceneFullscreen]）。
 *
 * 全屏主窗口覆盖屏幕大部分 → true；浮在其上的小窗 / 分屏小块 / 分屏双开 → false；
 * 系统栏 / 沉浸式让全屏窗口略小于屏幕仍视为全屏；零尺寸 / 零屏幕恒 false。
 */
class A11yTextSnapshotReaderTest {

    private val screenW = 1080
    private val screenH = 2400

    @Test
    fun `fullscreen window is fullscreen`() {
        assertTrue(isSceneFullscreen(0, 0, screenW, screenH, screenW, screenH))
    }

    @Test
    fun `window offset from origin but covering screen is fullscreen`() {
        assertTrue(isSceneFullscreen(0, 0, screenW, screenH, screenW, screenH))
    }

    @Test
    fun `window with system bar inset is still fullscreen`() {
        // 全屏窗口去掉导航栏 / 状态栏后仍 ≥ 0.85 面积。
        assertTrue(isSceneFullscreen(0, 0, screenW, screenH - 100, screenW, screenH))
        assertTrue(isSceneFullscreen(0, 0, screenW - 60, screenH, screenW, screenH))
    }

    @Test
    fun `split screen halves are not fullscreen`() {
        // 横向 / 纵向分屏各约 0.5 面积。
        assertFalse(isSceneFullscreen(0, 0, screenW / 2, screenH, screenW, screenH))
        assertFalse(isSceneFullscreen(0, 0, screenW, screenH / 2, screenW, screenH))
    }

    @Test
    fun `floating small window is not fullscreen`() {
        assertFalse(isSceneFullscreen(100, 100, 500, 800, screenW, screenH))
    }

    @Test
    fun `zero size or zero screen is not fullscreen`() {
        assertFalse(isSceneFullscreen(0, 0, 0, screenH, screenW, screenH))
        assertFalse(isSceneFullscreen(0, 0, screenW, 0, screenW, screenH))
        assertFalse(isSceneFullscreen(0, 0, screenW, screenH, 0, screenH))
    }

    @Test
    fun `boundary is exactly zero point eight five`() {
        // 0.85 恰好：2040/2400 = 0.85 → true；2039/2400 < 0.85 → false。
        assertTrue(isSceneFullscreen(0, 0, screenW, 2040, screenW, screenH))
        assertFalse(isSceneFullscreen(0, 0, screenW, 2039, screenW, screenH))
    }
}
