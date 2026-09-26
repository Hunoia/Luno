package hunoia.luno.gesture

import hunoia.luno.config.model.Action
import hunoia.luno.config.model.GestureActionsDefaults

fun List<Action>.isEmptyOrNone(): Boolean {
    return isEmpty() || first() == GestureActionsDefaults.ActionNone
}
