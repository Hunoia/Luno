package hunoia.luno.runtime.scene

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SceneScanLedgerTest {

    @Test
    fun `canceled scan does not suppress the same screen`() {
        val ledger = SceneScanLedger()
        // 首扫被新事件取消：只有真正 publish 过的结果才记账。
        assertFalse(ledger.isSameAsPublished("com.tencent.mm", "k1"))
        ledger.onPublished("com.tencent.mm", "k1")
        assertTrue(ledger.isSameAsPublished("com.tencent.mm", "k1"))
        assertFalse(ledger.isSameAsPublished("com.tencent.mm", "k2"))
        assertFalse(ledger.isSameAsPublished("com.tencent.mm2", "k1"))
    }

    @Test
    fun `leaving the screen allows the same screen to pop again`() {
        val ledger = SceneScanLedger()
        ledger.onPublished("com.tencent.mm", "k1")
        assertTrue(ledger.isSameAsPublished("com.tencent.mm", "k1"))
        ledger.forgetScreen()
        assertFalse(ledger.isSameAsPublished("com.tencent.mm", "k1"))
        ledger.onPublished("com.tencent.mm", "k1")
        assertTrue(ledger.isSameAsPublished("com.tencent.mm", "k1"))
    }

    @Test
    fun `empty snapshot retries are bounded per screen`() {
        val ledger = SceneScanLedger(maxEmptyRetries = 2)
        assertTrue(ledger.shouldRetryEmpty("com.tencent.mm"))
        assertTrue(ledger.shouldRetryEmpty("com.tencent.mm"))
        assertFalse(ledger.shouldRetryEmpty("com.tencent.mm"))
        // 推送成功 / 换应用都能重新拿到补扫次数。
        ledger.onPublished("com.tencent.mm", "k1")
        assertTrue(ledger.shouldRetryEmpty("com.tencent.mm"))
        assertTrue(ledger.shouldRetryEmpty("com.tencent.mm"))
        assertFalse(ledger.shouldRetryEmpty("com.tencent.mm"))
        assertTrue(ledger.shouldRetryEmpty("com.tencent.mm2"))
    }

    @Test
    fun `empty retries reset when the screen changes`() {
        val ledger = SceneScanLedger(maxEmptyRetries = 1)
        assertTrue(ledger.shouldRetryEmpty("com.tencent.mm"))
        assertFalse(ledger.shouldRetryEmpty("com.tencent.mm"))
        // 换到另一个应用再回来：不因为上一个应用把次数用完而被挡。
        assertTrue(ledger.shouldRetryEmpty("com.tencent.mm2"))
        assertTrue(ledger.shouldRetryEmpty("com.tencent.mm"))
    }
}
