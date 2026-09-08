package hunoia.luno.quicklaunch.model

import androidx.annotation.Keep
import kotlinx.serialization.Serializable

@Serializable
@Keep
data class AppInfo(
    val packageName: String,
    val className: String,
    val label: String,
    val miniWindow: Boolean = false,
)
