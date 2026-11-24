package hunoia.luno.runtime.floatball.alert.engine

import hunoia.luno.config.model.AlertColorRef
import hunoia.luno.config.model.AlertKind
import hunoia.luno.config.model.AlertLayer
import hunoia.luno.config.model.AlertLimits
import hunoia.luno.config.model.AlertPreset
import hunoia.luno.config.model.AlertTiming
import hunoia.luno.config.model.CapsuleParams
import hunoia.luno.config.model.EdgeParams
import hunoia.luno.config.model.ParticleEmitter
import hunoia.luno.config.model.ParticleParams
import hunoia.luno.config.model.ScreenParams
import hunoia.luno.runtime.floatball.alert.AlertPaletteSpec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 渲染计划构建：时序合并、调色板解析、预算与失败原因。 */
class AlertRenderPlanBuilderTest {

    private fun layer(
        id: String,
        kind: AlertKind,
        baseMillis: Int = 1000,
        cycles: Int = 2,
        speedPercent: Int = 100,
        count: Int = 40,
    ): AlertLayer = when (kind) {
        AlertKind.Particle -> AlertLayer(
            id = id,
            kind = kind,
            timing = AlertTiming(baseMillis, cycles, speedPercent),
            particle = ParticleParams(emitter = ParticleEmitter(count = count)),
        )
        AlertKind.Edge -> AlertLayer(
            id = id,
            kind = kind,
            timing = AlertTiming(baseMillis, cycles, speedPercent),
            edge = EdgeParams(),
        )
        AlertKind.Ball -> AlertLayer(
            id = id,
            kind = kind,
            timing = AlertTiming(baseMillis, cycles, speedPercent),
            ball = hunoia.luno.config.model.BallParams(),
        )
        AlertKind.Capsule -> AlertLayer(
            id = id,
            kind = kind,
            timing = AlertTiming(baseMillis, cycles, speedPercent),
            capsule = CapsuleParams(),
        )
        AlertKind.Screen -> AlertLayer(
            id = id,
            kind = kind,
            timing = AlertTiming(baseMillis, cycles, speedPercent),
            screen = ScreenParams(),
        )
        AlertKind.Reveal -> AlertLayer(
            id = id,
            kind = kind,
            timing = AlertTiming(baseMillis, cycles, speedPercent),
            reveal = hunoia.luno.config.model.RevealParams(),
        )
    }

    private val palette = AlertPaletteSpec(colors = listOf(0xFFE53935.toInt()))

    @Test
    fun `builds layers with timing intensity and palette`() {
        val preset = AlertPreset(
            id = "p",
            name = "P",
            layers = listOf(
                layer("a", AlertKind.Particle),
                layer("b", AlertKind.Capsule, baseMillis = 800, cycles = 1),
                layer("c", AlertKind.Screen, baseMillis = 600, cycles = 1),
            ),
        )
        val plan = AlertRenderPlanBuilder.build(
            preset = preset,
            presetId = preset.id,
            paletteFor = { palette },
            appPackage = "com.example",
            intensityScale = 0.6f,
            speedScale = 2f,
            tierForKind = { 1.5f },
            seed = 11,
        )

        assertEquals(3, plan.layers.size)
        val particle = plan.layers[0]
        assertEquals(AlertKind.Particle, particle.kind)
        assertEquals(750, particle.cycleMillis) // 1000 × 1.5 ÷ 2
        assertEquals(2, particle.cycles)
        assertEquals(1500, particle.totalMillis)
        assertEquals(0.6f, particle.intensity, 0.001f)
        assertEquals(palette, particle.palette)
        assertEquals("com.example", particle.appPackage)
        assertEquals(1, plan.capsuleLayers.size)
        assertEquals(2, plan.stageLayers.size)
        assertEquals(11, plan.seed)
        assertEquals(1500, plan.durationMillis)
        assertEquals(listOf("a", "b", "c"), plan.layers.map { it.layerId })
    }

    @Test
    fun `intensity and speed scales are bounded`() {
        val preset = AlertPreset(
            id = "p",
            name = "P",
            layers = listOf(layer("a", AlertKind.Edge)),
        )
        val plan = AlertRenderPlanBuilder.build(
            preset = preset,
            presetId = preset.id,
            paletteFor = { null },
            intensityScale = 5f,
            speedScale = 99f,
        )
        val layer = plan.layers.single()
        assertEquals(2f, layer.intensity, 0.001f)
        assertEquals(250, layer.cycleMillis) // 1000 ÷ 4
    }

    @Test
    fun `unknown preset is reported and empty preset never renders`() {
        val unknown = AlertRenderPlanBuilder.build(
            preset = null,
            presetId = "missing",
            paletteFor = { palette },
        )
        assertTrue(unknown.isEmpty)
        assertTrue(unknown.skipped.single() is PresetResolveError.UnknownPreset)

        val empty = AlertRenderPlanBuilder.build(
            preset = AlertPreset(id = "e", name = "E"),
            presetId = "e",
            paletteFor = { palette },
        )
        assertTrue(empty.isEmpty)
        assertTrue(empty.skipped.single() is PresetResolveError.EmptyPreset)
    }

    @Test
    fun `only the matching param block is carried`() {
        val preset = AlertPreset(
            id = "p",
            name = "P",
            layers = listOf(layer("s", AlertKind.Screen)),
        )
        val layer = AlertRenderPlanBuilder.build(
            preset = preset,
            presetId = preset.id,
            paletteFor = { palette },
        ).layers.single()
        assertEquals(AlertKind.Screen, layer.kind)
        assertTrue(layer.screen != null)
        assertNull(layer.particle)
        assertNull(layer.edge)
        assertNull(layer.capsule)
    }

    @Test
    fun `preset palette wins over the layer color ref`() {
        var received: AlertColorRef? = null
        val preset = AlertPreset(
            id = "p",
            name = "P",
            paletteId = "shared",
            layers = listOf(layer("a", AlertKind.Edge).copy(color = AlertColorRef())),
        )
        AlertRenderPlanBuilder.build(
            preset = preset,
            presetId = preset.id,
            paletteFor = { received = it; null },
        )
        assertEquals("shared", received?.paletteId)
    }

    @Test
    fun `particle budget is sequential across layers`() {
        val preset = AlertPreset(
            id = "p",
            name = "P",
            layers = listOf(
                layer("a", AlertKind.Particle, count = 400),
                layer("b", AlertKind.Particle, count = 400),
            ),
        )
        val plan = AlertRenderPlanBuilder.build(
            preset = preset,
            presetId = preset.id,
            paletteFor = { null },
        )
        assertEquals(2, plan.layers.size)
        assertEquals(400, plan.layers[0].particle?.emitter?.count)
        assertEquals(AlertLimits.ParticleBudget - 400, plan.layers[1].particle?.emitter?.count)
    }

    @Test
    fun `particle budget counts waves not just per-spawn count`() {
        // 渲染端每波都会再乘一份 count，预算必须按 count × waves 算，否则实际粒子数翻倍。
        val preset = AlertPreset(
            id = "p",
            name = "P",
            layers = listOf(
                AlertLayer(
                    id = "a",
                    kind = AlertKind.Particle,
                    particle = ParticleParams(emitter = ParticleEmitter(count = 300, waves = 2)),
                ),
                AlertLayer(
                    id = "b",
                    kind = AlertKind.Particle,
                    particle = ParticleParams(emitter = ParticleEmitter(count = 300, waves = 2)),
                ),
            ),
        )
        val plan = AlertRenderPlanBuilder.build(
            preset = preset,
            presetId = preset.id,
            paletteFor = { null },
        )
        assertEquals(1, plan.layers.size)
        assertEquals(300, plan.layers.single().particle?.emitter?.count)
    }

    @Test
    fun `layer speed percent divides its own cycle`() {
        val preset = AlertPreset(
            id = "p",
            name = "P",
            layers = listOf(layer("a", AlertKind.Edge, baseMillis = 1000, speedPercent = 200)),
        )
        val layer = AlertRenderPlanBuilder.build(
            preset = preset,
            presetId = preset.id,
            paletteFor = { null },
        ).layers.single()
        assertEquals(500, layer.cycleMillis)
    }
}
