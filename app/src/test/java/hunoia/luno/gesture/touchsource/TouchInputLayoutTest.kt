package hunoia.luno.gesture.touchsource

import hunoia.luno.config.model.GestureButton
import hunoia.luno.config.model.GestureButtonBounds
import hunoia.luno.config.model.TouchInputConfig
import hunoia.luno.config.model.TouchInputMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 拦截窗几何与来源仲裁的标志关系。
 *
 * 曾经踩过的坑：摆放拦截窗时问的是 `interceptRegions()`，而它按 `shizukuStreaming`
 * 门控，`shizukuStreaming` 又来自“拦截窗是否已生效”——互相当前置条件，窗永远建不出来，
 * 内核触摸流静默失效，触钮退回无障碍窗硬扛系统边缘手势。这里把“要摆的几何”与
 * “已生效的辖区”分开钉死。
 */
class TouchInputLayoutTest {

    private val screenWidth = 1000
    private val screenHeight = 2000
    private val density = 2f

    private val shizukuButton = GestureButton(
        id = "edge",
        bounds = GestureButtonBounds(x = 0f, y = 0.5f, width = 0.06f, height = 0.3f),
        mirrorHorizontal = false,
        touchInput = TouchInputConfig(mode = TouchInputMode.ONLY_SHIZUKU, interceptWidthDp = 10),
    )

    private fun layout(
        buttons: List<GestureButton> = listOf(shizukuButton),
        streaming: Boolean = false,
    ) = TouchInputLayout(
        buttons = buttons,
        shizukuStreaming = streaming,
        screenWidth = screenWidth,
        screenHeight = screenHeight,
        density = density,
    )

    @Test
    fun `desired regions are available before the windows are up`() {
        val layout = layout()
        assertEquals(1, layout.desiredInterceptRegions().size)
        // 窗还没摆上：路由不能把像素判给还没人监听的来源。
        assertTrue(layout.interceptRegions().isEmpty())
    }

    @Test
    fun `arbitration sees the same regions once streaming`() {
        val streaming = layout(streaming = true)
        assertEquals(streaming.desiredInterceptRegions(), streaming.interceptRegions())
        assertFalse(streaming.interceptRegions().isEmpty())
    }

    @Test
    fun `accessibility-only buttons never get intercept geometry`() {
        val windowOnly = shizukuButton.copy(
            touchInput = TouchInputConfig(mode = TouchInputMode.ONLY_ACCESSIBILITY),
        )
        val layout = layout(buttons = listOf(windowOnly))
        assertTrue(layout.desiredInterceptRegions().isEmpty())
    }
}
