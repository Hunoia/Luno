package hunoia.luno.runtime.scene

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 无产出退避：只压「这一屏确实没变化」的重复扫描，任何真实推进都要能立刻恢复。
 */
class SceneScanSchedulerTest {

    @Test
    fun `empty scan backs off past the idle window`() {
        val scheduler = SceneScanScheduler()
        scheduler.onSignal(nowMs = 0, packageName = "a")
        scheduler.onScanStarted(500)
        scheduler.onScanResult(1_500, published = false)

        // idle 应到 2000+500，但退避把下一次推到 1500+3000。
        assertEquals(2_500L, scheduler.onSignal(nowMs = 2_000, packageName = "a"))
    }

    @Test
    fun `publishing resets backoff`() {
        val scheduler = SceneScanScheduler()
        scheduler.onSignal(nowMs = 0, packageName = "a")
        scheduler.onScanStarted(500)
        scheduler.onScanResult(1_500, published = false)
        scheduler.onScanStarted(3_000)
        scheduler.onScanResult(3_000, published = true)

        // 退避清零后只剩 idle 与最小间隔：minInterval 4500 比 idle 3600 更晚。
        assertEquals(1_400L, scheduler.onSignal(nowMs = 3_100, packageName = "a"))
    }

    @Test
    fun `screen change clears backoff immediately`() {
        val scheduler = SceneScanScheduler()
        scheduler.onSignal(nowMs = 0, packageName = "a")
        scheduler.onScanStarted(500)
        scheduler.onScanResult(1_500, published = false)
        scheduler.onScanStarted(3_000)
        scheduler.onScanResult(3_000, published = false)

        // 同一包里换页（窗口状态变化）：退避计时起点清零，只剩 idle 的 500ms。
        assertEquals(
            500L,
            scheduler.onSignal(nowMs = 5_000, packageName = "a", screenChanged = true),
        )
    }
}
