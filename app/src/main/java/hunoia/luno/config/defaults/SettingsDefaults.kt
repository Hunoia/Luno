@file:Suppress("ConstPropertyName")

package hunoia.luno.config.defaults

object AdvancedSettingsDefaults {

    /**
     * 小窗默认布局，竖屏横屏各一套，约 16:10。
     *
     * 这组值取代了更早的 0.15/0.14/1.0/0.72（满屏宽）。那组是 `eee82bc` 定的，
     * 早于 `ced7c5f` 引入的 MIUI 1.42 补偿：MIUI13+/澎湃 按 freeformScale(0.7) 缩放
     * freeform 窗口，而 WindowManager 帧仍是请求的 bounds，于是
     * `frame = 屏宽 × width × 1.42` 溢出屏幕 42%（`x` 不参与补偿，位置也没抵消）。
     * 现在这组取自真机上验过能用的读数（精度 ±0.5%）。
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
    const val VolumeScrubHorizontalEnabled = true
}
