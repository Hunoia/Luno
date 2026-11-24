package hunoia.luno.runtime.floatball.alert.engine

import hunoia.luno.config.model.AlertColorRef
import hunoia.luno.config.model.AlertHost
import hunoia.luno.config.model.AlertKind
import hunoia.luno.config.model.AlertLayer
import hunoia.luno.config.model.AlertLibrary
import hunoia.luno.config.model.AlertLimits
import hunoia.luno.config.model.AlertPalette
import hunoia.luno.config.model.AlertPlaybackPolicy
import hunoia.luno.config.model.AlertPreset
import hunoia.luno.config.model.AlertTiming
import hunoia.luno.config.model.BallParams
import hunoia.luno.config.model.EdgeParams
import hunoia.luno.config.model.NotificationAlertSettings
import hunoia.luno.config.model.ParticleEmitter
import hunoia.luno.config.model.ParticleParams
import hunoia.luno.core.effect.AlertEffectRequest
import hunoia.luno.runtime.floatball.alert.AnchorAlertStyle
import hunoia.luno.runtime.floatball.alert.CapsuleAlertStyle
import hunoia.luno.runtime.floatball.alert.EdgeAlertStyle
import hunoia.luno.runtime.floatball.alert.ParticleAlertStyle
import hunoia.luno.runtime.floatball.alert.ScreenAlertStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 一个预设 → 渲染计划 → 舞台 / 胶囊播放计划。 */
class AlertPlanFactoryTest {

    private fun layer(
        id: String,
        kind: AlertKind,
        enabled: Boolean = true,
        baseMillis: Int = 1000,
        count: Int = 40,
    ): AlertLayer = when (kind) {
        AlertKind.Particle -> AlertLayer(
            id = id,
            enabled = enabled,
            kind = kind,
            timing = AlertTiming(baseMillis = baseMillis),
            particle = ParticleParams(emitter = ParticleEmitter(count = count)),
        )
        AlertKind.Edge -> AlertLayer(
            id = id,
            enabled = enabled,
            kind = kind,
            timing = AlertTiming(baseMillis = baseMillis),
            edge = EdgeParams(),
        )
        AlertKind.Ball -> AlertLayer(
            id = id,
            enabled = enabled,
            kind = kind,
            timing = AlertTiming(baseMillis = baseMillis),
            ball = BallParams(),
        )
        AlertKind.Capsule -> AlertLayer(
            id = id,
            enabled = enabled,
            kind = kind,
            timing = AlertTiming(baseMillis = baseMillis),
            capsule = hunoia.luno.config.model.CapsuleParams(),
        )
        AlertKind.Screen -> AlertLayer(
            id = id,
            enabled = enabled,
            kind = kind,
            timing = AlertTiming(baseMillis = baseMillis),
            screen = hunoia.luno.config.model.ScreenParams(),
        )
        AlertKind.Reveal -> AlertLayer(
            id = id,
            enabled = enabled,
            kind = kind,
            timing = AlertTiming(baseMillis = baseMillis),
            reveal = hunoia.luno.config.model.RevealParams(),
        )
    }

    private fun preset(
        id: String = "p",
        vararg kinds: AlertKind,
    ): AlertPreset = AlertPreset(
        id = id,
        name = id,
        builtin = false,
        layers = kinds.mapIndexed { index, kind -> layer("l$index", kind) },
    )

    private val library = AlertLibrary(
        palettes = listOf(
            AlertPalette(id = "custom_p", name = "P", colors = listOf(0xFF00FF00.toInt())),
        ),
        presets = listOf(preset("combo", AlertKind.Edge, AlertKind.Particle, AlertKind.Ball)),
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
    fun `preset palette overrides layer color`() {
        val preset = AlertPreset(
            id = "pal",
            name = "pal",
            paletteId = "custom_p",
            layers = listOf(layer("l0", AlertKind.Edge).copy(color = AlertColorRef())),
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
    fun `capsule layers are separated from stage layers`() {
        val preset = preset("cap", AlertKind.Capsule, AlertKind.Edge)
        val plan = AlertPlanFactory.buildFromPreset(preset, preset.id, library).toPlayPlan()
        assertEquals(1, plan.capsuleLayers.size)
        assertEquals(CapsuleAlertStyle.Pulse, plan.capsuleLayers.single().style)
        assertEquals(EdgeAlertStyle.EdgeGlow, plan.stageLayers.single().style)
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
            layers = listOf(layer("l0", AlertKind.Edge, enabled = false)),
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
                layer("a", AlertKind.Particle, count = 400),
                layer("b", AlertKind.Particle, count = 400),
            ),
        )
        val plan = AlertPlanFactory.buildFromPreset(preset, preset.id, library)
        assertEquals(AlertLimits.ParticleBudget, plan.layers.sumOf { it.particle?.emitter?.count ?: 0 })
        assertEquals(400, plan.layers[0].particle?.emitter?.count)
        assertEquals(200, plan.layers[1].particle?.emitter?.count)
    }

    @Test
    fun `setting order is preserved and onlyLayer filters a preview`() {
        val preset = preset("two", AlertKind.Edge, AlertKind.Ball)
        val plan = AlertPlanFactory.buildFromPreset(preset, preset.id, library)
        assertEquals("l1", plan.onlyLayer("l1").layers.single().layerId)
        assertEquals(2, plan.onlyLayer(null).layers.size)
    }

    @Test
    fun `preset playback policy rides the plan`() {
        val preset = preset("policy", AlertKind.Edge).copy(playbackPolicy = AlertPlaybackPolicy.Queue)
        val stage = AlertPlanFactory.buildFromPreset(preset, preset.id, library)
        assertEquals(AlertPlaybackPolicy.Queue, stage.playbackPolicy)
        assertEquals(AlertPlaybackPolicy.Queue, stage.toPlayPlan().playbackPolicy)
        // 未声明策略的旧预设 → 跳过（与 v4 原有「在播不播」一致）。
        assertEquals(
            AlertPlaybackPolicy.Skip,
            AlertPlanFactory.buildFromPreset(preset("plain", AlertKind.Edge), "plain", library)
                .playbackPolicy,
        )
    }

    @Test
    fun `builtin ball and screen presets map to their own renderers`() {
        assertEquals(
            AnchorAlertStyle.Ring,
            AlertPlanFactory.buildFromPreset(
                hunoia.luno.runtime.floatball.alert.preset.BuiltinAlertPresets.find("builtin_ball_ring"),
                "builtin_ball_ring",
                library,
            ).toPlayPlan().stageLayers.single().style,
        )
        assertEquals(
            AnchorAlertStyle.Orbit3D,
            AlertPlanFactory.buildFromPreset(
                hunoia.luno.runtime.floatball.alert.preset.BuiltinAlertPresets.find("builtin_ball_orbit"),
                "builtin_ball_orbit",
                library,
            ).toPlayPlan().stageLayers.single().style,
        )
        assertEquals(
            ScreenAlertStyle.Vignette,
            AlertPlanFactory.buildFromPreset(
                hunoia.luno.runtime.floatball.alert.preset.BuiltinAlertPresets.find("builtin_screen_vignette"),
                "builtin_screen_vignette",
                library,
            ).toPlayPlan().stageLayers.single().style,
        )
        assertEquals(
            ScreenAlertStyle.CenterBlackout,
            AlertPlanFactory.buildFromPreset(
                hunoia.luno.runtime.floatball.alert.preset.BuiltinAlertPresets.find("builtin_screen_blackout"),
                "builtin_screen_blackout",
                library,
            ).toPlayPlan().stageLayers.single().style,
        )
        assertEquals(
            ScreenAlertStyle.CrtOff,
            AlertPlanFactory.buildFromPreset(
                hunoia.luno.runtime.floatball.alert.preset.BuiltinAlertPresets.find("builtin_screen_crt_off"),
                "builtin_screen_crt_off",
                library,
            ).toPlayPlan().stageLayers.single().style,
        )
        assertEquals(
            ScreenAlertStyle.Shatter,
            AlertPlanFactory.buildFromPreset(
                hunoia.luno.runtime.floatball.alert.preset.BuiltinAlertPresets.find("builtin_screen_shatter"),
                "builtin_screen_shatter",
                library,
            ).toPlayPlan().stageLayers.single().style,
        )
        assertEquals(
            ScreenAlertStyle.Blinds,
            AlertPlanFactory.buildFromPreset(
                hunoia.luno.runtime.floatball.alert.preset.BuiltinAlertPresets.find("builtin_screen_blinds"),
                "builtin_screen_blinds",
                library,
            ).toPlayPlan().stageLayers.single().style,
        )
        assertEquals(
            ScreenAlertStyle.Iris,
            AlertPlanFactory.buildFromPreset(
                hunoia.luno.runtime.floatball.alert.preset.BuiltinAlertPresets.find("builtin_screen_iris"),
                "builtin_screen_iris",
                library,
            ).toPlayPlan().stageLayers.single().style,
        )
        assertEquals(
            ParticleAlertStyle.TopRain,
            AlertPlanFactory.buildFromPreset(
                hunoia.luno.runtime.floatball.alert.preset.BuiltinAlertPresets.find("builtin_particle_rain"),
                "builtin_particle_rain",
                library,
            ).toPlayPlan().stageLayers.single().style,
        )
        assertEquals(
            EdgeAlertStyle.Comet,
            AlertPlanFactory.buildFromPreset(
                hunoia.luno.runtime.floatball.alert.preset.BuiltinAlertPresets.find("builtin_edge_comet"),
                "builtin_edge_comet",
                library,
            ).toPlayPlan().stageLayers.single().style,
        )
    }
}
