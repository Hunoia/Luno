package hunoia.luno.core.domain

import kotlinx.coroutines.runBlocking
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
    fun `staged keeps previous imported state and icon`() {
        val existing = DynamicShortcutRefs(listOf(ref("a", imported = true, iconPath = "/old.png")))
        val staged = existing.withStaged(listOf(ref("a")))

        assertTrue(staged.items.single().imported)
        assertEquals("/old.png", staged.items.single().iconPath)
    }

    @Test
    fun `new staged shortcut is not imported yet`() {
        val staged = DynamicShortcutRefs().withStaged(listOf(ref("a")))

        assertFalse(staged.items.single().imported)
    }

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
    fun `same id in different profiles is kept apart`() {
        val refs = DynamicShortcutRefs()
            .withStaged(listOf(ref("a", userSerial = 0), ref("a", userSerial = 10)))
            .withConfirmed(listOf(DynamicShortcutIdentity("com.example", "a", 10)))

        assertEquals(listOf(false, true), refs.items.map { it.imported })
        assertEquals(listOf(0L, 10L), refs.items.map { it.userSerial })
    }

    @Test
    fun `confirm only publishes matching identity`() {
        val refs = DynamicShortcutRefs(listOf(ref("a"), ref("b")))
            .withConfirmed(listOf(DynamicShortcutIdentity("com.example", "b", 0)))

        assertEquals(listOf(false, true), refs.items.map { it.imported })
    }

    @Test
    fun `system snapshot only refreshes existing records`() {
        val existing = DynamicShortcutRefs(listOf(ref("a", imported = true, iconPath = "/old.png")))
        val updated = existing.withSystemSnapshot(
            listOf(
                ref("a", imported = false, iconPath = null),
                ref("b", imported = true),
            ),
        )

        // 未勾选过的候选不入库；已有记录保持 imported 与原图标。
        assertEquals(listOf("a"), updated.items.map { it.shortcutId })
        assertTrue(updated.items.single().imported)
        assertEquals("/old.png", updated.items.single().iconPath)
    }

    @Test
    fun `system snapshot confirms a staged record pinned by an interrupted import`() {
        val staged = DynamicShortcutRefs(listOf(ref("a", imported = false)))
        val updated = staged.withSystemSnapshot(listOf(ref("a", imported = true)))

        assertTrue(updated.items.single().imported)
    }

    @Test
    fun `import group keeps existing pins and confirms only actual pins`() = runBlocking {
        val pinned = mutableSetOf("old")
        val staged = mutableListOf<DynamicShortcutIdentity>()
        val confirmed = mutableListOf<DynamicShortcutIdentity>()
        val backend = object : ShortcutImportBackend {
            override suspend fun pinnedIds(packageName: String, userSerial: Long): Set<String> =
                pinned.toSet()

            override suspend fun stage(shortcuts: List<DynamicShortcutIdentity>) {
                staged += shortcuts
            }

            override suspend fun pin(packageName: String, userSerial: Long, ids: Set<String>) {
                pinned.clear()
                pinned += ids
            }

            override suspend fun confirm(shortcuts: List<DynamicShortcutIdentity>) {
                confirmed += shortcuts
            }
        }

        importShortcutGroup(
            backend,
            listOf(DynamicShortcutIdentity("com.example", "a", 0)),
        )

        assertEquals(setOf("old", "a"), pinned)
        assertEquals(1, staged.size)
        assertEquals(listOf("a"), confirmed.map { it.shortcutId })
    }

    @Test(expected = IllegalStateException::class)
    fun `import group fails when system does not pin the shortcut`() = runBlocking {
        val backend = object : ShortcutImportBackend {
            override suspend fun pinnedIds(packageName: String, userSerial: Long): Set<String> = emptySet()
            override suspend fun stage(shortcuts: List<DynamicShortcutIdentity>) = Unit
            override suspend fun pin(packageName: String, userSerial: Long, ids: Set<String>) = Unit
            override suspend fun confirm(shortcuts: List<DynamicShortcutIdentity>) = Unit
        }

        importShortcutGroup(
            backend,
            listOf(DynamicShortcutIdentity("com.example", "a", 0)),
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun `import group rejects mixed package or profile`() = runBlocking {
        val backend = object : ShortcutImportBackend {
            override suspend fun pinnedIds(packageName: String, userSerial: Long): Set<String> = emptySet()
            override suspend fun stage(shortcuts: List<DynamicShortcutIdentity>) = Unit
            override suspend fun pin(packageName: String, userSerial: Long, ids: Set<String>) = Unit
            override suspend fun confirm(shortcuts: List<DynamicShortcutIdentity>) = Unit
        }

        importShortcutGroup(
            backend,
            listOf(
                DynamicShortcutIdentity("com.example", "a", 0),
                DynamicShortcutIdentity("com.example", "b", 10),
            ),
        )
    }
}
