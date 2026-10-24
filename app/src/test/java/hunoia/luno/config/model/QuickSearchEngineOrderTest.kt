package hunoia.luno.config.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 引擎换序（拖拽/上移下移共用）与隐藏结果时间戳归一化。
 */
class QuickSearchEngineOrderTest {

    @Test
    fun `move down inserts after target`() {
        val order = listOf("a", "b", "c", "d")
        assertEquals(listOf("b", "c", "a", "d"), moveEngineBetween(order, "a", "c"))
    }

    @Test
    fun `move up inserts before target`() {
        val order = listOf("a", "b", "c", "d")
        assertEquals(listOf("a", "d", "b", "c"), moveEngineBetween(order, "d", "b"))
    }

    @Test
    fun `move to self or unknown is no-op`() {
        val order = listOf("a", "b")
        assertEquals(order, moveEngineBetween(order, "a", "a"))
        assertEquals(order, moveEngineBetween(order, "a", "missing"))
        assertEquals(order, moveEngineBetween(order, "missing", "b"))
    }

    @Test
    fun `adjacent swap works both directions`() {
        assertEquals(listOf("b", "a", "c"), moveEngineBetween(listOf("a", "b", "c"), "a", "b"))
        assertEquals(listOf("a", "c", "b"), moveEngineBetween(listOf("a", "b", "c"), "c", "b"))
    }

    @Test
    fun `hidden result times are dropped with their keys`() {
        val settings = QuickSearchSettings(
            hiddenResultKeys = listOf("a", "b"),
            hiddenResultTimes = mapOf("a" to 10L, "b" to 20L, "orphan" to 30L),
        )
        val normalized = settings.withUniqueEngineTriggers()
        assertEquals(mapOf("a" to 10L, "b" to 20L), normalized.hiddenResultTimes)
    }

    @Test
    fun `imported blank ids get stable derived ids`() {
        // 导入引擎 id 撞已有引擎时留空入库，归一化按内容派生稳定 id：
        // 同一份导入数据两次解码-归一化得到同一 id，重复导入可被 order/hidden 正确引用。
        val imported = SearchEngine(
            name = "Naver",
            urlTemplate = "https://search.naver.com/search.naver?query=%s",
            trigger = "nv",
        )
        val first = QuickSearchSettings(customEngines = listOf(imported)).withUniqueEngineTriggers()
        val second = QuickSearchSettings(customEngines = listOf(imported)).withUniqueEngineTriggers()
        val id = first.customEngines.first().id
        assertTrue(id.isNotBlank())
        assertEquals(id, second.customEngines.first().id)
    }
}
