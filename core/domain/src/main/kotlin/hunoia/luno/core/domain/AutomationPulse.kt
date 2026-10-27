package hunoia.luno.core.domain

/**
 * Trigger → 自动化的事件值对象。
 *
 * 手势、链接、搜索等触发方只负责发布脉冲，不直接调用自动化引擎；
 * `:feature:automation` 订阅后同步求值、异步执行规则步骤。
 *
 * [key] 对应 `hunoia.luno.runtime.automation.AutomationEventKey` 的事件域；
 * [payload] 只放触发变量（模板可读），不放平台对象。
 */
data class AutomationPulse(
    val key: String,
    val atMs: Long,
    val payload: Map<String, String> = emptyMap(),
)
