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

    @Test
    fun `fullscreen event window is never a small window event`() {
        // 事件来源窗口即全屏主窗口：主窗口事件，不忽略（它是扫描的正当来源）。
        assertFalse(isSceneSmallWindowSignal(eventWindowFullscreen = true, hasOtherFullscreenMain = true))
        assertFalse(isSceneSmallWindowSignal(eventWindowFullscreen = true, hasOtherFullscreenMain = false))
    }

    @Test
    fun `small event window over a fullscreen main window is a small window event`() {
        // 小红书/Paseo 小窗浮在 QQ 全屏主窗口上：事件来源窗口非全屏 + 屏幕另有全屏主窗口 →
        // 忽略（方案 A）。
        assertTrue(isSceneSmallWindowSignal(eventWindowFullscreen = false, hasOtherFullscreenMain = true))
    }

    @Test
    fun `small event window with no fullscreen main window is not a small window event`() {
        // 纯小窗 / 分屏 / 桌面：无全屏主窗口 → 不算小窗事件，识别事件应用（现状）。
        assertFalse(isSceneSmallWindowSignal(eventWindowFullscreen = false, hasOtherFullscreenMain = false))
    }

    @Test
    fun `lowest layer fullscreen window is the main window`() {
        // 主窗口（layer 10）+ 浮在其上的 freeform 放大 frame（layer 12，都报全屏 bounds）：
        // 最低 layer 的全屏窗口是主窗口。
        val main = SnapshotRect(0, 0, screenW, screenH)
        val windows = listOf(10 to main, 12 to main)
        assertTrue(isSceneFullscreenMain(10, main, windows, screenW, screenH))
    }

    @Test
    fun `higher layer fullscreen window is a freeform inflated frame not the main window`() {
        // MIUI freeform 小窗的合成放大 frame：报全屏 bounds 但浮在主窗口之上（更高 layer）。
        val main = SnapshotRect(0, 0, screenW, screenH)
        val windows = listOf(10 to main, 12 to main)
        assertFalse(isSceneFullscreenMain(12, main, windows, screenW, screenH))
    }

    @Test
    fun `only fullscreen window is the main window`() {
        // 唯一全屏窗口（纯小窗放大 frame / 单窗口应用）→ 当作主窗口（无更低层全屏可区分）。
        val main = SnapshotRect(0, 0, screenW, screenH)
        val windows = listOf(10 to main)
        assertTrue(isSceneFullscreenMain(10, main, windows, screenW, screenH))
    }

    @Test
    fun `non fullscreen window is never the main window`() {
        val main = SnapshotRect(0, 0, screenW, screenH)
        val small = SnapshotRect(100, 100, 500, 800)
        val windows = listOf(10 to main, 12 to small)
        assertFalse(isSceneFullscreenMain(12, small, windows, screenW, screenH))
    }
}
