package hunoia.luno.runtime.automation

import hunoia.luno.config.model.AutomationRule
import hunoia.luno.config.model.ConditionType
import hunoia.luno.config.model.WhenGroup
import hunoia.luno.runtime.condition.ConditionContext

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
fun keysForConditionType(type: String): Set<String> = when (type) {
    ConditionType.FOREGROUND_APP,
    ConditionType.APP_CHANGED_TO -> setOf(AutomationEventKey.APP)

    /**
     * 屏幕状态（桌面/锁屏/横竖屏/键盘）：桌面切换伴随前台应用变化走 APP 键，锁屏伴随屏幕事件走
     * SCREEN 键，键盘弹起同时带窗口变化由 APP 键覆盖。落 else 空集会让纯桌面规则只能靠通用刷新，
     * 而通用刷新常被窗口变化的 APP 评估取消，导致永不触发；KEYBOARD 并入使键盘翻转时状态型规则也被评估。
     */
    ConditionType.SCREEN_STATE -> setOf(AutomationEventKey.APP, AutomationEventKey.SCREEN, AutomationEventKey.KEYBOARD)

    ConditionType.SCREEN_EVENT -> setOf(AutomationEventKey.SCREEN)

    ConditionType.KEYBOARD_EVENT -> setOf(AutomationEventKey.KEYBOARD)

    ConditionType.MUSIC_PLAYING,
    ConditionType.MUSIC_CHANGED -> setOf(AutomationEventKey.MUSIC)

    ConditionType.TORCH_ON,
    ConditionType.TORCH_CHANGED -> setOf(AutomationEventKey.TORCH)

    ConditionType.DND_ON,
    ConditionType.DND_CHANGED -> setOf(AutomationEventKey.DND)

    ConditionType.BATTERY_RANGE,
    ConditionType.CHARGING,
    ConditionType.CHARGING_CHANGED -> setOf(AutomationEventKey.CHARGING)

    ConditionType.TIME_RANGE,
    ConditionType.SCHEDULED_TIME -> setOf(AutomationEventKey.TIME)

    ConditionType.VOLUME_RANGE,
    ConditionType.RINGER_SILENT,
    ConditionType.VOLUME_CHANGED -> setOf(AutomationEventKey.VOLUME)

    ConditionType.HEADPHONES,
    ConditionType.HEADPHONES_CHANGED -> setOf(AutomationEventKey.HEADPHONES)

    // NETWORK_TYPE / AIRPLANE_MODE 无专属事件键：来源只在状态回调走通用刷新
    //（空键 → withState 已覆盖），原先挂 APP 键是多余的全量评估。

    ConditionType.BLUETOOTH,
    ConditionType.BLUETOOTH_CHANGED -> setOf(AutomationEventKey.BLUETOOTH)

    ConditionType.NOTIFICATION_POSTED,
    ConditionType.NOTIFICATION_REMOVED,
    ConditionType.NOTIFICATION_EXISTS -> setOf(AutomationEventKey.NOTIFICATION)

    ConditionType.APP_PACKAGE_CHANGED -> setOf(AutomationEventKey.PACKAGE)
    ConditionType.INTERVAL -> setOf(AutomationEventKey.INTERVAL)
    ConditionType.SHARE_RECEIVED -> setOf(AutomationEventKey.SHARE)
    ConditionType.CLIPBOARD_CHANGED -> setOf(AutomationEventKey.CLIPBOARD)

    ConditionType.GESTURE_PERFORMED -> setOf(AutomationEventKey.GESTURE)

    ConditionType.VAR_COMPARE -> setOf(AutomationEventKey.VARIABLE)

    else -> emptySet()
}

/** 叶子是否为脉冲条件（一次性消费）；其余为状态条件（参与边沿快照）。 */
fun isPulseConditionType(type: String): Boolean = when (type) {
    ConditionType.APP_CHANGED_TO,
    ConditionType.SCREEN_EVENT,
    ConditionType.CHARGING_CHANGED,
    ConditionType.VOLUME_CHANGED,
    ConditionType.NOTIFICATION_POSTED,
    ConditionType.NOTIFICATION_REMOVED,
    ConditionType.APP_PACKAGE_CHANGED,
    ConditionType.INTERVAL,
    ConditionType.SHARE_RECEIVED,
    ConditionType.CLIPBOARD_CHANGED,
    ConditionType.BLUETOOTH_CHANGED,
    ConditionType.HEADPHONES_CHANGED,
    ConditionType.SCHEDULED_TIME,
    ConditionType.GESTURE_PERFORMED,
    ConditionType.KEYBOARD_EVENT,
    ConditionType.MUSIC_CHANGED,
    ConditionType.TORCH_CHANGED,
    ConditionType.DND_CHANGED -> true
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
    if (ctx.notificationRemoved != null) keys.add(AutomationEventKey.NOTIFICATION)
    if (ctx.packageChanged != null) keys.add(AutomationEventKey.PACKAGE)
    if (ctx.intervalDueKeys.isNotEmpty()) keys.add(AutomationEventKey.INTERVAL)
    if (ctx.shareReceived != null) keys.add(AutomationEventKey.SHARE)
    if (ctx.clipboardChanged != null) keys.add(AutomationEventKey.CLIPBOARD)
    if (ctx.bluetoothChanged != null) keys.add(AutomationEventKey.BLUETOOTH)
    if (ctx.headphonesChanged != null) keys.add(AutomationEventKey.HEADPHONES)
    if (ctx.scheduledTimeHit) keys.add(AutomationEventKey.TIME)
    if (ctx.gesturePerformedId != null) keys.add(AutomationEventKey.GESTURE)
    if (ctx.keyboardEvent != null) keys.add(AutomationEventKey.KEYBOARD)
    if (ctx.musicChanged != null) keys.add(AutomationEventKey.MUSIC)
    if (ctx.torchChangedTo != null) keys.add(AutomationEventKey.TORCH)
    if (ctx.dndChangedTo != null) keys.add(AutomationEventKey.DND)
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

/**
 * 否定脉冲（如「没有收到通知」）被当作非状态叶：只在对应脉冲事件到达时评估，
 * 语义是「本次事件不是这个脉冲」；纯否定脉冲规则不进通用刷新，否则恒真会疯狂触发。
 */
fun hasStateLeaf(group: WhenGroup): Boolean {
    for (item in group.items) {
        if (item.type == ConditionType.GROUP) {
            if (item.group?.let(::hasStateLeaf) == true) return true
        } else if (item.type != ConditionType.MANUAL && !isPulseConditionType(item.type)) {
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
