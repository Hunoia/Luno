@file:Suppress("ConstPropertyName")

package hunoia.luno.config.defaults

object AdvancedSettingsDefaults {

    /** 小窗默认布局：宽=屏宽、高=屏宽/0.625(16:10)、左=15%屏宽、垂直居中(以 0.45 宽高比近似)。 */
    const val MiniWindowX = 0.15f
    const val MiniWindowY = 0.14f
    const val MiniWindowWidth = 1.0f
    const val MiniWindowHeight = 0.72f
    const val KeepAliveEnabled = true
}

object GestureSettingsDefaults {

    const val SubGestureTimeoutMs = 5000L
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
