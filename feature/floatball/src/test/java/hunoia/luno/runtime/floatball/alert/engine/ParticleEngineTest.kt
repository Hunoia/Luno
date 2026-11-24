package hunoia.luno.runtime.floatball.alert.engine

import hunoia.luno.config.model.ParticleArea
import hunoia.luno.config.model.ParticleEmitter
import hunoia.luno.config.model.ParticleMotion
import hunoia.luno.config.model.ParticlePath
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 粒子引擎的物理口径：重力加速、阻力终端速度、风向、轨道/螺旋、反弹、路径端点与可复现性。
 * 这些都是纯函数，调参回归先在这里卡住，再上真机看观感。
 */
class ParticleEngineTest {

    private val frame = ParticleFrame(aspect = 0.5f)
    private val anchor = NormPoint(0.25f, 0.5f)

    private fun state(x: Float, y: Float, vx: Float = 0f, vy: Float = 0f, life: Float = 1f) =
        ParticleState(
            x = x, y = y, vx = vx, vy = vy, age = 0f, life = life,
            delay = 0f, spinDeg = 0f, phase = 0f, seed = 7,
        )

    private fun step(state: ParticleState, motion: ParticleMotion, seconds: Float, frames: Int = 30): ParticleState {
        var s = state
        val dt = seconds / frames
        repeat(frames) { s = advanceParticle(s, dt, motion, frame, anchor) }
        return s
    }

    @Test
    fun `gravity pulls particles down`() {
        val start = state(0.2f, 0.1f)
        val end = step(start, ParticleMotion(gravity = 1f, drag = 0f, sway = 0f), seconds = 0.5f)
        assertTrue("应向下移动", end.y > start.y)
        assertTrue("速度应向下", end.vy > 0f)
        assertEquals(0.2f, end.x, 0.02f)
    }

    @Test
    fun `drag reduces terminal speed`() {
        val motionFree = ParticleMotion(gravity = 1f, drag = 0f, sway = 0f)
        val motionDrag = ParticleMotion(gravity = 1f, drag = 0.6f, sway = 0f)
        val a = step(state(0.25f, 0.2f), motionFree, seconds = 0.8f)
        val b = step(state(0.25f, 0.2f), motionDrag, seconds = 0.8f)
        assertTrue("有阻力时速度更小", b.vy < a.vy)
        assertTrue("终端速度为正", terminalVelocity(1f, 0.6f) > 0f)
    }

    @Test
    fun `wind pushes horizontally in both directions`() {
        val right = step(state(0.25f, 0.5f), ParticleMotion(gravity = 0f, sway = 0f, wind = 0.8f), 0.5f)
        val left = step(state(0.25f, 0.5f), ParticleMotion(gravity = 0f, sway = 0f, wind = -0.8f), 0.5f)
        assertTrue("正风向右", right.x > 0.25f)
        assertTrue("负风向左", left.x < 0.25f)
    }

    @Test
    fun `orbit keeps radius and spiral pulls inward`() {
        val start = state(0.45f, 0.5f)
        val orbit = step(
            start,
            ParticleMotion(gravity = 0f, drag = 0f, sway = 0f, orbit = 1f),
            seconds = 0.4f,
        )
        val radiusOrbit = hypot(orbit.x - anchor.x, orbit.y - anchor.y)
        assertEquals(0.2f, radiusOrbit, 0.03f)

        val spiral = step(
            start,
            ParticleMotion(gravity = 0f, drag = 0f, sway = 0f, spiral = 1f),
            seconds = 0.6f,
        )
        val radiusSpiral = hypot(spiral.x - anchor.x, spiral.y - anchor.y)
        assertTrue("螺旋应向锚点收拢", radiusSpiral < 0.2f)
    }

    @Test
    fun `bounce keeps particle inside the frame and flips velocity`() {
        val start = state(0.25f, 0.98f, vy = 1.2f)
        val end = step(
            start,
            ParticleMotion(gravity = 1f, drag = 0f, sway = 0f, bounce = 0.6f),
            seconds = 0.3f,
            frames = 12,
        )
        assertTrue("不出下边界", end.y <= 1.001f)
        assertTrue("反弹后向上", end.vy < 0f)
        assertTrue("不出左右边界", end.x in -0.001f..frame.aspect + 0.001f)
    }

    @Test
    fun `path evaluation hits start and end points`() {
        val start = NormPoint(0.2f, 0.1f)
        val dir = NormPoint(0f, 1f)
        listOf(ParticlePath.Bezier, ParticlePath.Arc, ParticlePath.Sine, ParticlePath.ZigZag)
            .forEach { path ->
                val begin = pathPosition(path, 0f, start, dir, frame.aspect)
                assertEquals("$path 起点 x", start.x, begin.x, 0.001f)
                assertEquals("$path 起点 y", start.y, begin.y, 0.001f)
                val end = pathPosition(path, 1f, start, dir, frame.aspect)
                assertTrue("$path 终点应向下移动", end.y > start.y)
            }
    }

    @Test
    fun `noise is deterministic and seed dependent`() {
        assertEquals(noise01(7, 0.3f), noise01(7, 0.3f), 1e-6f)
        assertTrue(noise01(7, 0.3f) in 0f..1f)
        assertTrue(abs(noise01(7, 0.3f) - noise01(8, 0.3f)) > 1e-4f)
    }

    @Test
    fun `spawner follows emitters and anchors`() {
        val top = spawnParticle(
            ParticleEmitter(area = ParticleArea.TopEdge, count = 1),
            frame,
            anchor = null,
            random = Random(1),
        )
        assertTrue("顶缘初始在屏幕上方", top.y < 0f)

        val point = spawnParticle(
            ParticleEmitter(area = ParticleArea.Point, count = 1, spreadDeg = 0f),
            frame,
            anchor = anchor,
            random = Random(1),
        )
        assertEquals(anchor.x, point.x, 0.001f)
        assertEquals(anchor.y, point.y, 0.001f)
    }

    @Test
    fun `same seed produces the same spawn`() {
        val a = spawnParticle(ParticleEmitter(count = 1), frame, null, Random(42))
        val b = spawnParticle(ParticleEmitter(count = 1), frame, null, Random(42))
        assertEquals(a, b)
    }

    @Test
    fun `tilt scale stays visible`() {
        assertEquals(1f, tiltScale(0f), 0.001f)
        assertEquals(1f, tiltScale(180f), 0.001f)
        assertTrue(tiltScale(90f) >= 0.15f)
        assertTrue(tiltScale(270f) >= 0.15f)
    }
}