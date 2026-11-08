package hunoia.luno.capability.notify

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 岛超时的两处 payload 字段（`islandTimeout` 秒 / `timeout` 分钟）必须同源换算：
 * 曾对秒值直接做分钟封顶，13 分钟以上的配置全被截成 12 分钟。
 */
class IslandTimeoutTest {

    @Test
    fun `秒字段原值透传`() {
        assertEquals(0, islandTimeoutSeconds(0))
        assertEquals(300, islandTimeoutSeconds(300))
        assertEquals(3_600, islandTimeoutSeconds(3_600))
    }

    @Test
    fun `分钟字段按秒值换算不截短`() {
        assertEquals(0L, islandTimeoutMinutes(0))
        assertEquals(5L, islandTimeoutMinutes(300))
        assertEquals(30L, islandTimeoutMinutes(1_800))
        assertEquals(60L, islandTimeoutMinutes(3_600))
    }

    @Test
    fun `不足一分钟向上取整`() {
        assertEquals(1L, islandTimeoutMinutes(1))
        assertEquals(1L, islandTimeoutMinutes(59))
    }

    @Test
    fun `两处字段同一封顶`() {
        val overLimit = 24 * 3_600
        assertEquals(12 * 3_600, islandTimeoutSeconds(overLimit))
        assertEquals(720L, islandTimeoutMinutes(overLimit))
    }

    @Test
    fun `负值按不限处理`() {
        assertEquals(0, islandTimeoutSeconds(-1))
        assertEquals(0L, islandTimeoutMinutes(-1))
    }

    @Test
    fun `展开停留限制在有界区间`() {
        assertEquals(1, expandedSeconds(0))
        assertEquals(1, expandedSeconds(-5))
        assertEquals(5, expandedSeconds(5))
        assertEquals(60, expandedSeconds(600))
    }

    @Test
    fun `断网时长按设置取值并夹在区间内`() {
        assertEquals(100L, blockWindowMs(100, fallbackMs = 220L))
        assertEquals(50L, blockWindowMs(10, fallbackMs = 220L))
        assertEquals(2_000L, blockWindowMs(5_000, fallbackMs = 220L))
    }

    @Test
    fun `断网时长未配置时用发布器默认`() {
        assertEquals(220L, blockWindowMs(0, fallbackMs = 220L))
        assertEquals(150L, blockWindowMs(-1, fallbackMs = 150L))
    }
}
