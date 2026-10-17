package hunoia.luno.config.model

import androidx.annotation.Keep
import hunoia.luno.action.model.StoredAction
import hunoia.luno.config.defaults.InitialSettingsDefaults.GestureEnabled
import kotlinx.serialization.Serializable

@Serializable
@Keep
data class DirectionActions(
    /**
     * 每个方向一个动作，单值。**值类型不可空**：没有动作就是没有这个 key，
     * 清空即 [withAction] 删键（往 Map 里塞显式 null 只会让序列化多写一串 `null`）。
     */
    val actions: Map<GestureDirection, StoredAction> = emptyMap()
) {
    fun actionBy(direction: GestureDirection): StoredAction? = actions[direction]

    fun withAction(direction: GestureDirection, action: StoredAction?): DirectionActions {
        if (action == null) return copy(actions = actions - direction)
        return copy(actions = actions + (direction to action))
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