package hunoia.luno.config.model

import androidx.annotation.Keep
import hunoia.luno.config.defaults.InitialSettingsDefaults.GestureEnabled
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

/** 手势总开关（首次启动引导用）。 */
@Serializable
@Keep
data class InitialSettings(
    val gestureEnabled: Boolean = GestureEnabled,
)

/** 手势面板行为开关。 */
@Serializable
@Keep
data class GestureSettings(
    val actionPanelVibrate: Boolean = true,
)
