package hunoia.luno.ui.actionlibrary

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import hunoia.luno.action.definitions.ActionDefinition
import hunoia.luno.action.definitions.ActionDefinitions
import hunoia.luno.action.definitions.ParameterDefinition
import hunoia.luno.action.model.NewActionLibraryEntry
import hunoia.luno.config.ConfigProvider
import hunoia.luno.ui.navigation.NEW_ACTION_LIBRARY_ENTRY_ID
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put
import java.util.UUID

internal const val DEFAULT_ACTION_TYPE_ID = "app.launch"

internal fun JsonElement?.scalarText(): String? = (this as? JsonPrimitive)?.contentOrNull

class NewActionLibraryEditVM : ViewModel() {
    private val _uiState = MutableStateFlow(NewEditState())
    val uiState: StateFlow<NewEditState> = _uiState

    suspend fun load(entryId: String, typeId: String?) {
        val isNewEntry = entryId == NEW_ACTION_LIBRARY_ENTRY_ID
        val draft = if (isNewEntry) {
            buildNewDraft(typeId)
        } else {
            ConfigProvider.getNewActionLibrarySettings().entries
                .find { it.id == entryId }
                ?.let { entry ->
                    NewEditDraft(
                        id = entry.id,
                        typeId = entry.typeId,
                        name = entry.name,
                        params = entry.params,
                        createdAt = entry.createdAt,
                    )
                }
        }
        _uiState.value = NewEditState(
            draft = draft,
            baseline = draft,
            notFound = !isNewEntry && draft == null,
        )
    }

    private fun buildNewDraft(typeId: String?): NewEditDraft {
        val type = typeId ?: DEFAULT_ACTION_TYPE_ID
        val definition = ActionDefinitions.byTypeId(type)
        return NewEditDraft(
            typeId = type,
            name = definition?.name ?: type,
            params = defaultParams(definition),
        )
    }

    private fun defaultParams(definition: ActionDefinition?): Map<String, JsonElement> =
        definition?.parameters.orEmpty()
            .mapNotNull { p -> p.defaultValue?.let { p.key to JsonPrimitive(it) } }
            .toMap()

    fun selectType(typeId: String) {
        val definition = ActionDefinitions.byTypeId(typeId) ?: return
        _uiState.update { state ->
            val draft = state.draft
                ?: NewEditDraft(typeId = typeId, name = definition.name, params = defaultParams(definition))
            val oldDefinition = ActionDefinitions.byTypeId(draft.typeId)
            val keepName = draft.name.isNotBlank() && draft.name != oldDefinition?.name
            state.copy(
                draft = draft.copy(
                    typeId = typeId,
                    name = if (keepName) draft.name else definition.name,
                    params = defaultParams(definition),
                ),
            )
        }
    }

    fun updateParam(key: String, value: String) {
        _uiState.update { state ->
            state.copy(draft = state.draft?.copy(params = state.draft.params + (key to JsonPrimitive(value))))
        }
    }

    fun updateParamMulti(key: String, values: List<String>) {
        _uiState.update { state ->
            state.copy(
                draft = state.draft?.copy(
                    params = state.draft.params + (key to JsonArray(values.map { JsonPrimitive(it) })),
                ),
            )
        }
    }

    fun updateName(name: String) {
        _uiState.update { state -> state.copy(draft = state.draft?.copy(name = name)) }
    }

    fun save(onSaved: () -> Unit = {}) {
        val draft = _uiState.value.draft ?: return
        if (!draft.isValid) return
        _uiState.update { it.copy(isSaving = true) }
        val newEntry = NewActionLibraryEntry(
            id = draft.id ?: UUID.randomUUID().toString(),
            name = draft.name,
            typeId = draft.typeId,
            params = draft.params.toJsonObject(),
            createdAt = draft.createdAt,
        )
        viewModelScope.launch {
            withContext(NonCancellable) {
                try {
                    ConfigProvider.updateNewActionLibrarySettings { settings ->
                        val updatedEntries = if (draft.id == null) {
                            settings.entries + newEntry
                        } else {
                            settings.entries.map { if (it.id == newEntry.id) newEntry else it }
                        }
                        settings.copy(entries = updatedEntries)
                    }
                    onSaved()
                } finally {
                    _uiState.update { it.copy(isSaving = false) }
                }
            }
        }
    }

    fun delete() {
        val id = _uiState.value.draft?.id ?: return
        viewModelScope.launch {
            withContext(NonCancellable) { removeNewLibraryEntry(id) }
        }
    }
}

data class NewEditState(
    val draft: NewEditDraft? = null,
    val baseline: NewEditDraft? = null,
    val isSaving: Boolean = false,
    val notFound: Boolean = false,
) {
    val isDirty: Boolean
        get() {
            val current = draft ?: return false
            val base = baseline ?: return false
            return current.typeId != base.typeId ||
                current.name != base.name ||
                current.params != base.params
        }
}

data class NewEditDraft(
    val id: String? = null,
    val typeId: String = "",
    val name: String = "",
    val params: Map<String, JsonElement> = emptyMap(),
    val createdAt: Long = System.currentTimeMillis(),
) {
    val definition: ActionDefinition? get() = ActionDefinitions.byTypeId(typeId)

    val isValid: Boolean
        get() = typeId.isNotBlank() &&
            name.isNotBlank() &&
            definition?.parameters?.all { p -> !p.required || paramText(p).isNotBlank() } == true

    private fun paramText(p: ParameterDefinition): String =
        params[p.key]?.scalarText() ?: p.defaultValue ?: ""
}

private fun Map<String, JsonElement>.toJsonObject(): JsonObject =
    buildJsonObject { for ((key, value) in this@toJsonObject) put(key, value) }
