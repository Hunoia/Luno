package hunoia.luno.actions.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import hunoia.luno.config.model.LibraryReferences
import hunoia.luno.core.domain.ActionLibraryEntry
import hunoia.luno.domain.action.ActionLibrary
import hunoia.luno.domain.gesture.GestureConfig
import hunoia.luno.domain.tune.TuneConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/** 新建动作库条目时占位用的 entryId（与 app 导航层共用同一常量）。 */
const val ACTION_LIBRARY_NEW_ENTRY_ID = "new"

class ActionLibraryVM : ViewModel() {
    private val _uiState = MutableStateFlow(NewLibraryUiState())
    val uiState: StateFlow<NewLibraryUiState> = _uiState

    init {
        viewModelScope.launch {
            combine(
                ActionLibrary.settings,
                GestureConfig.buttons,
                GestureConfig.subGestures,
                GestureConfig.globalGesture,
                TuneConfig.action,
            ) { newSettings, buttons, subGestures, globalGesture, actionSettings ->
                NewLibraryUiState(
                    entries = newSettings.entries,
                    referenceCounts = LibraryReferences.count(
                        action = actionSettings,
                        buttons = buttons,
                        subGestures = subGestures.subGestures,
                        globalGesture = globalGesture,
                    ),
                )
            }.collect { _uiState.value = it }
        }
    }

    fun remove(entry: ActionLibraryEntry) {
        viewModelScope.launch { ActionLibrary.removeEntry(entry.id) }
    }

    fun updateIcon(entryId: String, iconKey: String?) {
        viewModelScope.launch {
            ActionLibrary.updateSettings { settings ->
                settings.copy(
                    entries = settings.entries.map { entry ->
                        if (entry.id == entryId) entry.copy(iconKey = iconKey) else entry
                    },
                )
            }
        }
    }
}

data class NewLibraryUiState(
    val entries: List<ActionLibraryEntry> = emptyList(),
    val referenceCounts: Map<String, Int> = emptyMap(),
)
