package hunoia.luno.config.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AutomationRuleNormalizerTest {

    @Test
    fun `normalizer sets schema version and clamps loop bounds`() {
        val rule = AutomationRule(
            id = "r",
            name = "r",
            condition = WhenGroup(),
            effect = RuleEffect(
                steps = listOf(
                    RuleStep(kind = RuleStepKind.REPEAT, repeatCount = 999),
                    RuleStep(kind = RuleStepKind.WAIT_UNTIL, waitTimeoutMs = 999_999, waitIntervalMs = 1),
                ),
            ),
        )
        val normalized = AutomationRuleNormalizer.normalize(rule)
        assertEquals(AutomationRuleNormalizer.CURRENT_SCHEMA_VERSION, normalized.schemaVersion)
        assertEquals(SequenceLimits.MAX_REPEAT_COUNT, normalized.effect.steps[0].repeatCount)
        assertEquals(SequenceLimits.MAX_WAIT_MS, normalized.effect.steps[1].waitTimeoutMs)
        assertEquals(SequenceLimits.MIN_WAIT_INTERVAL_MS, normalized.effect.steps[1].waitIntervalMs)
    }

    @Test
    fun `unknown step kind is disabled instead of silently dropped`() {
        val rule = AutomationRule(
            id = "r",
            name = "r",
            condition = WhenGroup(),
            effect = RuleEffect(steps = listOf(RuleStep(kind = "legacy.unknown"))),
        )
        val step = AutomationRuleNormalizer.normalize(rule).effect.steps.single()
        assertEquals(RuleStepKind.ACTION, step.kind)
        assertFalse(step.enabled)
    }

    @Test
    fun `params are trimmed and capped but kept for editor validation`() {
        val rule = AutomationRule(
            id = "r",
            name = "r",
            condition = WhenGroup(),
            effect = RuleEffect(),
            params = listOf(
                RuleParam(" ok ", "1"),
                RuleParam("bad-name", "2"),
            ) + (1..25).map { RuleParam("p$it", "$it") },
        )
        val params = AutomationRuleNormalizer.normalize(rule).params
        assertEquals(20, params.size)
        assertEquals("ok", params[0].name)
        assertEquals("bad-name", params[1].name)
    }

    @Test
    fun `deeply nested condition groups are bounded`() {
        var group = WhenGroup(items = listOf(ConditionItem(type = ConditionType.FOREGROUND_APP)))
        repeat(20) {
            group = WhenGroup(items = listOf(ConditionItem(type = ConditionType.GROUP, group = group)))
        }
        val normalized = AutomationRuleNormalizer.normalize(
            AutomationRule(id = "r", name = "r", condition = group, effect = RuleEffect()),
        )
        var depth = 0
        var current = normalized.condition
        while (true) {
            val nested = current.items.singleOrNull()
                ?.takeIf { it.type == ConditionType.GROUP }
                ?.group ?: break
            current = nested
            depth++
        }
        assertTrue(depth <= 9)
    }

    @Test
    fun `global variables normalize names values and duplicates`() {
        val variables = GlobalVariables(
            listOf(
                GlobalVariable(" count ", " 1 "),
                GlobalVariable("bad name", "2"),
                GlobalVariable("count", "3"),
            ),
        ).normalized()
        assertEquals(1, variables.entries.size)
        assertEquals("count", variables.entries[0].name)
        assertEquals("3", variables.entries[0].value)
        assertTrue(variables.put("count", "4").valueOf("count") == "4")
    }
}
