package hunoia.luno.config.model

import hunoia.luno.core.domain.JsonSerializer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 预设 / 层 schema：序列化往返、层自洽、组合约束与旧配置容错。 */
class AlertEffectModelTest {

    private fun layer(
        id: String,
        family: AlertFamily,
        enabled: Boolean = true,
        baseMillis: Int = 1000,
    ): AlertLayer = when (family) {
        AlertFamily.Edge -> AlertLayer(
            id = id,
            enabled = enabled,
            family = family,
            timing = AlertTiming(baseMillis = baseMillis),
            edge = EdgeParams(),
        )
        AlertFamily.Ring -> AlertLayer(id, enabled = enabled, family = family, ring = RingParams())
        AlertFamily.Capsule ->
            AlertLayer(id, enabled = enabled, family = family, capsule = CapsuleParams())
        AlertFamily.Wash -> AlertLayer(id, enabled = enabled, family = family, wash = WashParams())
        AlertFamily.Cover -> AlertLayer(id, enabled = enabled, family = family, cover = CoverParams())
        AlertFamily.Particles -> AlertLayer(
            id = id,
            enabled = enabled,
            family = family,
            particle = ParticleParams(),
        )
        AlertFamily.Reveal ->
            AlertLayer(id, enabled = enabled, family = family, reveal = RevealParams())
    }

    @Test
    fun `preset round trips with new shape switches`() {
        val preset = AlertPreset(
            id = "custom_combo",
            name = "我的组合",
            kind = AlertPresetKind.Combo,
            paletteId = "builtin_palette_rainbow",
            layers = listOf(
                AlertLayer(
                    id = "edge",
                    family = AlertFamily.Edge,
                    timing = AlertTiming(baseMillis = 1200, cycles = 2, speedPercent = 90),
                    opacity = 0.8f,
                    edge = EdgeParams(
                        shape = EdgeShape.Lightning,
                        lightning = EdgeLightning(
                            bolts = 5,
                            widthDp = 2.2f,
                            reachPercent = 55,
                        ),
                        sweep = EdgeSweep(tailPercent = 22),
                    ),
                ),
                AlertLayer(
                    id = "capsule",
                    family = AlertFamily.Capsule,
                    capsule = CapsuleParams(
                        shape = CapsuleShape.Flip,
                        flip = CapsuleFlip(degrees = 270),
                    ),
                ),
                AlertLayer(
                    id = "particles",
                    family = AlertFamily.Particles,
                    particle = ParticleParams(
                        emitter = ParticleEmitter(
                            area = ParticleArea.EdgePerimeter,
                            count = 80,
                            emission = ParticleEmission.Stream,
                            direction = ParticleDirection.Inward,
                        ),
                        cluster = ParticleCluster(anchors = 3, launchHeight = 0.5f),
                        look = ParticleLook(
                            shape = AlertParticleShape.Star,
                            sizeShrink = 0.4f,
                            colorPerWave = true,
                            alphaJitter = 0.3f,
                        ),
                    ),
                ),
            ),
        )

        val decoded = JsonSerializer.decodeFromString<AlertPreset>(
            JsonSerializer.encodeToString(preset),
        )

        assertEquals(preset, decoded)
        assertEquals(EdgeShape.Lightning, decoded.layers[0].edge?.shape)
        assertEquals(5, decoded.layers[0].edge?.lightning?.bolts)
        assertEquals(22, decoded.layers[0].edge?.sweep?.tailPercent)
        assertEquals(CapsuleShape.Flip, decoded.layers[1].capsule?.shape)
        assertEquals(270, decoded.layers[1].capsule?.flip?.degrees)
        val particle = decoded.layers[2].particle
        assertEquals(ParticleEmission.Stream, particle?.emitter?.emission)
        assertEquals(3, particle?.cluster?.anchors)
        assertEquals(0.5f, particle?.cluster?.launchHeight ?: 0f, 0.001f)
        assertEquals(ParticleDirection.Inward, particle?.emitter?.direction)
        assertEquals(0.4f, particle?.look?.sizeShrink ?: 0f, 0.001f)
    }

    @Test
    fun `layer matches family only when exactly one matching block is present`() {
        assertTrue(layer("a", AlertFamily.Edge).matchesFamily())
        assertFalse(AlertLayer(id = "b", family = AlertFamily.Edge).matchesFamily())
        assertFalse(
            AlertLayer(id = "c", family = AlertFamily.Particles, edge = EdgeParams()).matchesFamily(),
        )
        assertFalse(
            AlertLayer(
                id = "d",
                family = AlertFamily.Particles,
                particle = ParticleParams(),
                edge = EdgeParams(),
            ).matchesFamily(),
        )
    }

    @Test
    fun `preset kind becomes combo once it holds more than one layer`() {
        val multi = AlertPreset(
            id = "p",
            name = "P",
            layers = listOf(layer("a", AlertFamily.Edge), layer("b", AlertFamily.Particles)),
        )
        assertEquals(AlertPresetKind.Combo, multi.resolvedKind)
        assertEquals(AlertPresetKind.Single, AlertPreset(id = "s", name = "S").resolvedKind)
    }

    @Test
    fun `renderable layers keep order and drop the over-limit ones`() {
        val preset = AlertPreset(
            id = "p",
            name = "P",
            layers = listOf(
                layer("1", AlertFamily.Edge),
                layer("2", AlertFamily.Particles),
                layer("3", AlertFamily.Ring),
                layer("4", AlertFamily.Cover),
                layer("5", AlertFamily.Edge),
                layer("6", AlertFamily.Edge),
                layer("7", AlertFamily.Edge),
                layer("8", AlertFamily.Capsule),
            ),
        )
        val ids = preset.renderableLayers().map { it.id }
        // 上限 5 层、每类 3 层：前五层合法，第四层 Edge（第 4 条边类）被类别上限挤掉。
        assertEquals(listOf("1", "2", "3", "4", "5"), ids)
    }

    @Test
    fun `capsule screen and reveal are capped to one while disabled layers do not count`() {
        val preset = AlertPreset(
            id = "p",
            name = "P",
            layers = listOf(
                layer("cap1", AlertFamily.Capsule),
                layer("cap2", AlertFamily.Capsule),
                layer("scr1", AlertFamily.Cover),
                layer("scr2", AlertFamily.Wash),
                layer("rev1", AlertFamily.Reveal),
                layer("rev2", AlertFamily.Reveal),
                layer("off", AlertFamily.Particles, enabled = false),
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
            layer("a", AlertFamily.Edge, enabled = false),
            layer("b", AlertFamily.Edge, enabled = false),
            layer("c", AlertFamily.Edge),
            layer("d", AlertFamily.Edge),
            layer("e", AlertFamily.Edge),
        )
        val preset = AlertPreset(id = "p", name = "P", layers = layers)
        assertEquals(listOf("c", "d", "e"), preset.renderableLayers().map { it.id })
        assertTrue(preset.validate().isEmpty())
        assertEquals(AlertLimit.TooManyInCategory, preset.addBlockedBy(AlertFamily.Edge))
    }

    @Test
    fun `empty preset and stored cap are reported`() {
        val empty = AlertPreset(id = "p", name = "P")
        assertTrue(AlertLimit.EmptyPreset in empty.validate())

        val tooMany = AlertPreset(
            id = "p",
            name = "P",
            layers = (1..11).map { layer("$it", AlertFamily.Particles) },
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
                  "presetId": "user_a",
                  "intensityPercent": 120,
                  "speedPercent": 80,
                  "readNotificationText": false,
                  "randomize": true
                }
              }
            }
            """,
        )
        val alert = decoded.notificationCapsule.alert
        assertTrue(alert.enabled)
        assertEquals("user_a", alert.presetId)
        assertEquals(120, alert.intensityPercent)
        assertEquals(80, alert.speedPercent)
        assertFalse(alert.readNotificationText)
        assertTrue(alert.randomize)
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
