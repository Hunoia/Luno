@file:Suppress("ConstPropertyName")

package hunoia.luno.config.defaults

object AdvancedSettingsDefaults {

    const val MiniWindowHorizontalBias = 0f
    const val MiniWindowVerticalBias = 0f
    const val MiniWindowWidthFraction = 0.85f
    const val MiniWindowHeightFraction = 0.72f
    const val MiniWindowOverrideBounds = false
    const val KeepAliveEnabled = false
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
