package hunoia.luno.config.model

import hunoia.luno.core.domain.JsonSerializer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 预设 / 素材库 schema：序列化往返、kind 自洽校验、与旧配置共存。 */
class AlertEffectModelTest {

    @Test
    fun `preset round trips with particle params`() {
        val preset = AlertPreset(
            id = "custom_rain",
            name = "我的雨",
            builtin = false,
            variants = listOf(
                AlertVariant(
                    host = AlertHost.Stage,
                    kind = AlertKind.Particle,
                    timing = AlertTiming(baseMillis = 1200, cycles = 2, speedPercent = 90),
                    color = AlertColorRef(colors = listOf(0xFFE53935.toInt(), 0xFF43A047.toInt())),
                    particle = ParticleParams(
                        emitter = ParticleEmitter(count = 80, speed = 0.7f),
                        motion = ParticleMotion(gravity = 1.2f, drag = 0.25f, wind = -0.2f),
                        look = ParticleLook(shape = AlertParticleShape.Star, sizeDp = 4f),
                    ),
                ),
            ),
        )

        val decoded = JsonSerializer.decodeFromString<AlertPreset>(JsonSerializer.encodeToString(preset))

        assertEquals(preset, decoded)
        assertEquals(AlertParticleShape.Star, decoded.variants.single().particle?.look?.shape)
        assertEquals(2, decoded.variants.single().color.colors.size)
    }

    @Test
    fun `variant matches kind only when exactly one matching block is present`() {
        val valid = AlertVariant(host = AlertHost.Edge, kind = AlertKind.Edge, edge = EdgeParams())
        assertTrue(valid.matchesKind())

        val missing = AlertVariant(host = AlertHost.Edge, kind = AlertKind.Edge)
        assertFalse(missing.matchesKind())

        val wrongBlock = AlertVariant(
            host = AlertHost.Stage,
            kind = AlertKind.Particle,
            edge = EdgeParams(),
        )
        assertFalse(wrongBlock.matchesKind())

        val twoBlocks = AlertVariant(
            host = AlertHost.Stage,
            kind = AlertKind.Particle,
            particle = ParticleParams(),
            edge = EdgeParams(),
        )
        assertFalse(twoBlocks.matchesKind())
    }

    @Test
    fun `color ref defaults follow alert color`() {
        val ref = AlertColorRef()
        assertNull(ref.paletteId)
        assertTrue(ref.colors.isEmpty())
    }

    @Test
    fun `library defaults are empty and decode next to alert`() {
        val decoded = JsonSerializer.decodeFromString<FloatBallSettings>(
            """
            {
              "notificationCapsule": {
                "alertLibrary": {
                  "presets": [
                    { "id": "custom_a", "name": "A", "variants": [] }
                  ]
                }
              }
            }
            """,
        )

        val library = decoded.notificationCapsule.alertLibrary
        assertEquals(1, library.presets.size)
        assertEquals("custom_a", library.presets.single().id)
        assertTrue(library.palettes.isEmpty())
    }

    @Test
    fun `library is empty when absent`() {
        val decoded = JsonSerializer.decodeFromString<FloatBallSettings>(
            """{"notificationCapsule":{"enabled":true}}""",
        )

        assertTrue(decoded.notificationCapsule.alertLibrary.presets.isEmpty())
        assertTrue(decoded.notificationCapsule.alertLibrary.palettes.isEmpty())
    }
}