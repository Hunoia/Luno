package hunoia.luno.gesture.settings

import androidx.annotation.Keep
import kotlinx.serialization.Serializable

/** 手势按钮设置页的路由参数（页面已在本模块，路由类型跟着走）。 */
@Serializable
@Keep
data class GestureButtonSettings(
    val buttonId: String,
)
