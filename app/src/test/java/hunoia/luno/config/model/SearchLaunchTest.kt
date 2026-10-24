package hunoia.luno.config.model

import hunoia.luno.action.model.StoredAction
import hunoia.luno.core.JsonSerializer
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** 搜索引擎打开方式：旧数据回退、模板填词与长按可复制链接。 */
class SearchLaunchTest {

    @Test
    fun `legacy engine falls back to urlTemplate`() {
        val engine = SearchEngine(urlTemplate = "https://example.com/?q=%s")
        assertEquals(SearchLaunch.Url("https://example.com/?q=%s"), engine.effectiveLaunch())
    }

    @Test
    fun `explicit launch wins over legacy template`() {
        val engine = SearchEngine(
            urlTemplate = "https://old.example/?q=%s",
            launch = SearchLaunch.Chooser("https://new.example/?q=%s"),
        )
        assertEquals(SearchLaunch.Chooser("https://new.example/?q=%s"), engine.effectiveLaunch())
    }

    @Test
    fun `fillSearchQuery encodes and replaces every placeholder`() {
        assertEquals("a+b", "%s".fillSearchQuery("a b"))
        assertEquals("x?a+b&b=a+b", "x?%s&b=%s".fillSearchQuery("a b"))
    }

    @Test
    fun `fillSearchQuery supports raw q and encoded s`() {
        assertEquals("a b", "%q".fillSearchQuery("a b"))
        assertEquals("a+b", "%s".fillSearchQuery("a b"))
        assertEquals("raw=a b&enc=a+b", "raw=%q&enc=%s".fillSearchQuery("a b"))
    }

    @Test
    fun `copyUrl uses template for url and chooser launches`() {
        assertEquals("https://a/?q=a+b", SearchLaunch.Url("https://a/?q=%s").copyUrl("a b"))
        assertEquals("https://c/?q=a+b", SearchLaunch.Chooser("https://c/?q=%s").copyUrl("a b"))
    }

    @Test
    fun `copyUrl prefers copyTemplate then action url for action launches`() {
        val withCopy = SearchLaunch.Action(
            action = StoredAction.of("app.launch", "packageName" to "com.example"),
            copyTemplate = "https://copy/?q=%s",
        )
        assertEquals("https://copy/?q=a+b", withCopy.copyUrl("a b"))

        val withUrlParam = SearchLaunch.Action(
            action = StoredAction.of("intent.openUrl", "url" to "https://param/?q=%s"),
        )
        assertEquals("https://param/?q=a+b", withUrlParam.copyUrl("a b"))

        assertNull(SearchLaunch.Action(StoredAction.of("app.launch")).copyUrl("a b"))
    }

    @Test
    fun `share launch has no copy url`() {
        assertNull(SearchLaunch.Share("%q", "com.example").copyUrl("a b"))
    }

    @Test
    fun `SearchEngine serializes launch and reads legacy json`() {
        val engine = SearchEngine(
            id = "e1",
            name = "Example",
            urlTemplate = "https://example.com/?q=%s",
            launch = SearchLaunch.Url(
                template = "https://example.com/?q=%s",
                packageName = "com.example.browser",
                fallbackTemplate = "https://fallback/?q=%s",
            ),
        )
        val decoded = JsonSerializer.decodeFromString<SearchEngine>(
            JsonSerializer.encodeToString(engine),
        )
        assertEquals(engine, decoded)

        val legacy = JsonSerializer.decodeFromString<SearchEngine>(
            """{"id":"old","name":"Old","urlTemplate":"https://old/?q=%s"}""",
        )
        assertEquals(null, legacy.launch)
        assertEquals("https://old/?q=%s", legacy.effectiveLaunch().let { (it as SearchLaunch.Url).template })
    }

    @Test
    fun `StoredAction fillSearchQuery replaces nested string params only`() {
        val action = StoredAction(
            typeId = "test",
            params = kotlinx.serialization.json.buildJsonObject {
                put("url", JsonPrimitive("https://x/?q=%s"))
                put("count", JsonPrimitive(3))
                put("raw", JsonPrimitive("%s"))
            },
        )
        val filled = action.fillSearchQuery("a b")
        assertEquals("https://x/?q=a+b", (filled.params["url"] as JsonPrimitive).content)
        assertEquals("3", (filled.params["count"] as JsonPrimitive).content)
        assertEquals("a+b", (filled.params["raw"] as JsonPrimitive).content)
        // 原对象不被修改。
        assertEquals("https://x/?q=%s", (action.params["url"] as JsonPrimitive).content)
    }
}
