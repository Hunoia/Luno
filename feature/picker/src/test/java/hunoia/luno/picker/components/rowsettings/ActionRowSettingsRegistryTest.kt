package hunoia.luno.picker.components.rowsettings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

class ActionRowSettingsRegistryTest {

    @Test
    fun `all built-in configurable actions are registered`() {
        val expected = setOf(
            "internal.volumeScrub",
            "internal.floatBallVisibility",
            "internal.buttonsVisibility",
            "system.openSettings",
            "system.volume",
            "system.toggle",
        )
        assertEquals(expected, ActionRowSettingsRegistry.typeIds)
    }

    @Test
    fun `quick toggle canonicalize keeps known kind and falls back to default`() {
        val descriptor = ActionRowSettingsRegistry.byTypeId("system.toggle")!!
        fun kind(value: String?) = JsonObject(
            if (value == null) emptyMap() else mapOf("kind" to JsonPrimitive(value)),
        )
        assertEquals(kind("WIFI"), descriptor.canonicalize(kind("WIFI")))
        // 未选/脏值统一落第一个变体（勿扰），不把非法值写进存储。
        assertEquals(kind("DND"), descriptor.canonicalize(kind(null)))
        assertEquals(kind("DND"), descriptor.canonicalize(kind("NOPE")))
    }

    @Test
    fun `duplicate typeId fails without replacing existing`() {
        val existing = ActionRowSettingsRegistry.byTypeId("system.volume")!!
        try {
            ActionRowSettingsRegistry.register(existing)
            fail("重复描述符应直接失败")
        } catch (expected: IllegalArgumentException) {
            assertTrue(expected.message.orEmpty().contains("duplicate"))
        }
        assertEquals(existing, ActionRowSettingsRegistry.byTypeId("system.volume"))
    }
}
