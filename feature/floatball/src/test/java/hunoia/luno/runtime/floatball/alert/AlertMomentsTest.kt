package hunoia.luno.runtime.floatball.alert

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 锁屏等待：回调先到立即返回；回调丢失也绝不卡住锁屏。 */
class AlertMomentsTest {

    @Test
    fun `finishes as soon as the callback arrives`() = runBlocking {
        val ok = AlertMoments.awaitMomentFinished(timeoutMs = 1_000L) { onDone ->
            onDone()
        }
        assertTrue(ok)
    }

    @Test
    fun `does not hang when the callback is lost`() = runBlocking {
        val ok = AlertMoments.awaitMomentFinished(timeoutMs = 50L) { _ ->
            // 模拟服务未回调（例如动画被系统杀掉）
        }
        assertFalse(ok)
    }
}
