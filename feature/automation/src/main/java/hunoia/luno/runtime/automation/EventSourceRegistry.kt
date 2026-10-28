package hunoia.luno.runtime.automation

import hunoia.luno.runtime.condition.ConditionContext

/**
 * 事件源扩展点：声明「本次快照里实际携带了哪些脉冲键」。
 *
 * 自动化总线的订阅键来自 Condition，脉冲键来自各事件源；新增事件域时只注册一个
 * [EventSourceRegistry] 条目，不再修改总线里的中心 `if` 链。
 */
fun interface EventSource {
    fun pulseKeysIn(context: ConditionContext): Set<String>
}

/** 事件源注册表：id 唯一；空集合表示本次没有该源脉冲。 */
object EventSourceRegistry {

    private val sources = LinkedHashMap<String, EventSource>()

    init {
        register("app") { ctx -> keysOrEmpty(ctx.appChangedTo != null, AutomationEventKey.APP) }
        register("screen") { ctx -> keysOrEmpty(ctx.screenEvent != null, AutomationEventKey.SCREEN) }
        register("charging") { ctx -> keysOrEmpty(ctx.chargingChangedTo != null, AutomationEventKey.CHARGING) }
        register("volume") { ctx -> keysOrEmpty(ctx.volumeChanged != null, AutomationEventKey.VOLUME) }
        register("notification") { ctx ->
            keysOrEmpty(
                ctx.notificationPosted != null || ctx.notificationRemoved != null,
                AutomationEventKey.NOTIFICATION,
            )
        }
        register("package") { ctx -> keysOrEmpty(ctx.packageChanged != null, AutomationEventKey.PACKAGE) }
        register("interval") { ctx ->
            keysOrEmpty(ctx.intervalDueKeys.isNotEmpty(), AutomationEventKey.INTERVAL)
        }
        register("share") { ctx -> keysOrEmpty(ctx.shareReceived != null, AutomationEventKey.SHARE) }
        register("clipboard") { ctx -> keysOrEmpty(ctx.clipboardChanged != null, AutomationEventKey.CLIPBOARD) }
        register("bluetooth") { ctx -> keysOrEmpty(ctx.bluetoothChanged != null, AutomationEventKey.BLUETOOTH) }
        register("headphones") { ctx -> keysOrEmpty(ctx.headphonesChanged != null, AutomationEventKey.HEADPHONES) }
        register("time") { ctx -> keysOrEmpty(ctx.scheduledTimeHit, AutomationEventKey.TIME) }
        register("gesture") { ctx -> keysOrEmpty(ctx.gesturePerformedId != null, AutomationEventKey.GESTURE) }
        register("keyboard") { ctx -> keysOrEmpty(ctx.keyboardEvent != null, AutomationEventKey.KEYBOARD) }
        register("music") { ctx -> keysOrEmpty(ctx.musicChanged != null, AutomationEventKey.MUSIC) }
        register("torch") { ctx -> keysOrEmpty(ctx.torchChangedTo != null, AutomationEventKey.TORCH) }
        register("dnd") { ctx -> keysOrEmpty(ctx.dndChangedTo != null, AutomationEventKey.DND) }
    }

    fun register(id: String, source: EventSource) {
        require(id.isNotBlank()) { "event source id must not be blank" }
        require(!sources.containsKey(id)) { "duplicate event source: $id" }
        sources[id] = source
    }

    /** 单个事件源的脉冲键（诊断 / 测试用）。 */
    fun pulseKeysFrom(id: String, context: ConditionContext): Set<String> =
        sources[id]?.pulseKeysIn(context).orEmpty()

    /** 汇总所有事件源本次携带的脉冲键。 */
    fun pulseKeysIn(context: ConditionContext): Set<String> {
        val keys = mutableSetOf<String>()
        for (source in sources.values) keys.addAll(source.pulseKeysIn(context))
        return keys
    }

    val ids: Set<String> get() = sources.keys.toSet()

    private fun keysOrEmpty(condition: Boolean, key: String): Set<String> =
        if (condition) setOf(key) else emptySet()
}
