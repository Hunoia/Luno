package hunoia.luno.core.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DynamicShortcutRefsTest {

    private fun ref(
        id: String,
        userSerial: Long = 0,
        imported: Boolean = false,
        iconPath: String? = null,
    ) = DynamicShortcutRef(
        packageName = "com.example",
        shortcutId = id,
        userSerial = userSerial,
        label = "label-$id",
        appLabel = "Example",
        imported = imported,
        iconPath = iconPath,
    )

    @Test
    fun `collect all imports every entry`() {
        val existing = DynamicShortcutRefs(listOf(ref("a", imported = true)))
        val collected = existing.collectAll(
            listOf(
                ref("a", iconPath = "/new.png"),
                ref("b"),
                ref("c"),
            ),
        )

        val byId = collected.items.associateBy { it.shortcutId }
        // 采集导入全部：快照里的每个入口都标为已导入。
        assertTrue(byId.getValue("a").imported)
        assertEquals("/new.png", byId.getValue("a").iconPath)
        assertTrue(byId.getValue("b").imported)
        assertTrue(byId.getValue("c").imported)
    }

    @Test
    fun `collect all keeps orphan imported records`() {
        val existing = DynamicShortcutRefs(listOf(ref("a", imported = true), ref("b")))
        val collected = existing.collectAll(listOf(ref("c")))

        val byId = collected.items.associateBy { it.shortcutId }
        assertTrue(byId.containsKey("a"))
        assertTrue(byId.getValue("a").imported)
        assertTrue(byId.containsKey("c"))
        // 未导入且不在快照里的记录被清掉；孤立已导入保留。
        assertFalse(byId.containsKey("b"))
    }
}
