package hunoia.luno.config.model

import androidx.annotation.Keep
import kotlinx.serialization.Serializable

object GestureActionsDefaults {
    const val ActionValue = "0"
    val ActionNone = Action(value = ActionValue, data = "")
}

@Serializable
@Keep
data class DirectionActions(
    val actions: Map<GestureDirection, List<Action>> = emptyMap()
) {
    fun actionsBy(direction: GestureDirection): List<Action> = actions[direction].orEmpty()

    fun withActions(direction: GestureDirection, newActions: List<Action>): DirectionActions {
        return copy(actions = actions + (direction to newActions))
    }
}

