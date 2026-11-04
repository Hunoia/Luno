package hunoia.luno.config.model

import androidx.annotation.Keep
import kotlinx.serialization.Serializable

/**
 * 固定通知隐藏配置。目标是单条常驻通知：包名 + 通知 id + tag。
 *
 * [channelId] 只用于列表展示，不参与匹配：同一个 id/tag 重投时 channel 不应变化，
 * 但即使变化也不该让用户重新勾选。
 */
@Serializable
@Keep
data class PersistentNotificationHidingSettings(
    val enabled: Boolean = true,
    val hideLunoNotifications: Boolean = true,
    val hiddenTargets: List<HiddenNotificationTarget> = emptyList(),
)

@Serializable
@Keep
data class HiddenNotificationTarget(
    val packageName: String,
    val notificationId: Int,
    val tag: String? = null,
    val channelId: String? = null,
)

/**
 * 是否应隐藏这条通知。只处理常驻通知；Luno 自身按包名统一命中两条前台服务通知。
 */
fun PersistentNotificationHidingSettings.shouldHide(
    ownPackage: String,
    packageName: String,
    notificationId: Int,
    tag: String?,
    isPersistent: Boolean,
): Boolean {
    if (!enabled || !isPersistent) return false
    if (hideLunoNotifications && packageName == ownPackage) return true
    return hiddenTargets.any { target ->
        target.packageName == packageName &&
            target.notificationId == notificationId &&
            target.tag == tag
    }
}

/** 列表行用：当前单条通知是否已被配置隐藏（不检查 enabled / 常驻，由调用方决定）。 */
fun PersistentNotificationHidingSettings.isTargetHidden(
    packageName: String,
    notificationId: Int,
    tag: String?,
): Boolean = hiddenTargets.any { target ->
    target.packageName == packageName &&
        target.notificationId == notificationId &&
        target.tag == tag
}
