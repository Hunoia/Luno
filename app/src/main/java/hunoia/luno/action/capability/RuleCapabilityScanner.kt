package hunoia.luno.action.capability

import android.content.Context
import hunoia.luno.action.definitions.ActionDefinitions
import hunoia.luno.core.domain.ActionLibrarySettings
import hunoia.luno.core.domain.Capability
import hunoia.luno.core.domain.RUN_RULE_TYPE_ID
import hunoia.luno.core.domain.StoredAction
import hunoia.luno.config.model.AutomationRule
import hunoia.luno.config.model.ConditionType
import hunoia.luno.config.model.RuleStep
import hunoia.luno.config.model.RuleStepKind
import hunoia.luno.config.model.WhenGroup
import hunoia.luno.runtime.automation.AutomationNotificationListener
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/** P3-3 静态权限报告：动作能力 + 通知监听访问。 */
data class RuleCapabilityReport(
    val missingCapabilities: Set<Capability> = emptySet(),
    val notificationAccessMissing: Boolean = false,
) {
    val hasMissing: Boolean get() = missingCapabilities.isNotEmpty() || notificationAccessMissing
}

/**
 * 保存时/规则卡片/触发前共用的能力扫描。
 *
 * 只做静态可达性分析：步骤启用状态、动作库引用、runRule 目标都会展开；
 * 不执行动作，也不因为一次执行成功就假定权限永远存在。
 */
object RuleCapabilityScanner {

    fun report(
        context: Context,
        rule: AutomationRule,
        allRules: List<AutomationRule>,
        library: ActionLibrarySettings?,
    ): RuleCapabilityReport {
        val gate = CapabilityGate(context.applicationContext)
        val required = requiredCapabilities(rule, allRules, library)
        return RuleCapabilityReport(
            missingCapabilities = required.filterNot(gate::isAvailable).toSet(),
            notificationAccessMissing = notificationAccessRequired(rule, allRules, library) &&
                !AutomationNotificationListener.isAccessGranted(context),
        )
    }

    /**
     * 规则（含约束、IF 分支、动作库引用和 runRule 目标）是否读取通知监听数据。
     * 触发条件与约束都算：约束缺失时规则同样永远不会进入仲裁。
     */
    fun notificationAccessRequired(
        rule: AutomationRule,
        allRules: List<AutomationRule>,
        library: ActionLibrarySettings?,
    ): Boolean {
        val visitedRules = mutableSetOf<String>()
        val visitedLibraryRefs = mutableSetOf<String>()
        return notificationAccessInRule(rule, allRules, library, visitedRules, visitedLibraryRefs)
    }

    private fun notificationAccessInRule(
        rule: AutomationRule,
        allRules: List<AutomationRule>,
        library: ActionLibrarySettings?,
        visitedRules: MutableSet<String>,
        visitedLibraryRefs: MutableSet<String>,
    ): Boolean {
        if (!visitedRules.add(rule.id)) return false
        return notificationAccessInGroup(rule.condition) ||
            notificationAccessInGroup(rule.constraints) ||
            notificationAccessInSteps(rule.effect.steps, allRules, library, visitedRules, visitedLibraryRefs)
    }

    private fun notificationAccessInSteps(
        steps: List<RuleStep>,
        allRules: List<AutomationRule>,
        library: ActionLibrarySettings?,
        visitedRules: MutableSet<String>,
        visitedLibraryRefs: MutableSet<String>,
    ): Boolean = steps.any { step ->
        step.enabled && (
            notificationAccessInGroup(step.condition) ||
                notificationAccessInSteps(step.thenSteps, allRules, library, visitedRules, visitedLibraryRefs) ||
                notificationAccessInSteps(step.elseSteps, allRules, library, visitedRules, visitedLibraryRefs) ||
                (step.kind == RuleStepKind.ACTION && step.action?.let { action ->
                    notificationAccessInAction(action, allRules, library, visitedRules, visitedLibraryRefs)
                } == true)
            )
    }

    private fun notificationAccessInAction(
        stored: StoredAction,
        allRules: List<AutomationRule>,
        library: ActionLibrarySettings?,
        visitedRules: MutableSet<String>,
        visitedLibraryRefs: MutableSet<String>,
    ): Boolean = when (stored.typeId) {
        "internal.libraryRef" -> {
            val entryId = (stored.params["entryId"] as? JsonPrimitive)?.contentOrNull.orEmpty()
            if (entryId.isEmpty() || !visitedLibraryRefs.add(entryId)) {
                false
            } else {
                val entry = library?.entries?.firstOrNull { it.id == entryId }
                entry != null && notificationAccessInAction(
                    entry.storedAction,
                    allRules,
                    library,
                    visitedRules,
                    visitedLibraryRefs,
                )
            }
        }
        RUN_RULE_TYPE_ID -> {
            val targetId = (stored.params["ruleId"] as? JsonPrimitive)?.contentOrNull.orEmpty()
            val target = allRules.firstOrNull { it.id == targetId }
            target != null && notificationAccessInRule(
                target,
                allRules,
                library,
                visitedRules,
                visitedLibraryRefs,
            )
        }
        else -> false
    }

    fun requiredCapabilities(
        rule: AutomationRule,
        allRules: List<AutomationRule>,
        library: ActionLibrarySettings?,
    ): Set<Capability> {
        val result = mutableSetOf<Capability>()
        val visited = mutableSetOf<String>()
        val visitedLibraryRefs = mutableSetOf<String>()
        collectRule(rule, allRules, library, result, visited, visitedLibraryRefs)
        return result
    }

    private fun collectRule(
        rule: AutomationRule,
        allRules: List<AutomationRule>,
        library: ActionLibrarySettings?,
        out: MutableSet<Capability>,
        visited: MutableSet<String>,
        visitedLibraryRefs: MutableSet<String>,
    ) {
        if (!visited.add(rule.id)) return
        collectSteps(rule.effect.steps, allRules, library, out, visited, visitedLibraryRefs)
    }

    private fun collectSteps(
        steps: List<RuleStep>,
        allRules: List<AutomationRule>,
        library: ActionLibrarySettings?,
        out: MutableSet<Capability>,
        visited: MutableSet<String>,
        visitedLibraryRefs: MutableSet<String>,
    ) {
        steps.forEach { step ->
            if (!step.enabled) return@forEach
            collectSteps(step.thenSteps, allRules, library, out, visited, visitedLibraryRefs)
            collectSteps(step.elseSteps, allRules, library, out, visited, visitedLibraryRefs)
            if (step.kind != RuleStepKind.ACTION) return@forEach
            val action = step.action ?: return@forEach
            collectStoredAction(action, allRules, library, out, visited, visitedLibraryRefs)
        }
    }

    private fun collectStoredAction(
        stored: StoredAction,
        allRules: List<AutomationRule>,
        library: ActionLibrarySettings?,
        out: MutableSet<Capability>,
        visited: MutableSet<String>,
        visitedLibraryRefs: MutableSet<String>,
    ) {
        when (stored.typeId) {
            "internal.libraryRef" -> {
                val entryId = (stored.params["entryId"] as? JsonPrimitive)?.contentOrNull.orEmpty()
                if (entryId.isEmpty() || !visitedLibraryRefs.add(entryId)) return
                val entry = library?.entries?.firstOrNull { it.id == entryId } ?: return
                collectStoredAction(entry.storedAction, allRules, library, out, visited, visitedLibraryRefs)
            }
            RUN_RULE_TYPE_ID -> {
                val targetId = (stored.params["ruleId"] as? JsonPrimitive)?.contentOrNull.orEmpty()
                val target = allRules.firstOrNull { it.id == targetId } ?: return
                collectRule(target, allRules, library, out, visited, visitedLibraryRefs)
            }
            else -> {
                val capability = ActionDefinitions.byTypeId(stored.typeId)?.capability
                    ?: Capability.None
                if (capability != Capability.None) out += capability
            }
        }
    }

    private fun notificationAccessInGroup(group: WhenGroup): Boolean =
        group.items.any { item ->
            item.type == ConditionType.NOTIFICATION_POSTED ||
                item.type == ConditionType.NOTIFICATION_REMOVED ||
                item.type == ConditionType.NOTIFICATION_EXISTS ||
                (item.type == ConditionType.GROUP && item.group?.let(::notificationAccessInGroup) == true)
        }
}
