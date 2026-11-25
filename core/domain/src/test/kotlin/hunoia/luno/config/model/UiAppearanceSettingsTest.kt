package hunoia.luno.config.model

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class UiAppearanceSettingsTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `old payload without theme falls back to system dynamic`() {
        val decoded = json.decodeFromString<UiAppearanceSettings>("{}")
        assertEquals(ThemeMode.System, decoded.theme.mode)
        assertEquals(null, decoded.theme.paletteId)
    }

    @Test
    fun `theme palette selection round-trips`() {
        val source = UiAppearanceSettings(
            theme = ThemePaletteSetting(
                mode = ThemeMode.Palette,
                paletteId = "builtin_catppuccin_mocha",
            ),
        )
        val decoded = json.decodeFromString<UiAppearanceSettings>(json.encodeToString(source))
        assertEquals(source.theme, decoded.theme)
    }
}