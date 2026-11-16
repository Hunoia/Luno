package hunoia.luno.domain.action

import hunoia.luno.core.domain.ActionLibrarySettings
import hunoia.luno.config.ConfigProvider
import hunoia.luno.config.repository.LibraryCleaner
import kotlinx.coroutines.flow.Flow

/** 动作库域门面（ui 层唯一入口）。 */
object ActionLibrary {

    val settings: Flow<ActionLibrarySettings> = ConfigProvider.actionLibrarySettings

    suspend fun getSettings(): ActionLibrarySettings = ConfigProvider.getActionLibrarySettings()

    suspend fun updateSettings(transform: suspend (ActionLibrarySettings) -> ActionLibrarySettings) =
        ConfigProvider.updateActionLibrarySettings(transform)

    /**
     * 删除库条目：按钮/子手势里指向该条目的动作清掉，最后删条目本身。
     */
    suspend fun removeEntry(entryId: String) {
        val entry = ConfigProvider.getActionLibrarySettings().entries.firstOrNull { it.id == entryId }
        removeEntryRefs(entryId)
        updateSettings { settings ->
            settings.copy(entries = settings.entries.filterNot { it.id == entryId })
        }
        entry?.let { hunoia.luno.action.model.ShortcutIconFiles.deleteFor(listOf(it.storedAction)) }
    }

    /**
     * 删除库条目后的引用清理：按钮/子手势/面板/全局手势/悬浮球里指向该条目的动作清掉。
     */
    suspend fun removeEntryRefs(entryId: String) {
        LibraryCleaner.cleanLibraryReferences(entryId)
    }
}
