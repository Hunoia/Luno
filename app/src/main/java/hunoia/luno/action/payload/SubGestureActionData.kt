package hunoia.luno.action.payload

import androidx.annotation.Keep
import kotlinx.serialization.Serializable

@Serializable
@Keep
data class SubGestureActionData(
    val id: String = ""
)
