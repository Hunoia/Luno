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
}
