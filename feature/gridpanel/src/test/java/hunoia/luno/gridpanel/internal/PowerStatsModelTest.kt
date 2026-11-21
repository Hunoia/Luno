package hunoia.luno.gridpanel.internal

import hunoia.luno.config.model.PowerStatsWidgetParams
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 耗电段解析回归。fixture 是 2026-11-21 从真机（24129PN74C / Android 14）抓的
 * `dumpsys batterystats --charged` 耗电段原文，不手写格式——真机格式才是唯一标准。
 */
class PowerStatsModelTest {

    private val fixture: String by lazy {
        checkNotNull(javaClass.classLoader?.getResourceAsStream("batterystats_power.txt")) {
            "缺少 fixture: batterystats_power.txt"
        }.bufferedReader().use { it.readText() }
    }

    @Test
    fun `parses capacity and drain totals from real dump`() {
        val snapshot = checkNotNull(parseBatteryStatsPower(fixture))
        assertEquals(4680f, snapshot.capacityMah, 0.5f)
        assertEquals(433f, snapshot.computedDrainMah, 0.5f)
        assertNotNull(snapshot.actualDrainMah)
        assertEquals(433f, snapshot.actualDrainMah!!, 0.5f)
    }

    @Test
    fun `parses every top-level uid line`() {
        val snapshot = checkNotNull(parseBatteryStatsPower(fixture))
        // fixture 里 117 条顶层 UID 行，解析结果不能少。
        assertEquals(117, snapshot.entries.size)
        // uid 不重复。
        assertEquals(snapshot.entries.size, snapshot.entries.map { it.uid }.toSet().size)
    }

    @Test
    fun `maps u-prefixed uid tokens to real uid`() {
        // u0a496 → 0 * 100000 + 10000 + 496 = 10496
        assertEquals(10496, parseBatteryStatsUid("u0a496"))
        // 纯数字原样保留（system / 1000 / root 0）。
        assertEquals(1000, parseBatteryStatsUid("1000"))
        assertEquals(0, parseBatteryStatsUid("0"))
        // 多用户：u10a496 → 10 * 100000 + 10496
        assertEquals(1010496, parseBatteryStatsUid("u10a496"))
        assertNull(parseBatteryStatsUid(""))
        assertNull(parseBatteryStatsUid("garbage"))
    }

    @Test
    fun `isolated uid tokens are parsed not dropped`() {
        // 真机 dump 里有 `UID u999s1000:` 这类 isolated 进程行。
        assertEquals(99900000 + 90000 + 1000, parseBatteryStatsUid("u999s1000"))
        val snapshot = checkNotNull(parseBatteryStatsPower(fixture))
        assertTrue(snapshot.entries.any { it.uid == 99900000 + 90000 + 1000 })
    }

    @Test
    fun `known entries match the dump`() {
        val snapshot = checkNotNull(parseBatteryStatsPower(fixture))
        val byUid = snapshot.entries.associateBy { it.uid }
        // 段内第一条是 UID 1000: 108
        assertEquals(108f, byUid.getValue(1000).powerMah, 0.01f)
        // UID u0a496: 91.6
        assertEquals(91.6f, byUid.getValue(10496).powerMah, 0.01f)
        // UID 0: 54.3
        assertEquals(54.3f, byUid.getValue(0).powerMah, 0.01f)
    }

    @Test
    fun `sub-section lines are not mistaken for uid entries`() {
        val snapshot = checkNotNull(parseBatteryStatsPower(fixture))
        // 子段行形如 `      cpu=35.3 cpu:fg=0.0780 ...`，缩进 6 空格，不该产生条目。
        // 若误解析，条目数会远超 117。
        assertTrue(snapshot.entries.size <= 117)
        // 所有条目耗电非负且有限。
        snapshot.entries.forEach {
            assertTrue("uid ${it.uid} 耗电异常: ${it.powerMah}", it.powerMah.isFinite() && it.powerMah >= 0f)
        }
    }

    @Test
    fun `empty or unrelated text yields null`() {
        assertNull(parseBatteryStatsPower(""))
        assertNull(parseBatteryStatsPower("   \n  \n"))
        assertNull(parseBatteryStatsPower("Some other section:\n  nothing here\n"))
        // 有段头但一条 uid 都没有：视为解析失败，走降级。
        assertNull(parseBatteryStatsPower("  Estimated power use (mAh):\n    Capacity: 4000\n"))
    }

    @Test
    fun `lowercase Uid spelling is accepted`() {
        // 旧版本 / 部分 ROM 写作 `Uid`，解析器要兼容。
        // 缩进是解析依据（顶层 2 空格 / 子段 6 空格），不能用 trimIndent 抹平。
        val text = listOf(
            "  Estimated power use (mAh):",
            "    Capacity: 4000, Computed drain: 100, actual drain: 100",
            "    Global",
            "    cpu: 100 apps: 100 duration: 1h",
            "  Uid u0a100: 40.0 bg: 40.0",
            "      cpu=40.0 cpu:bg=40.0",
            "  Uid 1000: 60.0 bg: 60.0",
            "      cpu=60.0 cpu:bg=60.0",
        ).joinToString("\n")
        val snapshot = checkNotNull(parseBatteryStatsPower(text))
        assertEquals(2, snapshot.entries.size)
        assertEquals(10000 + 100, snapshot.entries[0].uid)
        assertEquals(40f, snapshot.entries[0].powerMah, 0.01f)
    }

    @Test
    fun `actual drain range takes midpoint`() {
        val text = listOf(
            "  Estimated power use (mAh):",
            "    Capacity: 4000, Computed drain: 100, actual drain: 90-110",
            "  UID 1000: 100 bg: 100",
        ).joinToString("\n")
        val snapshot = checkNotNull(parseBatteryStatsPower(text))
        assertEquals(100f, snapshot.actualDrainMah!!, 0.01f)
    }

    @Test
    fun `missing computed drain falls back to sum of entries`() {
        val text = listOf(
            "  Estimated power use (mAh):",
            "    Capacity: 4000",
            "  UID 1000: 30 bg: 30",
            "  UID 1001: 70 bg: 70",
        ).joinToString("\n")
        val snapshot = checkNotNull(parseBatteryStatsPower(text))
        assertEquals(100f, snapshot.computedDrainMah, 0.01f)
    }

    // ---- 整机组件 / 前后台拆分 / 时长 ----

    @Test
    fun `parses global component breakdown with durations`() {
        val snapshot = checkNotNull(parseBatteryStatsPower(fixture))
        val byName = snapshot.components.associateBy { it.name }
        assertEquals(33.5f, byName.getValue("screen").powerMah, 0.01f)
        assertEquals(365f, byName.getValue("cpu").powerMah, 0.01f)
        assertEquals(34.5f, byName.getValue("audio").powerMah, 0.01f)
        assertEquals(6.34f, byName.getValue("wifi").powerMah, 0.01f)
        assertEquals(10.3f, byName.getValue("mobile_radio").powerMah, 0.01f)
        assertEquals(parseDurationMs("1h 7m 48s 220ms"), byName.getValue("cpu").durationMs)
        // screen 行没有 duration，不能凭空造一个。
        assertEquals(0L, byName.getValue("screen").durationMs)
    }

    @Test
    fun `parses per-uid foreground background split and runtime`() {
        val snapshot = checkNotNull(parseBatteryStatsPower(fixture))
        val u = snapshot.entries.associateBy { it.uid }.getValue(10496)
        assertEquals(0.0978f, u.fgMah, 0.0001f)
        assertEquals(6.73f, u.bgMah, 0.01f)
        assertEquals(84.7f, u.fgsMah, 0.01f)
        assertEquals(0.000813f, u.cachedMah, 0.00001f)
        assertEquals(parseDurationMs("3s 119ms"), u.fgMs)
        assertEquals(parseDurationMs("53m 46s 464ms"), u.fgsMs)
        assertEquals(parseDurationMs("38ms"), u.cachedMs)
    }

    @Test
    fun `parses aggregate detail components per uid without overriding totals`() {
        val snapshot = checkNotNull(parseBatteryStatsPower(fixture))
        val u = snapshot.entries.associateBy { it.uid }.getValue(10496)
        // 总项 cpu=58.7；cpu:fg / cpu:bg 等拆分不得覆盖它。
        assertEquals(58.7f, u.componentsMah.getValue("cpu"), 0.01f)
        assertEquals(32.4f, u.componentsMah.getValue("audio"), 0.01f)
        assertTrue(u.componentsMah.containsKey("wifi"))
        assertTrue(u.componentsMah.containsKey("wakelock"))
        // `:fg` 之类的键不应进入组件表。
        assertTrue(u.componentsMah.keys.none { it.contains(':') })
    }

    @Test
    fun `duration parser handles every unit and ignores garbage`() {
        assertEquals(3_600_000L, parseDurationMs("1h"))
        assertEquals(60_000L, parseDurationMs("1m"))
        assertEquals(1_000L, parseDurationMs("1s"))
        assertEquals(220L, parseDurationMs("220ms"))
        assertEquals(4_028_220L, parseDurationMs("1h 7m 8s 220ms"))
        assertEquals(0L, parseDurationMs(null))
        assertEquals(0L, parseDurationMs("garbage"))
    }

    @Test
    fun `duration formatting stays compact`() {
        assertEquals("1h 7m", formatDuration(parseDurationMs("1h 7m 48s 220ms")))
        assertEquals("53m", formatDuration(parseDurationMs("53m 46s 464ms")))
        assertEquals("24s", formatDuration(24_000L))
        assertEquals("—", formatDuration(0L))
    }

    // ---- 排序 ----

    private fun row(uid: Int, label: String, share: Float, isSystem: Boolean = false) = PowerRow(
        uid = uid,
        label = label,
        packageName = "pkg.$label",
        isSystem = isSystem,
        powerMah = share,
        sharePct = share,
    )

    @Test
    fun `power sort orders by bucket then keeps previous order`() {
        val rows = listOf(
            row(1, "alpha", 5.2f),
            row(2, "beta", 5.9f),
            row(3, "gamma", 30.1f),
        )
        // 5.2 与 5.9 同档（都 toInt()==5），按 previousOrder 决定先后。
        val sorted = sortPowerRows(
            rows,
            PowerSort.POWER,
            mapOf("uid:2" to 0, "uid:1" to 1, "uid:3" to 2),
        )
        assertEquals(listOf(3, 2, 1), sorted.map { it.uid })
    }

    @Test
    fun `name sort is locale independent and stable`() {
        val rows = listOf(row(1, "Zeta", 1f), row(2, "alpha", 2f), row(3, "Beta", 3f))
        val sorted = sortPowerRows(rows, PowerSort.NAME, emptyMap())
        assertEquals(listOf("alpha", "Beta", "Zeta"), sorted.map { it.label })
    }

    @Test
    fun `bucket is one percent wide`() {
        assertEquals(0, powerBucket(row(1, "a", 0.9f)))
        assertEquals(5, powerBucket(row(1, "a", 5.9f)))
        assertEquals(30, powerBucket(row(1, "a", 30.9f)))
    }

    // ---- 格式化 ----

    @Test
    fun `mah formatting scales with magnitude`() {
        assertEquals("412 mAh", formatMah(412.4f))
        assertEquals("12 mAh", formatMah(12.3f))
        assertEquals("3.4 mAh", formatMah(3.42f))
        assertEquals("0.83 mAh", formatMah(0.834f))
        assertEquals("—", formatMah(-1f))
        assertEquals("—", formatMah(Float.NaN))
    }

    @Test
    fun `share formatting rounds and guards zero`() {
        assertEquals("33%", formatSharePct(33.4f))
        assertEquals("17%", formatSharePct(16.6f))
        assertEquals("—", formatSharePct(0f))
        assertEquals("—", formatSharePct(Float.NaN))
    }

    @Test
    fun `power formatting switches between watts and milliwatts`() {
        assertEquals("2.0 W", formatPowerMw(2000f))
        assertEquals("20.5 W", formatPowerMw(20_500f))
        assertEquals("850 mW", formatPowerMw(850f))
        assertEquals("—", formatPowerMw(null))
        assertEquals("—", formatPowerMw(Float.NaN))
        assertEquals("—", formatPowerMw(-1f))
    }

    // ---- 参数 ----

    @Test
    fun `params round trip and normalize unknown sort`() {
        val encoded = encodePowerStatsParams(PowerSort.NAME, includeSystem = true, range = PowerRange.D7)
        val parsed = parsePowerStatsParams(encoded)
        assertEquals(PowerStatsWidgetParams.SORT_NAME, parsed.sort)
        assertTrue(parsed.includeSystem)
        assertEquals(PowerStatsWidgetParams.RANGE_7D, parsed.chartRange)

        // 未知 sort / range 归一化为默认值。
        val unknown = parsePowerStatsParams("""{"sort":"bogus","chartRange":"bogus"}""")
        assertEquals(PowerStatsWidgetParams.SORT_POWER, unknown.sort)
        assertEquals(PowerStatsWidgetParams.RANGE_24H, unknown.chartRange)
        // 空 / 非法 JSON 走默认值。
        assertEquals(PowerStatsWidgetParams(), parsePowerStatsParams(null))
        assertEquals(PowerStatsWidgetParams(), parsePowerStatsParams("{not json"))
    }

    @Test
    fun `sort and range enums map unknown keys to defaults`() {
        assertEquals(PowerSort.POWER, PowerSort.of("power"))
        assertEquals(PowerSort.NAME, PowerSort.of("name"))
        assertEquals(PowerSort.POWER, PowerSort.of("bogus"))
        assertEquals(PowerSort.POWER, PowerSort.of(null))

        assertEquals(PowerRange.H24, PowerRange.of("24h"))
        assertEquals(PowerRange.D7, PowerRange.of("7d"))
        assertEquals(PowerRange.H24, PowerRange.of("bogus"))
        assertEquals(PowerRange.H24, PowerRange.of(null))
    }
}
