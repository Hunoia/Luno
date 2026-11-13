package hunoia.luno.config.model

import org.junit.Assert.assertEquals
import org.junit.Test

class ClipFloatSettingsTest {

    @Test
    fun `coercion clamps alpha size and placement`() {
        val out = ClipFloatSettings(
            alpha = 0.1f,
            widthDp = 9f,
            heightDp = 5000f,
            portrait = ClipFloatPlacement(x = -1f, y = 2f),
            landscape = ClipFloatPlacement(x = 0.4f, y = 0.6f),
        ).coerced()

        assertEquals(ClipFloatAlphaRange.start, out.alpha, 0f)
        assertEquals(ClipFloatWidthRange.start, out.widthDp, 0f)
        assertEquals(ClipFloatHeightRange.endInclusive, out.heightDp, 0f)
        assertEquals(0f, out.portrait.x, 0f)
        assertEquals(1f, out.portrait.y, 0f)
        assertEquals(0.4f, out.landscape.x, 0f)
        assertEquals(0.6f, out.landscape.y, 0f)
    }

    @Test
    fun `already valid settings are returned as is`() {
        val settings = ClipFloatSettings(alpha = 0.8f, widthDp = 240f, heightDp = 200f)
        assertEquals(settings, settings.coerced())
    }

    @Test
    fun `placement is stored per orientation`() {
        val settings = ClipFloatSettings()
        val portrait = ClipFloatPlacement(0.1f, 0.2f)
        val landscape = ClipFloatPlacement(0.7f, 0.8f)
        val updated = settings
            .withPlacement(landscape = false, placement = portrait)
            .withPlacement(landscape = true, placement = landscape)

        assertEquals(portrait, updated.placement(landscape = false))
        assertEquals(landscape, updated.placement(landscape = true))
    }
}
