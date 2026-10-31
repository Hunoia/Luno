package hunoia.luno.runtime.floatball.inbox

import org.junit.Assert.assertEquals
import org.junit.Test

class BallInboxConfigTest {

    @Test
    fun `defaults match the v1 design`() {
        val config = BallInboxConfig()

        assertEquals(120_000L, config.notificationTtlMs)
        assertEquals(300_000L, config.clipboardTtlMs)
        assertEquals(10, config.maxItems)
        assertEquals(120_000L, config.ttlFor(BallSource.NOTIFICATION))
        assertEquals(300_000L, config.ttlFor(BallSource.CLIPBOARD))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `max items must be positive`() {
        BallInboxConfig(maxItems = 0)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `ttl must be positive`() {
        BallInboxConfig(notificationTtlMs = 0L)
    }
}
