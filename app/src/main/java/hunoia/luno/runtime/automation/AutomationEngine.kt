package hunoia.luno.runtime.automation

import hunoia.luno.config.model.AutomationRule
import hunoia.luno.config.model.ConditionItem
import hunoia.luno.config.model.ConditionType
import hunoia.luno.config.model.FireMode
import hunoia.luno.config.model.RuleEffectType
import hunoia.luno.config.model.ThrottleKind
import hunoia.luno.config.model.ThrottlePolicy
import hunoia.luno.config.model.WhenGroup
import hunoia.luno.runtime.condition.ConditionContext
import hunoia.luno.runtime.condition.matches

/**
 * v2 自动化引擎：整树边沿（fireOn）+ 优先级仲裁 + 节流策略 + 执行历史。
 * 仲裁顺序固定：Event → Condition → Priority Arbitration → Throttle → Execution。
 * 选中的规则即使被节流也不降级到低优先级规则；落选的边沿命中记跳过。
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

    /** 订阅索引：规则变更时重建，事件到达直接查表（只评估相关规则）。 */
    private val subscriptionIndex = SubscriptionIndex()

    /** 上次 cleanup 过的 rules 引用,用于跳过没变的情况 */
    private var cleanedRules: List<AutomationRule>? = null

    /**
     * @param eventKeys 本轮刷新的事件域。空集 = 通用刷新，只评估含状态叶子的规则；
     * 快照里实际携带的脉冲键会自动并入（防抖合并导致一次刷新带多个脉冲时不丢）。
     */
    fun evaluate(rules: List<AutomationRule>, ctx: ConditionContext, eventKeys: Set<String> = emptySet()) {
        subscriptionIndex.refreshIfNeeded(rules)
        val effectiveKeys = eventKeys + pulseKeysIn(ctx)
        val allowedIds = subscriptionIndex.ruleIdsForKeys(effectiveKeys)
        // 第一遍：只求值订阅命中的规则。被跳过的规则保持旧 prev：
        // 它的输入只会在自己订阅的域刷新时变化，那时必被评估；跨域刷新时输入不变，
        // prev 不更新也不影响正确性，只是不提前刷新——边沿不误触发。
        val candidates = ArrayList<AutomationRule>(rules.size)
        for (rule in rules) {
            if (!rule.enabled) continue
            if (rule.id !in allowedIds) continue
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
            // 脉冲命中即边沿：脉冲读一次即消费，prev 机制对它恒卡死
            //（默认 true + 跳过不更新 → 首评及之后永无 ENTER 边沿）。
            // 非否定脉冲叶命中即视为边沿，只影响 ENTER/BOTH 的进入侧。
            val pulseEdge = rule.fireOn != FireMode.EXIT && hasPresentPulse(rule.condition, ctx)
            val edge = when (rule.fireOn) {
                FireMode.ENTER -> hit && (!prev || pulseEdge)
                FireMode.EXIT -> !hit && prev
                FireMode.BOTH -> hit != prev || (hit && pulseEdge)
            }
            if (edge) candidates.add(rule)
        }
        if (candidates.isEmpty()) {
            cleanup(rules)
            return
        }
        // 仲裁：优先级降序；sort 稳定，同级保持用户列表顺序。
        // createdAtMs 只做导入/合并等列表顺序不可信时的兜底元数据，不参与本次比较。
        val ordered = candidates.sortedByDescending { it.priority }
        val winner = ordered.first()
        for (rule in ordered) {
            if (rule.id == winner.id) continue
            onEvent(
                AutomationEvent(
                    ruleId = rule.id, ruleName = rule.name, timeMs = ctx.nowMs, wallTimeMs = System.currentTimeMillis(),
                    fired = false, reason = SkipReason.OUTPRIORITIZED.code,
                )
            )
        }
        val steps = winner.effect.effectiveSteps()
        if (steps.isEmpty()) {
            onEvent(
                AutomationEvent(
                    ruleId = winner.id, ruleName = winner.name, timeMs = ctx.nowMs, wallTimeMs = System.currentTimeMillis(),
                    fired = false, reason = SkipReason.NO_STEPS.code,
                )
            )
            cleanup(rules)
            return
        }
        val throttle = winner.effectiveThrottle()
        val blockReason = throttleBlockReason(winner.id, throttle, ctx.nowMs)
        if (blockReason != null) {
            onEvent(
                AutomationEvent(
                    ruleId = winner.id, ruleName = winner.name, timeMs = ctx.nowMs, wallTimeMs = System.currentTimeMillis(),
                    fired = false, reason = blockReason.code,
                )
            )
            cleanup(rules)
            return
        }
        markFired(winner.id, throttle, ctx.nowMs)
        onFireRule(winner, ctx)
        cleanup(rules)
    }

    /**
     * 值驱动节流：kind==OFF 是存量总闸直接放行；其余看值——
     * 冷却（cooldownMs>0）与窗口（count>=1 且 window>=1s）各算各的，先冷后窗。
     */
    private fun throttleBlockReason(ruleId: String, throttle: ThrottlePolicy, nowMs: Long): SkipReason? {
        if (throttle.kind == ThrottleKind.OFF) return null
        if (throttle.cooldownMs > 0) {
            val last = cooldownLastFiredAt[ruleId]
            if (last != null && nowMs - last < throttle.cooldownMs) {
                return SkipReason.THROTTLED_COOLDOWN
            }
        }
        if (throttle.maxCount >= 1 && throttle.windowMs >= 1000L) {
            val windowMs = throttle.windowMs
            val maxCount = throttle.maxCount
            val queue = windowHits.getOrPut(ruleId) { ArrayDeque() }
            while (queue.isNotEmpty() && nowMs - queue.first() >= windowMs) queue.removeFirst()
            if (queue.size >= maxCount) return SkipReason.THROTTLED_WINDOW
        }
        return null
    }

    private fun markFired(ruleId: String, throttle: ThrottlePolicy, nowMs: Long) {
        if (throttle.kind != ThrottleKind.OFF && throttle.cooldownMs > 0) {
            cooldownLastFiredAt[ruleId] = nowMs
        }
        if (throttle.kind != ThrottleKind.OFF && throttle.maxCount >= 1 && throttle.windowMs >= 1000L) {
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

/** 脉冲条件类型：快照携带即命中，读一次即消费。 */
private val PULSE_TYPES = setOf(
    ConditionType.APP_CHANGED_TO,
    ConditionType.SCREEN_EVENT,
    ConditionType.CHARGING_CHANGED,
    ConditionType.VOLUME_CHANGED,
    ConditionType.NOTIFICATION_POSTED,
    ConditionType.BLUETOOTH_CHANGED,
    ConditionType.HEADPHONES_CHANGED,
    ConditionType.SCHEDULED_TIME,
    ConditionType.GESTURE_PERFORMED,
)

/**
 * 条件树里是否有命中的非否定脉冲叶（raw 语义，未取反）。
 * raw 求值复用 matches()（copy 去否定），不复制各脉冲的过滤逻辑。
 */
private fun hasPresentPulse(group: WhenGroup, ctx: ConditionContext): Boolean {
    return group.items.any { item -> item.hasPresentPulse(ctx) }
}

private fun ConditionItem.hasPresentPulse(ctx: ConditionContext): Boolean {
    if (negated) return false
    if (type == ConditionType.GROUP) return group?.let { hasPresentPulse(it, ctx) } == true
    if (type !in PULSE_TYPES) return false
    return copy(negated = false).matches(ctx)
}
