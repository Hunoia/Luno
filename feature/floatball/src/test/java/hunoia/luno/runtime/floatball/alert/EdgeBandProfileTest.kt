package hunoia.luno.runtime.floatball.alert

import hunoia.luno.config.model.EdgeSweep
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 光带剖面：头部 + **单侧弧长**拖尾。
 *
 * 旧实现把 `tailLength` 当高斯 sigma 喂给着色器，sigma 0.3 在 [0,1] 周长上几乎
 * 铺满整圈，屏幕上就是「一圈常亮光」；这里锁住「拖尾之外必须是 0」的口径。
 */
class EdgeBandProfileTest {

    private val form = EdgeSweep(tailPercent = 20, tailFalloff = 2f, tailAlpha = 0.5f)

    @Test
    fun `head sits at zero delta`() {
        assertTrue(bandProfile(0f, 0.02f, form) > 0.7f)
    }

    @Test
    fun `tail is zero beyond its arc length`() {
        // 20% 拖尾：0.02（头）+ 0.2 + 余量之后必须完全为 0。
        assertEquals(0f, bandProfile(0.5f, 0.02f, form), 0.0001f)
        assertEquals(0f, bandProfile(0.9f, 0.02f, form), 0.0001f)
    }

    @Test
    fun `tail decays monotonically behind the head`() {
        val near = bandProfile(0.06f, 0.02f, form)
        val far = bandProfile(0.16f, 0.02f, form)
        assertTrue("近头比远头亮", near > far)
        assertTrue("远头仍可见", far > 0f)
    }

    @Test
    fun `opposite side stays dark at the default tail`() {
        // 0.5 是头的正对面：默认 20% 拖尾够不到那里，不该有底光。
        assertEquals(0f, bandProfile(0.5f, 0.02f, form), 0.0001f)
    }

    @Test
    fun `a 60 percent tail reaches the opposite side by design`() {
        // 上限 60% 是用户主动拉满的「几乎整圈」；这是显式选择，不是默认底光。
        val long = EdgeSweep(tailPercent = 60, tailFalloff = 2f, tailAlpha = 1f)
        assertTrue(bandProfile(0.5f, 0.02f, long) > 0f)
        assertEquals(0f, bandProfile(0.9f, 0.02f, long), 0.0001f)
    }
}
