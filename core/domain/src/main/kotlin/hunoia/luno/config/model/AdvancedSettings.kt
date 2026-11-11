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
    /** 是否把 Luno 主页面 task 从最近任务隐藏；图片标注等独立页面不受影响。 */
    val hideLunoFromRecents: Boolean = true,
    /** 设置列表视觉风格：分段式（默认）/ 大卡分割线。 */
    val listStyle: ListStyle = ListStyle.Segmented,
    /** 动效档位：面板 / 浮层 / 浮窗各自独立（ui-guidelines §5.1）。 */
    val motion: MotionTuningSettings = MotionTuningSettings(),
    /** 通用通知显示设置（呈现模式 + 覆盖层，见 [NotificationDisplaySettings]）。 */
    val notificationDisplay: NotificationDisplaySettings = NotificationDisplaySettings(),
)
