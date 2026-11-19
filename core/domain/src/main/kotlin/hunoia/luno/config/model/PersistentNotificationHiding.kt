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
 * 是否应隐藏这条通知。只处理常驻通知；Luno 自身按保活通知 id 精确命中（[KeepAliveNotificationIds]），
 * 其余 Luno 通知（习惯 / 待办 / 趋势 / 识别提醒）一律不隐藏。
 */
fun PersistentNotificationHidingSettings.shouldHide(
    ownPackage: String,
    packageName: String,
    notificationId: Int,
    tag: String?,
    isPersistent: Boolean,
): Boolean {
    if (!enabled || !isPersistent) return false
    if (hideLunoNotifications &&
        isSameApplicationVariant(packageName, ownPackage) &&
        notificationId in KeepAliveNotificationIds
    ) {
        return true
    }
    return hiddenTargets.any { target ->
        target.packageName == packageName &&
            target.notificationId == notificationId &&
            target.tag == tag
    }
}

/**
 * 两个包名是否同一应用的构建变体（如 `hunoia.luno` 与 `hunoia.luno.debug`）。
 *
 * 按 applicationId 前缀 + 点判：`.debug` 这类 suffix 变体算同应用，`hunoia.lunoX` 不算。
 * 用于通知监听把「同应用另一构建的通知」也当自家通知，避免双包（release / debug）并存时
 * 互相进胶囊、被「隐藏系统通知」取消。
 */
fun isSameApplicationVariant(a: String, b: String): Boolean =
    a == b || a.startsWith("$b.") || b.startsWith("$a.")

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
