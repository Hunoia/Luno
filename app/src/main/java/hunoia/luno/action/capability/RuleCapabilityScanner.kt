package hunoia.luno.action.capability

import android.content.Context
import hunoia.luno.action.definitions.ActionDefinitions
import hunoia.luno.action.model.ActionLibrarySettings
import hunoia.luno.action.model.Capability
import hunoia.luno.action.model.RUN_RULE_TYPE_ID
import hunoia.luno.action.model.StoredAction
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
            notificationAccessMissing = needsNotificationAccess(rule) &&
                !AutomationNotificationListener.isAccessGranted(context),
        )
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

    private fun needsNotificationAccess(rule: AutomationRule): Boolean =
        needsNotificationAccess(rule.condition) || needsNotificationAccess(rule.effect.steps)

    private fun needsNotificationAccess(steps: List<RuleStep>): Boolean = steps.any { step ->
        step.enabled && (
            needsNotificationAccess(step.condition) ||
                needsNotificationAccess(step.thenSteps) ||
                needsNotificationAccess(step.elseSteps)
            )
    }

    private fun needsNotificationAccess(group: WhenGroup): Boolean =
        group.items.any { item ->
            item.type == ConditionType.NOTIFICATION_POSTED ||
                (item.type == ConditionType.GROUP && item.group?.let(::needsNotificationAccess) == true)
        }
}
