package hunoia.luno.config.model

import org.junit.Assert.assertEquals
import org.junit.Test

/** 引擎顺序/隐藏/内置覆盖/默认回退。 */
class QuickSearchEngineSettingsTest {

    @Test
    fun `engine order applies and appends unknown ids`() {
        val settings = QuickSearchSettings(
            customEngines = listOf(
                SearchEngine(id = "c1", name = "C1", urlTemplate = "https://c1/?q=%s"),
            ),
            engineOrder = listOf("google", "c1"),
        )
        assertEquals(listOf("google", "c1", "bing", "duckduckgo"), settings.allEngines().map { it.id })
    }

    @Test
    fun `hidden engines are excluded from effective list`() {
        val settings = QuickSearchSettings(hiddenEngineIds = listOf("bing"))
        assertEquals(listOf("google", "duckduckgo"), settings.effectiveEngines().map { it.id })
    }

    @Test
    fun `builtin launch override wins over legacy template`() {
        val settings = QuickSearchSettings(
            engineLaunchOverrides = mapOf(
                "bing" to SearchLaunch.Url("https://override/?q=%s", packageName = "com.app"),
            ),
        )
        val bing = settings.allEngines().first { it.id == "bing" }
        assertEquals(
            SearchLaunch.Url("https://override/?q=%s", packageName = "com.app"),
            bing.effectiveLaunch(),
        )
    }

    @Test
    fun `primary engine is first visible engine`() {
        val settings = QuickSearchSettings(hiddenEngineIds = listOf("bing"))
        assertEquals("google", settings.primaryEngineOrNull()?.id)
    }

    @Test
    fun `all hidden engines are normalized back to visible`() {
        val settings = QuickSearchSettings(
            hiddenEngineIds = listOf("bing", "google", "duckduckgo"),
        ).withUniqueEngineTriggers()
        assertEquals(emptyList<String>(), settings.hiddenEngineIds)
        assertEquals("bing", settings.primaryEngineOrNull()?.id)
    }

    @Test
    fun `legacy default engine migrates to front of order`() {
        val settings = QuickSearchSettings(
            defaultEngineId = "duckduckgo",
            engineOrder = listOf("bing", "google", "duckduckgo"),
        ).withUniqueEngineTriggers()
        assertEquals(listOf("duckduckgo", "bing", "google"), settings.engineOrder)
        // 迁移一次后清空，之后不再干预拖拽顺序。
        assertEquals("", settings.defaultEngineId)
        val again = settings.withUniqueEngineTriggers()
        assertEquals(settings.engineOrder, again.engineOrder)
    }

    @Test
    fun `legacy default engine order is derived when order empty`() {
        val settings = QuickSearchSettings(
            defaultEngineId = "google",
        ).withUniqueEngineTriggers()
        assertEquals("google", settings.engineOrder.firstOrNull())
        assertEquals("", settings.defaultEngineId)
    }

    @Test
    fun `hidden legacy default engine is not migrated`() {
        val settings = QuickSearchSettings(
            defaultEngineId = "bing",
            hiddenEngineIds = listOf("bing"),
        ).withUniqueEngineTriggers()
        // 默认引擎已隐藏：不前移（前移也没意义），只清遗留字段。
        assertEquals(emptyList<String>(), settings.engineOrder)
        assertEquals("", settings.defaultEngineId)
    }
}
