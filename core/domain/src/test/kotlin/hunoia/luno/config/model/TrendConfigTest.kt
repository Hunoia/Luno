package hunoia.luno.config.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TrendConfigTest {

    private fun metric(id: String = "m1", name: String = "体重") =
        TrendMetric(id = id, name = name)

    private fun entry(id: String, at: Long, value: Double) =
        TrendEntry(id = id, atMillis = at, value = value)

    // ---------- 指标 CRUD ----------

    @Test
    fun `add metric appends`() {
        val cfg = TrendConfig().addMetric(metric())
        assertEquals(1, cfg.metrics.size)
        assertEquals("体重", cfg.metricById("m1")?.name)
        assertTrue(cfg.hasMetrics)
    }

    @Test
    fun `update metric replaces in place and keeps order`() {
        val cfg = TrendConfig(metrics = listOf(metric("a"), metric("b")))
        val updated = cfg.updateMetric(metric("b", name = "腰围"))
        assertEquals(listOf("a", "b"), updated.metrics.map { it.id })
        assertEquals("腰围", updated.metricById("b")?.name)
    }

    @Test
    fun `delete metric removes metric and its entries`() {
        val cfg = TrendConfig(
            metrics = listOf(metric("a"), metric("b")),
            entries = mapOf(
                "a" to listOf(entry("e1", 1L, 1.0)),
                "b" to listOf(entry("e2", 2L, 2.0)),
            ),
        )
        val after = cfg.deleteMetric("a")
        assertEquals(listOf("b"), after.metrics.map { it.id })
        assertTrue(after.entriesOf("a").isEmpty())
        assertFalse(after.entries.containsKey("a"))
        assertEquals(1, after.entriesOf("b").size)
    }

    // ---------- 记录 ----------

    @Test
    fun `add entry keeps ascending time order`() {
        var cfg = TrendConfig().addMetric(metric())
        cfg = cfg.addEntry("m1", entry("e2", 200L, 2.0))!!
        cfg = cfg.addEntry("m1", entry("e1", 100L, 1.0))!!
        cfg = cfg.addEntry("m1", entry("e3", 300L, 3.0))!!
        assertEquals(listOf("e1", "e2", "e3"), cfg.entriesOf("m1").map { it.id })
    }

    @Test
    fun `add entry on unknown metric returns null`() {
        val cfg = TrendConfig()
        // 直接调纯函数，不经过 ConfigProvider：未知指标必须拒绝。
        assertNull(cfg.addEntry("missing", entry("e1", 1L, 1.0)))
    }

    @Test
    fun `add entry rejects when metric reached the limit`() {
        val existing = (0 until TrendMaxEntriesPerMetric).map {
            entry("e$it", it.toLong(), it.toDouble())
        }
        val cfg = TrendConfig(
            metrics = listOf(metric()),
            entries = mapOf("m1" to existing),
        )
        assertNull(cfg.addEntry("m1", entry("overflow", Long.MAX_VALUE, 0.0)))
        assertEquals(TrendMaxEntriesPerMetric, cfg.entriesOf("m1").size)
    }

    @Test
    fun `update entry readings changes values only`() {
        val cfg = TrendConfig(
            metrics = listOf(metric()),
            entries = mapOf("m1" to listOf(entry("e1", 100L, 70.0))),
        )
        val after = cfg.updateEntryReadings("m1", "e1", listOf(71.5, 72.0))
        val updated = after.entriesOf("m1").single()
        assertEquals(71.5, updated.value, 0.0001)
        assertEquals(listOf(71.5, 72.0), updated.readings)
        assertEquals(100L, updated.atMillis)
        assertEquals("e1", updated.id)
    }

    @Test
    fun `update entry readings on unknown entry is a no-op`() {
        val cfg = TrendConfig(
            metrics = listOf(metric()),
            entries = mapOf("m1" to listOf(entry("e1", 100L, 70.0))),
        )
        assertEquals(cfg, cfg.updateEntryReadings("m1", "nope", listOf(1.0)))
    }

    @Test
    fun `trend entry factory keeps the legacy mirror in sync`() {
        val entry = trendEntry(100L, listOf(65.0, 66.0))
        assertEquals(65.0, entry.value, 0.0001)
        assertEquals(listOf(65.0, 66.0), entry.readings)
        assertEquals(65.0, entry.readingAt(0)!!, 0.0001)
        assertEquals(66.0, entry.readingAt(1)!!, 0.0001)
        assertNull(entry.readingAt(2))
    }

    @Test
    fun `legacy entry without values falls back to value`() {
        val legacy = TrendEntry(id = "e1", atMillis = 1L, value = 65.5)
        assertEquals(listOf(65.5), legacy.readings)
        assertEquals(65.5, legacy.readingAt(0)!!, 0.0001)
        assertNull(legacy.readingAt(1))
    }

    @Test
    fun `value count is clamped and defaults to one`() {
        val single = TrendMetric()
        assertEquals(1, single.valueCount)
        assertEquals(1, TrendMetric(valueNames = listOf("晨重")).valueCount)
        assertEquals(
            TrendMaxValues,
            TrendMetric(valueNames = List(TrendMaxValues + 2) { "v$it" }).valueCount,
        )
    }

    @Test
    fun `delete entry removes one and drops empty bucket`() {
        val cfg = TrendConfig(
            metrics = listOf(metric()),
            entries = mapOf(
                "m1" to listOf(entry("e1", 100L, 70.0), entry("e2", 200L, 71.0)),
            ),
        )
        val one = cfg.deleteEntry("m1", "e1")
        assertEquals(listOf("e2"), one.entriesOf("m1").map { it.id })
        val none = one.deleteEntry("m1", "e2")
        assertTrue(none.entriesOf("m1").isEmpty())
        assertFalse(none.entries.containsKey("m1"))
        assertNotNull(none.metricById("m1"))
    }

    @Test
    fun `add entry keeps entries of other metrics intact`() {
        var cfg = TrendConfig().addMetric(metric("a")).addMetric(metric("b"))
        cfg = cfg.addEntry("a", entry("a1", 1L, 1.0))!!
        cfg = cfg.addEntry("b", entry("b1", 2L, 2.0))!!
        assertEquals(listOf("a1"), cfg.entriesOf("a").map { it.id })
        assertEquals(listOf("b1"), cfg.entriesOf("b").map { it.id })
    }

    // ---------- 指标默认值 ----------

    @Test
    fun `metric defaults are safe`() {
        val m = TrendMetric()
        assertEquals(1, m.safeDecimals)
        assertEquals(0, TrendMetric(decimals = -1).safeDecimals)
        assertEquals(TrendMaxDecimals, TrendMetric(decimals = 99).safeDecimals)
        assertNull(m.target)
    }
}
