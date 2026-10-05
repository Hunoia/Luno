package hunoia.luno.runtime.automation

import hunoia.luno.config.model.AutomationRule
import hunoia.luno.config.model.ConditionType
import hunoia.luno.config.model.WhenGroup
import hunoia.luno.runtime.condition.ConditionContext

/**
 * 自动化事件总线命名与订阅索引。
 *
 * 事件分两类：状态刷新（StateEvent，快照求值）与一次性脉冲（PulseEvent，
 * 命中即边沿）。每次事件只评估订阅索引命中的规则，不扫描全部。
 */
object AutomationEventKey {
    const val APP = "app"
    const val SCREEN = "screen"
    const val CHARGING = "charging"
    const val VOLUME = "volume"
    const val NOTIFICATION = "notification"
    const val BLUETOOTH = "bluetooth"
    const val HEADPHONES = "headphones"
    const val TIME = "time"
    const val GESTURE = "gesture"
}

/** 条件类型 → 订阅键。状态与脉冲同域归一键；GROUP 自身不订阅。 */
fun keysForConditionType(type: String): Set<String> = when (type) {
    ConditionType.FOREGROUND_APP,
    ConditionType.APP_CHANGED_TO -> setOf(AutomationEventKey.APP)

    ConditionType.SCREEN_STATE,
    ConditionType.SCREEN_EVENT -> setOf(AutomationEventKey.SCREEN)

    ConditionType.BATTERY_RANGE,
    ConditionType.CHARGING,
    ConditionType.CHARGING_CHANGED -> setOf(AutomationEventKey.CHARGING)

    ConditionType.TIME_RANGE,
    ConditionType.SCHEDULED_TIME -> setOf(AutomationEventKey.TIME)

    ConditionType.VOLUME_RANGE,
    ConditionType.RINGER_SILENT,
    ConditionType.VOLUME_CHANGED -> setOf(AutomationEventKey.VOLUME)

    ConditionType.NETWORK_TYPE,
    ConditionType.AIRPLANE_MODE -> setOf(AutomationEventKey.APP)

    ConditionType.HEADPHONES,
    ConditionType.HEADPHONES_CHANGED -> setOf(AutomationEventKey.HEADPHONES)

    ConditionType.BLUETOOTH,
    ConditionType.BLUETOOTH_CHANGED -> setOf(AutomationEventKey.BLUETOOTH)

    ConditionType.NOTIFICATION_POSTED -> setOf(AutomationEventKey.NOTIFICATION)

    ConditionType.GESTURE_PERFORMED -> setOf(AutomationEventKey.GESTURE)

    else -> emptySet()
}

/** 叶子是否为脉冲条件（一次性消费）；其余为状态条件（参与边沿快照）。 */
fun isPulseConditionType(type: String): Boolean = when (type) {
    ConditionType.APP_CHANGED_TO,
    ConditionType.SCREEN_EVENT,
    ConditionType.CHARGING_CHANGED,
    ConditionType.VOLUME_CHANGED,
    ConditionType.NOTIFICATION_POSTED,
    ConditionType.BLUETOOTH_CHANGED,
    ConditionType.HEADPHONES_CHANGED,
    ConditionType.SCHEDULED_TIME,
    ConditionType.GESTURE_PERFORMED -> true
    else -> false
}

/** 快照里实际携带的脉冲键（防抖合并导致一次刷新带多个脉冲时不丢）。 */
fun pulseKeysIn(ctx: ConditionContext): Set<String> {
    val keys = mutableSetOf<String>()
    if (ctx.appChangedTo != null) keys.add(AutomationEventKey.APP)
    if (ctx.screenEvent != null) keys.add(AutomationEventKey.SCREEN)
    if (ctx.chargingChangedTo != null) keys.add(AutomationEventKey.CHARGING)
    if (ctx.volumeChanged != null) keys.add(AutomationEventKey.VOLUME)
    if (ctx.notificationPosted != null) keys.add(AutomationEventKey.NOTIFICATION)
    if (ctx.bluetoothChanged != null) keys.add(AutomationEventKey.BLUETOOTH)
    if (ctx.headphonesChanged != null) keys.add(AutomationEventKey.HEADPHONES)
    if (ctx.scheduledTimeHit) keys.add(AutomationEventKey.TIME)
    if (ctx.gesturePerformedId != null) keys.add(AutomationEventKey.GESTURE)
    return keys
}

/** 规则订阅的全部键（状态 + 脉冲各域）。 */
fun subscribedKeys(rule: AutomationRule): Set<String> {
    val keys = mutableSetOf<String>()
    collectKeys(rule.condition, keys)
    return keys
}

private fun collectKeys(group: WhenGroup, out: MutableSet<String>) {
    for (item in group.items) {
        if (item.type == ConditionType.GROUP) {
            item.group?.let { collectKeys(it, out) }
        } else {
            out.addAll(keysForConditionType(item.type))
        }
    }
}

private fun hasStateLeaf(group: WhenGroup): Boolean {
    for (item in group.items) {
        if (item.type == ConditionType.GROUP) {
            if (item.group?.let(::hasStateLeaf) == true) return true
        } else if (!isPulseConditionType(item.type)) {
            return true
        }
    }
    return false
}

/**
 * 订阅索引：事件键 → 规则 id。规则变更时重建，事件到达时直接查表。
 * 空键（通用刷新）只评估含状态叶子的规则，纯脉冲规则跳过——无脉冲时它们恒不命中。
 */
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
            for (key in keys) {
                keyed.getOrPut(key) { mutableSetOf() }.add(rule.id)
            }
            if (hasStateLeaf(rule.condition)) state.add(rule.id)
        }
        byKey = keyed
        withState = state
    }

    fun ruleIdsFor(eventKey: String?): Set<String>? {
        if (eventKey == null) return withState
        return byKey[eventKey] ?: emptySet()
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
