package hunoia.luno.actions

import hunoia.luno.action.definitions.ActionDefinition
import hunoia.luno.action.definitions.ActionDefinitions
import hunoia.luno.action.model.Action
import hunoia.luno.action.model.ActionMapper
import hunoia.luno.action.model.InvalidActionParameterException
import hunoia.luno.action.model.UnknownActionTypeException
import hunoia.luno.core.domain.StoredAction
import hunoia.luno.execute.ActionCatalog
import hunoia.luno.execute.ActionFactory
import hunoia.luno.execute.ActionMappingException
import hunoia.luno.core.domain.ActionMetadata

/**
 * 执行口端口在 `:feature:actions` 的实现：动作目录与 StoredAction 映射。
 *
 * 执行的元数据视图（能力 + 模板规则）从这里投影出来；展示元件仍在本模块的
 * `ActionDefinitions` 里，执行口看不到它们。
 */

/** 动作目录端口：`ActionDefinition` 本身就是执行口需要的 [ActionMetadata]。 */
object LunoActionCatalog : ActionCatalog {
    override fun definition(typeId: String): ActionMetadata? =
        ActionDefinitions.byTypeId(typeId)
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

/**
 * 搜索动作源（architecture §9.4）：把动作模块的「可搜动作」投影成契约条目。
 *
 * 搜索侧只依赖 `hunoia.luno.core.domain.ActionSearchSource`，看不到
 * `ActionDefinitions` / 图标实现 / 资源 id；`:app` 组装时把本实现注入搜索。
 */
object LunoActionSearchSource : hunoia.luno.core.domain.ActionSearchSource {
    override fun index(): List<hunoia.luno.core.domain.ActionSearchEntry> =
        ActionDefinitions.pickerDefinitions().flatMap { def -> def.searchEntries() }

    /**
     * 合并式动作（[ActionDefinition.variants] 非空）按变体展开成独立条目：一个「快捷开关」
     * 在搜索里就是 10 行独立开关，和合并前的体验一致。普通动作仍是一行。
     * 变体 label 直接作为条目 label（搜索按 label 召回），不带动作名前缀。
     */
    private fun ActionDefinition.searchEntries(): List<hunoia.luno.core.domain.ActionSearchEntry> {
        if (variants.isEmpty()) {
            return listOf(
                hunoia.luno.core.domain.ActionSearchEntry(
                    key = "action:$typeId",
                    group = "action",
                    label = name,
                    typeId = typeId,
                    icon = icon,
                    action = StoredAction(typeId),
                ),
            )
        }
        return variants.map { variant ->
            hunoia.luno.core.domain.ActionSearchEntry(
                key = "action:$typeId#${variant.key}",
                group = "action",
                label = variant.label,
                typeId = typeId,
                icon = variant.icon ?: icon,
                action = StoredAction.of(
                    typeId,
                    *variant.params.map { (key, value) -> key to value }.toTypedArray(),
                ),
            )
        }
    }

    override fun definitionName(typeId: String): String? =
        ActionDefinitions.byTypeId(typeId)?.name

    override fun resolveLibraryEntry(
        entry: hunoia.luno.core.domain.ActionLibraryEntry,
    ): hunoia.luno.core.domain.ActionSearchEntry = hunoia.luno.core.domain.ActionSearchEntry(
        key = "library:${entry.id}",
        group = "action",
        label = entry.name.ifBlank {
            ActionDefinitions.byTypeId(entry.typeId)?.name ?: entry.typeId
        },
        typeId = entry.typeId,
        icon = entry.resolvedIcon(),
        action = StoredAction.of("internal.libraryRef", "entryId" to entry.id),
    )
}
