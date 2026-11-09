package hunoia.luno.link

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ShortLinkScanTest {

    @Test
    fun `meta refresh comes first`() {
        val html = """
            <html><head>
            <meta http-equiv="refresh" content="0; url=https://www.xiaohongshu.com/discovery/item/abc">
            </head><body><a href="https://example.com/nav">x</a></body></html>
        """.trimIndent()
        val urls = ShortLinkScan.urlsInHtml(html)
        assertEquals("https://www.xiaohongshu.com/discovery/item/abc", urls.first())
    }

    @Test
    fun `js location assignment and replace`() {
        val html = """
            <script>window.location.href = "https://a.example.com/x";</script>
            <script>location.replace('https://b.example.com/y')</script>
        """.trimIndent()
        val urls = ShortLinkScan.urlsInHtml(html)
        assertEquals(
            listOf("https://a.example.com/x", "https://b.example.com/y"),
            urls,
        )
    }

    @Test
    fun `unescapes slashes and protocol relative urls`() {
        val html = """<script>location.href="https:\/\/c.example.com\/y"</script>
            <script>location.assign("//d.example.com/z")</script>"""
        val urls = ShortLinkScan.urlsInHtml(html)
        assertEquals(
            listOf("https://c.example.com/y", "https://d.example.com/z"),
            urls,
        )
    }

    @Test
    fun `dedupes and drops unusable schemes`() {
        val html = """
            <script>location.href="https://a.example.com/x"</script>
            <a href="https://a.example.com/x">dup</a>
            <script>location.href="javascript:void(0)"</script>
            <a href="/relative/path">rel</a>
        """.trimIndent()
        assertEquals(listOf("https://a.example.com/x"), ShortLinkScan.urlsInHtml(html))
    }

    @Test
    fun `empty html yields nothing`() {
        assertTrue(ShortLinkScan.urlsInHtml("").isEmpty())
        assertTrue(ShortLinkScan.urlsInHtml("   ").isEmpty())
    }
}
