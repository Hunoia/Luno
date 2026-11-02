package hunoia.luno.config.model

import androidx.annotation.Keep
import hunoia.luno.core.domain.StoredAction
import hunoia.luno.config.defaults.GestureSettingsDefaults
import hunoia.luno.config.defaults.InitialSettingsDefaults.GestureEnabled
import kotlinx.serialization.Serializable

/** 手势总开关（首次启动引导用）。 */
@Serializable
@Keep
data class InitialSettings(
    val gestureEnabled: Boolean = GestureEnabled,
    /** 识屏长按手势：全屏任意位置按住 → 幽灵卡。默认关闭（需 Shizuku 读内核触摸流）。 */
    val screenPickGesture: ScreenPickGesture = ScreenPickGesture(),
    /** 触钮可见性场景：按应用 / 界面派生显隐，不写回按钮 enabled。 */
    val buttonVisibility: ButtonVisibilitySettings = ButtonVisibilitySettings(),
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