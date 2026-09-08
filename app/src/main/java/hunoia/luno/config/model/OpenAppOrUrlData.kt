package hunoia.luno.config.model

import androidx.annotation.Keep
import kotlinx.serialization.Serializable

@Serializable
@Keep
data class OpenAppOrUrlData(
    val type: Int = TYPE_ACTIVITY,
    val packageName: String = "",
    val activityClassName: String = "",
    val url: String = "",
    val miniWindow: Boolean = false,
    val queryParameters: List<OpenUrlQueryParameter> = emptyList(),
) {
    companion object {
        const val TYPE_ACTIVITY = 0
        const val TYPE_URL = 1
    }
}

@Serializable
@Keep
data class OpenUrlQueryParameter(
    val name: String = "",
    val value: String = "",
    val enabled: Boolean = true,
)
