package hunoia.luno.runtime.floatball.suggestion

import hunoia.luno.core.domain.NotificationPulse
import hunoia.luno.runtime.floatball.inbox.SuggestionKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SuggestionFactoryTest {

    private fun pulse(
        title: String = "标题",
        text: String = "",
        bigText: String? = null,
    ) = NotificationPulse(
        packageName = "com.test",
        title = title,
        text = text,
        bigText = bigText,
        key = "k1",
    )

    @Test
    fun `builds a code suggestion from a notification`() {
        val item = SuggestionFactory.verificationCode(
            pulse = pulse(text = "验证码 778899"),
            sourceKey = "k1",
            nowMs = 1_000,
            ttlMs = 300_000,
        )

        assertEquals(SuggestionKind.CODE, item?.kind)
        assertEquals("778899", item?.code)
        assertEquals(300_000L, item?.ttlMs)
        assertEquals(1_000L, item?.receivedAtElapsedMs)
    }

    @Test
    fun `notification without a unique code yields no suggestion`() {
        assertNull(
            SuggestionFactory.verificationCode(
                pulse = pulse(text = "订单已发货"),
                sourceKey = "k1",
                nowMs = 1_000,
                ttlMs = 300_000,
            ),
        )
    }

    @Test
    fun `builds a link suggestion from clipboard text`() {
        val item = SuggestionFactory.link(
            text = "看看这个 https://example.com/a?b=1",
            sourceKey = "clip",
            nowMs = 2_000,
        )

        assertEquals(SuggestionKind.LINK, item?.kind)
        assertEquals(listOf("https://example.com/a?b=1"), item?.urls)
        assertNull(item?.ttlMs)
    }

    @Test
    fun `plain text yields no link suggestion`() {
        assertNull(SuggestionFactory.link("普通文本", "clip", 1))
    }

    @Test
    fun `suggestion tostring does not expose the code`() {
        val item = SuggestionFactory.verificationCode(
            pulse = pulse(text = "验证码 123456"),
            sourceKey = "k1",
            nowMs = 1,
            ttlMs = 300_000,
        )!!
        assertTrue(item.toString().contains("CODE"))
        assertTrue(!item.toString().contains("123456"))
    }
}
