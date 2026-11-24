package hunoia.luno.runtime.floatball.alert.engine

import hunoia.luno.config.model.AlertHost
import hunoia.luno.config.model.AlertKind
import hunoia.luno.config.model.AlertPreset
import hunoia.luno.config.model.AlertTiming
import hunoia.luno.config.model.AlertVariant
import hunoia.luno.config.model.EdgeParams
import hunoia.luno.config.model.ParticleParams
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 预设解析：宿主匹配、时序折算（档位 / 速度）、失败原因与批量跳过。 */
class AlertPresetResolverTest {

    private val preset = AlertPreset(
        id = "p",
        name = "P",
        builtin = true,
        variants = listOf(
            AlertVariant(
                host = AlertHost.Stage,
                kind = AlertKind.Particle,
                timing = AlertTiming(baseMillis = 1000, cycles = 2, speedPercent = 100),
                particle = ParticleParams(),
            ),
            AlertVariant(
                host = AlertHost.Edge,
                kind = AlertKind.Edge,
                timing = AlertTiming(baseMillis = 800, cycles = 1, speedPercent = 200),
                edge = EdgeParams(),
            ),
        ),
    )

    private fun ok(result: PresetResolveResult): ResolvedAlertLayer =
        (result as PresetResolveResult.Ok).layer

    @Test
    fun `resolves the variant by host`() {
        val stage = ok(AlertPresetResolver.resolve(preset, "p", AlertHost.Stage))
        assertEquals(AlertKind.Particle, stage.kind)
        assertEquals(1000, stage.cycleMillis)
        assertEquals(2, stage.cycles)
        assertEquals(2000, stage.totalMillis)

        val edge = ok(AlertPresetResolver.resolve(preset, "p", AlertHost.Edge))
        assertEquals(AlertKind.Edge, edge.kind)
        assertEquals(400, edge.cycleMillis)
        assertEquals(400, edge.totalMillis)
    }

    @Test
    fun `tier factor shortens the cycle`() {
        val fast = ok(AlertPresetResolver.resolve(preset, "p", AlertHost.Stage, tierFactor = 1.5f))
        assertEquals(1500, fast.cycleMillis)
    }

    @Test
    fun `unknown preset and missing host fail with reasons`() {
        val unknown = AlertPresetResolver.resolve(null, "x", AlertHost.Stage)
        assertTrue((unknown as PresetResolveResult.Failed).error is PresetResolveError.UnknownPreset)

        val missing = AlertPresetResolver.resolve(preset, "p", AlertHost.Ball)
        val error = (missing as PresetResolveResult.Failed).error
        assertTrue(error is PresetResolveError.MissingHost)
        assertEquals(AlertHost.Ball, (error as PresetResolveError.MissingHost).host)
    }

    @Test
    fun `resolve all skips failures and keeps seed`() {
        val plan = AlertPresetResolver.resolveAll(
            presets = mapOf("p" to preset),
            requests = listOf("p" to AlertHost.Stage, "missing" to AlertHost.Stage),
            seed = 9,
        )
        assertEquals(1, plan.layers.size)
        assertEquals(9, plan.seed)
        assertEquals(2000, plan.durationMillis)
        assertEquals(1, plan.stageLayers.size)
        assertTrue(plan.capsuleLayers.isEmpty())
    }

    @Test
    fun `empty plan has zero duration`() {
        val plan = AlertPresetResolver.resolveAll(emptyMap(), emptyList())
        assertTrue(plan.isEmpty)
        assertEquals(0, plan.durationMillis)
    }
}
