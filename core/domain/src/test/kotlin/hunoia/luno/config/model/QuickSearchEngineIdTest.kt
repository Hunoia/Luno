package hunoia.luno.config.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 引擎 id 稳定性：旧备份/导入数据解码后没有 id，
 * 归一化要按内容派生一个稳定 id（每次解码都一样的），
 * 否则顺序会指向不存在的引擎。
 */
class QuickSearchEngineIdTest {

    private fun legacy(name: String = "My Engine", trigger: String = "") = SearchEngine(
        name = name,
        urlTemplate = "https://example.com/?q=%s",
        trigger = trigger,
    )

    @Test
    fun `engine without id gets a stable derived id`() {
        // 导入引擎 id 撞已有引擎时留空入库，归一化按内容派生稳定 id：
        // 同一份导入数据两次解码-归一化得到同一 id，重复导入可被 order 正确引用。
        for (fixture in listOf(
            legacy(),
            SearchEngine(
                name = "Naver",
                urlTemplate = "https://search.naver.com/search.naver?query=%s",
                trigger = "nv",
            ),
        )) {
            val first = QuickSearchSettings(customEngines = listOf(fixture)).withUniqueEngineTriggers()
            val second = QuickSearchSettings(customEngines = listOf(fixture)).withUniqueEngineTriggers()
            val id = first.customEngines.first().id
            assertTrue(id.isNotBlank())
            assertEquals(id, second.customEngines.first().id)
        }
    }

    @Test
    fun `engine order reference survives id normalization`() {
        val normalized = QuickSearchSettings(customEngines = listOf(legacy()))
            .withUniqueEngineTriggers()
        val id = normalized.customEngines.first().id
        val ordered = normalized.copy(engineOrder = listOf(id)).withUniqueEngineTriggers()
        assertEquals(listOf(id), ordered.engineOrder)
        assertEquals(id, ordered.customEngines.first().id)
    }

    @Test
    fun `duplicate content engines get distinct ids`() {
        val normalized = QuickSearchSettings(customEngines = listOf(legacy(), legacy()))
            .withUniqueEngineTriggers()
        assertEquals(2, normalized.customEngines.map { it.id }.toSet().size)
    }

    @Test
    fun `existing ids are never rewritten`() {
        val engine = legacy().copy(id = "keep-me")
        val normalized = QuickSearchSettings(customEngines = listOf(engine)).withUniqueEngineTriggers()
        assertEquals("keep-me", normalized.customEngines.first().id)
    }
}
