package hunoia.luno.quicklaunch.panel

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * stableKeys 是 LazyColumn/Row 的 key 唯一性守门：同一次组合内 key 必须唯一，
 * 否则 debug 直抛 IllegalArgumentException 崩溃（见 GroupedCards 撞 key 闪退）。
 * 这条不变量被 Flat 与 GroupedCards 两个列表形态共用，必须锁死。
 */
class QuickSearchKeysTest {

    @Test
    fun `unique items keep their base key`() {
        val items = listOf("a", "b", "c")
        assertEquals(items, stableKeys(items))
    }

    @Test
    fun `duplicate items get suffixed to stay unique`() {
        val items = listOf("sc#x", "sc#x", "sc#x")
        assertEquals(listOf("sc#x", "sc#x~1", "sc#x~2"), stableKeys(items))
    }

    @Test
    fun `mixed duplicates keep first occurrence bare`() {
        val items = listOf("a", "b", "a", "b", "c")
        assertEquals(listOf("a", "b", "a~1", "b~1", "c"), stableKeys(items))
    }

    @Test
    fun `empty list returns empty`() {
        assertEquals(emptyList<String>(), stableKeys(emptyList()))
    }
}
