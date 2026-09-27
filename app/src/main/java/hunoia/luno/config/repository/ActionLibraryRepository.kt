package hunoia.luno.config.repository

import hunoia.luno.config.store.SettingsStores
import hunoia.luno.config.cleanActions
import hunoia.luno.config.model.Action
import hunoia.luno.config.model.actionLibraryRefId
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

internal class ActionLibraryRepository(private val stores: SettingsStores) {

    suspend fun removeActionLibraryEntry(entryId: String) =
        removeAllActionLibraryEntries(setOf(entryId))

    suspend fun removeAllActionLibraryEntries(entryIds: Set<String>) {
        if (entryIds.isEmpty()) return
        coroutineScope {
            launch {
                stores._actionLibrarySettings.updateData { settings ->
                    settings.copy(entries = settings.entries.filterNot { it.id in entryIds })
                }
            }
            launch {
                stores._gestureButtons.updateData { buttons ->
                    buttons.map { button ->
                        button.cleanActions { action -> isReferenceToAny(action, entryIds) }
                    }
                }
            }
            launch {
                stores._subGestureSettings.updateData { settings ->
                    settings.copy(subGestures = settings.subGestures.map { subGesture ->
                        subGesture.cleanActions { action -> isReferenceToAny(action, entryIds) }
                    })
                }
            }
        }
    }

    private fun isReferenceToAny(action: Action, entryIds: Set<String>): Boolean {
        val refId = action.actionLibraryRefId() ?: return false
        return refId in entryIds
    }
}
