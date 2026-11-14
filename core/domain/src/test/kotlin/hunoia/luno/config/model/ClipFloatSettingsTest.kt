package hunoia.luno.config.model

import org.junit.Assert.assertEquals
import org.junit.Test

class ClipFloatSettingsTest {

    @Test
    fun `coercion clamps size and placement`() {
        val out = ClipFloatSettings(
            widthDp = 9f,
            heightDp = 5000f,
            portrait = ClipFloatPlacement(x = -1f, y = 2f),
            landscape = ClipFloatPlacement(x = 0.4f, y = 0.6f),
        ).coerced()

        assertEquals(ClipFloatWidthRange.start, out.widthDp, 0f)
        assertEquals(ClipFloatHeightRange.endInclusive, out.heightDp, 0f)
        assertEquals(0f, out.portrait.x, 0f)
        assertEquals(1f, out.portrait.y, 0f)
        assertEquals(0.4f, out.landscape.x, 0f)
        assertEquals(0.6f, out.landscape.y, 0f)
    }

    @Test
    fun `already valid settings are returned as is`() {
        val settings = ClipFloatSettings(widthDp = 240f, heightDp = 200f)
        assertEquals(settings, settings.coerced())
    }

    @Test
    fun `placement is stored per orientation`() {
        val settings = ClipFloatSettings()
        val portrait = ClipFloatPlacement(0.1f, 0.2f)
        val landscape = ClipFloatPlacement(0.7f, 0.8f, ClipFloatPeekRight)
        val updated = settings
            .withPlacement(landscape = false, placement = portrait)
            .withPlacement(landscape = true, placement = landscape)

        assertEquals(portrait, updated.placement(landscape = false))
        assertEquals(landscape, updated.placement(landscape = true))
    }

    @Test
    fun `peek side survives placement coercion`() {
        val left = ClipFloatPlacement(x = 0f, y = 0.5f, peek = ClipFloatPeekLeft).coerced()
        val right = ClipFloatPlacement(x = 1f, y = 0.5f, peek = ClipFloatPeekRight).coerced()
        assertEquals(ClipFloatPeekLeft, left.peek)
        assertEquals(ClipFloatPeekRight, right.peek)
        assertEquals(0f, left.x, 0f)
        assertEquals(1f, right.x, 0f)
    }

    @Test
    fun `peek width stays narrower than the minimum window`() {
        // 停靠段是唯一命中区：它必须比最小窗口窄，否则「停靠」等于没移出屏外。
        assertEquals(true, ClipFloatPeekWidth < ClipFloatWidthRange.start)
    }
}
