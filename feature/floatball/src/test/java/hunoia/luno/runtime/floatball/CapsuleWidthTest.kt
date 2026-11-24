package hunoia.luno.runtime.floatball

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.roundToInt

/**
 * 胶囊窗口宽度的唯一口径：窗口宽 = 画面宽 + 中侧余量，任何上限都必须把余量和
 * 屏缘留白先扣掉，窗口才不越出屏幕（见 [CapsuleWidths]）。
 *
 * 覆盖通知行（0.6 × 屏宽 / 250dp）、结果胶囊（0.48 × 屏宽 / 180dp，内容驱动兜底）与
 * 同朝向折叠 / 分屏 / 改显示大小后旧宽值落在新屏上这三种来源。
 */
class CapsuleWidthTest {

    private val density = 3.4f
    private val centerMargin = 16 * density.roundToInt()  // 中侧余量 = 辉光出血（不撑横滑橡皮筋）
    private val edgeInset = CapsuleWidths.edgeInsetPx(density)
    private val notifAbsCap = 250 * density.roundToInt()
    private val resultAbsCap = 180 * density.roundToInt()

    private fun assertFits(screen: Int, surface: Int) {
        val window = CapsuleWidths.windowWidthPx(surface, centerMargin, screen)
        assertTrue("window=$window screen=$screen", window <= screen)
        assertTrue("window=$window", window >= 1)
    }

    @Test
    fun `notification row never exceeds screen`() {
        for (screen in 600..1440 step 60) {
            val surface = CapsuleWidths.surfaceCapPx(screen, centerMargin, edgeInset, notifAbsCap, 0.6f)
            assertTrue("surface=$surface screen=$screen", surface <= CapsuleWidths.maxSurfaceWidthPx(screen, centerMargin, edgeInset))
            assertFits(screen, surface)
        }
    }

    @Test
    fun `result capsule never exceeds screen`() {
        for (screen in 600..1440 step 60) {
            val surface = CapsuleWidths.surfaceCapPx(screen, centerMargin, edgeInset, resultAbsCap, 0.48f)
            assertFits(screen, surface)
        }
    }

    @Test
    fun `same-orientation display shrink clamps to new screen`() {
        // 旧屏 1200px 按通知上限算出 720px 内容宽，折叠到 600px 后必须重算且不越屏。
        val oldSurface = CapsuleWidths.surfaceCapPx(1200, centerMargin, edgeInset, notifAbsCap, 0.6f)
        assertFits(1200, oldSurface)
        val newSurface = CapsuleWidths.surfaceCapPx(600, centerMargin, edgeInset, notifAbsCap, 0.6f)
        assertFits(600, newSurface)
    }

    @Test
    fun `window width hard clamp`() {
        // 旧宽 1007px 落在 600px 新屏：夹到 600，画面同步收窄。
        val clamped = CapsuleWidths.windowWidthPx(720, centerMargin, 600)
        assertEquals(600, clamped)
    }

    @Test
    fun `degenerate tiny screen stays positive`() {
        val surface = CapsuleWidths.surfaceCapPx(200, centerMargin, edgeInset, notifAbsCap, 0.6f)
        assertTrue(surface >= 1)
        assertFits(200, surface)
    }
}
