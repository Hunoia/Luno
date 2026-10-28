package hunoia.luno.quicklaunch.query

import org.junit.Assert.assertEquals
import org.junit.Test

/** shortcut XML 的 targetClass 归一化：相对类名/短类名不能直接交给 ComponentName。 */
class ShortcutQueryTest {

    @Test
    fun `relative class name gets package prefix`() {
        assertEquals(
            "com.example.MainActivity",
            normalizeShortcutClassName("com.example", ".MainActivity"),
        )
    }

    @Test
    fun `short class name gets package prefix`() {
        assertEquals(
            "com.example.MainActivity",
            normalizeShortcutClassName("com.example", "MainActivity"),
        )
    }

    @Test
    fun `fully qualified class name is unchanged`() {
        assertEquals(
            "com.other.TargetActivity",
            normalizeShortcutClassName("com.example", "com.other.TargetActivity"),
        )
    }

    @Test
    fun `inner class dollar sign is preserved`() {
        assertEquals(
            "com.example.Outer\$Inner",
            normalizeShortcutClassName("com.example", "com.example.Outer\$Inner"),
        )
    }
}
