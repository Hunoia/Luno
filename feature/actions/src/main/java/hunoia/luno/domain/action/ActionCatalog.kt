package hunoia.luno.domain.action

import hunoia.luno.action.definitions.ActionDefinition
import hunoia.luno.action.definitions.ActionDefinitions
import hunoia.luno.core.domain.StoredAction
import hunoia.luno.core.domain.RUN_RULE_TYPE_ID
import hunoia.luno.core.domain.RUN_RULE_PARAM_RULE_ID
import hunoia.luno.core.domain.runRuleId
import hunoia.luno.config.model.AutomationRule

/**
 * 统一动作来源：动作库、动作选择页、搜索面板三端只从这里取数，只是展示过滤不同。
 * 定义表分 library / picker / search 三份，过滤收敛在此；库条目与规则由调用方传入。
 * `ActionDefinitions` 的原始列表保持原样（`categoryOrder` 依赖声明顺序）。
 */
object ActionCatalog {

    /** 动作库可新建的类型：再藏掉严格无参动作（选择页已有免配置入口）。 */
    fun libraryDefinitions(): List<ActionDefinition> =
        ActionDefinitions.libraryDefinitions().filter { it.parameters.isNotEmpty() }

    fun pickerDefinitions(excluded: Set<String> = ActionDefinitions.pickerExcludedTypeIds()): List<ActionDefinition> =
        ActionDefinitions.pickerDefinitions(excluded)

    /** 搜索面板动作索引：与选择页同源（picker 默认排除集）。 */
    fun searchDefinitions(): List<ActionDefinition> =
        ActionDefinitions.pickerDefinitions()

    /**
     * 选择页“规则”分组：只列启用的规则；编辑自身时剔除自身（禁自引用）。
     * 规则嵌套允许（A→B→A 由执行层深度上限兜底）。
     */
    fun rulesForPicker(rules: List<AutomationRule>, excludeRuleId: String = ""): List<AutomationRule> =
        rules.filter { it.enabled && it.id != excludeRuleId }

    fun ruleStoredAction(ruleId: String): StoredAction =
        StoredAction.of(RUN_RULE_TYPE_ID, RUN_RULE_PARAM_RULE_ID to ruleId)

    /** 非 runRule 返回 null；ruleId 为空也视为无效（转调 model 层，与库引用同语义）。 */
    fun runRuleIdOf(action: StoredAction): String? = action.runRuleId()
}
