@file:Suppress("ConstPropertyName")

package hunoia.luno.config.defaults

object AdvancedSettingsDefaults {

    /**
     * 小窗默认布局（竖屏），约 16:10，真机实测值（±0.5%）。
     * MIUI13+/澎湃 按 freeformScale(0.7) 缩放 freeform 窗口，而 WindowManager 帧仍是
     * 请求的 bounds（`frame = 屏宽 × width × 1.42`，x 不参与补偿），改值需真机验证。
     */
    const val MiniWindowX = 0.11f
    const val MiniWindowY = 0.36f
    const val MiniWindowWidth = 0.77f
    const val MiniWindowHeight = 0.54f
    const val KeepAliveEnabled = true

    /** 横屏那套。约 16:10 的竖长小窗，靠左。横屏 freeformScale 为 0.56（竖屏 0.7），同样乘 1/0.56 抵消。 */
    const val MiniWindowLandscapeX = 0.05f
    const val MiniWindowLandscapeY = 0.11f
    const val MiniWindowLandscapeWidth = 0.24f
    const val MiniWindowLandscapeHeight = 0.79f
}

object GestureSettingsDefaults {

    const val SubGestureTimeoutMs = 5000L
    /** 动作面板内按住多久进入长按变体（ms）；可调，这只是默认值。 */
    const val ActionPanelLongPressMs = 500L
    /** 可调区间：低于 200ms 几乎一碰就是长按，高于 1200ms 形同不可用。 */
    const val MinActionPanelLongPressMs = 200L
    const val MaxActionPanelLongPressMs = 1200L
    const val PointerSensitivityX = 1.6f
    const val PointerSensitivityY = 1.6f
    const val PointerAcceleration = 0.8f
    const val PointerFollowTrigger = true
    const val PointerInitialXRatio = 0.5f
    const val PointerInitialYRatio = 0.35f
    /** 跟随偏移以「左下分区」为基准、按触发点半屏翻转；默认略高于手指。 */
    const val PointerFollowOffsetXRatio = 0f
    const val PointerFollowOffsetYRatio = -0.10f
    const val PointerFollowOffsetMinRatio = -1f
    const val PointerFollowOffsetMaxRatio = 1f
    const val PointerCursorSizeDp = 28
    const val PointerCursorAlpha = 0.9f
}

object InitialSettingsDefaults {

    const val GestureEnabled = true
}

object ActionSettingsDefaults {

    const val HideGestureButtonDelayMs = 1000L
    const val VolumeScrubStepThresholdDp = 14

    /** 最近应用滚轮：列表外滑动灵敏度（百分比，100 = 1.0x）。 */
    const val RecentsPickerSensitivityPercent = 100
    /** 最近应用滚轮：图标边长 dp。 */
    const val RecentsPickerIconSizeDp = 60
}
