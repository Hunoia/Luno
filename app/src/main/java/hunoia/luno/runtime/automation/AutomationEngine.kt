package hunoia.luno.runtime.automation

import hunoia.luno.config.model.AutomationRule
import hunoia.luno.config.model.WhenGroup
import hunoia.luno.config.model.RuleEffectType
import hunoia.luno.runtime.condition.ConditionContext
import hunoia.luno.runtime.condition.matches

class AutomationEngine(
    private val onRunEntry: (String) -> Unit,
) {
    private val prevMatches = mutableMapOf<String, Boolean>()
    private val lastFiredAt = mutableMapOf<String, Long>()
    private val conditionKey = mutableMapOf<String, String>()

    fun evaluate(rules: List<AutomationRule>, ctx: ConditionContext) {
        for (rule in rules) {
            if (!rule.enabled) continue
            val hit = rule.matches(ctx)
            val signature = rule.condition.signature()
            val knownSignature = conditionKey[rule.id]
            val prev = if (knownSignature != null && knownSignature != signature) {
                hit
            } else {
                prevMatches.getOrDefault(rule.id, true)
            }
            conditionKey[rule.id] = signature
            prevMatches[rule.id] = hit

            if (rule.effect.type != RuleEffectType.RUN_ACTION) continue
            if (!hit || prev) continue
            val entryId = rule.effect.entryId
            if (entryId.isBlank()) continue
            val last = lastFiredAt[rule.id]
            if (last != null && ctx.nowMs - last < rule.cooldownMs) continue
            lastFiredAt[rule.id] = ctx.nowMs
            onRunEntry(entryId)
        }
        cleanup(rules)
    }

    private fun cleanup(rules: List<AutomationRule>) {
        val ids = rules.mapTo(HashSet()) { it.id }
        prevMatches.keys.retainAll(ids)
        lastFiredAt.keys.retainAll(ids)
        conditionKey.keys.retainAll(ids)
    }
}

private fun WhenGroup.signature(): String = toString()
