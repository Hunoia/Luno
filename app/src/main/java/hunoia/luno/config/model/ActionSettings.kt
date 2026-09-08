package hunoia.luno.config.model

import androidx.annotation.Keep
import hunoia.luno.config.defaults.ActionSettingsDefaults.HideGestureButtonDelayMs
import hunoia.luno.config.defaults.ActionSettingsDefaults.VolumeScrubHorizontalEnabled
import hunoia.luno.config.defaults.ActionSettingsDefaults.VolumeScrubStepThresholdDp
import hunoia.luno.config.defaults.ActionSettingsDefaults.PasswordDefaultLength
import hunoia.luno.config.defaults.ActionSettingsDefaults.PasswordDigitsEnabled
import hunoia.luno.config.defaults.ActionSettingsDefaults.PasswordLowercaseEnabled
import hunoia.luno.config.defaults.ActionSettingsDefaults.PasswordSymbolsEnabled
import hunoia.luno.config.defaults.ActionSettingsDefaults.PasswordUppercaseEnabled
import kotlinx.serialization.Serializable

@Serializable
@Keep
data class ActionSettings(
    val previousApp: PreviousApp = PreviousApp(),
    val passwordGenerator: PasswordGenerator = PasswordGenerator(),
    val hideGestureButton: HideGestureButton = HideGestureButton(),
    val volumeScrub: VolumeScrub = VolumeScrub()
) {
    @Serializable
    @Keep
    data class PreviousApp(val packageNames: List<String> = emptyList())

    @Serializable
    @Keep
    data class HideGestureButton(
        val delayMs: Long = HideGestureButtonDelayMs
    )

    @Serializable
    @Keep
    data class VolumeScrub(
        val horizontalEnabled: Boolean = VolumeScrubHorizontalEnabled,
        val stepThresholdDp: Int = VolumeScrubStepThresholdDp,
    )

    @Serializable
    @Keep
    data class PasswordGenerator(
        val length: Int = PasswordDefaultLength,
        val lowercase: Boolean = PasswordLowercaseEnabled,
        val uppercase: Boolean = PasswordUppercaseEnabled,
        val digits: Boolean = PasswordDigitsEnabled,
        val symbols: Boolean = PasswordSymbolsEnabled
    )
}
