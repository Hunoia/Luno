package hunoia.luno.runtime.floatball.alert.engine

import hunoia.luno.config.model.AlertHost
import hunoia.luno.core.effect.AlertEffectHost
import hunoia.luno.core.effect.AlertEffectLayer
import hunoia.luno.core.effect.AlertEffectRequest
import hunoia.luno.core.effect.AlertOverrides
import hunoia.luno.core.effect.AlertColorOverride
import hunoia.luno.config.model.AlertKind
import hunoia.luno.config.model.AlertLibrary
import hunoia.luno.config.model.AlertPalette
import hunoia.luno.config.model.AlertSlotOverrides
import hunoia.luno.config.model.AlertSlotSettings
import hunoia.luno.config.model.AlertSlots
import hunoia.luno.config.model.NotificationAlertSettings
import hunoia.luno.runtime.floatball.alert.AnchorAlertStyle
import hunoia.luno.runtime.floatball.alert.CapsuleAlertStyle
import hunoia.luno.runtime.floatball.alert.EdgeAlertStyle
import hunoia.luno.runtime.floatball.alert.ScreenAlertStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 五槽 → 渲染计划 → 舞台 / 胶囊播放计划。 */
class AlertSlotPlanFactoryTest {

    private val library = AlertLibrary(
        palettes = listOf(
            AlertPalette(id = "custom_p", name = "P", colors = listOf(0xFF00FF00.toInt())),
        ),
    )

    @Test
    fun `enabled slots only and default presets are used`() {
        val alert = NotificationAlertSettings(
            slots = AlertSlots(
                edge = AlertSlotSettings(enabled = true),
                particle = AlertSlotSettings(enabled = false),
                screen = AlertSlotSettings(enabled = true),
            ),
        )
        val plan = AlertSlotPlanFactory.build(alert, library, seed = 7)
        assertEquals(2, plan.layers.size)
        assertEquals(AlertKind.Edge, plan.layers[0].kind)
        assertEquals("builtin_edge_glow", plan.layers[0].presetId)
        assertEquals(AlertKind.Screen, plan.layers[1].kind)
        assertEquals("builtin_screen_flash", plan.layers[1].presetId)
        assertEquals(7, plan.seed)
    }

    @Test
    fun `ball slot can fall back to a capsule host`() {
        val alert = NotificationAlertSettings(
            slots = AlertSlots(
                ball = AlertSlotSettings(
                    enabled = true,
                    presetId = "builtin_capsule_pulse",
                ),
            ),
        )
        val plan = AlertSlotPlanFactory.build(alert, library)
        val layer = plan.layers.single()
        assertEquals(AlertHost.Capsule, layer.host)
        assertEquals(AlertKind.Capsule, layer.kind)
        val playPlan = plan.toPlayPlan()
        assertEquals(CapsuleAlertStyle.Pulse, playPlan.capsuleLayers.single().style)
    }

    @Test
    fun `overrides drive speed intensity and palette`() {
        val alert = NotificationAlertSettings(
            slots = AlertSlots(
                edge = AlertSlotSettings(
                    enabled = true,
                    presetId = "builtin_edge_flow",
                    overrides = AlertSlotOverrides(
                        paletteId = "custom_p",
                        intensityPercent = 50,
                        speedPercent = 200,
                    ),
                ),
            ),
        )
        val plan = AlertSlotPlanFactory.build(alert, library)
        val layer = plan.layers.single()
        assertEquals(0.5f, layer.intensity, 0.001f)
        assertEquals(800, layer.cycleMillis) // 1600 基准 ÷ 2
        assertNotNull(layer.palette)
        assertEquals(listOf(0xFF00FF00.toInt()), layer.palette?.colors)
        val playPlan = plan.toPlayPlan()
        val playLayer = playPlan.stageLayers.single()
        assertEquals(EdgeAlertStyle.EdgeFlow, playLayer.style)
        assertEquals(0.275f, playLayer.brightness, 0.001f) // 0.55 × 0.5
    }

    @Test
    fun `missing preset and unusable host are skipped`() {
        val alert = NotificationAlertSettings(
            slots = AlertSlots(
                edge = AlertSlotSettings(enabled = true, presetId = "missing"),
                screen = AlertSlotSettings(enabled = true, presetId = "builtin_particle_rain"),
            ),
        )
        val plan = AlertSlotPlanFactory.build(alert, library)
        assertTrue(plan.isEmpty)
    }

    @Test
    fun `adapter maps ball and screen presets to render styles`() {
        val ball = AlertSlotPlanFactory.build(
            NotificationAlertSettings(
                slots = AlertSlots(ball = AlertSlotSettings(enabled = true)),
            ),
            library,
        ).toPlayPlan()
        assertEquals(AnchorAlertStyle.Ring, ball.stageLayers.single().style)

        val ripple = AlertSlotPlanFactory.build(
            NotificationAlertSettings(
                slots = AlertSlots(ball = AlertSlotSettings(enabled = true, presetId = "builtin_ball_ripple")),
            ),
            library,
        ).toPlayPlan()
        assertEquals(AnchorAlertStyle.Ripple, ripple.stageLayers.single().style)

        val screen = AlertSlotPlanFactory.build(
            NotificationAlertSettings(
                slots = AlertSlots(screen = AlertSlotSettings(enabled = true, presetId = "builtin_screen_vignette")),
            ),
            library,
        ).toPlayPlan()
        assertEquals(ScreenAlertStyle.Vignette, screen.stageLayers.single().style)
    }

    @Test
    fun `external request builds the same plan with layer overrides`() {
        val request = AlertEffectRequest(
            id = "req-1",
            layers = listOf(
                AlertEffectLayer(
                    presetId = "builtin_edge_ripple",
                    host = AlertEffectHost.Edge,
                    overrides = AlertOverrides(
                        color = AlertColorOverride(colors = listOf(0xFF123456.toInt())),
                        intensity = 1.5f,
                        speed = 0.5f,
                    ),
                ),
            ),
            speed = 1.5f,
            seed = 42,
        )
        val plan = AlertSlotPlanFactory.buildFromRequest(request, library)
        assertEquals(42, plan.seed)
        val layer = plan.layers.single()
        assertEquals("builtin_edge_ripple", layer.presetId)
        assertEquals(1733, layer.cycleMillis) // 1300 ÷ (request 1.5 × layer 0.5)
        assertEquals(1.5f, layer.intensity, 0.001f)
        assertEquals(listOf(0xFF123456.toInt()), layer.palette?.colors)
    }

    @Test
    fun `path slot uses the particle engine with a path style`() {
        val plan = AlertSlotPlanFactory.build(
            NotificationAlertSettings(
                slots = AlertSlots(path = AlertSlotSettings(enabled = true)),
            ),
            library,
        ).toPlayPlan()
        val layer = plan.stageLayers.single()
        assertEquals(hunoia.luno.runtime.floatball.alert.ParticleAlertStyle.Path, layer.style)
        assertTrue(layer.spec is hunoia.luno.runtime.floatball.alert.ParticlesSpec)
    }
}