package hunoia.luno.bridge.system

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationAccessTest {
    private val expected =
        "hunoia.luno.debug/hunoia.luno.runtime.floatball.notification.NotificationPulseListener"

    @Test
    fun `matches the exact listener component`() {
        assertTrue(NotificationAccess.isListenerEnabled(expected, expected))
        assertFalse(
            NotificationAccess.isListenerEnabled(
                "hunoia.luno.debug/hunoia.luno.runtime.OtherListener",
                expected,
            ),
        )
    }

    @Test
    fun `does not accept another component from the same package`() {
        val value = listOf(
            "hunoia.luno.debug/hunoia.luno.runtime.OtherListener",
            expected,
        ).joinToString(":")

        assertTrue(NotificationAccess.isListenerEnabled(value, expected))
        assertFalse(
            NotificationAccess.isListenerEnabled(
                "hunoia.luno.debug/hunoia.luno.runtime.OtherListener",
                expected,
            ),
        )
    }
}
