@file:Suppress("ConstPropertyName")

package hunoia.luno.config.defaults

object AdvancedSettingsDefaults {

    const val MiniWindowHorizontalBias = 0.13586f
    const val MiniWindowVerticalBias = 0.25574f
    const val MiniWindowWidthFraction = 1.0f
    const val MiniWindowHeightFraction = 0.69935f
    const val MiniWindowOverrideBounds = false
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
