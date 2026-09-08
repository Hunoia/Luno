package hunoia.luno.action

import hunoia.luno.action.api.ActionFacade
import hunoia.luno.config.ConfigProvider
import hunoia.luno.config.model.Action
import hunoia.luno.config.model.ActionLibraryEntry
import hunoia.luno.config.model.ActionLibraryRefData
import hunoia.luno.config.model.ActionLibraryType
import hunoia.luno.core.JsonSerializer

internal object ActionLibraryResolver {

    suspend fun resolve(action: Action): Action? =
        resolveReference(action, ConfigProvider.getActionLibrarySettings().entries)

    fun parseReference(action: Action): ActionLibraryRefData? {
        if (action.value != ActionFacade.EXECUTE_SHELL_COMMAND &&
            action.value != ActionFacade.OPEN_URL &&
            action.value != ActionFacade.OPEN_APP_ACTIVITY
        ) {
            return null
        }
        return runCatching {
            JsonSerializer.decodeFromString<ActionLibraryRefData>(action.data)
        }.getOrNull()
    }

    fun resolveReference(
        action: Action,
        entries: List<ActionLibraryEntry>,
    ): Action? {
        val ref = parseReference(action) ?: return action
        if (ref.entryId.isBlank()) return action
        val entry = entries.firstOrNull { it.id == ref.entryId } ?: return null
        val value = when (entry.type) {
            ActionLibraryType.Shell -> ActionFacade.EXECUTE_SHELL_COMMAND
            ActionLibraryType.Url -> ActionFacade.OPEN_URL
            ActionLibraryType.Activity -> ActionFacade.OPEN_APP_ACTIVITY
        }
        val data = when (entry.type) {
            ActionLibraryType.Shell -> JsonSerializer.encodeToString(entry.shellCommand)
            ActionLibraryType.Url,
            ActionLibraryType.Activity -> JsonSerializer.encodeToString(entry.openAppOrUrl)
        }
        return action.copy(value = value, data = data)
    }
}
