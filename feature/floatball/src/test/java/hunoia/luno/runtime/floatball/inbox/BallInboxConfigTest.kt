package hunoia.luno.runtime.floatball.inbox

import org.junit.Assert.assertEquals
import org.junit.Test

class BallInboxConfigTest {

    @Test
    fun `defaults match the design`() {
        val config = BallInboxConfig()

        assertEquals(120_000L, config.notificationTtlMs)
        assertEquals(300_000L, config.suggestionTtlMs)
        assertEquals(10, config.maxItems)
        assertEquals(120_000L, config.ttlFor(BallSource.NOTIFICATION))
        assertEquals(300_000L, config.ttlFor(BallSource.SUGGESTION))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `max items must be positive`() {
        BallInboxConfig(maxItems = 0)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `ttl must be positive`() {
        BallInboxConfig(notificationTtlMs = 0L)
    }

    @Test
    fun `item ttl overrides the source default`() {
        val config = BallInboxConfig(notificationTtlMs = 2_000L, suggestionTtlMs = 5_000L)
        val code = SuggestionItem(
            sourceKey = "k",
            title = "验证码",
            text = "1234",
            receivedAtElapsedMs = 0L,
            ttlMs = 300_000L,
        )

        assertEquals(300_000L, code.effectiveTtlMs(config))
    }
}
