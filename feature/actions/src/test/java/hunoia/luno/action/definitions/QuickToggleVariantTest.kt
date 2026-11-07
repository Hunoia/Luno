package hunoia.luno.action.definitions

import hunoia.luno.actions.LunoActionSearchSource
import hunoia.luno.core.domain.QuickToggleKind
import hunoia.luno.core.domain.SettingsPage
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class QuickToggleVariantTest {

    @Test
    fun `toggle action exposes one variant per kind`() {
        val definition = ActionDefinitions.byTypeId("system.toggle")!!
        assertEquals(QuickToggleKind.entries.size, definition.variants.size)
        assertEquals(
            QuickToggleKind.entries.map { it.name }.toSet(),
            definition.variants.map { it.key }.toSet(),
        )
        // 缺省变体固定第一位（与解码默认值一致）。
        assertEquals(QuickToggleKind.DEFAULT.name, definition.defaultVariant?.key)
        assertTrue(
            definition.variants.all { variant ->
                variant.label.isNotBlank() && variant.params["kind"] == variant.key
            },
        )
    }

    @Test
    fun `variant lookup matches stored params and ignores unknown values`() {
        val definition = ActionDefinitions.byTypeId("system.toggle")!!
        val wifi = definition.variantOf(JsonObject(mapOf("kind" to JsonPrimitive("WIFI"))))
        assertEquals(QuickToggleKind.WIFI.name, wifi?.key)
        assertNull(definition.variantOf(JsonObject(mapOf("kind" to JsonPrimitive("NOPE")))))
        assertNull(definition.variantOf(JsonObject(emptyMap())))
        // 展示/执行回退同一口径：未配置 = 缺省变体，不是“无变体”。
        assertEquals(
            QuickToggleKind.DEFAULT.name,
            definition.resolvedVariant(JsonObject(emptyMap()))?.key,
        )
    }

    @Test
    fun `search index expands toggle variants into standalone entries`() {
        val toggleEntries = LunoActionSearchSource.index().filter { it.typeId == "system.toggle" }
        assertEquals(QuickToggleKind.entries.size, toggleEntries.size)
        // 每个变体独立 key：置顶/去重按 key 走，不能共用动作 typeId。
        assertEquals(toggleEntries.size, toggleEntries.map { it.key }.distinct().size)
        val dnd = toggleEntries.first { it.label == "勿扰" }
        assertEquals("DND", (dnd.action.params["kind"] as? JsonPrimitive)?.contentOrNull)
        // 变体不能从搜索里再点出一个「没选开关」的空动作。
        assertTrue(toggleEntries.all { it.action.params["kind"] != null })
    }

    @Test
    fun `merged actions only surface their variants in search`() {
        val entries = LunoActionSearchSource.index()
        listOf("system.toggle", "system.openSettings").forEach { typeId ->
            val rows = entries.filter { it.typeId == typeId }
            assertEquals(ActionDefinitions.byTypeId(typeId)!!.variants.size, rows.size)
            // 每行都带完整变体参数，不能出现“没选开关/页面”的空动作。
            assertTrue("$typeId 存在空参数条目", rows.all { it.action.params.isNotEmpty() })
            assertEquals("$typeId 变体 key 重复", rows.size, rows.map { it.key }.distinct().size)
        }
    }

    @Test
    fun `open settings keeps main page as default`() {
        val definition = ActionDefinitions.byTypeId("system.openSettings")!!
        assertEquals(SettingsPage.DEFAULT.name, definition.defaultVariant?.key)
        assertEquals("主设置", definition.defaultVariant?.label)
    }
}
