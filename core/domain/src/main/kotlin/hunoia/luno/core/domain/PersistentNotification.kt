package hunoia.luno.core.domain

/**
 * 通知栏里的一条常驻通知快照。只用于设置页展示与目标选择，不携带 PendingIntent。
 */
data class PersistentNotificationEntry(
    val key: String,
    val packageName: String,
    val notificationId: Int,
    val tag: String?,
    val channelId: String?,
    val title: String,
    val text: String,
)
