package hunoia.luno.runtime.floatball

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 持机侧判定的衰减加权多数票：两票新鲜事件即切、误触被稀释、
 * 双手交替不乒乓、久置两票即切、手动停靠确证清池。
 */
class HandSideHysteresisTest {

    @Test
    fun `seed applies only once`() {
        val h = HandSideHysteresis()
        assertNull(h.currentLeft)
        h.seed(false)
        h.seed(true)
        assertEquals(false, h.currentLeft)
    }

    @Test
    fun `two fresh opposite votes flip`() {
        val h = HandSideHysteresis()
        h.seed(false)
        // 单票权重不足 1.5。
        assertFalse(h.onSideEvent(true, 0))
        assertTrue(h.onSideEvent(true, 1_000))
        assertEquals(true, h.currentLeft)
    }

    @Test
    fun `same side votes never flip`() {
        val h = HandSideHysteresis()
        h.seed(true)
        assertFalse(h.onSideEvent(true, 0))
        assertFalse(h.onSideEvent(true, 1_000))
        assertEquals(true, h.currentLeft)
    }

    @Test
    fun `alternating votes stay balanced`() {
        val h = HandSideHysteresis()
        h.seed(false)
        // 双手交替：两侧权重相近，占比到不了 0.7，永不切换。
        h.onSideEvent(false, 0)
        var t = 10_000L
        repeat(5) {
            assertFalse(h.onSideEvent(true, t))
            assertFalse(h.onSideEvent(false, t + 1_000))
            t += 2_000
        }
        assertEquals(false, h.currentLeft)
    }

    @Test
    fun `interleaved majority flips despite cross-reach`() {
        val h = HandSideHysteresis()
        h.seed(false)
        h.onSideEvent(false, 0)
        // 新侧多次触发、夹杂一次旧侧跨手误触：连击计数会被重置，多数票不会。
        assertFalse(h.onSideEvent(true, 10_000))
        assertFalse(h.onSideEvent(true, 11_000))
        assertFalse(h.onSideEvent(false, 12_000))
        assertFalse(h.onSideEvent(true, 13_000))
        assertFalse(h.onSideEvent(true, 14_000))
        assertTrue(h.onSideEvent(true, 15_000))
        assertEquals(true, h.currentLeft)
    }

    @Test
    fun `stale window needs only two fresh votes`() {
        val h = HandSideHysteresis()
        h.seed(false)
        h.onSideEvent(false, 0)
        // 6.6 分钟后旧票已被窗口裁剪。
        assertFalse(h.onSideEvent(true, 400_000))
        assertTrue(h.onSideEvent(true, 401_000))
        assertEquals(true, h.currentLeft)
    }

    @Test
    fun `manual park adopts immediately and clears votes`() {
        val h = HandSideHysteresis()
        h.seed(false)
        h.onSideEvent(false, 0)
        assertTrue(h.onManualPark(true))
        assertEquals(true, h.currentLeft)
        // 票池已清：单票反向不足权重。
        assertFalse(h.onSideEvent(false, 1_000))
    }

    @Test
    fun `manual park to same side reports no change`() {
        val h = HandSideHysteresis()
        h.seed(false)
        assertFalse(h.onManualPark(false))
    }
}
