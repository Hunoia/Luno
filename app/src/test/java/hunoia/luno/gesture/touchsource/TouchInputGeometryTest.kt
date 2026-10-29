package hunoia.luno.gesture.touchsource

import androidx.compose.ui.geometry.Offset
import hunoia.luno.config.model.GestureButton
import hunoia.luno.config.model.GestureButtonBounds
import hunoia.luno.config.model.TouchInputConfig
import hunoia.luno.config.model.TouchInputMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 触钮几何与来源仲裁的纯函数测试。
 *
 * 这两个函数决定了“这块像素归谁”，一旦漂移，表现是边缘点击随机丢失或动作触发两次，
 * 而且很难在真机上复现，所以放在单测里钉死。
 */
class TouchInputGeometryTest {

    private val screenWidth = 1000
    private val screenHeight = 2000
    private val density = 2f

    /** 左侧贴边按钮：x 0..0.06，y 0.5..0.8。 */
    private val leftButton = GestureButton(
        id = "left",
        bounds = GestureButtonBounds(x = 0f, y = 0.5f, width = 0.06f, height = 0.3f),
        mirrorHorizontal = false,
        touchInput = TouchInputConfig(mode = TouchInputMode.ONLY_SHIZUKU),
    )

    /** 镜像按钮：主按钮在右侧，镜像落在左侧。 */
    private val rightButton = GestureButton(
        id = "right",
        bounds = GestureButtonBounds(x = 0.94f, y = 0.5f, width = 0.06f, height = 0.3f),
        mirrorHorizontal = true,
        touchInput = TouchInputConfig(mode = TouchInputMode.ONLY_SHIZUKU),
    )

    private fun narrow(button: GestureButton, widthDp: Int): GestureButton =
        button.copy(touchInput = button.touchInput.copy(interceptWidthDp = widthDp))

    @Test
    fun `button rects follow normalized bounds`() {
        val rects = TouchInputGeometry.buttonRects(
            buttons = listOf(leftButton),
            screenWidth = screenWidth,
            screenHeight = screenHeight,
        )
        assertEquals(1, rects.size)
        val rect = rects.single().rect
        assertEquals(0f, rect.left, 0.01f)
        assertEquals(1000f, rect.top, 0.01f)
        assertEquals(60f, rect.right, 0.01f)
        assertEquals(1600f, rect.bottom, 0.01f)
    }

    @Test
    fun `ime padding shifts the whole strip up`() {
        val shifted = TouchInputGeometry.buttonRects(
            buttons = listOf(leftButton),
            imePadding = 400,
            screenWidth = screenWidth,
            screenHeight = screenHeight,
        ).single().rect
        assertEquals(600f, shifted.top, 0.01f)
        assertEquals(1200f, shifted.bottom, 0.01f)
    }

    @Test
    fun `mirrored button produces its own region`() {
        val rects = TouchInputGeometry.buttonRects(
            buttons = listOf(rightButton),
            screenWidth = screenWidth,
            screenHeight = screenHeight,
        )
        assertEquals(2, rects.size)
        val mirrored = rects.single { it.mirrored }
        assertEquals("right", mirrored.buttonId)
        assertEquals(0f, mirrored.rect.left, 0.01f)
        assertEquals(60f, mirrored.rect.right, 0.01f)
    }

    @Test
    fun `window rects shrink to the intercept strip`() {
        val rects = TouchInputGeometry.buttonRects(
            buttons = listOf(narrow(leftButton, 10)),
            screenWidth = screenWidth,
            screenHeight = screenHeight,
            density = density,
        )
        val rect = rects.single().rect
        assertEquals(0f, rect.left, 0.01f)
        assertEquals(20f, rect.right, 0.01f)
    }

    @Test
    fun `accessibility buttons keep their full rect`() {
        val windowOnly = listOf(
            leftButton.copy(touchInput = TouchInputConfig(mode = TouchInputMode.ONLY_ACCESSIBILITY)),
        )
        val rect = TouchInputGeometry.buttonRects(
            buttons = windowOnly,
            screenWidth = screenWidth,
            screenHeight = screenHeight,
            density = density,
        ).single().rect
        assertEquals(0f, rect.left, 0.01f)
        assertEquals(60f, rect.right, 0.01f)
    }

    @Test
    fun `default intercept region covers the whole trigger area`() {
        val regions = TouchInputGeometry.interceptRegions(
            buttons = listOf(leftButton),
            screenWidth = screenWidth,
            screenHeight = screenHeight,
            density = density,
        )
        val region = regions.single()
        assertEquals(0f, region.rect.left, 0.01f)
        assertEquals(60f, region.rect.right, 0.01f)
    }

    @Test
    fun `narrow intercept hugs the screen edge`() {
        val left = TouchInputGeometry.interceptRegions(
            buttons = listOf(narrow(leftButton, 10)),
            screenWidth = screenWidth,
            screenHeight = screenHeight,
            density = density,
        ).single()
        assertEquals(0f, left.rect.left, 0.01f)
        assertEquals(20f, left.rect.right, 0.01f)

        val right = TouchInputGeometry.interceptRegions(
            buttons = listOf(narrow(rightButton, 10)),
            screenWidth = screenWidth,
            screenHeight = screenHeight,
            density = density,
        ).first { !it.mirrored }
        // 主窗口本来就贴右边：窄条锚在屏幕右边缘。
        assertEquals(1000f, right.rect.right, 0.01f)
        assertEquals(980f, right.rect.left, 0.01f)
        // 镜像窗贴左边：锚在 0。
        val mirrored = TouchInputGeometry.interceptRegions(
            buttons = listOf(narrow(rightButton, 10)),
            screenWidth = screenWidth,
            screenHeight = screenHeight,
            density = density,
        ).first { it.mirrored }
        assertEquals(0f, mirrored.rect.left, 0.01f)
        assertEquals(20f, mirrored.rect.right, 0.01f)
    }

    @Test
    fun `narrow width never exceeds the trigger area`() {
        val region = TouchInputGeometry.interceptRegions(
            buttons = listOf(narrow(leftButton, 100)),
            screenWidth = screenWidth,
            screenHeight = screenHeight,
            density = density,
        ).single()
        assertEquals(60f, region.rect.width, 0.01f)
    }

    @Test
    fun `buttons on accessibility mode never intercept`() {
        val windowOnly = listOf(
            leftButton.copy(touchInput = TouchInputConfig(mode = TouchInputMode.ONLY_ACCESSIBILITY)),
        )
        assertTrue(
            TouchInputGeometry.interceptRegions(
                buttons = windowOnly,
                screenWidth = screenWidth,
                screenHeight = screenHeight,
                density = density,
            ).isEmpty(),
        )
    }

    @Test
    fun `points resolve to the region that owns them`() {
        val regions = TouchInputGeometry.interceptRegions(
            buttons = listOf(narrow(leftButton, 10)),
            screenWidth = screenWidth,
            screenHeight = screenHeight,
            density = density,
        )
        assertNotNull(TouchInputGeometry.contains(regions, Offset(5f, 1200f)))
        assertNull(TouchInputGeometry.contains(regions, Offset(40f, 1200f)))
        assertNull(TouchInputGeometry.contains(regions, Offset(5f, 200f)))
    }

    @Test
    fun `pixels outside the strip belong to nobody in this surface`() {
        // 窄带模式下按钮窗与拦截窗都只在条带内：条带外的像素应落给下层应用。
        val rects = TouchInputGeometry.buttonRects(
            buttons = listOf(narrow(leftButton, 10)),
            screenWidth = screenWidth,
            screenHeight = screenHeight,
            density = density,
        )
        assertFalse(TouchInputGeometry.containsButton(rects, Offset(40f, 1200f)) != null)
        assertFalse(TouchInputGeometry.containsButton(rects, Offset(500f, 1200f)) != null)
        // 条带内仍然算命中。
        assertTrue(TouchInputGeometry.containsButton(rects, Offset(10f, 1200f)) != null)
    }
}
