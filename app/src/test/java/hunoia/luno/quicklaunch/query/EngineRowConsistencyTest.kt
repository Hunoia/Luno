package hunoia.luno.quicklaunch.query

import hunoia.luno.config.model.SearchEngine
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 引擎行的两条产出路径必须完全一致：
 * 默认引擎行（buildEngineResultRows）与「标识直达」行（QuickSearchResult.EngineSearch）。
 * key 不同会导致同一引擎出两行，图标文字/动作缺失会让行为前后不一。
 */
class EngineRowConsistencyTest {

    private val engine = SearchEngine(
        id = "bing",
        name = "Bing",
        urlTemplate = "https://www.bing.com/search?q=%s",
        trigger = "bb",
        iconText = "丙",
    )

    @Test
    fun `directed engine row matches default engine row`() {
        val fromDirective = QuickSearchResult.EngineSearch("新闻", "用 Bing 搜索", engine)
            .toSearchResult()
        val fromDefault = engineResultRow("用 Bing 搜索", "新闻", engine)
        assertEquals(fromDefault.key, fromDirective.key)
        assertEquals(fromDefault.icon, fromDirective.icon)
        assertEquals(fromDefault.primary, fromDirective.primary)
        assertEquals(fromDefault.actions, fromDirective.actions)
    }

    @Test
    fun `engine row key is engine id plus query`() {
        assertEquals("es#bing|新闻", engineResultRow("用 Bing 搜索", "新闻", engine).key)
    }

    @Test
    fun `engine icon text wins over name initial`() {
        val row = engineResultRow("用 Bing 搜索", "新闻", engine)
        assertEquals(SearchIcon.Text("丙"), row.icon)
    }

    @Test
    fun `engine icon falls back to name initial`() {
        val plain = engine.copy(iconText = "")
        val row = engineResultRow("用 Bing 搜索", "新闻", plain)
        assertEquals(SearchIcon.Text("B"), row.icon)
    }
}
