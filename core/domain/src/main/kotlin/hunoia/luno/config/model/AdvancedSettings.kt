package hunoia.luno.config.model

import androidx.annotation.Keep
import hunoia.luno.config.defaults.AdvancedSettingsDefaults.KeepAliveEnabled
import kotlinx.serialization.Serializable

@Serializable
@Keep
data class AdvancedSettings(
    val miniWindow: MiniWindowSettings? = null,
    val keepAliveEnabled: Boolean = KeepAliveEnabled,
    /** 识屏产物是否落系统相册；关闭时只在缓存目录留一份供分享。 */
    val screenPickSaveToGallery: Boolean = false,
    /** 固定通知隐藏：常驻 / 前台服务通知从通知栏隐藏。 */
    val persistentNotificationHiding: PersistentNotificationHidingSettings =
        PersistentNotificationHidingSettings(),
)
