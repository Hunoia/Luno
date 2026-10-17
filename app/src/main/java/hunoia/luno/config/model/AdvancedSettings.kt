package hunoia.luno.config.model

import androidx.annotation.Keep
import hunoia.luno.config.defaults.AdvancedSettingsDefaults.KeepAliveEnabled
import kotlinx.serialization.Serializable

@Serializable
@Keep
data class AdvancedSettings(
    /** 小窗位置与大小，竖屏横屏各一套。 */
    val miniWindow: MiniWindowSettings? = null,
    val keepAliveEnabled: Boolean = KeepAliveEnabled,
)