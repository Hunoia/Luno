package hunoia.luno.runtime.floatball.alert

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 粒子共享物理曲线：风 / 阻力 / 行程 / 方差的映射口径。
 *
 * 这些函数是渲染层与未来新增运动的唯一换算源，必须保持纯函数、可预测。
 */
class ParticlePhysicsTest {

    @Test
    fun `drag eases from zero to one and is monotonic`() {
        val samples = listOf(0f, 0.1f, 0.5f, 0.9f, 1f)
        val eased = samples.map { dragEase(it, 0.5f) }
        assertEquals(0f, eased.first(), 0.001f)
        assertEquals(1f, eased.last(), 0.001f)
        for (i in 1 until eased.size) {
            assertTrue("dragEase 必须单调不减", eased[i] >= eased[i - 1])
        }
    }

    @Test
    fun `zero drag is linear`() {
        assertEquals(0.25f, dragEase(0.25f, 0f), 0.001f)
        assertEquals(1f, dragEase(1f, 0f), 0.001f)
    }

    @Test
    fun `stronger drag reaches terminal velocity sooner but keeps the end`() {
        val light = dragEase(0.5f, 0.1f)
        val heavy = dragEase(0.5f, 1f)
        // 终端速度：阻尼越大，前半段走得越多、后段越平；终点仍严格为 1。
        assertTrue("阻力越大中途位移越大", heavy > light)
        assertEquals(1f, dragEase(1f, 1f), 0.001f)
    }

    @Test
    fun `wind is signed and grows quadratically`() {
        assertEquals(0f, windOffsetNorm(0f, 1f), 0.0001f)
        assertTrue(windOffsetNorm(1f, 1f) > 0f)
        assertTrue(windOffsetNorm(-1f, 1f) < 0f)
        // 0.5 处的位移是 1 处的四分之一（t²）。
        assertEquals(
            windOffsetNorm(1f, 1f) / 4f,
            windOffsetNorm(1f, 0.5f),
            0.0001f,
        )
        // 越界风夹回 -1..1。
        assertEquals(windOffsetNorm(1f, 1f), windOffsetNorm(5f, 1f), 0.0001f)
    }

    @Test
    fun `travel norm applies the percent and the motion basis`() {
        assertEquals(1f, travelNorm(1f, 1f), 0.0001f)
        assertEquals(0.6f, travelNorm(1f, 0.6f), 0.0001f)
        assertEquals(1.2f, travelNorm(1f, 1.2f), 0.0001f)
        // 百分比越界夹回 0..2。
        assertEquals(2f, travelNorm(9f, 1f), 0.0001f)
    }

    @Test
    fun `variance is symmetric around one and zero variance is identity`() {
        assertEquals(1f, varianceScale(0f, 0f), 0.0001f)
        assertEquals(1f, varianceScale(0f, 0.7f), 0.0001f)
        val low = varianceScale(1f, 0f)
        val high = varianceScale(1f, 1f)
        assertTrue(low < 1f)
        assertTrue(high > 1f)
        // 对称：0 与 1 的偏差绝对值相同。
        assertEquals(1f - low, high - 1f, 0.0001f)
        assertEquals(1f, varianceScale(1f, 0.5f), 0.0001f)
    }
}