package hunoia.luno.runtime.floatball.alert.preset

import hunoia.luno.config.model.AlertHost
import hunoia.luno.config.model.AlertKind
import hunoia.luno.config.model.AlertLayerCategory
import hunoia.luno.config.model.AlertParticleShape
import hunoia.luno.config.model.category
import hunoia.luno.config.model.host
import hunoia.luno.config.model.matchesKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 内置预设门禁：全部单层素材、id 唯一、层自洽、宿主由 kind 推导、参数区间合理。
 *
 * 内置只提供「一层」的可信观感；组合与跨类别叠加由用户预设负责。
 */
class BuiltinAlertPresetsTest {

    private val presets = BuiltinAlertPresets.all
    private val layers = presets.flatMap { it.layers }

    @Test
    fun `registry size matches the declared count`() {
        assertEquals(BuiltinAlertPresets.Count, presets.size)
        assertEquals(18, presets.size)
    }

    @Test
    fun `preset ids are unique and builtin`() {
        assertEquals(presets.size, presets.map { it.id }.toSet().size)
        assertTrue(presets.all { it.builtin })
        assertTrue(presets.all { it.id.startsWith("builtin_") })
        assertTrue(presets.all { it.name.isNotBlank() })
    }

    @Test
    fun `builtin presets are single-layer material`() {
        presets.forEach { preset ->
            assertEquals("${preset.id} should be a single layer", 1, preset.layers.size)
            assertTrue("${preset.id} layer must be enabled", preset.layers.single().enabled)
            assertTrue("${preset.id} has no palette-level override", preset.paletteId == null)
            assertTrue("${preset.id} should validate", preset.validate().isEmpty())
        }
    }

    @Test
    fun `every layer matches its kind`() {
        layers.forEach { layer ->
            assertTrue("${layer.id} kind/params mismatch", layer.matchesKind())
            assertTrue("${layer.id} host derived from kind", layer.kind.host is AlertHost)
        }
    }

    @Test
    fun `timing and particle parameters stay in sane ranges`() {
        layers.forEach { layer ->
            assertTrue("${layer.id} baseMillis", layer.timing.baseMillis > 0)
            assertTrue("${layer.id} cycles", layer.timing.cycles in 1..20)
            assertTrue("${layer.id} speed", layer.timing.speedPercent in 10..300)
            layer.particle?.let { p ->
                assertTrue("${layer.id} count", p.emitter.count in 1..600)
                assertTrue("${layer.id} waves", p.emitter.waves in 1..20)
                assertTrue("${layer.id} speed", p.emitter.speed >= 0f)
                assertTrue("${layer.id} lifeShare", p.emitter.lifeShare > 0f && p.emitter.lifeShare <= 1f)
                assertTrue("${layer.id} delaySpread", p.emitter.delaySpread in 0f..1f)
                assertTrue("${layer.id} drag", p.motion.drag in 0f..1f)
                assertTrue("${layer.id} wind", p.motion.wind in -1f..1f)
                assertTrue("${layer.id} gravity", p.motion.gravity in 0f..3f)
                assertTrue("${layer.id} alpha", p.look.alpha in 0f..1f)
                assertTrue("${layer.id} size", p.look.sizeDp in 0.5f..32f)
            }
        }
    }

    @Test
    fun `every category has builtin coverage`() {
        val categories = layers.map { it.kind.category }.toSet()
        AlertLayerCategory.entries.forEach { category ->
            assertTrue("missing builtin for $category", category in categories)
        }
        val skins = layers.map { it.kind }.toSet()
        assertTrue(skins.contains(AlertKind.Capsule))
        assertTrue(skins.contains(AlertKind.Reveal))
    }

    @Test
    fun `particle presets use the new shape vocabulary`() {
        val shapes = layers.mapNotNull { it.particle?.look?.shape }.toSet()
        assertTrue(shapes.contains(AlertParticleShape.Dot))
        assertTrue(shapes.contains(AlertParticleShape.Line))
        assertTrue(shapes.contains(AlertParticleShape.Square))
        assertTrue(shapes.contains(AlertParticleShape.Star))
    }

    @Test
    fun `named forms map to distinct patterns`() {
        // 精简后的内置素材是「每类 1–3 个代表」：同一族内不重复形态，也不要求覆盖全部枚举。
        val edge = presets.filter { it.layers.single().kind == AlertKind.Edge }
            .map { it.layers.single().edge!!.pattern }
        assertEquals(3, edge.size)
        assertEquals(edge.size, edge.toSet().size)
        val ball = presets.filter { it.layers.single().kind == AlertKind.Ball }
            .map { it.layers.single().ball!!.pattern }
        assertEquals(3, ball.size)
        assertEquals(ball.size, ball.toSet().size)
        assertTrue(ball.contains(hunoia.luno.config.model.BallPattern.Orbit3D))
        val capsule = presets.filter { it.layers.single().kind == AlertKind.Capsule }
            .map { it.layers.single().capsule!!.pattern }
        assertEquals(3, capsule.size)
        assertEquals(capsule.size, capsule.toSet().size)
    }
}
