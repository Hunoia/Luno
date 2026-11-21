package hunoia.luno.quickaccess.query

import hunoia.luno.bridge.launcher.LauncherInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * 快捷方式条目的稳定身份：主/分身动态入口、同包多入口的 key 必须能区分，
 * 否则搜索面板 LazyColumn/LazyRow 会以「Key already used」崩溃。
 */
class ShortcutStableIdTest {

    /** 动态入口：无 Activity 类名、无 intent，靠 shortcutId + userSerial 区分。 */
    private fun dynamic(pkg: String, id: String, serial: Long) = LauncherInfo.ShortcutInfo(
        packageName = pkg,
        className = "",
        intents = emptyList(),
        label = "x",
        shortcutId = id,
        userSerial = serial,
    )

    private fun static(pkg: String, cls: String, intents: List<String>) = LauncherInfo.ShortcutInfo(
        packageName = pkg,
        className = cls,
        intents = intents,
        label = "x",
    )

    @Test
    fun `main and clone dynamic shortcuts get distinct ids`() {
        val main = dynamic("com.aistra.hail", "id1", 0).toResult().key
        val clone = dynamic("com.aistra.hail", "id1", 10).toResult().key
        assertNotEquals(main, clone)
    }

    @Test
    fun `same user different shortcut ids get distinct ids`() {
        val a = dynamic("com.aistra.hail", "id1", 0).toResult().key
        val b = dynamic("com.aistra.hail", "id2", 0).toResult().key
        assertNotEquals(a, b)
    }

    @Test
    fun `static shortcut key keeps original format`() {
        val sc = static("com.aistra.hail", "MainActivity", listOf("intent://a", "intent://b"))
        assertEquals(
            "sc#com.aistra.hail/MainActivity|intent://a\u0001intent://b",
            sc.toResult().key,
        )
    }
}
