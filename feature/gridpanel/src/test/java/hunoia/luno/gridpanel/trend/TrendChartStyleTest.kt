package hunoia.luno.gridpanel.trend

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TrendChartStyleTest {

    private val base = Color(0xFF4CAF50)

    @Test
    fun `single series keeps the base color`() {
        assertEquals(base, trendSeriesColor(base, 0, 1))
        assertEquals(base, trendSeriesColor(base, 0, 3))
    }

    @Test
    fun `later series darken in light theme and lighten in dark theme`() {
        val light = trendSeriesColor(base, 1, 3, dark = false)
        val dark = trendSeriesColor(base, 1, 3, dark = true)
        assertNotEquals(base, light)
        assertTrue(light.luminance() < base.luminance())
        assertTrue(dark.luminance() > base.luminance())
    }

    @Test
    fun `point time label follows span granularity`() {
        val zone = ZoneId.of("UTC")
        val millis = LocalDate.of(2026, 1, 10)
            .atTime(8, 5)
            .atZone(zone)
            .toInstant()
            .toEpochMilli()
        assertEquals(
            "08:05",
            trendPointTimeLabel(millis, spanMillis = 2L * 60 * 60 * 1000, zone = zone),
        )
        assertEquals(
            "01-10 08:05",
            trendPointTimeLabel(millis, spanMillis = 30L * 24 * 60 * 60 * 1000, zone = zone),
        )
        assertEquals(
            "2026-01-10",
            trendPointTimeLabel(millis, spanMillis = 400L * 24 * 60 * 60 * 1000, zone = zone),
        )
    }
}
