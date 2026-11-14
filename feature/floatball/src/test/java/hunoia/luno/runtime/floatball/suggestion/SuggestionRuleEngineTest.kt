package hunoia.luno.runtime.floatball.suggestion

import hunoia.luno.config.model.SuggestionRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SuggestionRuleEngineTest {

    @Test
    fun `uses the first capture group`() {
        val compiled = SuggestionRuleEngine.compile(
            listOf(SuggestionRule(id = "r", pattern = "验证码\\s*(\\d{4,8})")),
        )

        val hit = compiled.match("您的验证码 7788，请勿泄露").single()
        assertEquals("7788", hit.value)
        assertEquals("r", hit.rule.id)
    }

    @Test
    fun `falls back to the whole match without capture groups`() {
        val compiled = SuggestionRuleEngine.compile(
            listOf(SuggestionRule(id = "r", pattern = "PIN\\s+\\d{4}")),
        )

        assertEquals("PIN 4321", compiled.match("你的 PIN 4321 已生成").single().value)
    }

    @Test
    fun `only the first match of each rule is used`() {
        val compiled = SuggestionRuleEngine.compile(
            listOf(SuggestionRule(id = "r", pattern = "(\\d{4})")),
        )

        assertEquals("1111", compiled.match("1111 和 2222").single().value)
    }

    @Test
    fun `disabled and invalid rules are skipped`() {
        val compiled = SuggestionRuleEngine.compile(
            listOf(
                SuggestionRule(id = "off", pattern = "(\\d{4})", enabled = false),
                SuggestionRule(id = "bad", pattern = "([0-9"),
                SuggestionRule(id = "blank", pattern = " "),
                SuggestionRule(id = "ok", pattern = "验证码\\s*(\\d{4})"),
            ),
        )

        assertEquals(listOf("ok"), compiled.match("验证码 1234").map { it.rule.id })
    }

    @Test
    fun `long clipboard text is truncated before matching`() {
        val compiled = SuggestionRuleEngine.compile(
            listOf(SuggestionRule(id = "tail", pattern = "验证码\\s*(\\d{4})")),
        )
        val text = "x".repeat(SuggestionRuleEngine.MaxScanChars - 2) + "验证码 1234"

        assertTrue(compiled.match(text).isEmpty())
    }

    @Test
    fun `capture group must be non-blank to hit`() {
        val compiled = SuggestionRuleEngine.compile(
            listOf(SuggestionRule(id = "r", pattern = "验证码\\s*(\\d*)")),
        )

        assertTrue(compiled.match("验证码").isEmpty())
    }
}
