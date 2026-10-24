package hunoia.luno.quicklaunch.query

import hunoia.luno.config.model.SearchLaunch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 引擎打开方式 → SearchAction 的映射回归。 */
class SearchLaunchActionTest {

    @Test
    fun `chooser launch forces system chooser`() {
        val action = SearchLaunch.Chooser("https://example.com/?q=%s")
            .toSearchAction("a b") as SearchAction.OpenUrl
        assertEquals("https://example.com/?q=a+b", action.url)
        assertEquals("", action.packageName)
        assertTrue(action.forceChooser)
    }

    @Test
    fun `url launch carries target package and fallback`() {
        val action = SearchLaunch.Url(
            template = "https://example.com/?q=%s",
            packageName = "com.example.browser",
            fallbackTemplate = "https://fallback.example/?q=%s",
        ).toSearchAction("a b") as SearchAction.OpenUrl
        assertEquals("https://example.com/?q=a+b", action.url)
        assertEquals("com.example.browser", action.packageName)
        assertEquals("https://fallback.example/?q=a+b", action.fallbackUrl)
        assertFalse(action.forceChooser)
    }

    @Test
    fun `share launch round trips through serialization`() {
        val engine = hunoia.luno.config.model.SearchEngine(
            id = "share",
            name = "Share",
            urlTemplate = "https://example/?q=%s",
            launch = SearchLaunch.Share("搜索：%q", "com.example"),
        )
        val decoded = hunoia.luno.core.JsonSerializer.decodeFromString<hunoia.luno.config.model.SearchEngine>(
            hunoia.luno.core.JsonSerializer.encodeToString(engine),
        )
        assertEquals(engine, decoded)
    }
}
