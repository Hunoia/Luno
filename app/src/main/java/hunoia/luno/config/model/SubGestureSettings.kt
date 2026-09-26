package hunoia.luno.config.model

import androidx.annotation.Keep
import kotlinx.serialization.Serializable

@Serializable
@Keep
data class SubGestureSettings(
    val subGestures: List<SubGesture> = emptyList()
)
