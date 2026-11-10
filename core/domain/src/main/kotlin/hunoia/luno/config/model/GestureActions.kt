package hunoia.luno.config.model

import androidx.annotation.Keep
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
    /**
     * 动作面板内按住多久结算为长按变体（ms）。
     * 也是「长按变体是面板时不必等抬手」的点火时间（见 `ActionPanelState`）。
     */
    val actionPanelLongPressMs: Long = GestureSettingsDefaults.ActionPanelLongPressMs,
    val pointer: Pointer = Pointer(),
) {
    /**
     * 指针（光标）手感与外观：移动映射（灵敏度/加速）、起始位置与绘制（大小/透明度）。
     *
     * - [initialXRatio]/[initialYRatio]：不跟随时的初始落点，屏幕归一化比例。
     * - [followTrigger]：有触发点时从触点附近出现；抬手触发的入口用 overlay 首次点击。
     * - [followOffsetXRatio]/[followOffsetYRatio]：显示位置相对触发点的有符号偏移，
     *   以「左下分区」为存储基准，运行时按触发点所在半屏翻转对应轴（见 `followPoint`）。
     *   默认 (0, −10%)：下方触发时在手指上方，上方触发镜像到手指下方。
     */
    @Serializable
    @Keep
    data class Pointer(
        val sensitivityX: Float = GestureSettingsDefaults.PointerSensitivityX,
        val sensitivityY: Float = GestureSettingsDefaults.PointerSensitivityY,
        val acceleration: Float = GestureSettingsDefaults.PointerAcceleration,
        val followTrigger: Boolean = GestureSettingsDefaults.PointerFollowTrigger,
        val initialXRatio: Float = GestureSettingsDefaults.PointerInitialXRatio,
        val initialYRatio: Float = GestureSettingsDefaults.PointerInitialYRatio,
        val followOffsetXRatio: Float = GestureSettingsDefaults.PointerFollowOffsetXRatio,
        val followOffsetYRatio: Float = GestureSettingsDefaults.PointerFollowOffsetYRatio,
        val cursorSizeDp: Int = GestureSettingsDefaults.PointerCursorSizeDp,
        val cursorAlpha: Float = GestureSettingsDefaults.PointerCursorAlpha,
    )
}