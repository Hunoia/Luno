package hunoia.luno.runtime.floatball.alert.engine

import hunoia.luno.config.model.AlertColorRef
import hunoia.luno.config.model.AlertFamily
import hunoia.luno.config.model.AlertHost
import hunoia.luno.config.model.AlertLayer
import hunoia.luno.config.model.AlertLibrary
import hunoia.luno.config.model.AlertLimits
import hunoia.luno.config.model.AlertPalette
import hunoia.luno.config.model.AlertPlaybackPolicy
import hunoia.luno.config.model.AlertPreset
import hunoia.luno.config.model.AlertRenderBackend
import hunoia.luno.config.model.AlertTiming
import hunoia.luno.config.model.CapsuleParams
import hunoia.luno.config.model.CapsuleShape
import hunoia.luno.config.model.CoverParams
import hunoia.luno.config.model.EdgeLightning
import hunoia.luno.config.model.EdgeParams
import hunoia.luno.config.model.EdgeSweep
import hunoia.luno.config.model.EdgeWave
import hunoia.luno.config.model.CapsuleSweep
import hunoia.luno.config.model.ParticleCluster
import hunoia.luno.config.model.EdgeShape
import hunoia.luno.config.model.NotificationAlertSettings
import hunoia.luno.config.model.ParticleArea
import hunoia.luno.config.model.ParticleDirection
import hunoia.luno.config.model.ParticleEmitter
import hunoia.luno.config.model.ParticleParams
import hunoia.luno.config.model.RevealParams
import hunoia.luno.config.model.RingParams
import hunoia.luno.config.model.WashParams
import hunoia.luno.core.effect.AlertContent
import hunoia.luno.core.effect.AlertEffectRequest
import hunoia.luno.runtime.floatball.alert.AlertRenderStyle
import hunoia.luno.runtime.floatball.alert.CapsuleSpec
import hunoia.luno.runtime.floatball.alert.EdgeSpec
import hunoia.luno.runtime.floatball.alert.ParticlesSpec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 一个预设 → 渲染计划 → 舞台 / 胶囊播放计划。 */
class AlertPlanFactoryTest {

    private fun layer(
        id: String,
        family: AlertFamily,
        enabled: Boolean = true,
        baseMillis: Int = 1000,
        count: Int = 40,
    ): AlertLayer = AlertLayer(
        id = id,
        enabled = enabled,
        family = family,
        timing = AlertTiming(baseMillis = baseMillis),
        edge = EdgeParams().takeIf { family == AlertFamily.Edge },
        ring = RingParams().takeIf { family == AlertFamily.Ring },
        capsule = CapsuleParams().takeIf { family == AlertFamily.Capsule },
        wash = WashParams().takeIf { family == AlertFamily.Wash },
        cover = CoverParams().takeIf { family == AlertFamily.Cover },
        particle = ParticleParams(emitter = ParticleEmitter(count = count))
            .takeIf { family == AlertFamily.Particles },
        reveal = RevealParams().takeIf { family == AlertFamily.Reveal },
    )

    private fun preset(
        id: String = "p",
        vararg families: AlertFamily,
    ): AlertPreset = AlertPreset(
        id = id,
        name = id,
        layers = families.mapIndexed { index, family -> layer("l$index", family) },
    )

    private val library = AlertLibrary(
        palettes = listOf(
            AlertPalette(id = "custom_p", name = "P", colors = listOf(0xFF00FF00.toInt())),
        ),
        presets = listOf(
            preset("combo", AlertFamily.Edge, AlertFamily.Particles, AlertFamily.Ring),
            preset("other", AlertFamily.Cover),
        ),
    )

    @Test
    fun `no preset or disabled master switch produces an empty plan`() {
        val off = AlertPlanFactory.build(NotificationAlertSettings(), library)
        assertTrue(off.isEmpty)

        val onButNoPreset = AlertPlanFactory.build(
            NotificationAlertSettings(enabled = true),
            library,
        )
        assertTrue(onButNoPreset.isEmpty)

        val disabled = AlertPlanFactory.build(
            NotificationAlertSettings(enabled = false, presetId = "combo"),
            library,
        )
        assertTrue(disabled.isEmpty)
    }

    @Test
    fun `preset layers keep order and derive hosts`() {
        val plan = AlertPlanFactory.build(
            NotificationAlertSettings(enabled = true, presetId = "combo"),
            library,
            seed = 7,
        )
        assertEquals(listOf("l0", "l1", "l2"), plan.layers.map { it.layerId })
        assertEquals(AlertHost.Edge, plan.layers[0].host)
        assertEquals(AlertHost.Stage, plan.layers[1].host)
        assertEquals(AlertHost.Ball, plan.layers[2].host)
        assertEquals(7, plan.seed)
        assertEquals(3, plan.stageLayers.size)
        assertTrue(plan.capsuleLayers.isEmpty())
    }

    @Test
    fun `global speed and intensity apply to every layer`() {
        val plan = AlertPlanFactory.build(
            NotificationAlertSettings(
                enabled = true,
                presetId = "combo",
                intensityPercent = 50,
                speedPercent = 200,
            ),
            library,
        )
        plan.layers.forEach { layer ->
            assertEquals(500, layer.cycleMillis)
            assertEquals(0.5f, layer.intensity, 0.001f)
        }
        val brightness = plan.toPlayPlan().stageLayers.first().brightness
        assertEquals(0.275f, brightness, 0.001f) // 0.55 × 0.5
    }

    @Test
    fun `layer opacity multiplies the playback brightness`() {
        val preset = AlertPreset(
            id = "dim",
            name = "dim",
            layers = listOf(layer("l0", AlertFamily.Edge).copy(opacity = 0.5f)),
        )
        val plan = AlertPlanFactory.buildFromPreset(preset, preset.id, library).toPlayPlan()
        assertEquals(0.275f, plan.stageLayers.single().brightness, 0.001f)
    }

    @Test
    fun `preset palette overrides layer color`() {
        val preset = AlertPreset(
            id = "pal",
            name = "pal",
            paletteId = "custom_p",
            layers = listOf(layer("l0", AlertFamily.Edge).copy(color = AlertColorRef())),
        )
        val plan = AlertPlanFactory.buildFromPreset(
            preset = preset,
            presetId = preset.id,
            library = AlertLibrary(
                palettes = listOf(
                    AlertPalette(id = "custom_p", name = "P", colors = listOf(0xFF00FF00.toInt())),
                ),
            ),
        )
        assertNotNull(plan.layers.single().palette)
        assertEquals(listOf(0xFF00FF00.toInt()), plan.layers.single().palette?.colors)
    }

    @Test
    fun `capsule families are separated from stage layers`() {
        // 胶囊窗一次只能画一层：第二个胶囊层被上限丢掉，舞台层不受影响。
        val preset = preset("cap", AlertFamily.Capsule, AlertFamily.Edge, AlertFamily.Ring)
        val plan = AlertPlanFactory.buildFromPreset(preset, preset.id, library).toPlayPlan()
        assertEquals(listOf(AlertRenderStyle.Capsule), plan.capsuleLayers.map { it.style })
        assertEquals(
            listOf(AlertRenderStyle.Edge, AlertRenderStyle.Ring),
            plan.stageLayers.map { it.style },
        )
    }

    @Test
    fun `every family maps to its own renderer`() {
        AlertFamily.entries.forEach { family ->
            val preset = preset(family.name.lowercase(), family)
            val plan = AlertPlanFactory.buildFromPreset(
                preset = preset,
                presetId = preset.id,
                library = library,
                // 揭示层需要内容才会出计划。
                content = AlertContent(title = "t", body = "b"),
            ).toPlayPlan()
            assertEquals(family.name, family.toRenderStyle().name)
            assertTrue("$family produced no layer", plan.layers.isNotEmpty())
        }
    }

    @Test
    fun `edge and capsule shapes ride their specs`() {
        val preset = AlertPreset(
            id = "shapes",
            name = "shapes",
            layers = listOf(
                layer("e", AlertFamily.Edge).copy(
                    edge = EdgeParams(
                        shape = EdgeShape.Lightning,
                        lightning = EdgeLightning(
                            bolts = 4,
                            flashes = 2,
                            window = 0.5f,
                            reachPercent = 60,
                        ),
                        wave = EdgeWave(waveWidthDp = 12f),
                        sweep = EdgeSweep(tailPercent = 20),
                    ),
                ),
                layer("c", AlertFamily.Capsule).copy(
                    capsule = CapsuleParams(
                        shape = CapsuleShape.Sweep,
                        sweep = CapsuleSweep(fill = true),
                    ),
                ),
            ),
        )
        val plan = AlertPlanFactory.buildFromPreset(preset, preset.id, library).toPlayPlan()
        val edge = plan.stageLayers.single().spec as EdgeSpec
        assertEquals(EdgeShape.Lightning, edge.shape)
        assertEquals(4, edge.lightning.bolts)
        assertEquals(2, edge.lightning.flashes)
        assertEquals(0.5f, edge.lightning.window, 0.001f)
        assertEquals(60, edge.lightning.reachPercent)
        assertEquals(12f, edge.wave.waveWidthDp, 0.001f)
        assertEquals(20, edge.sweep.tailPercent)
        val capsule = plan.capsuleLayers.single().spec as CapsuleSpec
        assertEquals(CapsuleShape.Sweep, capsule.shape)
        assertTrue(capsule.sweep.fill)
    }

    @Test
    fun `renderer backend rides the edge spec`() {
        val preset = AlertPreset(
            id = "backend",
            name = "backend",
            layers = listOf(
                layer("b", AlertFamily.Edge).copy(
                    edge = EdgeParams(backend = AlertRenderBackend.Cpu),
                ),
            ),
        )
        val plan = AlertPlanFactory.buildFromPreset(preset, preset.id, library).toPlayPlan()
        assertEquals(
            AlertRenderBackend.Cpu,
            (plan.stageLayers.single().spec as EdgeSpec).backend,
        )
    }

    @Test
    fun `firework burst fields ride the particle spec`() {
        val preset = AlertPreset(
            id = "fw",
            name = "fw",
            layers = listOf(
                layer("p", AlertFamily.Particles).copy(
                    particle = ParticleParams(
                        emitter = ParticleEmitter(
                            area = ParticleArea.EdgePerimeter,
                            direction = ParticleDirection.Inward,
                        ),
                        cluster = ParticleCluster(
                            anchors = 3,
                            launchHeight = 0.55f,
                            launchShare = 0.4f,
                        ),
                    ),
                ),
            ),
        )
        val plan = AlertPlanFactory.buildFromPreset(preset, preset.id, library).toPlayPlan()
        val spec = plan.stageLayers.single().spec as ParticlesSpec
        assertEquals(3, spec.params.cluster?.anchors)
        assertEquals(ParticleDirection.Inward, spec.params.emitter.direction)
        assertEquals(0.55f, spec.params.cluster?.launchHeight ?: 0f, 0.001f)
        assertEquals(0.4f, spec.params.cluster?.launchShare ?: 0f, 0.001f)
    }

    @Test
    fun `layer delay rides startAt and extends the plan duration`() {
        val preset = AlertPreset(
            id = "delay",
            name = "delay",
            layers = listOf(
                layer("a", AlertFamily.Edge, baseMillis = 1000),
                layer("b", AlertFamily.Edge, baseMillis = 1000).copy(
                    timing = AlertTiming(baseMillis = 1000, delayMillis = 500),
                ),
            ),
        )
        val plan = AlertPlanFactory.buildFromPreset(preset, preset.id, library).toPlayPlan()
        val delayed = plan.stageLayers.single { it.layerId == "b" }
        assertEquals(500, delayed.startAt)
        assertEquals(1500, plan.durationMillis)
        assertEquals(0, plan.stageLayers.single { it.layerId == "a" }.startAt)
    }

    @Test
    fun `random mode picks one of the library presets`() {
        val settings = NotificationAlertSettings(enabled = true, randomize = true)
        val picks = (0 until 20).map { seed ->
            AlertPlanFactory.build(settings, library, seed = seed).layers.firstOrNull()?.presetId
        }.toSet()
        assertTrue(picks.all { it != null })
        assertEquals(setOf("combo", "other"), picks)
    }

    @Test
    fun `random mode with an empty library plays nothing`() {
        val plan = AlertPlanFactory.build(
            NotificationAlertSettings(enabled = true, randomize = true),
            AlertLibrary(),
            seed = 3,
        )
        assertTrue(plan.isEmpty)
    }

    @Test
    fun `unknown preset reports a skip reason`() {
        val plan = AlertPlanFactory.build(
            NotificationAlertSettings(enabled = true, presetId = "missing"),
            library,
        )
        assertTrue(plan.isEmpty)
        assertTrue(plan.skipped.single() is PresetResolveError.UnknownPreset)
    }

    @Test
    fun `preset with every layer disabled reports empty`() {
        val preset = AlertPreset(
            id = "off",
            name = "off",
            layers = listOf(layer("l0", AlertFamily.Edge, enabled = false)),
        )
        val plan = AlertPlanFactory.buildFromPreset(preset, preset.id, library)
        assertTrue(plan.isEmpty)
        assertTrue(plan.skipped.single() is PresetResolveError.EmptyPreset)
    }

    @Test
    fun `external request uses preset reference and global overrides`() {
        val request = AlertEffectRequest(
            id = "req-1",
            presetId = "combo",
            speed = 2f,
            intensity = 1.5f,
            seed = 42,
        )
        val plan = AlertPlanFactory.buildFromRequest(request, library)
        assertEquals(42, plan.seed)
        assertEquals(3, plan.layers.size)
        plan.layers.forEach {
            assertEquals(500, it.cycleMillis)
            assertEquals(1.5f, it.intensity, 0.001f)
        }
    }

    @Test
    fun `particle budget is shared across layers`() {
        val preset = AlertPreset(
            id = "budget",
            name = "budget",
            layers = listOf(
                layer("a", AlertFamily.Particles, count = 400),
                layer("b", AlertFamily.Particles, count = 400),
            ),
        )
        val plan = AlertPlanFactory.buildFromPreset(preset, preset.id, library)
        assertEquals(
            AlertLimits.ParticleBudget,
            plan.layers.sumOf { it.particle?.emitter?.count ?: 0 },
        )
        assertEquals(400, plan.layers[0].particle?.emitter?.count)
        assertEquals(200, plan.layers[1].particle?.emitter?.count)
    }

    @Test
    fun `setting order is preserved and onlyLayer filters a preview`() {
        val preset = preset("two", AlertFamily.Edge, AlertFamily.Ring)
        val plan = AlertPlanFactory.buildFromPreset(preset, preset.id, library)
        assertEquals("l1", plan.onlyLayer("l1").layers.single().layerId)
        assertEquals(2, plan.onlyLayer(null).layers.size)
    }

    @Test
    fun `preset playback policy rides the plan`() {
        val preset = preset("policy", AlertFamily.Edge)
            .copy(playbackPolicy = AlertPlaybackPolicy.Queue)
        val stage = AlertPlanFactory.buildFromPreset(preset, preset.id, library)
        assertEquals(AlertPlaybackPolicy.Queue, stage.playbackPolicy)
        assertEquals(AlertPlaybackPolicy.Queue, stage.toPlayPlan().playbackPolicy)
        assertEquals(
            AlertPlaybackPolicy.Skip,
            AlertPlanFactory.buildFromPreset(preset("plain", AlertFamily.Edge), "plain", library)
                .playbackPolicy,
        )
    }
}
