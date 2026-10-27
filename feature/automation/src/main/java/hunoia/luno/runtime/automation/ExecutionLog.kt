package hunoia.luno.runtime.automation

import hunoia.luno.core.domain.ExecutionReason

/** 兼容桥：历史事件模型已下沉 core.domain，Phase 2 删除。 */
typealias SkipReason = hunoia.luno.core.domain.SkipReason
typealias StepRecord = hunoia.luno.core.domain.StepRecord
typealias AutomationEvent = hunoia.luno.core.domain.AutomationEvent

/** 执行历史：全局环形 100 条，查询侧按规则过滤取后 20 条。未命中（hit=false）不记，只记边沿后的事。 */
object ExecutionLog {
    const val MAX_EVENTS = hunoia.luno.core.domain.AutomationHistoryLimits.MAX_EVENTS
    const val MAX_PER_RULE_QUERY = hunoia.luno.core.domain.AutomationHistoryLimits.MAX_PER_RULE_QUERY
    /** 跳过事件最多占一半：另一半点位留给真实触发记录，避免吵闹规则把历史挤空。 */
    const val MAX_SKIP_EVENTS = hunoia.luno.core.domain.AutomationHistoryLimits.MAX_SKIP_EVENTS
}

/** 跳过原因枚举（code 为执行历史的持久化线值）。 */
