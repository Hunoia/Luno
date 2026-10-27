package hunoia.luno.actions

import hunoia.luno.action.definitions.ActionDefinition
import hunoia.luno.action.definitions.ActionDefinitions
import hunoia.luno.action.definitions.ParameterDefinition
import hunoia.luno.action.definitions.excludedTemplateKeys
import hunoia.luno.action.model.Action
import hunoia.luno.action.model.ActionMapper
import hunoia.luno.action.model.InvalidActionParameterException
import hunoia.luno.action.model.UnknownActionTypeException
import hunoia.luno.core.domain.Capability
import hunoia.luno.core.domain.StoredAction
import hunoia.luno.execute.ActionCatalog
import hunoia.luno.execute.ActionFactory
import hunoia.luno.execute.ActionMappingException
import hunoia.luno.execute.ActionMetadata

/**
 * 执行口端口在 `:feature:actions` 的实现：动作目录与 StoredAction 映射。
 *
 * 执行的元数据视图（能力 + 模板规则）从这里投影出来；展示元件仍在本模块的
 * `ActionDefinitions` 里，执行口看不到它们。
 */

/** 动作目录端口：`ActionDefinitions` → 执行口需要的最小元数据。 */
object LunoActionCatalog : ActionCatalog {
    private val cache = HashMap<String, ActionMetadata>()

    override fun definition(typeId: String): ActionMetadata? {
        cache[typeId]?.let { return it }
        val metadata = ActionDefinitions.byTypeId(typeId)?.toExecuteMetadata() ?: return null
        cache[typeId] = metadata
        return metadata
    }

    private fun ActionDefinition.toExecuteMetadata(): ActionMetadata = object : ActionMetadata {
        override val capability: Capability = this@toExecuteMetadata.capability
        override val excludedTemplateKeys: Set<String> = excludedTemplateKeys()
        override val requiredTextKeys: Set<String> = parameters.mapNotNull { param ->
            val isText = param is ParameterDefinition.Text || param is ParameterDefinition.TextLarge
            if (isText && param.required) param.key else null
        }.toSet()
    }
}

/** 映射端口：受控异常翻译成执行口的 [ActionMappingException]，其余异常照旧上抛由分发口兜底。 */
object LunoActionFactory : ActionFactory {
    override fun fromStored(stored: StoredAction): Action = try {
        ActionMapper.fromStored(stored)
    } catch (e: UnknownActionTypeException) {
        throw ActionMappingException(unsupported = true, message = e.typeId)
    } catch (e: InvalidActionParameterException) {
        throw ActionMappingException(unsupported = false, message = "${e.key}=${e.raw}")
    }
}
