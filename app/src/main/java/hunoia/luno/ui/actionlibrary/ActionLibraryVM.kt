package hunoia.luno.ui.actionlibrary

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import hunoia.luno.config.ConfigProvider
import hunoia.luno.config.model.Action
import hunoia.luno.config.model.ActionLibraryEntry
import hunoia.luno.config.model.ActionLibraryRefData
import hunoia.luno.config.model.ActionLibraryType
import hunoia.luno.config.model.GestureButton
import hunoia.luno.config.model.SubGesture
import hunoia.luno.core.JsonSerializer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

data class ActionLibraryUiState(
    val entries: List<ActionLibraryEntry> = emptyList(),
    val referenceCounts: Map<String, Int> = emptyMap(),
)

class ActionLibraryVM : ViewModel() {
    private val _uiState = MutableStateFlow(ActionLibraryUiState())
    val uiState: StateFlow<ActionLibraryUiState> = _uiState

    init {
        viewModelScope.launch {
            combine(
                ConfigProvider.actionLibrarySettings,
                ConfigProvider.gestureButtons,
                ConfigProvider.subGestureSettings,
            ) { library, buttons, subGestures ->
                ActionLibraryUiState(
                    entries = library.entries,
                    referenceCounts = countReferences(buttons, subGestures.subGestures),
                )
            }.collect { _uiState.value = it }
        }
    }

    fun save(entry: ActionLibraryEntry) {
        viewModelScope.launch {
            ConfigProvider.updateActionLibrarySettings { settings ->
                val index = settings.entries.indexOfFirst { it.id == entry.id }
                val entries = settings.entries.toMutableList()
                if (index >= 0) entries[index] = entry else entries.add(entry)
                settings.copy(entries = entries)
            }
        }
    }

    fun remove(entry: ActionLibraryEntry) {
        viewModelScope.launch {
            ConfigProvider.removeActionLibraryEntry(entry.id)
        }
    }

    fun removeAll(entries: List<ActionLibraryEntry>) {
        viewModelScope.launch {
            entries.forEach { entry -> ConfigProvider.removeActionLibraryEntry(entry.id) }
        }
    }

    fun duplicate(entry: ActionLibraryEntry, name: String) {
        save(ActionLibraryEntry.duplicate(entry, name))
    }
}

fun ActionLibraryEntry.matchesQuery(query: String): Boolean {
    if (query.isBlank()) return true
    val q = query.trim()
    return name.contains(q, ignoreCase = true) ||
        shellCommand.command.contains(q, ignoreCase = true) ||
        openAppOrUrl.url.contains(q, ignoreCase = true) ||
        openAppOrUrl.packageName.contains(q, ignoreCase = true) ||
        openAppOrUrl.activityClassName.contains(q, ignoreCase = true) ||
        openAppOrUrl.queryParameters.any { parameter ->
            parameter.name.contains(q, ignoreCase = true) ||
                parameter.value.contains(q, ignoreCase = true)
        }
}

private fun countReferences(
    buttons: List<GestureButton>,
    subGestures: List<SubGesture>,
): Map<String, Int> {
    val counts = mutableMapOf<String, Int>()
    fun add(action: Action?) {
        val ref = action?.actionLibraryRef() ?: return
        counts[ref.entryId] = (counts[ref.entryId] ?: 0) + 1
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

private fun Action.actionLibraryRef(): ActionLibraryRefData? {
    return runCatching { JsonSerializer.decodeFromString<ActionLibraryRefData>(data) }.getOrNull()
}

fun ActionLibraryType.sortIndex(): Int = when (this) {
    ActionLibraryType.Shell -> 0
    ActionLibraryType.Url -> 1
    ActionLibraryType.Activity -> 2
}
