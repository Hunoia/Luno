package hunoia.luno.config.model

import androidx.annotation.Keep
import hunoia.luno.core.domain.StoredAction
import hunoia.luno.config.defaults.GestureSettingsDefaults
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
    /** 识屏长按手势：全屏任意位置按住 → 幽灵卡。默认关闭（需 Shizuku 读内核触摸流）。 */
    val screenPickGesture: ScreenPickGesture = ScreenPickGesture(),
)

@Serializable
@Keep
data class GestureSettings(
    val actionPanelVibrate: Boolean = true,
    val pointer: Pointer = Pointer(),
) {
    /**
     * 指针（光标）手感与外观：移动映射（灵敏度/加速）与绘制（大小/透明度），
     * [initialYRatio] 是初始高度比例。
     */
    @Serializable
    @Keep
    data class Pointer(
        val sensitivityX: Float = GestureSettingsDefaults.PointerSensitivityX,
        val sensitivityY: Float = GestureSettingsDefaults.PointerSensitivityY,
        val acceleration: Float = GestureSettingsDefaults.PointerAcceleration,
        val initialYRatio: Float = GestureSettingsDefaults.PointerInitialYRatio,
        val cursorSizeDp: Int = GestureSettingsDefaults.PointerCursorSizeDp,
        val cursorAlpha: Float = GestureSettingsDefaults.PointerCursorAlpha,
    )
}