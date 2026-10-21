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

    /** 横屏那套。约 16:10 的竖长小窗，靠左。 */
    const val MiniWindowLandscapeX = 0.05f
    const val MiniWindowLandscapeY = 0.11f
    const val MiniWindowLandscapeWidth = 0.24f
    const val MiniWindowLandscapeHeight = 0.79f
}

object GestureSettingsDefaults {

    const val SubGestureTimeoutMs = 5000L
    const val PointerSensitivityX = 1.6f
    const val PointerSensitivityY = 1.6f
    const val PointerAcceleration = 0.8f
    const val PointerInitialYRatio = 0.35f
    const val PointerCursorSizeDp = 28
    const val PointerCursorAlpha = 0.9f
}

object InitialSettingsDefaults {

    const val GestureEnabled = true
}

object ActionSettingsDefaults {

    const val PasswordMinLength = 4
    const val PasswordMaxLength = 32
    const val PasswordDefaultLength = 16
    const val PasswordLowercaseEnabled = true
    const val PasswordUppercaseEnabled = true
    const val PasswordDigitsEnabled = true
    const val PasswordSymbolsEnabled = true
    const val HideGestureButtonDelayMs = 1000L
    const val VolumeScrubStepThresholdDp = 14
}
