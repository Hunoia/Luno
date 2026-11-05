package hunoia.luno.gesture.settings

import androidx.annotation.Keep
import kotlinx.serialization.Serializable

/** 子手势编辑页路由参数。 */
@Keep
@Serializable
data class SubGestureEditor(
    val subGestureId: String,
)
