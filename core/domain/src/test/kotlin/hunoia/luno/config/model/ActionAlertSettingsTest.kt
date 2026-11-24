package hunoia.luno.config.model

import hunoia.luno.core.domain.JsonSerializer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 动作动效绑定：key 口径、条目不回退类型、清理，以及旧配置的默认值。 */
class ActionAlertSettingsTest {

    @Test
    fun `type and library bindings resolve independently`() {
        val settings = ActionAlertSettings(enabled = true)
            .withBinding(typeId = "system.back", entryId = null, presetId = "p1")
            .withBinding(typeId = null, entryId = "entry-1", presetId = "p2")

        assertEquals("p1", settings.presetFor("system.back", null))
        assertEquals("p2", settings.presetFor(null, "entry-1"))
        // 动作库引用只查条目；同类型的类型绑定不参与（互不回退）。
        assertEquals("p2", settings.presetFor("system.back", "entry-1"))
        assertNull(settings.presetFor(null, "missing"))
        assertNull(settings.presetFor("system.home", null))
    }

    @Test
    fun `null preset clears the binding`() {
        val settings = ActionAlertSettings()
            .withBinding(typeId = "system.back", entryId = null, presetId = "p1")
            .withBinding(typeId = "system.back", entryId = null, presetId = null)

        assertTrue(settings.bindings.isEmpty())
    }

    @Test
    fun `deleting a preset or entry removes the related bindings`() {
        val settings = ActionAlertSettings()
            .withBinding(typeId = "system.back", entryId = null, presetId = "p1")
            .withBinding(typeId = "system.home", entryId = null, presetId = "p1")
            .withBinding(typeId = null, entryId = "entry-1", presetId = "p2")

        val noP1 = settings.withoutPreset("p1")
        assertNull(noP1.presetFor("system.back", null))
        assertNull(noP1.presetFor("system.home", null))
        assertEquals("p2", noP1.presetFor(null, "entry-1"))

        val noEntry = settings.withoutEntry("entry-1")
        assertNull(noEntry.presetFor(null, "entry-1"))
        assertEquals("p1", noEntry.presetFor("system.back", null))
    }

    @Test
    fun `scales clamp to the shared ui ranges`() {
        assertEquals(0.5f, ActionAlertSettings(intensityPercent = 0).intensityScale)
        assertEquals(2f, ActionAlertSettings(intensityPercent = 999).intensityScale)
        assertEquals(0.25f, ActionAlertSettings(speedPercent = 0).speedScale)
        assertEquals(4f, ActionAlertSettings(speedPercent = 999).speedScale)
    }

    @Test
    fun `legacy config decodes to disabled and preset defaults to skip`() {
        val decoded = JsonSerializer.decodeFromString<FloatBallSettings>("{}")
        assertFalse(decoded.actionAlert.enabled)
        assertEquals(100, decoded.actionAlert.intensityPercent)
        assertTrue(decoded.actionAlert.bindings.isEmpty())

        val preset = JsonSerializer.decodeFromString<AlertPreset>("""{"id":"p","name":"n"}""")
        assertEquals(AlertPlaybackPolicy.Skip, preset.playbackPolicy)
    }

    @Test
    fun `playback policy roundtrips`() {
        val preset = AlertPreset(
            id = "p",
            name = "n",
            playbackPolicy = AlertPlaybackPolicy.Queue,
        )
        val json = JsonSerializer.globalJson.encodeToString(preset)
        val decoded = JsonSerializer.decodeFromString<AlertPreset>(json)
        assertEquals(AlertPlaybackPolicy.Queue, decoded.playbackPolicy)
    }
}