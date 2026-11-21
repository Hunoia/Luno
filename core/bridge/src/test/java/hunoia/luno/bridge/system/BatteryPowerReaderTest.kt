package hunoia.luno.bridge.system

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BatteryPowerReaderTest {

    @Test
    fun `microamp reading is converted to milliwatts`() {
        // 500_000 µA = 500 mA；500mA × 4V = 2W = 2000mW。
        assertEquals(2000f, normalizePowerMw(500_000L, 4f)!!, 0.5f)
    }

    @Test
    fun `milliamp rom reading is also accepted`() {
        // 部分 ROM 把 CURRENT_NOW 报成 mA：500 → 500mA × 4V = 2W。
        assertEquals(2000f, normalizePowerMw(500L, 4f)!!, 0.5f)
    }

    @Test
    fun `missing voltage falls back to a typical value`() {
        assertEquals(2000f, normalizePowerMw(500_000L, null)!!, 0.5f)
    }

    @Test
    fun `unusable readings become null instead of zero`() {
        assertNull(normalizePowerMw(null, 4f))
        assertNull(normalizePowerMw(0L, 4f))
        assertNull(normalizePowerMw(Long.MIN_VALUE, 4f))
        assertNull(normalizePowerMw(Int.MIN_VALUE.toLong(), 4f))
        // 两种解释都不在合理功率区间。
        assertNull(normalizePowerMw(1L, 4f))
    }

    @Test
    fun `idle current stays in the microamp interpretation`() {
        // 30_000 µA = 30mA × 4V = 0.12W = 120mW。
        assertEquals(120f, normalizePowerMw(30_000L, 4f)!!, 0.5f)
        // 报成 mA 的 30 也应得到同一结果。
        assertEquals(120f, normalizePowerMw(30L, 4f)!!, 0.5f)
    }
}
