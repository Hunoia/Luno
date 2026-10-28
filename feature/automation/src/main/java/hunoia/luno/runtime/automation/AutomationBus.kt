package hunoia.luno.runtime.automation

import hunoia.luno.config.model.AutomationRule
import hunoia.luno.config.model.ConditionType
import hunoia.luno.config.model.WhenGroup
import hunoia.luno.runtime.condition.ConditionContext
import hunoia.luno.runtime.condition.ConditionRegistry

/**
 * 自动化事件总线命名与订阅索引。
 * 事件分两类：状态刷新（StateEvent，快照求值）与一次性脉冲（PulseEvent，命中即边沿）；
 * 每次只评估订阅索引命中的规则，不扫描全部。
 */
object AutomationEventKey {
    const val APP = "app"
    const val SCREEN = "screen"
    const val KEYBOARD = "keyboard"
    const val CHARGING = "charging"
    const val VOLUME = "volume"
    const val NOTIFICATION = "notification"
    const val PACKAGE = "package"
    const val INTERVAL = "interval"
    const val SHARE = "share"
    const val CLIPBOARD = "clipboard"
    const val BLUETOOTH = "bluetooth"
    const val HEADPHONES = "headphones"
    const val TIME = "time"
    const val GESTURE = "gesture"
    const val MUSIC = "music"
    const val TORCH = "torch"
    const val DND = "dnd"
    const val VARIABLE = "variable"
}

/** 条件类型 → 订阅键。状态与脉冲同域归一键；GROUP 自身不订阅。 */
fun keysForConditionType(type: String): Set<String> = ConditionRegistry.subscriptionKeys(type)

/** 叶子是否为脉冲条件；分类声明在各自的 [hunoia.luno.runtime.condition.Condition] 实现里。 */
fun isPulseConditionType(type: String): Boolean = ConditionRegistry.isPulse(type)

/** 快照里实际携带的脉冲键（防抖合并导致一次刷新带多个脉冲时不丢）。 */
fun pulseKeysIn(ctx: ConditionContext): Set<String> = EventSourceRegistry.pulseKeysIn(ctx)

private fun collectKeys(group: WhenGroup, out: MutableSet<String>) {
    for (item in group.items) {
        if (item.type == ConditionType.GROUP) {
            item.group?.let { collectKeys(it, out) }
        } else {
            out.addAll(keysForConditionType(item.type))
        }
    }
}

/**
 * 否定脉冲（如「没有收到通知」）被当作非状态叶：只在对应脉冲事件到达时评估，
 * 语义是「本次事件不是这个脉冲」；纯否定脉冲规则不进通用刷新，否则恒真会疯狂触发。
 */
fun hasStateLeaf(group: WhenGroup): Boolean {
    for (item in group.items) {
        if (item.type == ConditionType.GROUP) {
            if (item.group?.let(::hasStateLeaf) == true) return true
        } else if (item.type != ConditionType.MANUAL && !ConditionRegistry.isPulse(item.type)) {
            // MANUAL 不订阅任何事件域：纯 MANUAL 规则永不评估，只能被 runRule 调用。
            return true
        }
    }
    return false
}

/** 订阅索引：事件键 → 规则 id。规则变更时重建；空键（通用刷新）只评估含状态叶子的规则。 */
class SubscriptionIndex {
    private var indexedRules: List<AutomationRule>? = null
    private var byKey: Map<String, Set<String>> = emptyMap()
    private var withState: Set<String> = emptySet()

    fun refreshIfNeeded(rules: List<AutomationRule>) {
        if (indexedRules === rules) return
        indexedRules = rules
        val keyed = mutableMapOf<String, MutableSet<String>>()
        val state = mutableSetOf<String>()
        for (rule in rules) {
            if (!rule.enabled) continue
            val keys = mutableSetOf<String>()
            collectKeys(rule.condition, keys)
            collectKeys(rule.constraints, keys)
            for (key in keys) {
                keyed.getOrPut(key) { mutableSetOf() }.add(rule.id)
            }
            if (hasStateLeaf(rule.condition) || hasStateLeaf(rule.constraints)) state.add(rule.id)
        }
        byKey = keyed
        withState = state
    }

    /** 空键（通用刷新）只评估含状态叶子的规则；给键则评估订阅该键的规则。 */
    fun ruleIdsForKeys(keys: Set<String>): Set<String> {
        if (keys.isEmpty()) return withState
        val out = mutableSetOf<String>()
        for (key in keys) {
            byKey[key]?.let { out.addAll(it) }
        }
        return out
    }
}
