package hunoia.luno.runtime.automation

import hunoia.luno.config.model.AutomationRule
import hunoia.luno.config.model.FireMode
import hunoia.luno.config.model.RuleEffectType
import hunoia.luno.config.model.ThrottleKind
import hunoia.luno.config.model.ThrottlePolicy
import hunoia.luno.config.model.WhenGroup
import hunoia.luno.runtime.condition.ConditionContext
import hunoia.luno.runtime.condition.matches

/**
 * v2 自动化引擎：整树边沿（fireOn）+ 节流策略 + 执行历史。
 * 未命中（hit=false）不记历史，只记边沿后的事，避免每次状态刷新刷屏。
 * 触发后只负责判定与节流记账，步骤执行由调用方（Coordinator 独立协程）跑完再补 fired 事件。
 */
class AutomationEngine(
    private val onFireRule: (AutomationRule, ConditionContext) -> Unit,
    private val onEvent: (AutomationEvent) -> Unit,
) {
    private val prevMatches = mutableMapOf<String, Boolean>()

    /**
     * 每条规则上次求值时的条件树。存对象本身而不是签名字符串 —— WhenGroup / ConditionItem
     * 都是 data class,结构相等就是 equals(),不需要像以前那样 toString() 把整棵条件树
     * 递归拼成字符串(每次求值、每条规则各一次,嵌套分组时字符串还不短)。
     * 用 equals 比 toString 严格更正确:后者对字段值里恰好含有分隔文本的情况会误判为相同。
     */
    private val lastCondition = mutableMapOf<String, WhenGroup>()

    private val cooldownLastFiredAt = mutableMapOf<String, Long>()
    private val windowHits = mutableMapOf<String, ArrayDeque<Long>>()

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
            val edge = when (rule.fireOn) {
                FireMode.ENTER -> hit && !prev
                FireMode.EXIT -> !hit && prev
                FireMode.BOTH -> hit != prev
            }
            if (!edge) continue

            val steps = rule.effect.effectiveSteps()
            if (steps.isEmpty()) {
                onEvent(
                    AutomationEvent(
                        ruleId = rule.id, ruleName = rule.name, timeMs = ctx.nowMs,
                        fired = false, reason = ExecutionReason.NO_STEPS,
                    )
                )
                continue
            }
            val throttle = rule.effectiveThrottle()
            val blockReason = throttleBlockReason(rule.id, throttle, ctx.nowMs)
            if (blockReason != null) {
                onEvent(
                    AutomationEvent(
                        ruleId = rule.id, ruleName = rule.name, timeMs = ctx.nowMs,
                        fired = false, reason = blockReason,
                    )
                )
                continue
            }
            markFired(rule.id, throttle, ctx.nowMs)
            onFireRule(rule, ctx)
        }
        cleanup(rules)
    }

    private fun throttleBlockReason(ruleId: String, throttle: ThrottlePolicy, nowMs: Long): String? {
        return when (throttle.kind) {
            ThrottleKind.COOLDOWN -> {
                val last = cooldownLastFiredAt[ruleId]
                if (last != null && nowMs - last < throttle.cooldownMs) {
                    ExecutionReason.THROTTLED_COOLDOWN
                } else {
                    null
                }
            }
            ThrottleKind.WINDOW -> {
                val windowMs = throttle.windowMs.coerceAtLeast(1L)
                val maxCount = throttle.maxCount.coerceAtLeast(1)
                val queue = windowHits.getOrPut(ruleId) { ArrayDeque() }
                while (queue.isNotEmpty() && nowMs - queue.first() >= windowMs) queue.removeFirst()
                if (queue.size >= maxCount) ExecutionReason.THROTTLED_WINDOW else null
            }
            else -> null
        }
    }

    private fun markFired(ruleId: String, throttle: ThrottlePolicy, nowMs: Long) {
        if (throttle.kind == ThrottleKind.COOLDOWN) {
            cooldownLastFiredAt[ruleId] = nowMs
        }
        if (throttle.kind == ThrottleKind.WINDOW) {
            val queue = windowHits.getOrPut(ruleId) { ArrayDeque() }
            queue.addLast(nowMs)
        }
    }

    private fun cleanup(rules: List<AutomationRule>) {
        // SettingsStore.snapshot() 在状态没变时返回同一个实例,所以引用相同就意味着规则集合没变,
        // 不必每次求值都建一个 HashSet 再对 map 做 retainAll。
        if (cleanedRules === rules) return
        cleanedRules = rules
        val ids = HashSet<String>(rules.size)
        for (rule in rules) ids.add(rule.id)
        prevMatches.keys.retainAll(ids)
        lastCondition.keys.retainAll(ids)
        cooldownLastFiredAt.keys.retainAll(ids)
        windowHits.keys.retainAll(ids)
    }
}
