package hunoia.luno.config.model

import org.junit.Assert.assertEquals
import org.junit.Test

class MiniWindowPlacementTest {

    private fun bounds(x: Float, y: Float, width: Float, height: Float) =
        WindowBounds(x = x, y = y, width = width, height = height)

    @Test
    fun `触发点在屏幕中间时窗口居中`() {
        val placed = bounds(0.1f, 0.2f, 0.2f, 0.2f)
            .centeredOnTrigger(anchorXRatio = 0.5f, anchorYRatio = 0.5f)

        assertEquals(0.4f, placed.x, 0.0001f)
        assertEquals(0.4f, placed.y, 0.0001f)
        assertEquals(0.2f, placed.width, 0.0001f)
        assertEquals(0.2f, placed.height, 0.0001f)
    }

    @Test
    fun `靠近右下时按屏幕边距夹取`() {
        val placed = bounds(0f, 0f, 0.2f, 0.2f)
            .centeredOnTrigger(
                anchorXRatio = 1f,
                anchorYRatio = 1f,
                gapXRatio = 0.01f,
                gapYRatio = 0.01f,
            )

        assertEquals(0.79f, placed.x, 0.0001f)
        assertEquals(0.79f, placed.y, 0.0001f)
    }

    @Test
    fun `靠近左上时同样保留边距`() {
        val placed = bounds(0.5f, 0.5f, 0.2f, 0.2f)
            .centeredOnTrigger(
                anchorXRatio = 0f,
                anchorYRatio = 0f,
                gapXRatio = 0.01f,
                gapYRatio = 0.01f,
            )

        assertEquals(0.01f, placed.x, 0.0001f)
        assertEquals(0.01f, placed.y, 0.0001f)
    }

    @Test
    fun `窗口比屏幕大时左上角落到零`() {
        val placed = bounds(0.5f, 0.5f, 1.5f, 1.5f)
            .centeredOnTrigger(
                anchorXRatio = 0.5f,
                anchorYRatio = 0.5f,
                gapXRatio = 0.01f,
                gapYRatio = 0.01f,
            )

        assertEquals(0f, placed.x, 0.0001f)
        assertEquals(0f, placed.y, 0.0001f)
    }

    @Test
    fun `边距为零时贴边`() {
        val placed = bounds(0f, 0f, 0.2f, 0.2f)
            .centeredOnTrigger(anchorXRatio = 1f, anchorYRatio = 1f)

        assertEquals(0.8f, placed.x, 0.0001f)
        assertEquals(0.8f, placed.y, 0.0001f)
    }
}
