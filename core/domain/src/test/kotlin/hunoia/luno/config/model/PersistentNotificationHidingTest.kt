package hunoia.luno.config.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PersistentNotificationHidingTest {

    private val own = "hunoia.luno"

    @Test
    fun `disabled never hides`() {
        val settings = PersistentNotificationHidingSettings(enabled = false)
        assertFalse(
            settings.shouldHide(own, own, 2001, null, isPersistent = true),
        )
    }

    @Test
    fun `non persistent is ignored`() {
        val settings = PersistentNotificationHidingSettings()
        assertFalse(
            settings.shouldHide(own, "com.example", 1, null, isPersistent = false),
        )
    }

    @Test
    fun `luno own persistent notifications follow one switch`() {
        val enabled = PersistentNotificationHidingSettings()
        assertTrue(enabled.shouldHide(own, own, 2001, null, true))
        assertTrue(enabled.shouldHide(own, own, 2002, null, true))

        val disabled = PersistentNotificationHidingSettings(hideLunoNotifications = false)
        assertFalse(disabled.shouldHide(own, own, 2001, null, true))
    }

    @Test
    fun `target matches package id and tag`() {
        val settings = PersistentNotificationHidingSettings(
            hiddenTargets = listOf(
                HiddenNotificationTarget("com.example", 42, tag = "sync"),
            ),
        )
        assertTrue(settings.shouldHide(own, "com.example", 42, "sync", true))
        assertFalse(settings.shouldHide(own, "com.example", 42, null, true))
        assertFalse(settings.shouldHide(own, "com.example", 43, "sync", true))
        assertFalse(settings.shouldHide(own, "com.other", 42, "sync", true))
    }

    @Test
    fun `channel does not participate in matching`() {
        val settings = PersistentNotificationHidingSettings(
            hiddenTargets = listOf(
                HiddenNotificationTarget("com.example", 7, tag = null, channelId = "old"),
            ),
        )
        assertTrue(settings.shouldHide(own, "com.example", 7, null, true))
        assertTrue(settings.isTargetHidden("com.example", 7, null))
    }
}
