package hunoia.luno.config.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 引擎顺序/主引擎/默认回退与归一化杂项。 */
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
    fun `effective engines equal all engines`() {
        val settings = QuickSearchSettings(
            customEngines = listOf(engine("a"), engine("b")),
            engineOrder = listOf("b", "a"),
        )
        assertEquals(settings.allEngines(), settings.effectiveEngines())
    }

    @Test
    fun `primary engine is first engine in order or null when empty`() {
        val settings = QuickSearchSettings(
            customEngines = listOf(engine("a"), engine("b")),
            engineOrder = listOf("b", "a"),
        )
        assertEquals("b", settings.primaryEngineOrNull()?.id)
        assertNull(QuickSearchSettings().primaryEngineOrNull())
    }

    @Test
    fun `legacy default engine migrates to front of order`() {
        val settings = QuickSearchSettings(
            customEngines = listOf(engine("a"), engine("b")),
            defaultEngineId = "b",
            engineOrder = listOf("a", "b"),
        ).withUniqueEngineTriggers()
        assertEquals(listOf("b", "a"), settings.engineOrder)
        // 迁移一次后清空，之后不再干预拖拽顺序。
        assertEquals("", settings.defaultEngineId)
        val again = settings.withUniqueEngineTriggers()
        assertEquals(settings.engineOrder, again.engineOrder)
    }

    @Test
    fun `legacy default engine order is derived when order empty`() {
        val settings = QuickSearchSettings(
            customEngines = listOf(engine("a"), engine("b")),
            defaultEngineId = "b",
        ).withUniqueEngineTriggers()
        assertEquals("b", settings.engineOrder.firstOrNull())
        assertEquals("", settings.defaultEngineId)
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
}
