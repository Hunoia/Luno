package hunoia.luno.config.store

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

/**
 * 设置流兜底缓存的语义：兜底值只用于「还没拿到过真值」的窗口，
 * 一旦流发出真值就必须是真实值（见 SettingsFlowCache 的注释：空默认会让列表偏移被钳住）。
 */
class SettingsFlowCacheTest {

    @Test
    fun fallbackIsUsedUntilTheFlowEmits() {
        val cache = SettingsFlowCache { "default" }
        assertEquals("default", cache.latest())
    }

    @Test
    fun observePassesValuesThroughAndPublishesThem() = runBlocking {
        val cache = SettingsFlowCache { "default" }
        val real = "real"
        val observed = cache.observe(flowOf(real))
        assertSame(real, observed.first())
        assertSame(real, cache.latest())
    }

    @Test
    fun latestFollowsTheMostRecentEmission() = runBlocking {
        val cache = SettingsFlowCache { "default" }
        cache.observe(flowOf("first", "second")).collect { }
        assertEquals("second", cache.latest())
    }
}
