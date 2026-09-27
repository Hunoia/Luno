package hunoia.luno.action

import hunoia.luno.action.template.SystemFunctionTemplates
import hunoia.luno.config.model.Action
import hunoia.luno.config.model.ActionLibraryEntry
import hunoia.luno.config.model.ActionLibraryPayload
import hunoia.luno.config.model.ShellCommandData
import hunoia.luno.config.model.actionLibraryRefId
import hunoia.luno.config.model.actionValue
import hunoia.luno.core.JsonSerializer

internal object ActionLibraryResolver {

    fun resolve(action: Action, entries: List<ActionLibraryEntry>): Action? =
        resolveReference(action, entries)

    fun resolveReference(
        action: Action,
        entries: List<ActionLibraryEntry>,
    ): Action? {
        val entryId = action.actionLibraryRefId() ?: return action
        val entry = entries.firstOrNull { it.id == entryId } ?: return null
        val data = when (val payload = entry.payload) {
            is ActionLibraryPayload.Shell -> JsonSerializer.encodeToString(payload.data)
            is ActionLibraryPayload.Url -> JsonSerializer.encodeToString(payload.data)
            is ActionLibraryPayload.Activity -> JsonSerializer.encodeToString(payload.data)
            is ActionLibraryPayload.SystemTemplate -> {
                val template = SystemFunctionTemplates.getById(payload.data.templateId) ?: return null
                JsonSerializer.encodeToString(
                    ShellCommandData(SystemFunctionTemplates.generateCommand(template, payload.data.params))
                )
            }
            is ActionLibraryPayload.SystemApi -> JsonSerializer.encodeToString(
                ShellCommandData(payload.data.command, payload.data.showToast)
            )
        }
        return action.copy(value = entry.type.actionValue(), data = data)
    }
}
