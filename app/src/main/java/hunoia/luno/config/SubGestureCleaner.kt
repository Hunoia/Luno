package hunoia.luno.config

import hunoia.luno.action.api.ActionFacade
import hunoia.luno.config.model.Action
import kotlinx.serialization.json.Json

object SubGestureCleaner {

    suspend fun cleanSubGestureReferences(
        deletedId: String,
        shouldRemove: (Action) -> Boolean,
    ) {
        ConfigProvider.updateGestureButtons { buttons ->
            buttons.map { it.cleanActions(shouldRemove) }
        }
        ConfigProvider.updateSubGestureSettings { settings ->
            settings.copy(
                subGestures = settings.subGestures.map { it.cleanActions(shouldRemove) }
            )
        }
    }

    fun isSubGestureAction(action: Action): Boolean =
        action.value == ActionFacade.SUB_GESTURE

    fun matchesDeletedSubGesture(action: Action, deletedId: String): Boolean {
        if (!isSubGestureAction(action)) return false
        return try {
            val data = Json.decodeFromString<hunoia.luno.action.payload.SubGestureActionData>(action.data)
            data.id == deletedId
        } catch (_: Exception) {
            false
        }
    }
}
