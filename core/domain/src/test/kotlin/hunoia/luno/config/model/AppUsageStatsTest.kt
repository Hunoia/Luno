package hunoia.luno.config.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppUsageStatsTest {

    @Test
    fun `records package count and recent time`() {
        val out = AppUsageStats().recordLaunch("com.example.app", now = 1_000L)
        assertEquals(1L, out.launchCount["com.example.app"])
        assertEquals(1_000L, out.recentLaunchTime["com.example.app"])
    }

    @Test
    fun `duplicate launch inside debounce window is ignored`() {
        val first = AppUsageStats().recordLaunch("pkg", now = 1_000L, minIntervalMs = 300L)
        val second = first.recordLaunch("pkg", now = 1_200L, minIntervalMs = 300L)
        assertTrue(first === second)
        assertEquals(1L, second.launchCount["pkg"])
    }

    @Test
    fun `clock going backwards does not freeze recording`() {
        val first = AppUsageStats().recordLaunch("pkg", now = 10_000L)
        val second = first.recordLaunch("pkg", now = 9_000L)
        assertEquals(2L, second.launchCount["pkg"])
        assertEquals(9_000L, second.recentLaunchTime["pkg"])
    }

    @Test
    fun `launch outside debounce window increments`() {
        val first = AppUsageStats().recordLaunch("pkg", now = 1_000L, minIntervalMs = 300L)
        val second = first.recordLaunch("pkg", now = 1_300L, minIntervalMs = 300L)
        assertEquals(2L, second.launchCount["pkg"])
        assertEquals(1_300L, second.recentLaunchTime["pkg"])
    }

    @Test
    fun `history cap trims oldest package and its count`() {
        var stats = AppUsageStats()
        stats = stats.recordLaunch("old", now = 1_000L, maxHistory = 2, minIntervalMs = 0L)
        stats = stats.recordLaunch("mid", now = 2_000L, maxHistory = 2, minIntervalMs = 0L)
        stats = stats.recordLaunch("new", now = 3_000L, maxHistory = 2, minIntervalMs = 0L)

        assertFalse("old" in stats.recentLaunchTime)
        assertFalse("old" in stats.launchCount)
        assertTrue("mid" in stats.launchCount)
        assertTrue("new" in stats.launchCount)
    }
}
