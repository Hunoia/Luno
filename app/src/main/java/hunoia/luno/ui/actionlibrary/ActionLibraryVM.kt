package hunoia.luno.ui.actionlibrary

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import hunoia.luno.core.domain.ActionLibraryEntry
import hunoia.luno.domain.action.ActionLibrary
import hunoia.luno.domain.gesture.GestureConfig
import hunoia.luno.core.domain.StoredAction
import hunoia.luno.core.domain.libraryEntryId
import hunoia.luno.core.domain.longPressVariant
import hunoia.luno.config.model.GestureButton
import hunoia.luno.config.model.SubGesture
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class ActionLibraryVM : ViewModel() {
    private val _uiState = MutableStateFlow(NewLibraryUiState())
    val uiState: StateFlow<NewLibraryUiState> = _uiState

    init {
        viewModelScope.launch {
            combine(
                ActionLibrary.settings,
                GestureConfig.buttons,
                GestureConfig.subGestures,
            ) { newSettings, buttons, subGestures ->
                NewLibraryUiState(
                    entries = newSettings.entries,
                    referenceCounts = countReferences(buttons, subGestures.subGestures),
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

private fun countReferences(
    buttons: List<GestureButton>,
    subGestures: List<SubGesture>,
): Map<String, Int> {
    val counts = mutableMapOf<String, Int>()
    fun count(action: StoredAction?) {
        val entryId = action?.libraryEntryId() ?: return
        counts[entryId] = (counts[entryId] ?: 0) + 1
    }
    (buttons.flatMap { it.actionList() } + subGestures.flatMap { it.actionList() }).forEach { action ->
        count(action)
        count(action.longPressVariant())
    }
    return counts
}

private fun GestureButton.actionList(): List<StoredAction> =
    slideActions.actions.values +
        slideHoldActions.actions.values +
        longSlideActions.actions.values +
        longSlideHoldActions.actions.values +
        listOfNotNull(tapActions, doubleTapActions, longPressActions)

private fun SubGesture.actionList(): List<StoredAction> =
    slideActions.actions.values +
        slideHoldActions.actions.values +
        longSlideActions.actions.values +
        longSlideHoldActions.actions.values
