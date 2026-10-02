package hunoia.luno.config.model

import androidx.annotation.Keep
import hunoia.luno.config.defaults.ActionSettingsDefaults.PasswordDefaultLength
import hunoia.luno.config.defaults.ActionSettingsDefaults.PasswordDigitsEnabled
import hunoia.luno.config.defaults.ActionSettingsDefaults.PasswordLowercaseEnabled
import hunoia.luno.config.defaults.ActionSettingsDefaults.PasswordSymbolsEnabled
import hunoia.luno.config.defaults.ActionSettingsDefaults.PasswordUppercaseEnabled
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
