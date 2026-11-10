package hunoia.luno.gridpanel.habit

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** 热力图几何 / 分档的唯一映射回归：内置部件与桌面部件必须同列数、同深浅。 */
class HabitHeatmapGeometryTest {

    @Test
    fun `step stays within readable bounds`() {
        assertEquals(4.dp, habitHeatmapGeometry(200.dp, 10.dp).step)
        assertEquals(18.dp, habitHeatmapGeometry(200.dp, 500.dp).step)
    }

    @Test
    fun `columns adapt to width and clamp between one and thirteen`() {
        // step = 18dp、宽 90dp → 5 列。
        assertEquals(5, habitHeatmapGeometry(90.dp, 126.dp).cols)
        // 极宽也只到 13 列。
        assertEquals(13, habitHeatmapGeometry(1000.dp, 126.dp).cols)
        // 极窄至少 1 列。
        assertEquals(1, habitHeatmapGeometry(10.dp, 126.dp).cols)
    }

    @Test
    fun `last column is the current week and today keeps its weekday row`() {
        val today = LocalDate.of(2026, 11, 10).toEpochDay() // 周二
        val cols = 13
        val firstDay = habitHeatmapFirstDay(today, cols)
        val todayDow = LocalDate.ofEpochDay(today).dayOfWeek.value - 1
        assertEquals(today, firstDay + (cols - 1) * HabitHeatmapRows + todayDow)
        assertEquals(0, LocalDate.ofEpochDay(firstDay).dayOfWeek.value - 1)
    }

    @Test
    fun `single-target checked day is the darkest level`() {
        val day = 100L
        assertEquals(5, habitHeatmapLevel(day, setOf(day), emptyMap(), 1))
        assertEquals(0, habitHeatmapLevel(day, emptySet(), emptyMap(), 1))
    }

    @Test
    fun `multi-target level scales with partial progress`() {
        val day = 100L
        assertEquals(1, habitHeatmapLevel(day, emptySet(), mapOf(day to 1), 5))
        assertEquals(2, habitHeatmapLevel(day, emptySet(), mapOf(day to 1), 3))
        assertEquals(3, habitHeatmapLevel(day, emptySet(), mapOf(day to 2), 3))
        assertEquals(5, habitHeatmapLevel(day, setOf(day), emptyMap(), 3))
    }

    @Test
    fun `level alpha has five increasing steps`() {
        assertEquals(5, HabitHeatmapLevelAlpha.size)
        assertTrue(
            (0 until HabitHeatmapLevelAlpha.lastIndex).all { index ->
                HabitHeatmapLevelAlpha[index] < HabitHeatmapLevelAlpha[index + 1]
            },
        )
    }
}
