package hunoia.luno.quicklaunch.query

import hunoia.luno.config.model.QuickSearchSettings
import org.junit.Assert.assertEquals
import org.junit.Test

/** 结果 Pin/Hide 的纯函数行为。 */
class SearchResultOverridesTest {

    private fun result(key: String, source: String = "app") = SearchResult(
        key = key,
        source = source,
        title = key,
        subtitle = "",
        icon = SearchIcon.None,
        primary = SearchAction.Copy(key),
    )

    @Test
    fun `hidden results are filtered globally`() {
        val settings = QuickSearchSettings(hiddenResultKeys = listOf("a"))
        val out = listOf(result("a"), result("b")).filterHiddenResults(settings)
        assertEquals(listOf("b"), out.map { it.key })
    }

    @Test
    fun `engine rows cannot be hidden`() {
        val settings = QuickSearchSettings(hiddenResultKeys = listOf("es#x"))
        val out = listOf(result("es#x", SOURCE_ENGINE)).filterHiddenResults(settings)
        assertEquals(1, out.size)
    }

    @Test
    fun `pins move matching results to front in saved order`() {
        val settings = QuickSearchSettings(
            pinnedResults = mapOf("hello" to listOf("c", "a")),
        )
        val out = listOf(result("a"), result("b"), result("c"))
            .applyResultPins(" HELLO ", settings)
        assertEquals(listOf("c", "a", "b"), out.map { it.key })
    }

    @Test
    fun `pins ignore disallowed sources`() {
        val settings = QuickSearchSettings(
            pinnedResults = mapOf("q" to listOf("u")),
        )
        val out = listOf(result("a"), result("u", SOURCE_URL))
            .applyResultPins("q", settings)
        assertEquals(listOf("a", "u"), out.map { it.key })
    }
}
