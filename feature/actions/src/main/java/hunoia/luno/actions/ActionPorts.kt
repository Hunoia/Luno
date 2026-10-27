package hunoia.luno.actions

import hunoia.luno.action.definitions.ActionDefinition
import hunoia.luno.action.definitions.ActionDefinitions
import hunoia.luno.action.model.labelRes
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

/**
 * 搜索动作源（ADR-0001 D2）：把动作模块的「可搜动作」投影成契约条目。
 *
 * 搜索侧只依赖 `hunoia.luno.core.domain.ActionSearchSource`，看不到
 * `ActionDefinitions` / 图标实现 / 资源 id；`:app` 组装时把本实现注入搜索。
 */
object LunoActionSearchSource : hunoia.luno.core.domain.ActionSearchSource {
    override fun index(): List<hunoia.luno.core.domain.ActionSearchEntry> =
        ActionDefinitions.pickerDefinitions().map { def ->
            hunoia.luno.core.domain.ActionSearchEntry(
                key = "action:${def.typeId}",
                group = "action",
                label = def.name,
                typeId = def.typeId,
                icon = def.icon,
                action = StoredAction(def.typeId),
            )
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

/** 设置页与动作库条目的搜索投影（ADR-0001 D2）。 */
object LunoActionSearchExtras : hunoia.luno.core.domain.ActionSearchSource {
    override fun index(): List<hunoia.luno.core.domain.ActionSearchEntry> = emptyList()

    override fun settingsPages(
        label: (resId: Int) -> String,
    ): List<hunoia.luno.core.domain.ActionSearchEntry> =
        hunoia.luno.action.model.SettingsPage.entries.map { page ->
            hunoia.luno.core.domain.ActionSearchEntry(
                key = "settings:${page.name}",
                group = "settings",
                label = label(page.labelRes),
                typeId = "system.openSettings",
                icon = null,
                action = StoredAction(
                    "system.openSettings",
                    kotlinx.serialization.json.JsonObject(
                        mapOf("page" to kotlinx.serialization.json.JsonPrimitive(page.name)),
                    ),
                ),
            )
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
