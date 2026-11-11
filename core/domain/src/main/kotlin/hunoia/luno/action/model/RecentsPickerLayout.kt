package hunoia.luno.action.model

import androidx.annotation.Keep
import kotlinx.serialization.Serializable

/** 最近应用滚动选择器的滚轮排列方向：竖向（上下滑）或横向（左右滑）。 */
@Serializable
@Keep
enum class RecentsPickerLayout { VERTICAL, HORIZONTAL }
