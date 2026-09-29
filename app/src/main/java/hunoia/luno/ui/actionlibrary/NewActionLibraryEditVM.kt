package hunoia.luno.ui.actionlibrary

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import hunoia.luno.action.definitions.ActionDefinition
import hunoia.luno.action.definitions.ActionDefinitions
import hunoia.luno.action.model.NewActionLibraryEntry
import hunoia.luno.action.model.StoredAction
import hunoia.luno.config.ConfigProvider
import hunoia.luno.ui.navigation.NEW_ACTION_LIBRARY_ENTRY_ID
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

class NewActionLibraryEditVM : ViewModel() {
    private val _uiState = MutableStateFlow(NewEditState())
    val uiState: StateFlow<NewEditState> = _uiState

    suspend fun load(entryId: String, typeId: String?) {
        val freshEntries = ConfigProvider.getNewActionLibrarySettings().entries
        val draft = if (entryId == NEW_ACTION_LIBRARY_ENTRY_ID) {
            buildNewDraft(typeId)
        } else {
            val entry = freshEntries.find { it.id == entryId }
            if (entry != null) {
                NewEditDraft(
                    id = entry.id,
                    typeId = entry.typeId,
                    name = entry.name,
                    params = entry.params.toParamMap(),
                    isNew = false,
                    createdAt = entry.createdAt,
                )
            } else {
                buildNewDraft(typeId)
            }
        }
        _uiState.value = NewEditState(draft = draft)
    }

    private fun buildNewDraft(typeId: String?): NewEditDraft {
        val type = typeId ?: "app.launch"
        val d = ActionDefinitions.byTypeId(type)
        return NewEditDraft(
            typeId = type,
            name = d?.name ?: type,
            params = d?.parameters?.associate { p -> p.key to (p.defaultValue ?: "") } ?: emptyMap(),
            isNew = true,
        )
    }

    fun selectType(typeId: String) {
        val def = ActionDefinitions.byTypeId(typeId) ?: return
        _uiState.update { state ->
            val draft = state.draft ?: NewEditDraft(typeId = typeId, name = def.name, params = emptyMap(), isNew = true)
            val oldDef = ActionDefinitions.byTypeId(draft.typeId)
            val keepName = draft.name.isNotBlank() && draft.name != oldDef?.name
            state.copy(
                draft = draft.copy(
                    typeId = typeId,
                    name = if (keepName) draft.name else def.name,
                    params = def.parameters.associate { p -> p.key to (p.defaultValue ?: "") },
                )
            )
        }
    }

    fun updateParam(name: String, value: String) {
        _uiState.update { state ->
            state.copy(draft = state.draft?.copy(params = state.draft.params + (name to value)))
        }
    }

    fun updateName(name: String) {
        _uiState.update { state ->
            state.copy(draft = state.draft?.copy(name = name))
        }
    }

    fun save() {
        val draft = _uiState.value.draft ?: return
        if (draft.name.isBlank()) return

        val stored = StoredAction(
            typeId = draft.typeId,
            params = draft.params.toJsonObject()
        )
        val newEntry = NewActionLibraryEntry(
            id = draft.id ?: java.util.UUID.randomUUID().toString(),
            name = draft.name,
            typeId = draft.typeId,
            params = stored.params,
            createdAt = draft.createdAt,
        )

        viewModelScope.launch {
            withContext(NonCancellable) {
                ConfigProvider.updateNewActionLibrarySettings { settings ->
                    val updatedEntries = if (draft.isNew) {
                        settings.entries + newEntry
                    } else {
                        settings.entries.map { if (it.id == newEntry.id) newEntry else it }
                    }
                    settings.copy(entries = updatedEntries)
                }
            }
        }
    }

    fun delete() {
        val draft = _uiState.value.draft ?: return
        if (draft.isNew || draft.id == null) return
        viewModelScope.launch {
            withContext(NonCancellable) {
                removeNewLibraryEntry(draft.id)
            }
        }
    }
}

data class NewEditState(
    val draft: NewEditDraft? = null,
)

data class NewEditDraft(
    val id: String? = null,
    val typeId: String = "",
    val name: String = "",
    val params: Map<String, String> = emptyMap(),
    val isNew: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
) {
    val definition: ActionDefinition? get() = ActionDefinitions.byTypeId(typeId)
    val isValid: Boolean get() = typeId.isNotBlank() && name.isNotBlank()
}

private fun Map<String, String>.toJsonObject(): JsonObject {
    return buildJsonObject {
        for ((key, value) in this@toJsonObject) {
            put(key, JsonPrimitive(value))
        }
    }
}

private fun JsonObject.toParamMap(): Map<String, String> {
    return this.mapValues { (_, value) ->
        value.jsonPrimitive.contentOrNull ?: ""
    }
}
