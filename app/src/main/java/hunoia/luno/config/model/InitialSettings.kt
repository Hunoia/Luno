package hunoia.luno.config.model

import androidx.annotation.Keep
import hunoia.luno.config.defaults.InitialSettingsDefaults.GestureEnabled
import kotlinx.serialization.Serializable

@Serializable
@Keep
data class InitialSettings(
    val gestureEnabled: Boolean = GestureEnabled,
)
