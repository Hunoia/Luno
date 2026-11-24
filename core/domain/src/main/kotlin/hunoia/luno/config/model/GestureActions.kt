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
    /** 识屏自身参数（OCR 文字体系）；触发方式在识别区配置里。 */
    val screenPick: ScreenPickSettings = ScreenPickSettings(),
    /** 触钮可见性场景：按应用 / 界面派生显隐，不写回按钮 enabled。 */
    val buttonVisibility: ButtonVisibilitySettings = ButtonVisibilitySettings(),
)

/** 触钮滑动指示条的绘制形态。 */
@Serializable
@Keep
enum class SwipeIndicatorStyle { Pill, Droplet, Wave, Glow, Ripple }

/**
 * 触钮滑动指示条（独立于预设动效的触摸反馈）。
 *
 * 观感是「屏幕边被手指拉出来的流体」：所有样式共用同一套几何量，只有剖面不同。
 *
 * - 边 = 触钮所在边，DOWN 时固定；底座夹在安全区内；
 * - 滑过该触钮的 `slideTriggerDistance` 才淡入（轻点不闪）；
 * - 凸起量由 **DOWN 点 → 手指的总位移** [maxDragDp] 驱动，沿边滑动也会持续鼓起来；
 * - 沿边跨度拖尾覆盖 DOWN 点与当前点，快速滑动时像边被扯着走；
 * - 抬手只淡出，不做回抽抖动；
 * - 默认跟随触钮颜色；关闭跟随后用 [color]（未设置 = 主题主色）；
 * - [style] 只换剖面 / 内部纹理；[sizePercent] 缩放整体，[strokeWidthDp] 选择是否描边。
 */
@Serializable
@Keep
data class SwipeIndicator(
    val enabled: Boolean = GestureSettingsDefaults.SwipeIndicatorEnabled,
    val style: SwipeIndicatorStyle = SwipeIndicatorStyle.Pill,
    val sizePercent: Int = GestureSettingsDefaults.SwipeIndicatorSizePercent,
    val strokeWidthDp: Int = GestureSettingsDefaults.SwipeIndicatorStrokeMinDp,
    val strokeColor: ColorSpec = ColorSpec.Unset,
    val maxDragDp: Int = GestureSettingsDefaults.SwipeIndicatorMaxDragDp,
    val followButtonColor: Boolean = true,
    val color: ColorSpec = ColorSpec.Unset,
) {
    val effectiveSizePercent: Int
        get() = sizePercent.coerceIn(
            GestureSettingsDefaults.SwipeIndicatorSizeMinPercent,
            GestureSettingsDefaults.SwipeIndicatorSizeMaxPercent,
        )

    val effectiveStrokeWidthDp: Int
        get() = strokeWidthDp.coerceIn(
            GestureSettingsDefaults.SwipeIndicatorStrokeMinDp,
            GestureSettingsDefaults.SwipeIndicatorStrokeMaxDp,
        )

    val effectiveMaxDragDp: Int
        get() = maxDragDp.coerceIn(
            GestureSettingsDefaults.SwipeIndicatorMaxDragMinDp,
            GestureSettingsDefaults.SwipeIndicatorMaxDragMaxDp,
        )
}

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
    val swipeIndicator: SwipeIndicator = SwipeIndicator(),
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