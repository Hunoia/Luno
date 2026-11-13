package hunoia.luno.gridpanel.trend

import hunoia.luno.config.model.TrendEntry
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TrendChartGeometryTest {

    private val zone: ZoneId = ZoneId.of("UTC")
    private val day = 86_400_000L

    private fun at(day: Int, hour: Int = 9): Long =
        LocalDate.of(2026, 1, day).atTime(hour, 0).atZone(zone).toInstant().toEpochMilli()

    private fun entry(id: String, day: Int, value: Double, hour: Int = 9) =
        TrendEntry(id = id, atMillis = at(day, hour), value = value)

    @Test
    fun `fixed range ends at now`() {
        val now = at(20, hour = 12)
        val window = trendWindow(listOf(entry("e", 10, 70.0)), TrendRange.DAYS_7, now)
        assertEquals(now, window.last)
        assertEquals(now - 7 * day, window.first)
    }

    @Test
    fun `fixed range follows a record in the future`() {
        val now = at(20, hour = 12)
        val future = now + 2 * day
        val window = trendWindow(
            listOf(entry("e", 10, 70.0).copy(atMillis = future)),
            TrendRange.DAYS_7,
            now,
        )
        assertEquals(future, window.last)
    }

    @Test
    fun `all range spans first to last`() {
        val entries = listOf(entry("a", 1, 70.0), entry("b", 20, 68.0))
        val window = trendWindow(entries, TrendRange.ALL, at(25))
        assertEquals(at(1), window.first)
        assertEquals(at(20), window.last)
    }

    @Test
    fun `single entry all range is widened`() {
        val only = entry("a", 10, 70.0)
        val window = trendWindow(listOf(only), TrendRange.ALL, at(25))
        assertTrue(window.first < only.atMillis)
        assertTrue(window.last > only.atMillis)
    }

    @Test
    fun `downsample keeps peaks and endpoints`() {
        val entries = (0 until 100).map { i ->
            TrendEntry(id = "e$i", atMillis = i * 1000L, value = if (i == 50) 999.0 else i.toDouble())
        }
        val sampled = downsampleForWidth(
            entries = entries,
            window = 0L..99_000L,
            widthPx = 4f,
            maxPoints = 8,
        )
        assertTrue(sampled.size < entries.size)
        assertEquals("e0", sampled.first().id)
        assertEquals("e99", sampled.last().id)
        assertTrue(sampled.any { it.id == "e50" })
        // 时间仍升序，画线不会回折。
        assertEquals(sampled.map { it.atMillis }.sorted(), sampled.map { it.atMillis })
    }

    @Test
    fun `build chart maps latest value into y range`() {
        val entries = listOf(entry("a", 10, 70.0), entry("b", 12, 72.0))
        val data = buildTrendChart(
            entries = entries,
            range = TrendRange.ALL,
            now = at(20),
            target = null,
            widthPx = 400f,
            zone = zone,
        )
        assertEquals(2, data.visibleCount)
        assertTrue(data.valueMin < 70.0)
        assertTrue(data.valueMax > 72.0)
        val last = data.points.last()
        assertEquals(1f, last.fractionX, 0.0001f)
        assertTrue(last.fractionY in 0f..1f)
    }

    @Test
    fun `build chart includes target in y range`() {
        val entries = listOf(entry("a", 10, 70.0), entry("b", 12, 72.0))
        val data = buildTrendChart(
            entries = entries,
            range = TrendRange.ALL,
            now = at(20),
            target = 90.0,
            widthPx = 400f,
            zone = zone,
        )
        assertTrue(data.valueMax > 90.0)
        assertTrue(data.targetFractionY!! in 0f..1f)
    }

    @Test
    fun `single point keeps a finite position`() {
        val data = buildTrendChart(
            entries = listOf(entry("a", 10, 70.0)),
            range = TrendRange.DAYS_30,
            now = at(20),
            target = null,
            widthPx = 400f,
            zone = zone,
        )
        assertTrue(data.isSinglePoint)
        assertEquals(1, data.points.size)
        assertTrue(data.points.single().fractionX in 0f..1f)
    }

    @Test
    fun `empty window has no points and no target`() {
        val data = buildTrendChart(
            entries = listOf(entry("old", 1, 70.0)),
            range = TrendRange.DAYS_7,
            now = at(20),
            target = 60.0,
            widthPx = 400f,
            zone = zone,
        )
        assertEquals(0, data.visibleCount)
        assertTrue(data.points.isEmpty())
        assertEquals(null, data.targetFractionY)
    }

    @Test
    fun `format helpers keep sign and clamp digits`() {
        assertEquals("+1.2", formatTrendDelta(1.24, 1, java.util.Locale.US))
        assertEquals("-1.2", formatTrendDelta(-1.24, 1, java.util.Locale.US))
        assertEquals("0.0", formatTrendDelta(-0.01, 1, java.util.Locale.US))
        assertEquals("70.0", formatTrendValue(70.0, 1, java.util.Locale.US))
        assertEquals(70.0, roundTrendValue(70.04, 1), 0.0001)
    }
}
