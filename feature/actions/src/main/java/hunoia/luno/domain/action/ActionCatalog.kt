package hunoia.luno.domain.action

import hunoia.luno.action.definitions.ActionDefinition
import hunoia.luno.action.definitions.ActionDefinitions

/**
 * 统一动作来源：动作库、动作选择页、搜索面板三端只从这里取数，只是展示过滤不同。
 * 定义表分 library / picker / search 三份，过滤收敛在此；库条目由调用方传入。
 * `ActionDefinitions` 的原始列表保持原样（`categoryOrder` 依赖声明顺序）。
 */
object ActionCatalog {

    /** 动作库可新建的类型：再藏掉严格无参动作（选择页已有免配置入口）。 */
    fun libraryDefinitions(): List<ActionDefinition> =
        ActionDefinitions.libraryDefinitions().filter { it.parameters.isNotEmpty() }

    fun pickerDefinitions(excluded: Set<String> = ActionDefinitions.pickerExcludedTypeIds()): List<ActionDefinition> =
        ActionDefinitions.pickerDefinitions(excluded)
}
