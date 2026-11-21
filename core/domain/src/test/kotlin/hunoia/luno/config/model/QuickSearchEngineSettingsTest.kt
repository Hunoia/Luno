package hunoia.luno.config.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 引擎顺序/主引擎/首装 seed 与归一化杂项。 */
class QuickSearchEngineSettingsTest {

    private fun engine(id: String, trigger: String = "") = SearchEngine(
        id = id,
        name = id,
        urlTemplate = "https://$id/?q=%s",
        trigger = trigger,
    )

    @Test
    fun `engine order applies and appends unknown ids`() {
        val settings = QuickSearchSettings(
            customEngines = listOf(engine("a"), engine("b"), engine("c")),
            engineOrder = listOf("b", "c"),
        )
        assertEquals(listOf("b", "c", "a"), settings.allEngines().map { it.id })
    }

    @Test
    fun `imported blank ids get stable derived ids`() {
        // 导入引擎 id 撞已有引擎时留空入库，归一化按内容派生稳定 id：
        // 同一份导入数据两次解码-归一化得到同一 id，重复导入可被 order 正确引用。
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

    /** 首装 seed 门禁：三条默认引擎自身合法（原预设表门禁的收窄版）。 */
    @Test
    fun `default seed engines are well formed`() {
        val templates = DefaultSearchEngines.mapTo(mutableSetOf()) { it.urlTemplate }
        val triggers = DefaultSearchEngines.mapTo(mutableSetOf()) { it.trigger.lowercase() }
        for (engine in DefaultSearchEngines) {
            assertTrue("name: ${engine.name}", engine.name.isNotBlank())
            assertTrue("icon: ${engine.name}", engine.badgeText.isNotBlank())
            assertTrue("trigger: ${engine.name}", engine.trigger.isNotBlank())
            assertTrue("template: ${engine.name}", engine.urlTemplate.startsWith("https://"))
            assertTrue("placeholder: ${engine.name}", engine.urlTemplate.contains("%s"))
            assertTrue("launch: ${engine.name}", engine.effectiveLaunch() is SearchLaunch.Url)
        }
        assertEquals("templates must be unique", DefaultSearchEngines.size, templates.size)
        assertEquals("triggers must be unique", DefaultSearchEngines.size, triggers.size)
    }

    @Test
    fun `seed engines survive normalization with stable ids`() {
        val normalized = QuickSearchSettings().withUniqueEngineTriggers()
        assertEquals(DefaultSearchEngines.map { it.name }, normalized.allEngines().map { it.name })
        // 主引擎 = 排序第一位 = seed 首条；再归一化一次 id 不变。
        val id = normalized.customEngines.first().id
        assertTrue(id.isNotBlank())
        assertEquals(id, normalized.withUniqueEngineTriggers().customEngines.first().id)
    }
}
