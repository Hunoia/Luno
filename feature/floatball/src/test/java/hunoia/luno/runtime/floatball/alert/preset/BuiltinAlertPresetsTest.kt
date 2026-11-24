package hunoia.luno.runtime.floatball.alert.preset

import hunoia.luno.config.model.AlertHost
import hunoia.luno.config.model.AlertKind
import hunoia.luno.config.model.AlertParticleShape
import hunoia.luno.config.model.matchesKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 内置预设门禁：数量、id 唯一、变体自洽、宿主 / 类型匹配、参数区间合理。
 *
 * 新增预设时这些约束会直接失败，避免「预设清单加了但参数块写错」这类静默问题。
 */
class BuiltinAlertPresetsTest {

    private val presets = BuiltinAlertPresets.all

    @Test
    fun `registry size matches the declared count`() {
        assertEquals(BuiltinAlertPresets.Count, presets.size)
        assertEquals(20, presets.size)
    }

    @Test
    fun `preset ids are unique and builtin`() {
        assertEquals(presets.size, presets.map { it.id }.toSet().size)
        assertTrue(presets.all { it.builtin })
        assertTrue(presets.all { it.id.startsWith("builtin_") })
        assertTrue(presets.all { it.name.isNotBlank() })
    }

    @Test
    fun `every variant matches its kind and host`() {
        presets.forEach { preset ->
            assertTrue("${preset.id} has no variants", preset.variants.isNotEmpty())
            assertTrue("${preset.id} has too many variants", preset.variants.size <= 2)
            preset.variants.forEach { variant ->
                assertTrue("${preset.id} kind/params mismatch", variant.matchesKind())
                assertTrue(
                    "${preset.id} host/kind mismatch: ${variant.host}/${variant.kind}",
                    variant.host.matches(variant.kind),
                )
            }
        }
    }

    @Test
    fun `timing and particle parameters stay in sane ranges`() {
        presets.forEach { preset ->
            preset.variants.forEach { variant ->
                assertTrue("${preset.id} baseMillis", variant.timing.baseMillis > 0)
                assertTrue("${preset.id} cycles", variant.timing.cycles in 1..20)
                assertTrue("${preset.id} speed", variant.timing.speedPercent in 10..300)
                variant.particle?.let { p ->
                    assertTrue("${preset.id} count", p.emitter.count in 1..600)
                    assertTrue("${preset.id} waves", p.emitter.waves in 1..20)
                    assertTrue("${preset.id} speed", p.emitter.speed >= 0f)
                    assertTrue("${preset.id} lifeShare", p.emitter.lifeShare > 0f && p.emitter.lifeShare <= 1f)
                    assertTrue("${preset.id} delaySpread", p.emitter.delaySpread in 0f..1f)
                    assertTrue("${preset.id} drag", p.motion.drag in 0f..1f)
                    assertTrue("${preset.id} wind", p.motion.wind in -1f..1f)
                    assertTrue("${preset.id} gravity", p.motion.gravity in 0f..3f)
                    assertTrue("${preset.id} alpha", p.look.alpha in 0f..1f)
                }
            }
        }
    }

    @Test
    fun `each slot has builtin coverage`() {
        val byHostKind = presets.flatMap { it.variants }.map { it.host to it.kind }.toSet()
        assertTrue(byHostKind.contains(AlertHost.Edge to AlertKind.Edge))
        assertTrue(byHostKind.contains(AlertHost.Stage to AlertKind.Particle))
        assertTrue(byHostKind.contains(AlertHost.Ball to AlertKind.Ball))
        assertTrue(byHostKind.contains(AlertHost.Capsule to AlertKind.Capsule))
        assertTrue(byHostKind.contains(AlertHost.Screen to AlertKind.Screen))
    }

    @Test
    fun `particle presets use the new shape vocabulary`() {
        val shapes = presets.flatMap { it.variants }.mapNotNull { it.particle?.look?.shape }.toSet()
        assertTrue(shapes.contains(AlertParticleShape.Dot))
        assertTrue(shapes.contains(AlertParticleShape.Line))
        assertTrue(shapes.contains(AlertParticleShape.Square))
        assertTrue(shapes.contains(AlertParticleShape.Star))
    }

    private fun AlertHost.matches(kind: AlertKind): Boolean = when (this) {
        AlertHost.Edge -> kind == AlertKind.Edge
        AlertHost.Ball -> kind == AlertKind.Ball
        AlertHost.Capsule -> kind == AlertKind.Capsule
        AlertHost.Screen -> kind == AlertKind.Screen
        AlertHost.Stage -> kind == AlertKind.Particle || kind == AlertKind.Reveal
    }
}