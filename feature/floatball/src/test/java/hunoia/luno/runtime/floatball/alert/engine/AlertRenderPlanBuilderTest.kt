package hunoia.luno.runtime.floatball.alert.engine

import hunoia.luno.config.model.AlertColorRef
import hunoia.luno.config.model.AlertHost
import hunoia.luno.config.model.AlertKind
import hunoia.luno.config.model.AlertPreset
import hunoia.luno.config.model.AlertTiming
import hunoia.luno.config.model.AlertVariant
import hunoia.luno.config.model.CapsuleParams
import hunoia.luno.config.model.ParticleParams
import hunoia.luno.config.model.ScreenParams
import hunoia.luno.runtime.floatball.alert.AlertPaletteSpec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 渲染计划构建：覆盖合并、调色板解析、失败跳过与宿主分区。 */
class AlertRenderPlanBuilderTest {

    private val particlePreset = AlertPreset(
        id = "p_particle",
        name = "P",
        builtin = true,
        variants = listOf(
            AlertVariant(
                host = AlertHost.Stage,
                kind = AlertKind.Particle,
                timing = AlertTiming(baseMillis = 1000, cycles = 2, speedPercent = 100),
                particle = ParticleParams(),
            ),
        ),
    )

    private val capsulePreset = AlertPreset(
        id = "p_capsule",
        name = "C",
        builtin = true,
        variants = listOf(
            AlertVariant(
                host = AlertHost.Capsule,
                kind = AlertKind.Capsule,
                timing = AlertTiming(baseMillis = 800, cycles = 1, speedPercent = 100),
                capsule = CapsuleParams(),
            ),
        ),
    )

    private val screenPreset = AlertPreset(
        id = "p_screen",
        name = "S",
        builtin = true,
        variants = listOf(
            AlertVariant(
                host = AlertHost.Screen,
                kind = AlertKind.Screen,
                timing = AlertTiming(baseMillis = 600, cycles = 1, speedPercent = 100),
                screen = ScreenParams(),
            ),
        ),
    )

    private val presets = mapOf(
        particlePreset.id to particlePreset,
        capsulePreset.id to capsulePreset,
        screenPreset.id to screenPreset,
    )

    private val palette = AlertPaletteSpec(colors = listOf(0xFFE53935.toInt()))

    @Test
    fun `builds layers with timing intensity and palette`() {
        val plan = AlertRenderPlanBuilder.build(
            requests = listOf(
                AlertSlotRequest(particlePreset.id, AlertHost.Stage, intensity = 0.6f, speedScale = 2f),
                AlertSlotRequest(capsulePreset.id, AlertHost.Capsule),
                AlertSlotRequest(screenPreset.id, AlertHost.Screen),
            ),
            presets = presets,
            paletteFor = { palette },
            appPackage = "com.example",
            tierFactor = 1.5f,
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
        assertEquals(1500, plan.durationMillis) // particle 750 × 2 最长
    }

    @Test
    fun `intensity clamps and speed scale is bounded`() {
        val plan = AlertRenderPlanBuilder.build(
            requests = listOf(
                AlertSlotRequest(particlePreset.id, AlertHost.Stage, intensity = 5f, speedScale = 99f),
            ),
            presets = presets,
            paletteFor = { null },
        )
        val layer = plan.layers.single()
        assertEquals(2f, layer.intensity, 0.001f)
        // speedScale 夹到 4 → 1000/4 = 250
        assertEquals(250, layer.cycleMillis)
    }

    @Test
    fun `unknown preset and missing host are skipped with reasons`() {
        val plan = AlertRenderPlanBuilder.build(
            requests = listOf(
                AlertSlotRequest("missing", AlertHost.Stage),
                AlertSlotRequest(particlePreset.id, AlertHost.Ball),
            ),
            presets = presets,
            paletteFor = { palette },
        )
        assertTrue(plan.isEmpty)
        assertEquals(2, plan.skipped.size)
        assertTrue(plan.skipped[0] is PresetResolveError.UnknownPreset)
        assertTrue(plan.skipped[1] is PresetResolveError.MissingHost)
    }

    @Test
    fun `only the matching param block is carried`() {
        val plan = AlertRenderPlanBuilder.build(
            requests = listOf(AlertSlotRequest(screenPreset.id, AlertHost.Screen)),
            presets = presets,
            paletteFor = { palette },
        )
        val layer = plan.layers.single()
        assertEquals(AlertKind.Screen, layer.kind)
        assertTrue(layer.screen != null)
        assertNull(layer.particle)
        assertNull(layer.edge)
        assertNull(layer.capsule)
    }

    @Test
    fun `palette resolution receives the variant color ref`() {
        var received: AlertColorRef? = null
        AlertRenderPlanBuilder.build(
            requests = listOf(AlertSlotRequest(particlePreset.id, AlertHost.Stage)),
            presets = presets,
            paletteFor = { received = it; null },
        )
        assertEquals(AlertColorRef(), received)
    }
}