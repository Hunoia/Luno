package hunoia.luno.config.model

import androidx.annotation.Keep
import hunoia.luno.config.defaults.InitialSettingsDefaults.GestureEnabled
import hunoia.luno.config.defaults.InitialSettingsDefaults.Unlocked
import kotlinx.serialization.Serializable

@Serializable
@Keep
data class InitialSettings(
    val gestureEnabled: Boolean = GestureEnabled,
    val unlocked: Boolean = Unlocked
)
