package hunoia.luno.quickaccess.query

import hunoia.luno.config.model.SearchEngine
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 引擎行的 key / 图标 / 动作契约：默认行与「标识直达」行现已共用同一构造
 * （[engineResultRow]，标识直达与默认兜底都经它），这里钉住对外形状。
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
