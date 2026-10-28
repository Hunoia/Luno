package hunoia.luno.runtime.automation

import hunoia.luno.config.model.ThrottleKind
import hunoia.luno.config.model.ThrottlePolicy

/** 单条规则的节流运行态：冷却最近触发时间与窗口命中队列。 */
class ThrottleRuntimeState {
    var lastFiredAtMs: Long? = null
    val windowHits = ArrayDeque<Long>()
}

/**
 * 节流策略扩展点：`kind → 语义`。新增策略只需注册一个 handler，不改引擎管线。
 *
 * handler 只读写 [ThrottleRuntimeState]，不碰仲裁、历史或执行。
 */
interface ThrottlePolicyHandler {

    /** 返回命中原因；null = 放行。 */
    fun blockReason(policy: ThrottlePolicy, state: ThrottleRuntimeState, nowMs: Long): SkipReason?

    /** 执行成功后记账。 */
    fun markFired(policy: ThrottlePolicy, state: ThrottleRuntimeState, nowMs: Long)
}

/** 节流策略注册表：kind 唯一，未知 kind 退回值驱动兼容实现。 */
object ThrottlePolicyRegistry {

    private val handlers = LinkedHashMap<String, ThrottlePolicyHandler>()

    init {
        register(ThrottleKind.OFF, OffPolicy)
        register(ThrottleKind.COOLDOWN, CooldownPolicy)
        register(ThrottleKind.WINDOW, WindowPolicy)
        register(ThrottleKind.BOTH, BothPolicy)
    }

    fun register(kind: String, handler: ThrottlePolicyHandler) {
        require(kind.isNotBlank()) { "throttle kind must not be blank" }
        require(handlers.put(kind, handler) == null) { "duplicate throttle kind: $kind" }
    }

    fun handler(kind: String): ThrottlePolicyHandler = handlers[kind] ?: LegacyValueDrivenPolicy

    private object OffPolicy : ThrottlePolicyHandler {
        override fun blockReason(policy: ThrottlePolicy, state: ThrottleRuntimeState, nowMs: Long) = null
        override fun markFired(policy: ThrottlePolicy, state: ThrottleRuntimeState, nowMs: Long) = Unit
    }

    private object CooldownPolicy : ThrottlePolicyHandler {
        override fun blockReason(policy: ThrottlePolicy, state: ThrottleRuntimeState, nowMs: Long): SkipReason? {
            if (policy.cooldownMs <= 0) return null
            val last = state.lastFiredAtMs ?: return null
            return SkipReason.THROTTLED_COOLDOWN.takeIf { nowMs - last < policy.cooldownMs }
        }

        override fun markFired(policy: ThrottlePolicy, state: ThrottleRuntimeState, nowMs: Long) {
            if (policy.cooldownMs > 0) state.lastFiredAtMs = nowMs
        }
    }

    private object WindowPolicy : ThrottlePolicyHandler {
        override fun blockReason(policy: ThrottlePolicy, state: ThrottleRuntimeState, nowMs: Long): SkipReason? {
            val windowMs = policy.windowMs
            val maxCount = policy.maxCount
            if (maxCount < 1 || windowMs < 1000L) return null
            trimWindow(state, windowMs, nowMs)
            return SkipReason.THROTTLED_WINDOW.takeIf { state.windowHits.size >= maxCount }
        }

        override fun markFired(policy: ThrottlePolicy, state: ThrottleRuntimeState, nowMs: Long) {
            if (policy.maxCount >= 1 && policy.windowMs >= 1000L) state.windowHits.addLast(nowMs)
        }
    }

    private object BothPolicy : ThrottlePolicyHandler {
        override fun blockReason(policy: ThrottlePolicy, state: ThrottleRuntimeState, nowMs: Long): SkipReason? =
            CooldownPolicy.blockReason(policy, state, nowMs)
                ?: WindowPolicy.blockReason(policy, state, nowMs)

        override fun markFired(policy: ThrottlePolicy, state: ThrottleRuntimeState, nowMs: Long) {
            CooldownPolicy.markFired(policy, state, nowMs)
            WindowPolicy.markFired(policy, state, nowMs)
        }
    }

    /**
     * 兼容旧数据：kind 非 OFF 时按字段值驱动（冷却 + 窗口都可能生效）。
     * 与重构前的引擎语义一致，避免手改配置的边角数据改变行为。
     */
    private object LegacyValueDrivenPolicy : ThrottlePolicyHandler {
        override fun blockReason(policy: ThrottlePolicy, state: ThrottleRuntimeState, nowMs: Long): SkipReason? {
            if (policy.kind == ThrottleKind.OFF) return null
            if (policy.cooldownMs > 0) {
                val last = state.lastFiredAtMs
                if (last != null && nowMs - last < policy.cooldownMs) {
                    return SkipReason.THROTTLED_COOLDOWN
                }
            }
            if (policy.maxCount >= 1 && policy.windowMs >= 1000L) {
                trimWindow(state, policy.windowMs, nowMs)
                if (state.windowHits.size >= policy.maxCount) return SkipReason.THROTTLED_WINDOW
            }
            return null
        }

        override fun markFired(policy: ThrottlePolicy, state: ThrottleRuntimeState, nowMs: Long) {
            if (policy.kind == ThrottleKind.OFF) return
            if (policy.cooldownMs > 0) state.lastFiredAtMs = nowMs
            if (policy.maxCount >= 1 && policy.windowMs >= 1000L) state.windowHits.addLast(nowMs)
        }
    }

    private fun trimWindow(state: ThrottleRuntimeState, windowMs: Long, nowMs: Long) {
        while (state.windowHits.isNotEmpty() && nowMs - state.windowHits.first() >= windowMs) {
            state.windowHits.removeFirst()
        }
    }
}
