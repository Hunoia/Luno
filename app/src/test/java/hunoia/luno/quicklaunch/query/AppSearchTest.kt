package hunoia.luno.quicklaunch.query

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 多音字 initials 变体匹配：字典读音序（茄:jiā,qié / 乐:lè,yuè）使基准首字母串
 * 取到冷门读音，换音首字母只存在于全拼变体中，双字母 query 在全拼里隔着韵母
 * 命不中连续块——必须有 initials 变体串兜底。
 */
class AppSearchTest {

    private fun tier(text: String, query: String) =
        AppSearch.matchTextTier(text, query.map { it.toString() })

    @Test
    fun `fq hits fanqie xiaoshuo at tier2 anchor via altInitials`() {
        val m = tier("番茄小说", "fq")
        assertEquals(2, m.tier)
        assertEquals(0, m.offset)
    }

    @Test
    fun `fj still hits via base initials`() {
        val m = tier("番茄小说", "fj")
        assertEquals(2, m.tier)
        assertEquals(0, m.offset)
    }

    @Test
    fun `yy hits yinyue label whose base initials are yl`() {
        assertEquals(2, tier("音乐播放器", "yy").tier)
        assertEquals(2, tier("音乐播放器", "yl").tier)
    }

    @Test
    fun `full pinyin fanqie hits via alt pinyin anchor`() {
        val m = tier("番茄小说", "fanqie")
        assertEquals(1, m.tier)
        assertEquals(0, m.offset)
    }

    @Test
    fun `altInitials contains swapped variant and stays within cap`() {
        val idx = AppSearch.buildIndex("番茄小说")
        assertTrue("altInitials=${idx.altInitials}", "fqxs" in idx.altInitials)
        assertTrue(idx.altInitials.size <= 16)
    }

    @Test
    fun `dense polyphone label keeps late char variant within cap 16`() {
        // 6 个多音字在前（长 1 个 initials 变体/字），茄排最后：
        // 全拼变体 cap 8 会给挤到第 7 名惊险过关，initials 独立 cap 16 更宽裕。
        val label = "长长长长长长茄"
        val idx = AppSearch.buildIndex(label)
        val expected = idx.initials.toMutableList().also { it[6] = 'q' }.joinToString("")
        assertTrue("altInitials=${idx.altInitials}", expected in idx.altInitials)
        // zq:前字取基准读音 z,后字取变体读音 q——同在单字换音变体串里相邻,
        // 但块在串中段(位置 5),按连续块语义降档 tier 0,关键是命中而非出局。
        val m = tier(label, "zq")
        assertEquals(0, m.tier)
        assertEquals(5, m.offset)
    }

    @Test
    fun `sparse letters still do not match`() {
        // fx 在 fjxs / fqxs / 全拼里都不构成连续块,不能因变体引入而松动成子序列匹配。
        assertEquals(-1, tier("番茄小说", "fx").tier)
    }

    @Test
    fun `mid segment initials hit stays degraded tier0`() {
        // 小说的 xs 在 initials 串中段:命中但不锚定,维持原有降档语义。
        val m = tier("番茄小说", "xs")
        assertEquals(0, m.tier)
        assertEquals(2, m.offset)
    }

    @Test
    fun `english label matching unchanged`() {
        assertEquals(2, tier("shopping list", "sh").tier)
        assertEquals(2, tier("shopping list", "shopping").tier)
    }

    @Test
    fun `altPinyins cap 8 behavior unchanged`() {
        // 重:zhong,chong,tong × 6 = 12 个全拼变体 > cap 8,超出的不收。
        val idx = AppSearch.buildIndex("重重重重重重重")
        assertEquals(8, idx.altPinyins.size)
        assertFalse("folding must not leak into exact altPinyins", idx.altPinyinsFolded.isEmpty())
    }
}
