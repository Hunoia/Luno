package hunoia.luno.runtime.floatball.alert

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 锁屏关闭态：压黑时刻必须有上限，且短预设不能被拉长。 */
class AlertStageWindowTest {

    @Test
    fun `closing cover keeps short presets but caps long ones`() {
        assertEquals(0, closingCoverMillis(0))
        assertEquals(700, closingCoverMillis(700))
        assertEquals(ClosingCoverMaxMs, closingCoverMillis(5_000))
        assertEquals(ClosingCoverMaxMs, closingCoverMillis(ClosingCoverMaxMs))
    }

    @Test
    fun `fade fits inside the shortest meaningful cover`() {
        assertTrue(ClosingFadeMs > 0)
        assertTrue(ClosingFadeMs < ClosingCoverMaxMs)
        assertTrue(ClosingFadeMs < closingCoverMillis(700))
    }
}
