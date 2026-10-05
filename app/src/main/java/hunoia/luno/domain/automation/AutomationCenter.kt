package hunoia.luno.domain.automation

import hunoia.luno.config.ConfigProvider
import hunoia.luno.config.model.AutomationRule
import hunoia.luno.config.model.WhenGroup
import hunoia.luno.runtime.condition.hasLeaf as runtimeHasLeaf
import kotlinx.coroutines.flow.Flow

/** 执行历史事件：展示用，经 domain 转调，ui 不得直引 runtime。 */
typealias AutomationEvent = hunoia.luno.runtime.automation.AutomationEvent

/** 自动化域门面（ui 层唯一入口）。 */
object AutomationCenter {

    val rules: Flow<List<AutomationRule>> = ConfigProvider.automationRules
    val log: Flow<List<AutomationEvent>> = ConfigProvider.automationLog

    suspend fun getRules(): List<AutomationRule> = ConfigProvider.getAutomationRules()

    suspend fun updateRules(transform: suspend (List<AutomationRule>) -> List<AutomationRule>) =
        ConfigProvider.updateAutomationRules(transform)

    suspend fun appendEvent(event: AutomationEvent) = ConfigProvider.appendAutomationEvent(event)

    // —— 历史页展示常量（直引 runtime 会违 B1，经此转调） ——

    val maxEventsPerRuleQuery: Int
        get() = hunoia.luno.runtime.automation.ExecutionLog.MAX_PER_RULE_QUERY

    fun skipReasonCode(reason: hunoia.luno.runtime.automation.SkipReason): String = reason.code

    val skipNoSteps: hunoia.luno.runtime.automation.SkipReason
        get() = hunoia.luno.runtime.automation.SkipReason.NO_STEPS
    val skipThrottledCooldown: hunoia.luno.runtime.automation.SkipReason
        get() = hunoia.luno.runtime.automation.SkipReason.THROTTLED_COOLDOWN
    val skipThrottledWindow: hunoia.luno.runtime.automation.SkipReason
        get() = hunoia.luno.runtime.automation.SkipReason.THROTTLED_WINDOW
    val skipOutprioritized: hunoia.luno.runtime.automation.SkipReason
        get() = hunoia.luno.runtime.automation.SkipReason.OUTPRIORITIZED
    val skipRuleBusy: hunoia.luno.runtime.automation.SkipReason
        get() = hunoia.luno.runtime.automation.SkipReason.RULE_BUSY

    fun isOkReason(reason: String): Boolean =
        reason == hunoia.luno.runtime.automation.ExecutionReason.OK

    fun isInterruptedReason(reason: String): Boolean =
        reason.startsWith(hunoia.luno.runtime.automation.ExecutionReason.INTERRUPTED_PREFIX)

    fun stripInterruptedPrefix(reason: String): String =
        reason.removePrefix(hunoia.luno.runtime.automation.ExecutionReason.INTERRUPTED_PREFIX)

    /** 条件组是否有叶子：编辑页保存按钮可用性（转调 runtime 纯函数）。 */
    fun hasLeafOf(group: WhenGroup): Boolean = with(group) { runtimeHasLeaf() }

    /** 通知监听权限是否已授：编辑页通知条件行展示（读系统设置，无副作用）。 */
    fun isNotificationAccessGranted(context: android.content.Context): Boolean =
        hunoia.luno.runtime.automation.AutomationNotificationListener.isAccessGranted(context)
}

// —— 纯函数转调（顶层，ui 可直接 import；直引 runtime 会违 B1） ——

/** 分钟数 → "HH:mm"：条件摘要展示。 */
fun formatTimeOfDay(minute: Int): String = hunoia.luno.runtime.condition.formatTimeOfDay(minute)
