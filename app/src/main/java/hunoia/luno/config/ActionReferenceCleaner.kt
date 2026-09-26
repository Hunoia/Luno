package hunoia.luno.config

import hunoia.luno.config.model.Action
import hunoia.luno.config.model.DirectionActions
import hunoia.luno.config.model.GestureButton
import hunoia.luno.config.model.SubGesture

fun GestureButton.cleanActions(
    shouldRemove: (Action) -> Boolean,
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
    shouldRemove: (Action) -> Boolean,
): SubGesture = copy(
    slideActions = slideActions.cleanActions(shouldRemove),
    slideHoldActions = slideHoldActions.cleanActions(shouldRemove),
    longSlideActions = longSlideActions.cleanActions(shouldRemove),
    longSlideHoldActions = longSlideHoldActions.cleanActions(shouldRemove),
)

fun DirectionActions.cleanActions(shouldRemove: (Action) -> Boolean): DirectionActions =
    copy(actions = actions.mapValues { (_, list) -> list.cleanList(shouldRemove) })

fun List<Action>.cleanList(shouldRemove: (Action) -> Boolean): List<Action> =
    mapNotNull { action ->
        if (shouldRemove(action)) null
        else action.copy(longPressAction = action.longPressAction?.takeUnless(shouldRemove))
    }
