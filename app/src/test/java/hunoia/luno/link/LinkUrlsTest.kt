package hunoia.luno.link

import hunoia.luno.config.model.LinkDispatchRule
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
        val rule = rule(pattern = "b23.tv", matchType = LinkMatchType.CONTAINS)
        assertTrue(LinkUrls.matches("https://b23.tv/abc", rule))
        assertTrue(LinkUrls.matches("https://www.b23.tv/abc", rule))
        // 域名不是「等于/子域」时还兜一层整串包含（与 LinkGo 同口径）。
        assertTrue(LinkUrls.matches("https://b23.tv.example.com/abc", rule))
    }

    @Test
    fun `contains falls back to plain substring`() {
        val rule = rule(pattern = "/video/", matchType = LinkMatchType.CONTAINS)
        assertTrue(LinkUrls.matches("https://example.com/video/1", rule))
        assertFalse(LinkUrls.matches("https://example.com/img/1", rule))
    }

    @Test
    fun `regex and exact matching`() {
        val regex = rule(pattern = """v\d+/BV\w+""", matchType = LinkMatchType.REGEX)
        assertTrue(LinkUrls.matches("https://x.com/v1/BVabc", regex))
        assertFalse(LinkUrls.matches("https://x.com/BVabc", regex))

        val exact = rule(pattern = "https://b23.tv/abc", matchType = LinkMatchType.EXACT)
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
        assertNull(LinkUrls.firstMatch("https://b23.tv/x", listOf(rule(pattern = "  "))))
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
    fun `presets are well formed and unique`() {
        val all = hunoia.luno.link.LinkPresets.all
        assertTrue(all.isNotEmpty())
        assertTrue(
            all.all {
                it.pattern.isNotBlank() && it.name.isNotBlank() &&
                    (it.targetPackage.contains('.') || (it.viaBrowser && it.targetPackage.isEmpty()))
            },
        )
        assertEquals(all.size, all.map { it.pattern.lowercase() }.distinct().size)
        assertTrue(all.all { LinkUrls.isDispatchable("https://${it.pattern}/x") })
    }

    @Test
    fun `browser rule needs no target package`() {
        val rule = hunoia.luno.config.model.LinkDispatchRule(
            pattern = "xhslink.com",
            targetPackage = "",
            viaBrowser = true,
        )
        assertTrue(rule.isValid)
        assertFalse(rule.copy(viaBrowser = false).isValid)
        assertFalse(rule.copy(pattern = " ").isValid)
    }

    private fun rule(
        id: String = "r",
        pattern: String,
        matchType: LinkMatchType = LinkMatchType.CONTAINS,
        enabled: Boolean = true,
    ) = LinkDispatchRule(
        id = id,
        name = id,
        enabled = enabled,
        matchType = matchType,
        pattern = pattern,
        targetPackage = "com.example",
    )
}
