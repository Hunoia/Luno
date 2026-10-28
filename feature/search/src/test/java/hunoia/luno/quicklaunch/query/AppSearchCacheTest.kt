package hunoia.luno.quicklaunch.query

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 拼音索引缓存的边界行为：有界、去重、结果等价。
 * 纯 JVM 跑（androidx.collection.LruCache 本身是纯 Java 工件，不依赖 android 运行时）。
 */
class AppSearchCacheTest {

    @Test
    fun `cache stays bounded under many distinct entries`() {
        // 5.2 万条 > 4.8 万上限：靠 LRU 淘汰收敛，不无限增长。
        repeat(52_000) { AppSearch.buildIndex("cachebound$it") }
        assertTrue(
            "缓存应为有界（≤ 48000），实际 ${AppSearch.cacheSizeForTest()}",
            AppSearch.cacheSizeForTest() <= 48_000,
        )
    }

    @Test
    fun `long texts sharing the same first 200 chars share one index`() {
        // 索引只由前 200 字决定：长文本用截断串做 key，同前缀共用一份。
        val head = "长".repeat(200)
        val a = AppSearch.buildIndex(head + "后缀A".repeat(100))
        val b = AppSearch.buildIndex(head + "后缀B".repeat(100))
        assertSame("同前缀长文本应命中同一份索引", a, b)
    }

    @Test
    fun `uppercase and lowercase share one cache entry`() {
        val lower = AppSearch.buildIndex("tieba")
        val upper = AppSearch.buildIndex("TieBa")
        assertSame("大小写应共用同一份索引", lower, upper)
        assertEquals("tieba", upper.raw)
    }

    @Test
    fun `index content is unchanged for indexed prefix`() {
        // 截断参与缓存去重后，前 200 字内的匹配行为保持不变。
        val text = "番".repeat(50) + "茄小说"
        val idx = AppSearch.buildIndex(text)
        assertEquals(200.coerceAtMost(text.length), idx.raw.length)
        assertTrue(idx.initials.isNotEmpty())
    }
}
