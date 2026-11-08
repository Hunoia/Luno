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
    /** 习惯提醒的呈现方式：标准通知 / 实时活动 / 小米超级岛（全局，默认标准）。 */
    val habitReminderStyle: HabitNotificationStyle = HabitNotificationStyle.DEFAULT,
)
