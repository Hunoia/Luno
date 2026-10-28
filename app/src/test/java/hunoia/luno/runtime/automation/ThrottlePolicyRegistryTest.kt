package hunoia.luno.runtime.automation

import hunoia.luno.config.model.ThrottleKind
import hunoia.luno.config.model.ThrottlePolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class ThrottlePolicyRegistryTest {

    @Test
    fun `off policy always passes`() {
        val state = ThrottleRuntimeState()
        assertNull(
            ThrottlePolicyRegistry.handler(ThrottleKind.OFF)
                .blockReason(ThrottlePolicy(kind = ThrottleKind.OFF), state, 0L),
        )
    }

    @Test
    fun `cooldown blocks inside window then allows after`() {
        val handler = ThrottlePolicyRegistry.handler(ThrottleKind.COOLDOWN)
        val policy = ThrottlePolicy(kind = ThrottleKind.COOLDOWN, cooldownMs = 1_000L)
        val state = ThrottleRuntimeState()

        handler.markFired(policy, state, 10_000L)
        assertEquals(
            SkipReason.THROTTLED_COOLDOWN,
            handler.blockReason(policy, state, 10_500L),
        )
        assertNull(handler.blockReason(policy, state, 11_000L))
    }

    @Test
    fun `window blocks after max count`() {
        val handler = ThrottlePolicyRegistry.handler(ThrottleKind.WINDOW)
        val policy = ThrottlePolicy(kind = ThrottleKind.WINDOW, maxCount = 2, windowMs = 60_000L)
        val state = ThrottleRuntimeState()

        handler.markFired(policy, state, 1_000L)
        assertNull(handler.blockReason(policy, state, 2_000L))
        handler.markFired(policy, state, 2_000L)
        assertEquals(SkipReason.THROTTLED_WINDOW, handler.blockReason(policy, state, 3_000L))
        assertNull(handler.blockReason(policy, state, 61_000L))
    }

    @Test
    fun `duplicate kind fails fast`() {
        try {
            ThrottlePolicyRegistry.register(
                ThrottleKind.OFF,
                object : ThrottlePolicyHandler {
                    override fun blockReason(
                        policy: ThrottlePolicy,
                        state: ThrottleRuntimeState,
                        nowMs: Long,
                    ): SkipReason? = null

                    override fun markFired(
                        policy: ThrottlePolicy,
                        state: ThrottleRuntimeState,
                        nowMs: Long,
                    ) = Unit
                },
            )
            fail("重复 kind 应直接失败")
        } catch (expected: IllegalArgumentException) {
            assertTrue(expected.message.orEmpty().contains("duplicate"))
        }
    }
}
