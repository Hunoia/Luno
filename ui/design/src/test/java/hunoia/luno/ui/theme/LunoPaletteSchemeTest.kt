package hunoia.luno.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import hunoia.luno.config.model.AlertPalette
import hunoia.luno.config.model.ThemeMode
import hunoia.luno.config.model.ThemePaletteSetting
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LunoPaletteSchemeTest {

    private val palette = AlertPalette(
        id = "custom_test",
        name = "Test",
        colors = listOf(0xFF3366CC.toInt(), 0xFFCC3366.toInt()),
    )

    @Test
    fun `seed uses first color and forces opaque alpha`() {
        assertEquals(0xFF3366CC.toInt(), LunoPaletteScheme.seedOf(palette))
        assertEquals(
            0xFF3366CC.toInt(),
            LunoPaletteScheme.seedOf(palette.copy(colors = listOf(0x003366CC))),
        )
    }

    @Test
    fun `same seed and mode are deterministic`() {
        val a = LunoPaletteScheme.schemeFor(palette, dark = false)
        val b = LunoPaletteScheme.schemeFor(palette, dark = false)
        assertEquals(a.primary, b.primary)
        assertEquals(a.surfaceContainer, b.surfaceContainer)
    }

    @Test
    fun `light and dark schemes differ`() {
        val light = LunoPaletteScheme.schemeFor(palette, dark = false)
        val dark = LunoPaletteScheme.schemeFor(palette, dark = true)
        assertNotEquals(light.primary, dark.primary)
        assertNotEquals(light.surface, dark.surface)
    }

    @Test
    fun `primary and on-primary keep readable contrast`() {
        listOf(false, true).forEach { dark ->
            val scheme = LunoPaletteScheme.schemeFor(palette, dark)
            assertTrue(contrast(scheme.primary, scheme.onPrimary) >= 3f)
        }
    }

    @Test
    fun `resolve returns null for system and missing id`() {
        assertNull(LunoPaletteScheme.resolve(ThemePaletteSetting(), emptyList()))
        assertNull(
            LunoPaletteScheme.resolve(
                ThemePaletteSetting(mode = ThemeMode.Palette, paletteId = "missing"),
                emptyList(),
            ),
        )
        assertEquals(
            palette,
            LunoPaletteScheme.resolve(
                ThemePaletteSetting(mode = ThemeMode.Palette, paletteId = palette.id),
                listOf(palette),
            ),
        )
    }

    private fun contrast(a: Color, b: Color): Float {
        val la = a.luminance()
        val lb = b.luminance()
        val hi = maxOf(la, lb)
        val lo = minOf(la, lb)
        return (hi + 0.05f) / (lo + 0.05f)
    }
}