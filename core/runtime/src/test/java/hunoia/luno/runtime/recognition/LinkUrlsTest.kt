package hunoia.luno.runtime.recognition

import hunoia.luno.config.model.RecognitionBuiltin
import hunoia.luno.config.model.RecognitionMatch
import hunoia.luno.config.model.RecognitionOutput
import hunoia.luno.config.model.RecognitionRule
import hunoia.luno.config.model.RecognitionSource
import hunoia.luno.config.model.LinkMatchType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LinkUrlsTest {

    @Test
    fun `extracts url from mixed chinese text`() {
        val text = "这个视频好看 https://www.bilibili.com/video/BV1xx411c7mD 快看"
        assertEquals(
            listOf("https://www.bilibili.com/video/BV1xx411c7mD"),
            LinkUrls.extractUrls(text),
        )
    }

    @Test
    fun `trims trailing sentence punctuation`() {
        val text = "详见 https://example.com/a?b=1。"
        assertEquals(listOf("https://example.com/a?b=1"), LinkUrls.extractUrls(text))
        assertEquals(
            listOf("https://example.com/x"),
            LinkUrls.extractUrls("(https://example.com/x),"),
        )
    }

    @Test
    fun `deduplicates and keeps order`() {
        val text = "https://a.com/1 https://b.com/2 https://a.com/1"
        assertEquals(
            listOf("https://a.com/1", "https://b.com/2"),
            LinkUrls.extractUrls(text),
        )
    }

    @Test
    fun `rejects blocked and malformed urls`() {
        assertTrue(LinkUrls.extractUrls("javascript://foo/bar").isEmpty())
        assertTrue(LinkUrls.extractUrls("https://").isEmpty())
        assertEquals(0, LinkUrls.extractUrls("没有链接的一段话").size)
    }

    @Test
    fun `parses host path and query`() {
        val url = "https://user@video.bilibili.com:8080/a/b?p=1#top"
        assertEquals("video.bilibili.com", LinkUrls.hostOf(url))
        assertEquals("/a/b", LinkUrls.pathOf(url))
        assertEquals("p=1", LinkUrls.queryOf(url))
        assertEquals("https", LinkUrls.schemeOf(url))
        assertNull(LinkUrls.queryOf("https://a.com/x"))
    }

    @Test
    fun `contains matches bare domain and subdomain`() {
        val m = match("b23.tv")
        assertTrue(LinkUrls.matches("https://b23.tv/abc", m))
        assertTrue(LinkUrls.matches("https://www.b23.tv/abc", m))
        // 域名不是「等于/子域」时还兜一层整串包含（与 LinkGo 同口径）。
        assertTrue(LinkUrls.matches("https://b23.tv.example.com/abc", m))
    }

    @Test
    fun `contains falls back to plain substring`() {
        val m = match("/video/")
        assertTrue(LinkUrls.matches("https://example.com/video/1", m))
        assertFalse(LinkUrls.matches("https://example.com/img/1", m))
    }

    @Test
    fun `regex and exact matching`() {
        val regex = match("""v\d+/BV\w+""", LinkMatchType.REGEX)
        assertTrue(LinkUrls.matches("https://x.com/v1/BVabc", regex))
        assertFalse(LinkUrls.matches("https://x.com/BVabc", regex))

        val exact = match("https://b23.tv/abc", LinkMatchType.EXACT)
        assertTrue(LinkUrls.matches("HTTPS://B23.TV/ABC", exact))
        assertFalse(LinkUrls.matches("https://b23.tv/abc/", exact))
    }

    @Test
    fun `first match follows rule order and skips disabled`() {
        val disabled = rule(id = "a", pattern = "b23.tv", enabled = false)
        val first = rule(id = "b", pattern = "b23.tv")
        val second = rule(id = "c", pattern = "tv")
        val match = LinkUrls.firstMatch("https://b23.tv/x", listOf(disabled, first, second))
        assertEquals("b", match?.id)
        assertNull(LinkUrls.firstMatch("https://b23.tv/x", listOf(disabled)))
        // 空 / 空白的匹配串是合法语义（= 任意可分发的链接），内置「链接」规则就用它。
        assertEquals(
            "r",
            LinkUrls.firstMatch("https://b23.tv/x", listOf(rule(pattern = "  ")))?.id,
        )
        assertNull(LinkUrls.firstMatch("https://b23.tv/x", listOf(rule(pattern = "(bad"))) )
    }

    @Test
    fun `template rewrites url pieces`() {
        val url = "https://b23.tv/abcd?p=2"
        assertEquals("https://b23.tv/abcd?p=2", LinkUrls.applyTemplate(url, ""))
        assertEquals(
            "bv://b23.tv/abcd?p=2",
            LinkUrls.applyTemplate(url, "bv://{host}{path}?{query}"),
        )
        assertEquals("https://b23.tv/abcd?p=2", LinkUrls.applyTemplate(url, "{url}"))
        assertEquals("keep {unknown}", LinkUrls.applyTemplate(url, "keep {unknown}"))
    }

    @Test
    fun `bare domains are opt-in`() {
        val text = "看这个 github.com/Hunoia/Luno 挺好"
        assertTrue(LinkUrls.extractUrls(text).isEmpty())
        assertEquals(
            listOf("https://github.com/Hunoia/Luno"),
            LinkUrls.extractUrls(text, allowBareDomains = true),
        )
    }

    @Test
    fun `bare domains reject packages files and emails`() {
        val text = """
            com.android.chrome
            photo.jpg
            user@mail.com
            v1.2.3
            github.com/a
        """.trimIndent()
        assertEquals(
            listOf("https://github.com/a"),
            LinkUrls.extractUrls(text, allowBareDomains = true),
        )
    }

    @Test
    fun `bare domains keep query and dedupe against schemed url`() {
        assertEquals(
            listOf("https://taobao.com/item.htm?id=1"),
            LinkUrls.extractUrls("item 在 taobao.com/item.htm?id=1", allowBareDomains = true),
        )
        assertEquals(
            listOf("https://github.com/a"),
            LinkUrls.extractUrls("https://github.com/a", allowBareDomains = true),
        )
    }

    @Test
    fun `link match validity only depends on regex compilability`() {
        // 目标的完整性由执行壳判（没目标就只剩回退），匹配层不管目标应用。
        assertTrue(RecognitionMatch.Link(pattern = "xhslink.com", viaBrowser = true).isValid)
        assertTrue(RecognitionMatch.Link(pattern = "").isValid)
        assertFalse(RecognitionMatch.Link(matchType = LinkMatchType.REGEX, pattern = "([bad").isValid)
    }

    @Test
    fun `bare domains do not mistake package names for links`() {
        val text = "com.example.app com.foo.dev org.example.io androidx.core.ktx"
        assertEquals(
            emptyList<String>(),
            LinkUrls.extractUrls(text, allowBareDomains = true),
        )
        // 真实域名不受影响（首段不是包名前缀）。
        assertEquals(
            listOf("https://www.example.com/a"),
            LinkUrls.extractUrls("www.example.com/a", allowBareDomains = true),
        )
    }

    @Test
    fun `domain match only accepts host or subdomain`() {
        val rule = match("bilibili.com", LinkMatchType.DOMAIN)
        assertTrue(LinkUrls.matches("https://bilibili.com/video/1", rule))
        assertTrue(LinkUrls.matches("https://www.bilibili.com/video/1", rule))
        assertTrue(LinkUrls.matches("https://bilibili.com.evil.com/x", rule.copy(pattern = "evil.com")))
        assertFalse(LinkUrls.matches("https://bilibili.com.evil.com/x", rule))
        assertFalse(LinkUrls.matches("https://notbilibili.com/x", rule))
        // 前导点/通配前缀都按同一语义处理。
        assertTrue(LinkUrls.matches("https://www.bilibili.com/x", rule.copy(pattern = "*.bilibili.com")))
    }

    @Test
    fun `exclude patterns skip a rule`() {
        val linkMatch = match("example.com").copy(excludes = listOf("/ads/"))
        assertTrue(LinkUrls.matches("https://example.com/video/1", linkMatch))
        assertFalse(LinkUrls.matches("https://example.com/ads/1", linkMatch))
        // 被排除时继续往下匹配。
        val rule = rule(pattern = "example.com", excludes = listOf("/ads/"))
        val fallback = rule(id = "fallback", pattern = "example.com/ads")
        assertEquals(
            "fallback",
            LinkUrls.firstMatch(
                "https://example.com/ads/1",
                listOf(rule, fallback),
            )?.id,
        )
    }

    @Test
    fun `private hosts are recognised`() {
        assertTrue(LinkUrls.isPrivateHost("localhost"))
        assertTrue(LinkUrls.isPrivateHost("app.localhost"))
        assertTrue(LinkUrls.isPrivateHost("nas.local"))
        assertTrue(LinkUrls.isPrivateHost("192.168.1.10"))
        assertTrue(LinkUrls.isPrivateHost("10.0.0.1:8080"))
        assertTrue(LinkUrls.isPrivateHost("172.20.1.1"))
        assertTrue(LinkUrls.isPrivateHost("127.0.0.1"))
        assertTrue(LinkUrls.isPrivateHost("[::1]"))
        assertFalse(LinkUrls.isPrivateHost("example.com"))
        assertFalse(LinkUrls.isPrivateHost("172.32.0.1"))
        assertFalse(LinkUrls.isPrivateHost("8.8.8.8"))
        assertFalse(LinkUrls.isPrivateHost(null))
    }

    private fun match(
        pattern: String,
        matchType: LinkMatchType = LinkMatchType.CONTAINS,
    ) = RecognitionMatch.Link(
        matchType = matchType,
        pattern = pattern,
        targetPackage = "com.example",
    )

    private fun rule(
        id: String = "r",
        pattern: String,
        matchType: LinkMatchType = LinkMatchType.CONTAINS,
        excludes: List<String> = emptyList(),
        enabled: Boolean = true,
    ) = RecognitionRule(
        id = id,
        name = id,
        enabled = enabled,
        sources = setOf(RecognitionSource.Clipboard),
        match = RecognitionMatch.Link(
            matchType = matchType,
            pattern = pattern,
            excludes = excludes,
            targetPackage = "com.example",
        ),
        output = RecognitionOutput.Builtin(RecognitionBuiltin.OpenLink),
    )
}
