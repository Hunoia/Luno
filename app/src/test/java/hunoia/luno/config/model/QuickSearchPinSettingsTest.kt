package hunoia.luno.config.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 结果 Pin 的容量淘汰：写侧把 query 前插（新的在前），
 * 归一化必须丢最旧的，不能把刚 pin 的那个淘汰掉。
 */
class QuickSearchPinSettingsTest {

    /** 模拟写侧 toggleResultPin：query 前插 + 取前 MAX 个。 */
    private fun pin(settings: QuickSearchSettings, query: String): QuickSearchSettings {
        val order = (listOf(query) + settings.pinnedQueryOrder)
            .distinct()
            .take(MAX_PINNED_QUERIES)
        return settings.copy(
            pinnedResults = settings.pinnedResults + (query to listOf("key-$query")),
            pinnedQueryOrder = order,
        )
    }

    @Test
    fun `newest pinned query survives capacity eviction`() {
        var settings = QuickSearchSettings()
        repeat(MAX_PINNED_QUERIES + 1) { index ->
            settings = pin(settings, "query-$index")
        }
        val normalized = settings.withUniqueEngineTriggers()
        // 刚 pin 的那个必须在
        assertTrue(normalized.pinnedResults.containsKey("query-${MAX_PINNED_QUERIES}"))
        assertEquals("query-${MAX_PINNED_QUERIES}", normalized.pinnedQueryOrder.first())
        // 淘汰的是最旧的
        assertFalse(normalized.pinnedResults.containsKey("query-0"))
    }

    @Test
    fun `pin order and pin map stay consistent`() {
        val settings = QuickSearchSettings(
            pinnedResults = mapOf("a" to listOf("k1"), "b" to listOf("k2")),
            pinnedQueryOrder = listOf("b", "a"),
        )
        val normalized = settings.withUniqueEngineTriggers()
        assertEquals(normalized.pinnedQueryOrder.toSet(), normalized.pinnedResults.keys)
    }

    @Test
    fun `empty pin buckets are dropped`() {
        val settings = QuickSearchSettings(
            pinnedResults = mapOf("a" to emptyList()),
            pinnedQueryOrder = listOf("a"),
        )
        val normalized = settings.withUniqueEngineTriggers()
        assertTrue(normalized.pinnedResults.isEmpty())
        assertTrue(normalized.pinnedQueryOrder.isEmpty())
    }
}
