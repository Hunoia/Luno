package hunoia.luno.config.model

import androidx.annotation.Keep
import hunoia.luno.action.dispatcher.MigratingStoredActionSerializer
import hunoia.luno.action.model.StoredAction
import hunoia.luno.config.defaults.InitialSettingsDefaults.GestureEnabled
import kotlinx.serialization.Serializable

object GestureActionsDefaults {
    val ActionNone = StoredAction.of("internal.none")
}

@Serializable
@Keep
data class DirectionActions(
    /**
     * 每个方向一个动作，单值。**值类型不可空**：没有动作就是没有这个 key，
     * 清空即 [withAction] 删键（往 Map 里塞显式 null 只会让序列化多写一串 `null`）。
     *
     * 值上挂迁移序列化器是为了读旧数据：单值化之前这里是 `List<StoredAction>`，
     * 旧数组由它的数组分支截断为首个元素（见 docs/action-select-single.md §3.3）。
     */
    val actions: Map<
        GestureDirection,
        @Serializable(MigratingStoredActionSerializer::class) StoredAction,
        > = emptyMap()
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