package hunoia.luno.runtime.automation

import hunoia.luno.config.model.AutomationRule
import hunoia.luno.config.model.ConditionItem
import hunoia.luno.config.model.ConditionType
import hunoia.luno.config.model.FireMode
import hunoia.luno.config.model.ThrottleKind
import hunoia.luno.config.model.ThrottlePolicy
import hunoia.luno.config.model.WhenGroup
import hunoia.luno.runtime.condition.ConditionContext
import hunoia.luno.runtime.condition.matches
import hunoia.luno.core.LOG_TAG

/**
 * 自动化引擎：整树边沿（fireOn）+ 优先级仲裁 + 节流策略。
 * 仲裁顺序固定：Event → Condition → Constraints → Priority Arbitration → Throttle → Execution；
 * 选中的规则被节流也不降级，落选边沿命中记跳过；未命中不记历史，触发只判定与记账。
 */
class AutomationEngine(
    private val onFireRule: (AutomationRule, ConditionContext) -> Unit,
    /** 一轮求值的全部跳过事件一次性交付（以前每条起协程写盘，N 条规则即 N 次全量重写）。 */
    private val onEvents: (List<AutomationEvent>) -> Unit,
    /**
     * 触发前预检（能力/通知访问等）。返回原因时只落跳过事件，不消耗节流窗口，
     * 因为规则根本没进入执行。
     */
    private val preflightBlockReason: (AutomationRule) -> SkipReason? = { null },
) {
    private val prevMatches = mutableMapOf<String, Boolean>()

    /** 每条规则上次求值时的条件树，存对象而非签名字符串：data class 结构相等即 equals，比 toString 更严格。 */
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
        if (hunoia.luno.BuildConfig.DEBUG && rules.isNotEmpty()) {
            android.util.Log.i(
                LOG_TAG,
                "eval keys=$effectiveKeys pkg=${ctx.packageName} " +
                    "rules=" + rules.filter { it.enabled }.joinToString(",") {
                        "${it.id.takeLast(4)}:${if (it.id in allowedIds) "hit=${it.matches(ctx, it.id)}" else "skip"}"
                    },
            )
        }
        // 第一遍：只求值订阅命中的规则。被跳过的规则保持旧 prev：
        // 它的输入只会在自己订阅的域刷新时变化，那时必被评估；跨域刷新时输入不变，
        // prev 不更新也不影响正确性，只是不提前刷新——边沿不误触发。
        val candidates = ArrayList<AutomationRule>(rules.size)
        val constraintSkipped = ArrayList<AutomationEvent>(rules.size)
        for (rule in rules) {
            if (!rule.enabled) continue
            if (rule.id !in allowedIds) continue
            val hit = rule.matches(ctx, rule.id)
            val known = lastCondition[rule.id]
            // 首次见到（known==null）或条件被改过就把 prev 当作 hit：
            // 等效于「不产生边沿」，服务启动/新建规则时不会因条件当前为假
            // 而把 EXIT/BOTH 规则误判成下降沿触发；脉冲边沿不受影响（另有 pulseEdge）。
            val prev = if (known != rule.condition) {
                hit
            } else {
                prevMatches.getOrDefault(rule.id, hit)
            }
            lastCondition[rule.id] = rule.condition
            prevMatches[rule.id] = hit

            // 纯事件树（无状态叶）没有持续边沿：进入/退出无意义，统一按进入时处理
            //（编辑页对纯事件树隐藏触发方式；这里防御手工构造的规则）。
            val fireOn = if (hasStateLeaf(rule.condition)) rule.fireOn else FireMode.ENTER
            // 脉冲命中即边沿：脉冲读一次即消费，prev 机制对它恒卡死
            //（首次求值 prev=hit → 非脉冲路径永无进入沿），所以由 pulseEdge 单独放行。
            // 非否定脉冲叶命中即视为边沿，只影响 ENTER/BOTH 的进入侧。
            val pulseEdge = fireOn != FireMode.EXIT && hasPresentPulse(rule.condition, ctx, rule.id)
            val edge = when (fireOn) {
                FireMode.ENTER -> hit && (!prev || pulseEdge)
                FireMode.EXIT -> !hit && prev
                FireMode.BOTH -> hit != prev || (hit && pulseEdge)
            }
            if (edge) {
                // 约束条件是「此刻是否适用」，空组视为不限。不满足的规则不进入仲裁，
                // 让同一轮里约束满足的低优先级规则有机会接管；也不消耗节流窗口。
                if (rule.constraints.items.isEmpty() || rule.constraints.matches(ctx)) {
                    candidates.add(rule)
                } else {
                    constraintSkipped.add(
                        AutomationEvent(
                            ruleId = rule.id, ruleName = rule.name,
                            timeMs = ctx.nowMs, wallTimeMs = System.currentTimeMillis(),
                            fired = false, reason = SkipReason.CONSTRAINT_UNMET.code,
                        ),
                    )
                }
            }
        }
        if (candidates.isEmpty()) {
            dispatch(constraintSkipped)
            cleanup(rules)
            return
        }
        // 仲裁：优先级降序；sort 稳定，同级保持用户列表顺序。
        // createdAtMs 只做导入/合并等列表顺序不可信时的兜底元数据，不参与本次比较。
        val ordered = candidates.sortedByDescending { it.priority }
        val winner = ordered.first()
        // 本轮所有跳过事件先在本地攒起来，最后一次性交付（见 onEvents 注释）。
        val skipped = ArrayList<AutomationEvent>(ordered.size + constraintSkipped.size)
        skipped.addAll(constraintSkipped)
        for (rule in ordered) {
            if (rule.id == winner.id) continue
            skipped.add(
                AutomationEvent(
                    ruleId = rule.id, ruleName = rule.name, timeMs = ctx.nowMs, wallTimeMs = System.currentTimeMillis(),
                    fired = false, reason = SkipReason.OUTPRIORITIZED.code,
                )
            )
        }
        val steps = winner.effect.steps
        if (steps.isEmpty()) {
            skipped.add(
                AutomationEvent(
                    ruleId = winner.id, ruleName = winner.name, timeMs = ctx.nowMs, wallTimeMs = System.currentTimeMillis(),
                    fired = false, reason = SkipReason.NO_STEPS.code,
                )
            )
            dispatch(skipped)
            cleanup(rules)
            return
        }
        val throttle = winner.throttle
        val blockReason = throttleBlockReason(winner.id, throttle, ctx.nowMs)
        if (blockReason != null) {
            skipped.add(
                AutomationEvent(
                    ruleId = winner.id, ruleName = winner.name, timeMs = ctx.nowMs, wallTimeMs = System.currentTimeMillis(),
                    fired = false, reason = blockReason.code,
                )
            )
            dispatch(skipped)
            cleanup(rules)
            return
        }
        // 权限预检放在节流判定之后、markFired 之前：被节流的规则不重复扫描，
        // 缺权限的规则也不会吃掉冷却/窗口次数。
        val preflightReason = preflightBlockReason(winner)
        if (preflightReason != null) {
            skipped.add(
                AutomationEvent(
                    ruleId = winner.id, ruleName = winner.name, timeMs = ctx.nowMs,
                    wallTimeMs = System.currentTimeMillis(),
                    fired = false, reason = preflightReason.code,
                )
            )
            dispatch(skipped)
            cleanup(rules)
            return
        }
        // 跳过记录先落，再触发执行：历史顺序保持「谁被压制」在前。
        dispatch(skipped)
        markFired(winner.id, throttle, ctx.nowMs)
        onFireRule(winner, ctx.copy(triggerRuleName = winner.name.ifBlank { null }))
        cleanup(rules)
    }

    private fun dispatch(events: List<AutomationEvent>) {
        if (events.isNotEmpty()) onEvents(events)
    }

    /** 值驱动节流：OFF 直接放行；冷却（cooldownMs>0）与窗口（count>=1 且 window>=1s）先冷后窗。 */
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

/**
 * 条件树里是否有命中的非否定脉冲叶（raw 语义，未取反）。
 * raw 求值复用 matches()（copy 去否定），脉冲类型判定复用 AutomationBus.isPulseConditionType，避免两处清单漂移。
 */
private fun hasPresentPulse(group: WhenGroup, ctx: ConditionContext, ruleId: String): Boolean {
    return group.items.any { item -> item.hasPresentPulse(ctx, ruleId) }
}

private fun ConditionItem.hasPresentPulse(ctx: ConditionContext, ruleId: String): Boolean {
    if (negated) return false
    if (type == ConditionType.GROUP) return group?.let { hasPresentPulse(it, ctx, ruleId) } == true
    if (!isPulseConditionType(type)) return false
    return copy(negated = false).matches(ctx, ruleId)
}
