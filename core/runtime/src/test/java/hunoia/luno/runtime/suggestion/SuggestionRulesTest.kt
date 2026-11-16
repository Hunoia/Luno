package hunoia.luno.runtime.suggestion

import hunoia.luno.config.model.SuggestionRule
import hunoia.luno.config.model.SuggestionRuleAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 规则 → 建议槽的投影：建议胶囊与识屏幽灵卡共用这一份，口径必须与规则表一致
 * （顺序、命名、动作原样透传；展示/执行侧的差异不在这里判）。
 */
class SuggestionRulesTest {

    private fun slots(text: String, vararg rules: SuggestionRule): List<SuggestionSlot> =
        SuggestionRules.slotsOf(text, SuggestionRules.compile(rules.toList()))

    @Test
    fun `capture group becomes the value`() {
        val hits = slots(
            "您的验证码是 482913，5分钟内有效",
            SuggestionRule(
                id = "code",
                name = "验证码",
                pattern = """验证码是\s*(\d{6})""",
            ),
        )
        assertEquals(1, hits.size)
        assertEquals("482913", hits[0].value)
        assertEquals("验证码", hits[0].label)
        assertEquals("code", hits[0].ruleId)
        assertEquals(SuggestionRuleAction.COPY, hits[0].action)
    }

    @Test
    fun `action is passed through untouched`() {
        val rules = SuggestionRuleAction.entries.map { action ->
            SuggestionRule(id = action.name, pattern = """id=(\w+)""", action = action)
        }
        val hits = slots("id=abc", *rules.toTypedArray())
        assertEquals(SuggestionRuleAction.entries, hits.map { it.action })
    }

    @Test
    fun `rule order and hit count follow the rule table`() {
        val hits = slots(
            "a=1 b=2",
            SuggestionRule(id = "b", pattern = """b=(\d)"""),
            SuggestionRule(id = "a", pattern = """a=(\d)"""),
        )
        assertEquals(listOf("2", "1"), hits.map { it.value })
    }

    @Test
    fun `blank label is kept for the caller to fall back`() {
        val hits = slots("x=9", SuggestionRule(id = "x", pattern = """x=(\d)"""))
        assertEquals("", hits[0].label)
    }

    @Test
    fun `disabled or invalid rules do not produce slots`() {
        val hits = slots(
            "x=9",
            SuggestionRule(id = "off", pattern = """x=(\d)""", enabled = false),
            SuggestionRule(id = "bad", pattern = """x=(\d"""),
            SuggestionRule(id = "blank", pattern = ""),
        )
        assertTrue(hits.isEmpty())
    }

    @Test
    fun `no rule or no text yields nothing`() {
        assertTrue(slots("x=9").isEmpty())
        assertTrue(slots("", SuggestionRule(pattern = """x=(\d)""")).isEmpty())
        assertTrue(SuggestionRules.compile(emptyList()).isEmpty)
    }
}
