package hunoia.luno.config.repository

import hunoia.luno.config.ActionLibraryReferenceMatcher
import hunoia.luno.config.store.SettingsStores
import hunoia.luno.config.cleanActions
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

internal class ActionLibraryRepository(private val stores: SettingsStores) {

    suspend fun removeActionLibraryEntry(entryId: String) = coroutineScope {
        launch {
            stores._actionLibrarySettings.updateData { settings ->
                settings.copy(entries = settings.entries.filterNot { it.id == entryId })
            }
        }
        launch {
            stores._gestureButtons.updateData { buttons ->
                buttons.map { button ->
                    button.cleanActions { action ->
                        ActionLibraryReferenceMatcher.isReferenceTo(action, entryId)
                    }
                }
            }
        }
        launch {
            stores._subGestureSettings.updateData { settings ->
                settings.copy(subGestures = settings.subGestures.map { subGesture ->
                    subGesture.cleanActions { action ->
                        ActionLibraryReferenceMatcher.isReferenceTo(action, entryId)
                    }
                })
            }
        }
    }
}
