package hunoia.luno.runtime.floatball.suggestion

import hunoia.luno.config.model.SuggestionRule
import hunoia.luno.config.model.SuggestionRuleAction
import hunoia.luno.config.model.SuggestionRuleDefaults
import hunoia.luno.core.domain.NotificationPulse
import hunoia.luno.runtime.floatball.inbox.SuggestionPrimaryAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

    private fun rules(vararg rules: SuggestionRule) = SuggestionRuleEngine.compile(rules.toList())

    @Test
    fun `notification hits the default verification code rule`() {
        val items = SuggestionFactory.fromNotification(
            pulse = pulse(text = "验证码 778899，5 分钟内有效"),
            rules = SuggestionRuleEngine.compile(SuggestionRuleDefaults.rules),
            sourceKey = "k1",
            nowMs = 1_000,
        )

        assertEquals(1, items.size)
        val item = items.first()
        assertEquals("default-code-zh", item.ruleId)
        assertEquals("验证码", item.title)
        assertEquals("778899", item.text)
        assertEquals(SuggestionPrimaryAction.COPY, item.action)
        assertTrue(item.sensitive)
        assertEquals(1_000L, item.receivedAtElapsedMs)
        assertEquals(null, item.ttlMs)
    }

    @Test
    fun `notification without a rule hit yields no suggestion`() {
        val items = SuggestionFactory.fromNotification(
            pulse = pulse(text = "订单已发货"),
            rules = rules(SuggestionRule(name = "验证码", pattern = "验证码\\s*(\\d{4,8})")),
            sourceKey = "k1",
            nowMs = 1_000,
        )

        assertTrue(items.isEmpty())
    }

    @Test
    fun `notification text prefers big text without duplicating the body`() {
        val text = SuggestionFactory.notificationText(
            pulse(title = "标题", text = "短文案", bigText = "短文案，验证码 1234"),
        )

        assertEquals("标题\n短文案，验证码 1234", text)
    }

    @Test
    fun `multiple rules produce items in rule table order`() {
        val items = SuggestionFactory.fromNotification(
            pulse = pulse(text = "验证码 1234，取件码 A-88"),
            rules = rules(
                SuggestionRule(id = "r1", name = "验证码", pattern = "验证码\\s*(\\d{4})"),
                SuggestionRule(id = "r2", name = "取件码", pattern = "取件码\\s*([A-Z]-\\d{2})"),
            ),
            sourceKey = "k1",
            nowMs = 2_000,
        )

        assertEquals(listOf("r1", "r2"), items.map { it.ruleId })
        assertEquals(listOf("1234", "A-88"), items.map { it.text })
    }

    @Test
    fun `clipboard plain text yields a link suggestion`() {
        val items = SuggestionFactory.fromClipboard(
            text = "看看这个 https://example.com/a?b=1",
            rules = rules(),
            sourceKey = "clip",
            nowMs = 2_000,
        )

        assertEquals(1, items.size)
        val item = items.first()
        assertEquals(null, item.ruleId)
        assertEquals("链接", item.title)
        assertEquals(listOf("https://example.com/a?b=1"), item.urls)
        assertEquals(SuggestionPrimaryAction.OPEN, item.action)
        assertFalse(item.sensitive)
        assertEquals(null, item.ttlMs)
    }

    @Test
    fun `clipboard text without links or rule hits yields nothing`() {
        val items = SuggestionFactory.fromClipboard(
            text = "普通文本",
            rules = rules(),
            sourceKey = "clip",
            nowMs = 1,
        )

        assertTrue(items.isEmpty())
    }

    @Test
    fun `rule captured link replaces the built-in link suggestion`() {
        val url = "https://example.com/invite?code=42"
        val items = SuggestionFactory.fromClipboard(
            text = "复制打开 $url",
            rules = rules(
                SuggestionRule(
                    id = "invite",
                    name = "邀请链接",
                    pattern = """(https://example\.com/[^\s]+)""",
                    action = SuggestionRuleAction.OPEN,
                ),
            ),
            sourceKey = "clip",
            nowMs = 3_000,
        )

        assertEquals(1, items.size)
        val item = items.first()
        assertEquals("invite", item.ruleId)
        assertEquals(url, item.text)
        assertEquals(listOf(url), item.urls)
        assertEquals(SuggestionPrimaryAction.OPEN, item.action)
        assertFalse(item.sensitive)
    }

    @Test
    fun `open action on non-link text falls back to copy and stays sensitive`() {
        val items = SuggestionFactory.fromClipboard(
            text = "验证码 5566",
            rules = rules(
                SuggestionRule(
                    id = "code",
                    name = "验证码",
                    pattern = "验证码\\s*(\\d{4})",
                    action = SuggestionRuleAction.OPEN,
                ),
            ),
            sourceKey = "clip",
            nowMs = 4_000,
        )

        assertEquals(1, items.size)
        val item = items.first()
        assertEquals(SuggestionPrimaryAction.COPY, item.action)
        assertTrue(item.sensitive)
        assertTrue(item.urls.isEmpty())
    }

    @Test
    fun `share action keeps the captured text verbatim`() {
        val items = SuggestionFactory.fromClipboard(
            text = "订单号 20240506001",
            rules = rules(
                SuggestionRule(
                    id = "order",
                    name = "订单号",
                    pattern = "订单号\\s*(\\d{6,})",
                    action = SuggestionRuleAction.SHARE,
                ),
            ),
            sourceKey = "clip",
            nowMs = 5_000,
        )

        assertEquals(SuggestionPrimaryAction.SHARE, items.single().action)
        assertEquals("20240506001", items.single().text)
    }

    @Test
    fun `suggestion tostring does not expose the captured text`() {
        val item = SuggestionFactory.fromNotification(
            pulse = pulse(text = "验证码 123456"),
            rules = SuggestionRuleEngine.compile(SuggestionRuleDefaults.rules),
            sourceKey = "k1",
            nowMs = 1,
        ).single()

        assertFalse(item.toString().contains("123456"))
        assertTrue(item.toString().contains("default-code-zh"))
    }
}
