package hunoia.luno.ui.actionselect.components.rowsettings

import org.junit.Assert.assertEquals
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
        )
        assertEquals(expected, ActionRowSettingsRegistry.typeIds)
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
