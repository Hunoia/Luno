package hunoia.luno.config.model

import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TrendStatsTest {

    private val zone: ZoneId = ZoneId.of("UTC")

    private fun at(day: Int, hour: Int = 9): Long =
        LocalDate.of(2026, 1, day).atTime(hour, 0).atZone(zone).toInstant().toEpochMilli()

    private fun entry(id: String, day: Int, value: Double, hour: Int = 9) =
        TrendEntry(id = id, atMillis = at(day, hour), value = value)

    @Test
    fun `empty entries has no data`() {
        val stats = trendStats(emptyList(), target = 60.0, direction = TrendGoalDirection.LOWER_IS_BETTER, zone = zone)
        assertFalse(stats.hasData)
        assertNull(stats.latest)
        assertNull(stats.deltaFromPrevious)
        assertNull(stats.deltaFromSevenDays)
        assertNull(stats.distanceToTarget)
        assertNull(stats.goalReached)
    }

    @Test
    fun `single entry has no baseline`() {
        val stats = trendStats(
            listOf(entry("e1", 10, 70.0)),
            target = 65.0,
            direction = TrendGoalDirection.LOWER_IS_BETTER,
            zone = zone,
        )
        assertTrue(stats.hasData)
        assertEquals("e1", stats.latest?.id)
        assertNull(stats.previous)
        assertNull(stats.deltaFromPrevious)
        assertNull(stats.deltaFromSevenDays)
        assertEquals(5.0, stats.distanceToTarget!!, 0.0001)
        assertFalse(stats.goalReached!!)
    }

    @Test
    fun `delta from previous uses the immediately preceding entry`() {
        val stats = trendStats(
            listOf(entry("e1", 1, 72.0), entry("e2", 10, 70.5)),
            target = null,
            direction = TrendGoalDirection.NEUTRAL,
            zone = zone,
        )
        assertEquals("e1", stats.previous?.id)
        assertEquals(-1.5, stats.deltaFromPrevious!!, 0.0001)
        assertNull(stats.distanceToTarget)
        assertNull(stats.goalReached)
    }

    @Test
    fun `seven day baseline uses latest date minus seven`() {
        // 最新 1/20；1/13 当天及之前最后一条是 1/12。
        val stats = trendStats(
            listOf(
                entry("old", 1, 80.0),
                entry("first", 12, 75.0),
                entry("second", 13, 74.0),
                entry("latest", 20, 72.0),
            ),
            target = null,
            direction = TrendGoalDirection.NEUTRAL,
            zone = zone,
        )
        assertEquals("second", stats.sevenDayBaseline?.id)
        // 最新 1/20 的 7 天前是 1/13，所以基线是 1/13 当天那条。
        assertEquals(-2.0, stats.deltaFromSevenDays!!, 0.0001)
    }

    @Test
    fun `seven day baseline is null when nothing is old enough`() {
        val stats = trendStats(
            listOf(entry("e1", 18, 70.0), entry("e2", 20, 69.0)),
            target = null,
            direction = TrendGoalDirection.NEUTRAL,
            zone = zone,
        )
        assertNull(stats.sevenDayBaseline)
        assertNull(stats.deltaFromSevenDays)
    }

    @Test
    fun `seven day baseline is based on local dates not 168 hours`() {
        // 最新 1/20 08:00；7 天前 = 1/13。1/13 23:00 的记录仍算「7 天前当天」。
        val stats = trendStats(
            listOf(entry("baseline", 13, 80.0, hour = 23), entry("latest", 20, 78.0, hour = 8)),
            target = null,
            direction = TrendGoalDirection.NEUTRAL,
            zone = zone,
        )
        assertEquals("baseline", stats.sevenDayBaseline?.id)
        assertEquals(-2.0, stats.deltaFromSevenDays!!, 0.0001)
    }

    @Test
    fun `goal reached respects direction`() {
        val entries = listOf(entry("e1", 10, 70.0))
        assertTrue(
            trendStats(entries, 70.0, TrendGoalDirection.LOWER_IS_BETTER, zone).goalReached!!,
        )
        assertTrue(
            trendStats(entries, 80.0, TrendGoalDirection.LOWER_IS_BETTER, zone).goalReached!!,
        )
        assertFalse(
            trendStats(entries, 60.0, TrendGoalDirection.LOWER_IS_BETTER, zone).goalReached!!,
        )
        assertTrue(
            trendStats(entries, 60.0, TrendGoalDirection.HIGHER_IS_BETTER, zone).goalReached!!,
        )
        assertFalse(
            trendStats(entries, 80.0, TrendGoalDirection.HIGHER_IS_BETTER, zone).goalReached!!,
        )
        assertNull(
            trendStats(entries, 60.0, TrendGoalDirection.NEUTRAL, zone).goalReached,
        )
    }

    @Test
    fun `distance to target is signed`() {
        val entries = listOf(entry("e1", 10, 70.0))
        assertEquals(-2.0, trendStats(entries, 72.0, TrendGoalDirection.NEUTRAL, zone).distanceToTarget!!, 0.0001)
        assertEquals(2.0, trendStats(entries, 68.0, TrendGoalDirection.NEUTRAL, zone).distanceToTarget!!, 0.0001)
    }

    // ---------- 日期辅助 ----------

    @Test
    fun `epoch day follows local zone`() {
        assertEquals(
            LocalDate.of(2026, 1, 10).toEpochDay(),
            epochDayOf(at(10, hour = 0), zone),
        )
        assertEquals(
            LocalDate.of(2026, 1, 10).toEpochDay(),
            epochDayOf(at(10, hour = 23) + 59 * 60_000, zone),
        )
    }

    @Test
    fun `has entry on matches local date`() {
        val entries = listOf(entry("e1", 10, 70.0), entry("e2", 12, 71.0))
        assertTrue(hasEntryOn(entries, LocalDate.of(2026, 1, 10).toEpochDay(), zone))
        assertFalse(hasEntryOn(entries, LocalDate.of(2026, 1, 11).toEpochDay(), zone))
    }

    @Test
    fun `format epoch day is iso date`() {
        assertEquals("2026-01-10", formatEpochDay(LocalDate.of(2026, 1, 10).toEpochDay()))
    }

    // ---------- 多读数槽位投影 ----------

    private fun reading(id: String, day: Int, values: List<Double>, hour: Int = 9) =
        trendEntry(at(day, hour), values, id = id)

    @Test
    fun `series projection keeps only the requested slot`() {
        val entries = listOf(
            reading("a", 1, listOf(70.0, 68.0)),
            reading("b", 2, listOf(70.5)),
            reading("c", 3, listOf(71.0, 67.0)),
        )
        val first = seriesEntries(entries, 0)
        assertEquals(listOf("a", "b", "c"), first.map { it.id })
        // 缺该读数的记录被跳过，既不补 0 也不拿别的槽位冒充。
        val second = seriesEntries(entries, 1)
        assertEquals(listOf("a", "c"), second.map { it.id })
        assertEquals(68.0, second.first().value, 0.0001)
        // 投影后收敛成单元素，`value` 与 `readings` 不分裂。
        assertEquals(listOf(68.0), second.first().readings)
        assertEquals(68.0, second.first().readingAt(0)!!, 0.0001)
        assertNull(second.first().readingAt(1))
    }

    @Test
    fun `series projection of a bad slot is empty`() {
        val entries = listOf(reading("a", 1, listOf(70.0)))
        assertTrue(seriesEntries(entries, -1).isEmpty())
        assertTrue(seriesEntries(entries, 5).isEmpty())
        assertTrue(seriesEntries(emptyList(), 0).isEmpty())
    }

    @Test
    fun `stats of a series only see that slot`() {
        val entries = listOf(
            reading("a", 1, listOf(70.0, 68.0)),
            reading("b", 5, listOf(70.0)),
            reading("c", 10, listOf(70.5, 69.0)),
        )
        val first = trendStats(seriesEntries(entries, 0), target = null, TrendGoalDirection.NEUTRAL, zone)
        val second = trendStats(seriesEntries(entries, 1), target = null, TrendGoalDirection.NEUTRAL, zone)
        assertEquals(0.5, first.deltaFromPrevious!!, 0.0001)
        // 「b」只填了第 1 个读数，所以第 2 个槽位的序列跳过它：上次基线回到「a」。
        assertEquals(1.0, second.deltaFromPrevious!!, 0.0001)
        assertEquals("a", second.previous?.id)
        assertEquals("c", first.latest?.id)
    }

    @Test
    fun `formula value comes from the latest entry`() {
        val metric = TrendMetric(formula = "#2-#1", valueNames = listOf("晨重", "晚重"))
        val latest = reading("e", 3, listOf(70.0, 68.5))
        assertEquals(-1.5, trendFormulaValue(metric, latest)!!, 0.0001)
        // 公式为空、没有记录都返回 null。
        assertNull(trendFormulaValue(metric.copy(formula = ""), latest))
        assertNull(trendFormulaValue(metric, null))
        // 该条记录缺 #2（只填了前缀）算不出，不是报错。
        assertNull(trendFormulaValue(metric, reading("p", 3, listOf(70.0))))
        assertNull(trendFormulaValue(TrendMetric(formula = "#1/0"), latest))
    }
}
