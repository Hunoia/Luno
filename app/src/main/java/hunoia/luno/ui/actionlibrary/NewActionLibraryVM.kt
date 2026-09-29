package hunoia.luno.ui.actionlibrary

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import hunoia.luno.action.definitions.ActionDefinitions
import hunoia.luno.action.model.NewActionLibraryEntry
import hunoia.luno.action.model.NewActionLibrarySettings
import hunoia.luno.action.model.StoredAction
import hunoia.luno.config.ConfigProvider
import hunoia.luno.config.model.ActionLibraryEntry
import hunoia.luno.config.model.ActionLibraryPayload
import hunoia.luno.config.model.ActionLibrarySettings
import hunoia.luno.config.model.ActionLibraryType
import hunoia.luno.config.model.GestureButton
import hunoia.luno.config.model.OpenAppOrUrlData
import hunoia.luno.config.model.ShellCommandData
import hunoia.luno.config.model.SubGesture
import hunoia.luno.config.model.actionLibraryRefId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

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
                migrateIfNeeded(newSettings)
                NewLibraryUiState(
                    entries = newSettings.entries,
                    referenceCounts = countReferences(buttons, subGestures.subGestures),
                )
            }.collect { _uiState.value = it }
        }
    }

    fun onCreateAction(typeId: String? = null) {
        val type = typeId ?: "app.launch"
        val def = ActionDefinitions.byTypeId(type)
        val entry = NewActionLibraryEntry(
            typeId = type,
            name = def?.name ?: type,
        )
        viewModelScope.launch {
            ConfigProvider.updateNewActionLibrarySettings { settings ->
                settings.copy(entries = settings.entries + entry)
            }
            syncLegacyEntry(entry)
        }
    }

    fun remove(entry: NewActionLibraryEntry) {
        viewModelScope.launch {
            ConfigProvider.updateNewActionLibrarySettings { settings ->
                settings.copy(entries = settings.entries.filter { it.id != entry.id })
            }
            ConfigProvider.removeActionLibraryEntry(entry.id)
        }
    }

    fun onDuplicateEntry(entryId: String) {
        viewModelScope.launch {
            ConfigProvider.updateNewActionLibrarySettings { settings ->
                val entry = settings.entries.find { it.id == entryId }
                    ?: return@updateNewActionLibrarySettings settings
                val newEntry = NewActionLibraryEntry.duplicate(entry, "${entry.name} (copy)")
                settings.copy(entries = settings.entries + newEntry)
            }
        }
    }

    fun onMoveEntry(entryId: String, targetIndex: Int) {
        viewModelScope.launch {
            ConfigProvider.updateNewActionLibrarySettings { settings ->
                val entries = settings.entries.toMutableList()
                val index = entries.indexOfFirst { it.id == entryId }
                if (index >= 0) {
                    val entry = entries.removeAt(index)
                    entries.add(targetIndex.coerceIn(0, entries.size), entry)
                    settings.copy(entries = entries)
                } else settings
            }
        }
    }

    private suspend fun migrateIfNeeded(newSettings: NewActionLibrarySettings) {
        if (newSettings.entries.isNotEmpty()) return
        val oldSettings = ConfigProvider.getActionLibrarySettings()
        if (oldSettings.entries.isEmpty()) return
        val migrated = oldSettings.entries.map { legacyToNewEntry(it) }
        ConfigProvider.updateNewActionLibrarySettings { it.copy(entries = migrated) }
    }

    companion object {
        suspend fun syncLegacyEntry(entry: NewActionLibraryEntry) {
            val legacyEntry = newEntryToLegacy(entry)
            ConfigProvider.updateActionLibrarySettings { settings ->
                val index = settings.entries.indexOfFirst { it.id == entry.id }
                val entries = settings.entries.toMutableList()
                if (index >= 0) entries[index] = legacyEntry else entries.add(legacyEntry)
                settings.copy(entries = entries)
            }
        }

        suspend fun removeLegacyEntry(entryId: String) {
            ConfigProvider.removeActionLibraryEntry(entryId)
        }

        fun legacyToNewEntry(legacy: ActionLibraryEntry): NewActionLibraryEntry {
            val stored = legacyToStored(legacy)
            return NewActionLibraryEntry(
                id = legacy.id,
                name = legacy.name,
                typeId = stored.typeId,
                params = stored.params,
                createdAt = legacy.createdAt,
            )
        }

        private fun legacyToStored(legacy: ActionLibraryEntry): StoredAction {
            return when (legacy.type) {
                ActionLibraryType.Shell -> {
                    val data = legacy.shellCommand
                    val cmd = data.command
                    val encodedTypeId = if (cmd.startsWith("#")) cmd.substring(1) else null
                    if (encodedTypeId != null && ActionDefinitions.byTypeId(encodedTypeId) != null) {
                        StoredAction(encodedTypeId)
                    } else {
                        StoredAction.of(
                            "shell.custom",
                            "command" to cmd,
                            "showToast" to data.showToast.toString(),
                        )
                    }
                }
                ActionLibraryType.Url -> {
                    val p = legacy.payload as? ActionLibraryPayload.Url
                        ?: return StoredAction.of("intent.openUrl")
                    StoredAction.of("intent.openUrl", "url" to p.data.url)
                }
                ActionLibraryType.Activity -> {
                    val p = legacy.payload as? ActionLibraryPayload.Activity
                        ?: return StoredAction.of("app.openActivity")
                    StoredAction.of(
                        "app.openActivity",
                        "packageName" to p.data.packageName,
                        "activityClassName" to p.data.activityClassName,
                        "miniWindow" to p.data.miniWindow.toString(),
                    )
                }
            }
        }
    }
}

suspend fun newEntryToLegacy(newEntry: NewActionLibraryEntry): ActionLibraryEntry {
    val params = newEntry.params
    val payload = when (newEntry.typeId) {
        "shell.custom" -> ActionLibraryPayload.Shell(ShellCommandData(
            command = params["command"]?.jsonPrimitive?.contentOrNull ?: "",
            showToast = params["showToast"]?.jsonPrimitive?.booleanOrNull ?: false,
        ))
        "intent.openUrl" -> ActionLibraryPayload.Url(OpenAppOrUrlData(
            type = OpenAppOrUrlData.TYPE_URL,
            url = params["url"]?.jsonPrimitive?.contentOrNull ?: "",
        ))
        "app.openActivity", "app.launch" -> ActionLibraryPayload.Activity(OpenAppOrUrlData(
            type = OpenAppOrUrlData.TYPE_ACTIVITY,
            packageName = params["packageName"]?.jsonPrimitive?.contentOrNull ?: "",
            activityClassName = params["activityClassName"]?.jsonPrimitive?.contentOrNull ?: "",
            miniWindow = params["miniWindow"]?.jsonPrimitive?.booleanOrNull ?: false,
        ))
        else -> ActionLibraryPayload.Shell(ShellCommandData(
            command = "#${newEntry.typeId}",
        ))
    }
    return ActionLibraryEntry(
        id = newEntry.id,
        name = newEntry.name,
        payload = payload,
        createdAt = newEntry.createdAt,
    )
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
    fun add(action: hunoia.luno.config.model.Action?) {
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
