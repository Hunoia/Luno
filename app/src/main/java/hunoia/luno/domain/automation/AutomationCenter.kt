package hunoia.luno.domain.automation

import hunoia.luno.config.ConfigProvider
import hunoia.luno.config.model.AutomationRule
import hunoia.luno.config.model.WhenGroup
import hunoia.luno.config.model.RuleStep
import hunoia.luno.config.repository.cleanActions
import hunoia.luno.action.model.runRuleId
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

    /**
     * 删除规则：先清理各处对它的 runRule 引用（对标库条目的 removeEntryRefs），
     * 再删规则本身。按钮/子手势里的引用整条清掉；其他规则步骤里的引用变空跳
     *（多步规则不该因为一条失效就整条失能）。
     */
    suspend fun removeRule(ruleId: String) {
        removeRunRuleRefs(ruleId)
        updateRules { rules -> rules.filterNot { it.id == ruleId } }
    }

    private suspend fun removeRunRuleRefs(ruleId: String) {
        ConfigProvider.updateGestureButtons { buttons ->
            buttons.map { button -> button.cleanActions { it.runRuleId() == ruleId } }
        }
        ConfigProvider.updateSubGestureSettings { settings ->
            settings.copy(subGestures = settings.subGestures.map { subGesture ->
                subGesture.cleanActions { it.runRuleId() == ruleId }
            })
        }
        updateRules { rules ->
            rules.map { rule ->
                rule.copy(effect = rule.effect.copy(steps = rule.effect.steps.cleanRunRuleSteps(ruleId)))
            }
        }
    }

    // —— 历史页展示常量（直引 runtime 会违 B1，经此转调） ——

    val maxEventsPerRuleQuery: Int
        get() = hunoia.luno.runtime.automation.ExecutionLog.MAX_PER_RULE_QUERY

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

    /**
     * 条件组是否含状态叶（非事件、非 MANUAL）：编辑页判定"纯事件树"用
     *（纯事件树没有持续边沿，隐藏规则级进入/退出），UI 不直引 runtime。
     */
    fun hasStateConditionOf(group: WhenGroup): Boolean =
        hunoia.luno.runtime.automation.hasStateLeaf(group)

    /** 通知监听权限是否已授：编辑页通知条件行展示（读系统设置，无副作用）。 */
    fun isNotificationAccessGranted(context: android.content.Context): Boolean =
        hunoia.luno.runtime.automation.AutomationNotificationListener.isAccessGranted(context)
}

// —— 纯函数转调（顶层，ui 可直接 import；直引 runtime 会违 B1） ——

/** 递归清理步骤树里对已删规则的 runRule 引用（含 if 分支），被清步骤变空跳。 */
private fun List<RuleStep>.cleanRunRuleSteps(ruleId: String): List<RuleStep> = map { step ->
    val cleared = step.action?.runRuleId() == ruleId
    step.copy(
        action = if (cleared) null else step.action,
        thenSteps = step.thenSteps.cleanRunRuleSteps(ruleId),
        elseSteps = step.elseSteps.cleanRunRuleSteps(ruleId),
    )
}

/** 分钟数 → "HH:mm"：条件摘要展示。 */
fun formatTimeOfDay(minute: Int): String = hunoia.luno.runtime.condition.formatTimeOfDay(minute)
