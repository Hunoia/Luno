package hunoia.luno.config.model

import androidx.annotation.Keep
import hunoia.luno.config.defaults.ActionSettingsDefaults.VolumeScrubHorizontalEnabled
import hunoia.luno.config.defaults.ActionSettingsDefaults.VolumeScrubStepThresholdDp
import kotlinx.serialization.Serializable

@Serializable
@Keep
data class VolumeScrubConfig(
    val horizontalEnabled: Boolean = VolumeScrubHorizontalEnabled,
    val stepThresholdDp: Int = VolumeScrubStepThresholdDp,
)
