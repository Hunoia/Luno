package hunoia.luno.config.repository

import hunoia.luno.action.model.StoredAction
import hunoia.luno.config.ConfigProvider
import hunoia.luno.config.model.DirectionActions
import hunoia.luno.config.model.GestureButton
import hunoia.luno.config.model.SubGesture
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

object SubGestureCleaner {

    suspend fun cleanSubGestureReferences(
        deletedId: String,
        shouldRemove: (StoredAction) -> Boolean,
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

    fun isSubGestureAction(action: StoredAction): Boolean =
        action.typeId == "internal.subGesture"

    fun matchesDeletedSubGesture(action: StoredAction, deletedId: String): Boolean {
        if (!isSubGestureAction(action)) return false
        val id = (action.params["id"] as? JsonPrimitive)?.contentOrNull.orEmpty()
        return id == deletedId
    }
}

fun GestureButton.cleanActions(
    shouldRemove: (StoredAction) -> Boolean,
): GestureButton = copy(
    slideActions = slideActions.cleanActions(shouldRemove),
    slideHoldActions = slideHoldActions.cleanActions(shouldRemove),
    longSlideActions = longSlideActions.cleanActions(shouldRemove),
    longSlideHoldActions = longSlideHoldActions.cleanActions(shouldRemove),
    tapActions = tapActions.cleanList(shouldRemove),
    doubleTapActions = doubleTapActions.cleanList(shouldRemove),
    longPressActions = longPressActions.cleanList(shouldRemove),
)

fun SubGesture.cleanActions(
    shouldRemove: (StoredAction) -> Boolean,
): SubGesture = copy(
    slideActions = slideActions.cleanActions(shouldRemove),
    slideHoldActions = slideHoldActions.cleanActions(shouldRemove),
    longSlideActions = longSlideActions.cleanActions(shouldRemove),
    longSlideHoldActions = longSlideHoldActions.cleanActions(shouldRemove),
)

fun DirectionActions.cleanActions(shouldRemove: (StoredAction) -> Boolean): DirectionActions =
    copy(actions = actions.mapValues { (_, list) -> list.cleanList(shouldRemove) })

fun List<StoredAction>.cleanList(shouldRemove: (StoredAction) -> Boolean): List<StoredAction> =
    filterNot(shouldRemove)
