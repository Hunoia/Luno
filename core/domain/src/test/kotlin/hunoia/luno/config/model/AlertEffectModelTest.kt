package hunoia.luno.config.model

import hunoia.luno.core.domain.JsonSerializer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 预设 / 层 schema：序列化往返、层自洽、组合约束与 v4 重置。 */
class AlertEffectModelTest {

    private fun layer(
        id: String,
        kind: AlertKind,
        enabled: Boolean = true,
        baseMillis: Int = 1000,
    ): AlertLayer = when (kind) {
        AlertKind.Particle -> AlertLayer(
            id = id,
            enabled = enabled,
            kind = kind,
            timing = AlertTiming(baseMillis = baseMillis),
            particle = ParticleParams(),
        )
        AlertKind.Edge -> AlertLayer(id, enabled = enabled, kind = kind, edge = EdgeParams())
        AlertKind.Ball -> AlertLayer(id, enabled = enabled, kind = kind, ball = BallParams())
        AlertKind.Capsule -> AlertLayer(id, enabled = enabled, kind = kind, capsule = CapsuleParams())
        AlertKind.Screen -> AlertLayer(id, enabled = enabled, kind = kind, screen = ScreenParams())
        AlertKind.Reveal -> AlertLayer(id, enabled = enabled, kind = kind, reveal = RevealParams())
    }

    @Test
    fun `preset round trips with layers`() {
        val preset = AlertPreset(
            id = "custom_combo",
            name = "我的组合",
            builtin = false,
            paletteId = "builtin_palette_rainbow",
            layers = listOf(
                AlertLayer(
                    id = "l0",
                    kind = AlertKind.Particle,
                    timing = AlertTiming(baseMillis = 1200, cycles = 2, speedPercent = 90),
                    color = AlertColorRef(colors = listOf(0xFFE53935.toInt(), 0xFF43A047.toInt())),
                    particle = ParticleParams(
                        emitter = ParticleEmitter(count = 80, speed = 0.7f),
                        motion = ParticleMotion(gravity = 1.2f, drag = 0.25f, wind = -0.2f),
                        look = ParticleLook(
                            shape = AlertParticleShape.Star,
                            sizeDp = 4f,
                            haloStyle = ParticleHaloStyle.Soft,
                        ),
                    ),
                ),
            ),
        )

        val decoded = JsonSerializer.decodeFromString<AlertPreset>(
            JsonSerializer.encodeToString(preset),
        )

        assertEquals(preset, decoded)
        val decodedLayer = decoded.layers.single()
        assertEquals(AlertParticleShape.Star, decodedLayer.particle?.look?.shape)
        assertEquals(ParticleHaloStyle.Soft, decodedLayer.particle?.look?.haloStyle)
        assertEquals(2, decodedLayer.color.colors.size)
    }

    @Test
    fun `layer matches kind only when exactly one matching block is present`() {
        assertTrue(layer("a", AlertKind.Edge).matchesKind())
        assertFalse(AlertLayer(id = "b", kind = AlertKind.Edge).matchesKind())
        assertFalse(
            AlertLayer(id = "c", kind = AlertKind.Particle, edge = EdgeParams()).matchesKind(),
        )
        assertFalse(
            AlertLayer(
                id = "d",
                kind = AlertKind.Particle,
                particle = ParticleParams(),
                edge = EdgeParams(),
            ).matchesKind(),
        )
    }

    @Test
    fun `renderable layers keep order and drop the over-limit ones`() {
        val preset = AlertPreset(
            id = "p",
            name = "P",
            layers = listOf(
                layer("1", AlertKind.Edge),
                layer("2", AlertKind.Particle),
                layer("3", AlertKind.Ball),
                layer("4", AlertKind.Screen),
                layer("5", AlertKind.Edge),
                layer("6", AlertKind.Edge),
                layer("7", AlertKind.Capsule),
                layer("8", AlertKind.Capsule),
            ),
        )
        val ids = preset.renderableLayers().map { it.id }
        // 上限 5：前五层；第三层边只能有 2 层，所以第 6 层被丢。
        assertEquals(listOf("1", "2", "3", "4", "5"), ids)
    }

    @Test
    fun `capsule screen and reveal are capped to one while disabled layers do not count`() {
        val preset = AlertPreset(
            id = "p",
            name = "P",
            layers = listOf(
                layer("cap1", AlertKind.Capsule),
                layer("cap2", AlertKind.Capsule),
                layer("scr1", AlertKind.Screen),
                layer("scr2", AlertKind.Screen),
                layer("rev1", AlertKind.Reveal),
                layer("rev2", AlertKind.Reveal),
                layer("off", AlertKind.Particle, enabled = false),
            ),
        )
        val ids = preset.renderableLayers().map { it.id }
        assertEquals(listOf("cap1", "scr1", "rev1"), ids)
        assertEquals(6, preset.enabledLayerCount)
        assertTrue(AlertLimit.TooManyCapsuleLayers in preset.validate())
        assertTrue(AlertLimit.TooManyScreenLayers in preset.validate())
        assertTrue(AlertLimit.TooManyRevealLayers in preset.validate())
    }

    @Test
    fun `disabled layers keep params but do not occupy the cap`() {
        val layers = listOf(
            layer("a", AlertKind.Edge, enabled = false),
            layer("b", AlertKind.Edge, enabled = false),
            layer("c", AlertKind.Edge),
            layer("d", AlertKind.Edge),
        )
        val preset = AlertPreset(id = "p", name = "P", layers = layers)
        assertEquals(listOf("c", "d"), preset.renderableLayers().map { it.id })
        assertTrue(preset.validate().isEmpty())
        assertEquals(AlertLimit.TooManyInCategory, preset.addBlockedBy(AlertKind.Edge))
    }

    @Test
    fun `empty preset and stored cap are reported`() {
        val empty = AlertPreset(id = "p", name = "P")
        assertTrue(AlertLimit.EmptyPreset in empty.validate())

        val tooMany = AlertPreset(
            id = "p",
            name = "P",
            layers = (1..11).map { layer("$it", AlertKind.Particle) },
        )
        assertTrue(AlertLimit.TooManyStoredLayers in tooMany.validate())
    }

    @Test
    fun `v3 alert subtree is skipped and library stays independent`() {
        val decoded = JsonSerializer.decodeFromString<FloatBallSettings>(
            """
            {
              "notificationCapsule": {
                "alert_v3": {
                  "slots": { "edge": { "enabled": true } }
                },
                "alertLibrary": {
                  "presets": [
                    { "id": "custom_a", "name": "A", "variants": [] }
                  ]
                }
              }
            }
            """,
        )

        val alert = decoded.notificationCapsule.alert
        assertFalse(alert.enabled)
        assertNull(alert.presetId)
        assertEquals(100, alert.intensityPercent)
        // 旧 variants 解成空层预设：保留数据但不参与渲染，等用户重新保存。
        assertEquals(1, decoded.notificationCapsule.alertLibrary.presets.size)
        assertTrue(decoded.notificationCapsule.alertLibrary.presets.single().layers.isEmpty())
    }

    @Test
    fun `v4 alert key decodes master switch preset and global knobs`() {
        val decoded = JsonSerializer.decodeFromString<FloatBallSettings>(
            """
            {
              "notificationCapsule": {
                "alert_v4": {
                  "enabled": true,
                  "presetId": "builtin_particle_snow",
                  "intensityPercent": 120,
                  "speedPercent": 80,
                  "readNotificationText": false
                }
              }
            }
            """,
        )
        val alert = decoded.notificationCapsule.alert
        assertTrue(alert.enabled)
        assertEquals("builtin_particle_snow", alert.presetId)
        assertEquals(120, alert.intensityPercent)
        assertEquals(80, alert.speedPercent)
        assertFalse(alert.readNotificationText)
        assertEquals(1.2f, alert.intensityScale, 0.001f)
        assertEquals(0.8f, alert.speedScale, 0.001f)
    }

    @Test
    fun `library is empty when absent`() {
        val decoded = JsonSerializer.decodeFromString<FloatBallSettings>(
            """{"notificationCapsule":{"enabled":true}}""",
        )
        assertTrue(decoded.notificationCapsule.alertLibrary.presets.isEmpty())
        assertTrue(decoded.notificationCapsule.alertLibrary.palettes.isEmpty())
    }

    @Test
    fun `color ref defaults follow alert color`() {
        val ref = AlertColorRef()
        assertNull(ref.paletteId)
        assertTrue(ref.colors.isEmpty())
    }
}