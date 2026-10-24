package hunoia.luno.config.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 引擎 id 稳定性：旧备份/导入数据解码后没有 id，
 * 归一化要按内容派生一个稳定 id（每次解码都一样的），
 * 否则顺序/隐藏/打开方式覆盖会指向不存在的引擎。
 */
class QuickSearchEngineIdTest {

    private fun legacy(name: String = "My Engine", trigger: String = "") = SearchEngine(
        name = name,
        urlTemplate = "https://example.com/?q=%s",
        trigger = trigger,
    )

    @Test
    fun `engine without id gets a stable derived id`() {
        val engine = legacy()
        val first = QuickSearchSettings(customEngines = listOf(engine)).withUniqueEngineTriggers()
        val second = QuickSearchSettings(customEngines = listOf(engine)).withUniqueEngineTriggers()
        val id = first.customEngines.first().id
        assertTrue(id.isNotBlank())
        assertEquals(id, second.customEngines.first().id)
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

    @Test
    fun `hidden result labels are dropped with their keys`() {
        val settings = QuickSearchSettings(
            hiddenResultKeys = listOf("a", "b"),
            hiddenResultLabels = mapOf("a" to "应用 A", "b" to "应用 B", "orphan" to "孤儿"),
        )
        val normalized = settings.withUniqueEngineTriggers()
        assertEquals(setOf("a", "b"), normalized.hiddenResultLabels.keys)
        assertEquals("应用 A", normalized.hiddenResultLabels["a"])
    }
}
