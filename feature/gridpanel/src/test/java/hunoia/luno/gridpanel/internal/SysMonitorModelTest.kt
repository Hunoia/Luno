package hunoia.luno.gridpanel.internal

import hunoia.luno.bridge.system.AppProcessUsage
import hunoia.luno.config.model.SysMonitorWidgetParams
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SysMonitorModelTest {

    private fun row(
        uid: Int,
        label: String,
        cpuPct: Float,
        memKb: Long,
        isSystem: Boolean = false,
    ) = AppProcessUsage(
        uid = uid,
        packageName = "pkg.$label",
        label = label,
        userId = 0,
        isSystem = isSystem,
        cpuPct = cpuPct,
        memKb = memKb,
    )

    @Test
    fun `history keeps window and clamps values`() {
        var history = emptyList<Float>()
        repeat(SysHistorySize + 5) { history = pushSample(history, it / 10f) }
        assertEquals(SysHistorySize, history.size)
        assertEquals(1f, history.last(), 0.0001f)

        val clamped = pushSample(listOf(0.5f), 3f)
        assertEquals(1f, clamped.last(), 0.0001f)

        val ignored = pushSample(listOf(0.5f), Float.NaN)
        assertEquals(listOf(0.5f), ignored)
    }

    @Test
    fun `memory sort groups by half percent of total and keeps previous order inside a bucket`() {
        val totalKb = 1_000_000L
        // 0.5% 总内存 = 5000kB 一档：27000 / 26000 落在同一档（bucket 5）。
        val big = row(1, "big", 0f, 27_000)
        val small = row(2, "small", 0f, 26_000)
        val tiny = row(3, "tiny", 0f, 4_000)
        val previous = mapOf(2 to 0, 1 to 1, 3 to 2)

        val sorted = sortAppRows(listOf(big, small, tiny), SysSort.MEM, totalKb, previous)
        assertEquals(listOf("small", "big", "tiny"), sorted.map { it.label })
    }

    @Test
    fun `cpu sort groups by two percent and keeps previous order inside a bucket`() {
        val a = row(1, "a", 41f, 0)
        val b = row(2, "b", 40f, 0) // 同一档（bucket 20）
        val c = row(3, "c", 10f, 0)
        val previous = mapOf(2 to 0, 1 to 1, 3 to 2)

        val sorted = sortAppRows(listOf(a, b, c), SysSort.CPU, 1_000_000L, previous)
        assertEquals(listOf("b", "a", "c"), sorted.map { it.label })
    }

    @Test
    fun `name sort ignores case`() {
        val rows = listOf(row(1, "Zeta", 0f, 0), row(2, "alpha", 0f, 0))
        assertEquals(listOf("alpha", "Zeta"), sortAppRows(rows, SysSort.NAME, 0L, emptyMap()).map { it.label })
    }

    @Test
    fun `format helpers stay readable across magnitudes`() {
        assertEquals("512K", formatKb(512))
        assertEquals("1.5M", formatKb(1536))
        assertEquals("512M", formatKb(512L * 1024))
        assertEquals("1.5G", formatKb(1536L * 1024))
        assertEquals("320B", formatBytesPerSec(320))
        assertEquals("2K", formatBytesPerSec(2048))
        assertEquals("1.5M", formatBytesPerSec(1536L * 1024))
        assertEquals("800MHz", formatFreqMhz(800))
        assertEquals("2.4GHz", formatFreqMhz(2400))
    }

    @Test
    fun `params round trip and normalize unknown sort`() {
        val encoded = encodeSysMonitorParams(SysSort.CPU, includeSystem = true)
        val decoded = parseSysMonitorParams(encoded)
        assertEquals(SysMonitorWidgetParams.SORT_CPU, decoded.sort)
        assertTrue(decoded.includeSystem)

        val garbage = parseSysMonitorParams("{\"sort\":\"whatever\"}")
        assertEquals(SysMonitorWidgetParams.SORT_MEM, garbage.sort)
        assertEquals(SysSort.MEM, SysSort.of("whatever"))
    }
}
