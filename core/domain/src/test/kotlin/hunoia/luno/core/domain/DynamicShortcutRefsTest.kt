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

    @Test
    fun `upsert imported adds new entry as imported`() {
        val updated = DynamicShortcutRefs(listOf(ref("a", imported = true)))
            .upsertImported(ref("b", iconPath = "/icon.png"))

        val byId = updated.items.associateBy { it.shortcutId }
        assertTrue(byId.getValue("b").imported)
        assertEquals("/icon.png", byId.getValue("b").iconPath)
        // 既有条目原样保留：单条导入没有全量快照，不做孤儿清理。
        assertTrue(byId.getValue("a").imported)
        assertEquals(2, updated.items.size)
    }

    @Test
    fun `upsert imported replaces same identity entirely`() {
        val existing = DynamicShortcutRefs(listOf(ref("a", imported = true, iconPath = "/old.png")))
        val replaced = existing.upsertImported(
            DynamicShortcutRef(
                packageName = "com.example",
                shortcutId = "a",
                userSerial = 0,
                label = "label-new",
                appLabel = "Example",
                iconPath = "/new.png",
            ),
        )

        assertEquals(1, replaced.items.size)
        val item = replaced.items.single()
        assertTrue(item.imported)
        assertEquals("label-new", item.label)
        assertEquals("/new.png", item.iconPath)
    }

    @Test
    fun `upsert imported keeps pin source and collect keeps snapshot source`() {
        val pin = ref("a").copy(source = SHORTCUT_SOURCE_PIN)
        val updated = DynamicShortcutRefs(listOf(pin))
            .upsertImported(ref("b").copy(source = SHORTCUT_SOURCE_COLLECT))
        val collected = updated.collectAll(
            listOf(ref("a", iconPath = "/refreshed.png"), ref("b", iconPath = "/b.png")),
        )

        val byId = collected.items.associateBy { it.shortcutId }
        // 重新采集不降级来源：pin 拦截进来的仍归 pin。
        assertEquals(SHORTCUT_SOURCE_PIN, byId.getValue("a").source)
        assertEquals(SHORTCUT_SOURCE_COLLECT, byId.getValue("b").source)
    }

    @Test
    fun `remove deletes only the matching identity`() {
        val refs = DynamicShortcutRefs(listOf(ref("a", imported = true), ref("b", imported = true)))
        val removed = refs.remove(DynamicShortcutIdentity("com.example", "a", 0))

        val byId = removed.items.associateBy { it.shortcutId }
        assertFalse(byId.containsKey("a"))
        assertTrue(byId.containsKey("b"))
    }
}
