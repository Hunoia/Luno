package hunoia.luno.quickaccess.query

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 选择页与快速搜索共用的文本匹配语义。 */
class SearchTextTest {

    private fun search(query: String): SearchText {
        val compiled = SearchText.compile(query)
        assertNotNull("query=$query", compiled)
        return compiled!!
    }

    @Test
    fun `initials hit Chinese label`() {
        assertTrue(search("wx").matches("微信"))
    }

    @Test
    fun `full pinyin hits Chinese label`() {
        assertTrue(search("weixin").matches("微信"))
    }

    @Test
    fun `fuzzy pinyin folds to short form`() {
        // 中国 = zhongguo，折叠后 zon…；输入 zongguo 折叠后同形，精确档不中、模糊档命中。
        assertTrue(search("zongguo").matches("中国"))
    }

    @Test
    fun `package substring matches`() {
        assertTrue(search("tencent").matches("com.tencent.mm"))
    }

    @Test
    fun `out of order letters do not match`() {
        assertFalse(search("xw").matches("微信"))
        assertFalse(search("xw").matches("com.tencent.mm"))
    }

    @Test
    fun `multi word chunks require every chunk to hit a field`() {
        val s = search("微信 发消息")
        assertTrue(s.matches(listOf("微信", "发消息")))
        assertFalse(s.matches(listOf("微信")))
        assertFalse(s.matches(listOf("发消息")))
    }

    @Test
    fun `any chunk matches partial fields`() {
        val s = search("微信 发消息")
        assertTrue(s.matchesAnyChunk(listOf("微信")))
        assertTrue(s.matchesAnyChunk(listOf("发消息")))
        assertFalse(s.matchesAnyChunk(listOf("支付宝")))
    }

    @Test
    fun `symbol only query compiles to null`() {
        assertNull(SearchText.compile("  #!  "))
        assertNull(SearchText.compile(""))
    }

    @Test
    fun `blank fields do not match`() {
        assertFalse(search("wx").matches(""))
    }
}
