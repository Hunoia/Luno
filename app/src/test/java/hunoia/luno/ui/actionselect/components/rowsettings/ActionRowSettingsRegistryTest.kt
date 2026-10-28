package hunoia.luno.ui.actionselect.components.rowsettings

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class ActionRowSettingsRegistryTest {

    @Test
    fun `all built-in configurable actions are registered`() {
        val expected = setOf(
            "internal.volumeScrub",
            "internal.floatBallVisibility",
            "internal.buttonsVisibility",
            "system.openSettings",
            "system.volume",
            "system.vibrate",
            "accessibility.tap",
            "accessibility.longPress",
            "accessibility.doubleTap",
            "accessibility.scroll",
            "intent.shareText",
            "accessibility.swipe",
        )
        assertEquals(expected, ActionRowSettingsRegistry.typeIds)
    }

    @Test
    fun `vibrate validates pattern`() {
        val descriptor = ActionRowSettingsRegistry.byTypeId("system.vibrate")!!
        assertTrue(descriptor.isValid(json("pattern" to "0,500")))
        assertFalse(descriptor.isValid(json("pattern" to "abc")))
    }

    @Test
    fun `tap canonicalizes trigger point and coordinates`() {
        val descriptor = ActionRowSettingsRegistry.byTypeId("accessibility.tap")!!
        val trigger = descriptor.canonicalize(json("useTriggerPoint" to "true"))
        assertEquals("true", trigger.str("useTriggerPoint", ""))

        val fixed = descriptor.canonicalize(
            json("useTriggerPoint" to "false", "xDp" to "12", "yDp" to "34"),
        )
        assertEquals("12", fixed.str("xDp", ""))
        assertEquals("34", fixed.str("yDp", ""))
    }

    @Test
    fun `duplicate typeId fails without replacing existing`() {
        val existing = ActionRowSettingsRegistry.byTypeId("system.vibrate")!!
        try {
            ActionRowSettingsRegistry.register(existing)
            fail("重复描述符应直接失败")
        } catch (expected: IllegalArgumentException) {
            assertTrue(expected.message.orEmpty().contains("duplicate"))
        }
        assertEquals(existing, ActionRowSettingsRegistry.byTypeId("system.vibrate"))
    }

    private fun json(vararg pairs: Pair<String, String>): JsonObject = buildJsonObject {
        pairs.forEach { (key, value) -> put(key, JsonPrimitive(value)) }
    }
}
