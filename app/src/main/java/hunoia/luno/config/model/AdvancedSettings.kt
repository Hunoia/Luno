package hunoia.luno.config.model

import androidx.annotation.Keep
import hunoia.luno.config.defaults.AdvancedSettingsDefaults.MiniWindowX
import hunoia.luno.config.defaults.AdvancedSettingsDefaults.MiniWindowY
import hunoia.luno.config.defaults.AdvancedSettingsDefaults.MiniWindowWidth
import hunoia.luno.config.defaults.AdvancedSettingsDefaults.MiniWindowHeight
import hunoia.luno.config.defaults.AdvancedSettingsDefaults.KeepAliveEnabled
import kotlinx.serialization.Serializable

@Serializable
@Keep
data class AdvancedSettings(
    val miniWindowX: Float = MiniWindowX,
    val miniWindowY: Float = MiniWindowY,
    val miniWindowWidth: Float = MiniWindowWidth,
    val miniWindowHeight: Float = MiniWindowHeight,
    val keepAliveEnabled: Boolean = KeepAliveEnabled,
)
