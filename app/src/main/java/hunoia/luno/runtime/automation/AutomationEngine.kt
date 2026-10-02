package hunoia.luno.runtime.automation

import hunoia.luno.config.model.AutomationRule
import hunoia.luno.config.model.RuleEffect
import hunoia.luno.config.model.WhenGroup
import hunoia.luno.config.model.RuleEffectType
import hunoia.luno.runtime.condition.ConditionContext
import hunoia.luno.runtime.condition.matches

class AutomationEngine(
    private val onRunEffect: (RuleEffect, ConditionContext) -> Unit,
) {
    private val prevMatches = mutableMapOf<String, Boolean>()
    private val lastFiredAt = mutableMapOf<String, Long>()

    /**
     * 每条规则上次求值时的条件树。存对象本身而不是签名字符串 —— WhenGroup / ConditionItem
     * 都是 data class,结构相等就是 equals(),不需要像以前那样 toString() 把整棵条件树
     * 递归拼成字符串(每次求值、每条规则各一次,嵌套分组时字符串还不短)。
     * 用 equals 比 toString 严格更正确:后者对字段值里恰好含有分隔文本的情况会误判为相同。
     */
    private val lastCondition = mutableMapOf<String, WhenGroup>()

    /** 上次 cleanup 过的 rules 引用,用于跳过没变的情况 */
    private var cleanedRules: List<AutomationRule>? = null

    fun evaluate(rules: List<AutomationRule>, ctx: ConditionContext) {
        for (rule in rules) {
            if (!rule.enabled) continue
            val hit = rule.matches(ctx)
            val known = lastCondition[rule.id]
            // 条件被改过就把 prev 当作 hit,等价于「条件一变就重新评估边沿」
            val prev = if (known != null && known != rule.condition) {
                hit
            } else {
                prevMatches.getOrDefault(rule.id, true)
            }
            lastCondition[rule.id] = rule.condition
            prevMatches[rule.id] = hit

            if (rule.effect.type != RuleEffectType.RUN_ACTION) continue
            if (!hit || prev) continue
            if (!rule.effect.hasAction) continue
            val last = lastFiredAt[rule.id]
            if (last != null && ctx.nowMs - last < rule.cooldownMs) continue
            lastFiredAt[rule.id] = ctx.nowMs
            onRunEffect(rule.effect, ctx)
        }
        cleanup(rules)
    }

    private fun cleanup(rules: List<AutomationRule>) {
        // SettingsStore.snapshot() 在状态没变时返回同一个实例,所以引用相同就意味着规则集合没变,
        // 不必每次求值都建一个 HashSet 再对三个 map 做 retainAll。
        if (cleanedRules === rules) return
        cleanedRules = rules
        val ids = HashSet<String>(rules.size)
        for (rule in rules) ids.add(rule.id)
        prevMatches.keys.retainAll(ids)
        lastFiredAt.keys.retainAll(ids)
        lastCondition.keys.retainAll(ids)
    }
}
