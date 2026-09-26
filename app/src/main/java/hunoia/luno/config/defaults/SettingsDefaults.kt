@file:Suppress("ConstPropertyName")

package hunoia.luno.config.defaults

import hunoia.luno.config.model.ActionPanelStyles

object AdvancedSettingsDefaults {

    val ExcludeApps = emptyList<String>()
    val ActionPanelStyles = ActionPanelStyles()
    const val MiniWindowHorizontalBias = 0f
    const val MiniWindowVerticalBias = 0f
    const val MiniWindowVerticalOffsetFraction = 0f
    const val MiniWindowWidthFraction = 0.85f
    const val MiniWindowHeightFraction = 0.72f
    const val MiniWindowOverrideBounds = false
    const val KeepAliveEnabled = false
    val ClipApps = emptyMap<String, Float>()
    val ClipShortcuts = emptyMap<String, Float>()
}

object GestureSettingsDefaults {

    const val SubGestureTimeoutMs = 5000L
    const val PointerSensitivityX = 1.6f
    const val PointerSensitivityY = 1.6f
    const val PointerAcceleration = 0.8f
    const val PointerInitialYRatio = 0.35f
    const val PointerEdgeCancelThresholdDp = 24
    const val PointerContinuousMode = false
    const val PointerContinuousModeTimeoutMs = 5000L
    const val PointerCursorSizeDp = 28
    const val PointerCursorAlpha = 0.9f
    const val PointerMovementDeadZoneDp = 3
}

object InitialSettingsDefaults {

    const val GestureEnabled = true
    const val Unlocked = false
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
    const val VolumeScrubStepThresholdDp = 18
    const val VolumeScrubHorizontalEnabled = false
}
