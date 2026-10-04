package hunoia.luno.config.model

import androidx.annotation.Keep
import hunoia.luno.config.defaults.ActionSettingsDefaults.PasswordDefaultLength
import hunoia.luno.config.defaults.ActionSettingsDefaults.PasswordDigitsEnabled
import hunoia.luno.config.defaults.ActionSettingsDefaults.PasswordLowercaseEnabled
import hunoia.luno.config.defaults.ActionSettingsDefaults.PasswordSymbolsEnabled
import hunoia.luno.config.defaults.ActionSettingsDefaults.PasswordUppercaseEnabled
import hunoia.luno.config.defaults.ActionSettingsDefaults.VolumeScrubHorizontalEnabled
import hunoia.luno.config.defaults.ActionSettingsDefaults.VolumeScrubStepThresholdDp
import kotlinx.serialization.Serializable

@Serializable
@Keep
data class ActionSettings(
    val passwordGenerator: PasswordGenerator = PasswordGenerator()
) {
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

/** 音量 scrub 手势配置。 */
@Serializable
@Keep
data class VolumeScrubConfig(
    val horizontalEnabled: Boolean = VolumeScrubHorizontalEnabled,
    val stepThresholdDp: Int = VolumeScrubStepThresholdDp,
)

/** 自定义 shell 命令动作数据。 */
@Serializable
@Keep
data class ShellCommandData(
    val command: String = "",
    val showToast: Boolean = true,
)
