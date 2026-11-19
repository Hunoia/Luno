package hunoia.luno.gridpanel.trend

import hunoia.luno.config.model.TrendEntry
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test

class TrendDayGroupsTest {

    private val zone: ZoneId = ZoneId.of("UTC")

    private fun at(day: Int, hour: Int): Long =
        LocalDate.of(2026, 1, day).atTime(hour, 0).atZone(zone).toInstant().toEpochMilli()

    private fun entry(id: String, day: Int, hour: Int) =
        TrendEntry(id = id, atMillis = at(day, hour), value = 1.0)

    @Test
    fun `groups newest day first and keeps entries ascending`() {
        val groups = trendDayGroups(
            entries = listOf(entry("a", 10, 8), entry("b", 10, 20), entry("c", 11, 7)),
            zone = zone,
        )
        assertEquals(
            listOf(LocalDate.of(2026, 1, 11), LocalDate.of(2026, 1, 10)),
            groups.map { it.day },
        )
        assertEquals(listOf("c"), groups[0].entries.map { it.id })
        assertEquals(listOf("a", "b"), groups[1].entries.map { it.id })
    }

    @Test
    fun `limit keeps the latest records`() {
        val groups = trendDayGroups(
            entries = listOf(entry("a", 8, 8), entry("b", 9, 8), entry("c", 10, 8)),
            limit = 2,
            zone = zone,
        )
        assertEquals(
            listOf(LocalDate.of(2026, 1, 10), LocalDate.of(2026, 1, 9)),
            groups.map { it.day },
        )
        assertEquals(listOf("c"), groups[0].entries.map { it.id })
    }

    @Test
    fun `day boundary follows the given zone`() {
        val millis = LocalDate.of(2026, 1, 10)
            .atTime(23, 30)
            .atZone(ZoneId.of("UTC"))
            .toInstant()
            .toEpochMilli()
        val entry = TrendEntry(id = "x", atMillis = millis, value = 1.0)
        assertEquals(
            LocalDate.of(2026, 1, 10),
            trendDayGroups(listOf(entry), zone = ZoneId.of("UTC")).single().day,
        )
        assertEquals(
            LocalDate.of(2026, 1, 11),
            trendDayGroups(listOf(entry), zone = ZoneId.of("+08:00")).single().day,
        )
    }

    @Test
    fun `empty input returns no groups`() {
        assertEquals(emptyList<TrendDayGroup>(), trendDayGroups(emptyList(), zone = zone))
    }
}
