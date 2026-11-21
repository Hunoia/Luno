package hunoia.luno.config.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BatteryHistoryTest {

    private fun sample(at: Long, level: Int, status: BatteryPowerStatus = BatteryPowerStatus.DISCHARGING) =
        BatterySample(atMillis = at, levelPct = level, status = status)

    @Test
    fun `appends and keeps chronological order`() {
        val history = BatteryHistory()
            .appended(sample(1_000L, 80))
            .appended(sample(2_000L, 79))
        assertEquals(listOf(80, 79), history.samples.map { it.levelPct })
        assertEquals(79, history.latest?.levelPct)
    }

    @Test
    fun `same level and status inside the debounce window is dropped`() {
        val history = BatteryHistory().appended(sample(1_000L, 80))
        val next = history.appended(sample(1_000L + BatterySampleMinIntervalMs - 1, 80))
        assertEquals(1, next.samples.size)
    }

    @Test
    fun `level change bypasses the debounce window`() {
        val history = BatteryHistory().appended(sample(1_000L, 80))
        val next = history.appended(sample(1_001L, 79))
        assertEquals(2, next.samples.size)
    }

    @Test
    fun `status change bypasses the debounce window`() {
        val history = BatteryHistory().appended(sample(1_000L, 80, BatteryPowerStatus.DISCHARGING))
        val next = history.appended(sample(1_001L, 80, BatteryPowerStatus.CHARGING))
        assertEquals(2, next.samples.size)
        assertEquals(BatteryPowerStatus.CHARGING, next.latest?.status)
    }

    @Test
    fun `clock rollback still inserts in order`() {
        val history = BatteryHistory()
            .appended(sample(10_000L, 80))
            .appended(sample(5_000L, 81))
        assertEquals(listOf(81, 80), history.samples.map { it.levelPct })
        assertTrue(history.samples[0].atMillis <= history.samples[1].atMillis)
    }

    @Test
    fun `samples older than the retention window are pruned`() {
        val now = 10L * BatteryHistoryRetentionMs
        val history = BatteryHistory()
            .appended(sample(now - BatteryHistoryRetentionMs - 1, 90), now = now)
            .appended(sample(now, 80), now = now)
        assertEquals(1, history.samples.size)
        assertEquals(80, history.samples.single().levelPct)
    }

    @Test
    fun `sample count is capped`() {
        var history = BatteryHistory()
        repeat(10) { index ->
            history = history.appended(
                sample(index.toLong() * 1_000L, index.coerceAtMost(100)),
                maxSamples = 3,
            )
        }
        assertEquals(3, history.samples.size)
        assertEquals(listOf(7, 8, 9), history.samples.map { it.levelPct })
    }

    @Test
    fun `sample normalizes level and invalid power`() {
        val normalized = BatterySample(1L, 140, powerMw = Float.NaN).normalized()
        assertEquals(100, normalized.levelPct)
        assertEquals(null, normalized.powerMw)
    }

    @Test
    fun `since filters by timestamp`() {
        val history = BatteryHistory()
            .appended(sample(1_000L, 80))
            .appended(sample(2_000L, 79))
            .appended(sample(3_000L, 78))
        assertEquals(listOf(79, 78), history.since(2_000L).map { it.levelPct })
    }
}
