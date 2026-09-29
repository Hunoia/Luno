package hunoia.luno.ui.actionlibrary

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import hunoia.luno.action.model.NewActionLibraryEntry
import hunoia.luno.config.ConfigProvider
import hunoia.luno.config.cleanActions
import hunoia.luno.config.model.Action
import hunoia.luno.config.model.GestureButton
import hunoia.luno.config.model.SubGesture
import hunoia.luno.config.model.actionLibraryRefId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class NewActionLibraryVM : ViewModel() {
    private val _uiState = MutableStateFlow(NewLibraryUiState())
    val uiState: StateFlow<NewLibraryUiState> = _uiState

    init {
        viewModelScope.launch {
            combine(
                ConfigProvider.newActionLibrarySettings,
                ConfigProvider.gestureButtons,
                ConfigProvider.subGestureSettings,
            ) { newSettings, buttons, subGestures ->
                NewLibraryUiState(
                    entries = newSettings.entries,
                    referenceCounts = countReferences(buttons, subGestures.subGestures),
                )
            }.collect { _uiState.value = it }
        }
    }

    fun remove(entry: NewActionLibraryEntry) {
        viewModelScope.launch { removeNewLibraryEntry(entry.id) }
    }
}

suspend fun removeNewLibraryEntry(entryId: String) {
    ConfigProvider.updateGestureButtons { buttons ->
        buttons.map { button -> button.cleanActions { it.actionLibraryRefId() == entryId } }
    }
    ConfigProvider.updateSubGestureSettings { settings ->
        settings.copy(subGestures = settings.subGestures.map { subGesture ->
            subGesture.cleanActions { it.actionLibraryRefId() == entryId }
        })
    }
    ConfigProvider.updateNewActionLibrarySettings { settings ->
        settings.copy(entries = settings.entries.filterNot { it.id == entryId })
    }
}

data class NewLibraryUiState(
    val entries: List<NewActionLibraryEntry> = emptyList(),
    val referenceCounts: Map<String, Int> = emptyMap(),
)

private fun countReferences(
    buttons: List<GestureButton>,
    subGestures: List<SubGesture>,
): Map<String, Int> {
    val counts = mutableMapOf<String, Int>()
    fun add(action: Action?) {
        val entryId = action?.actionLibraryRefId() ?: return
        counts[entryId] = (counts[entryId] ?: 0) + 1
    }
    buttons.forEach { button ->
        button.slideActions.actions.values.flatten().forEach { add(it); add(it.longPressAction) }
        button.slideHoldActions.actions.values.flatten().forEach { add(it); add(it.longPressAction) }
        button.longSlideActions.actions.values.flatten().forEach { add(it); add(it.longPressAction) }
        button.longSlideHoldActions.actions.values.flatten().forEach { add(it); add(it.longPressAction) }
        button.tapActions.forEach { add(it); add(it.longPressAction) }
        button.doubleTapActions.forEach { add(it); add(it.longPressAction) }
        button.longPressActions.forEach { add(it); add(it.longPressAction) }
    }
    subGestures.forEach { gesture ->
        gesture.slideActions.actions.values.flatten().forEach { add(it); add(it.longPressAction) }
        gesture.slideHoldActions.actions.values.flatten().forEach { add(it); add(it.longPressAction) }
        gesture.longSlideActions.actions.values.flatten().forEach { add(it); add(it.longPressAction) }
        gesture.longSlideHoldActions.actions.values.flatten().forEach { add(it); add(it.longPressAction) }
    }
    return counts
}
