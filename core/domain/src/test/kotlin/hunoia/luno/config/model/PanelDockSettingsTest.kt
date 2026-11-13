package hunoia.luno.config.model

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 底栏成员的两个不变量：**不重复**（同一块面板两个 tab 没有意义）与
 * **不超上限**（底栏是固定宽 tab 的胶囊，不滚动）。顺序只在成员变化时变，
 * 重新加入已存在的成员不能把它挪到末尾。
 */
class PanelDockSettingsTest {

    @Test
    fun `with member appends only when absent`() {
        val dock = PanelDockSettings().withMember("a").withMember("b")
        assertEquals(listOf("a", "b"), dock.members)
        // 已在列表里：顺序保持不变，不重复追加。
        assertEquals(listOf("a", "b"), dock.withMember("a").members)
    }

    @Test
    fun `with member stops at the max`() {
        var dock = PanelDockSettings()
        repeat(PanelDockDefaults.MaxMembers + 2) { index -> dock = dock.withMember("panel$index") }
        assertEquals(PanelDockDefaults.MaxMembers, dock.members.size)
        assertEquals(false, dock.canAddMore)
        // 满了以后仍然允许调整已有成员的顺序。
        assertEquals("panel1", dock.moved("panel1", -1).members.first())
    }

    @Test
    fun `moved clamps at the ends`() {
        val dock = PanelDockSettings(members = listOf("a", "b", "c"))
        assertEquals(listOf("b", "a", "c"), dock.moved("b", -1).members)
        assertEquals(listOf("a", "c", "b"), dock.moved("b", 1).members)
        assertEquals(listOf("a", "b", "c"), dock.moved("a", -1).members)
        assertEquals(listOf("a", "b", "c"), dock.moved("c", 1).members)
        assertEquals(listOf("a", "b", "c"), dock.moved("missing", 1).members)
    }

    @Test
    fun `without member leaves unknown ids untouched`() {
        val dock = PanelDockSettings(members = listOf("a", "b"))
        assertEquals(listOf("b"), dock.withoutMember("a").members)
        assertEquals(listOf("a", "b"), dock.withoutMember("missing").members)
    }

    @Test
    fun `blank ids are ignored`() {
        assertEquals(emptyList<String>(), PanelDockSettings().withMember("  ").members)
    }
}
