package hunoia.luno.core.domain

/**
 * 通知脉冲：包名+标题+正文。
 * `PendingIntent` 不进契约层；通知胶囊点击时按 [key] 从 runtime 侧取。
 */
data class NotificationPulse(
    val packageName: String,
    val title: String,
    val text: String,
    /** 原始 android.app.Notification.flags。 */
    val flags: Int = 0,
    val ongoing: Boolean = false,
    val clearable: Boolean = true,
    /** 默认 NotificationManager.IMPORTANCE_DEFAULT = 3（契约层不依赖 Android）。 */
    val importance: Int = DEFAULT_IMPORTANCE,
    val key: String? = null,
    val groupKey: String? = null,
    val channelId: String? = null,
    val category: String? = null,
) {
    companion object {
        const val DEFAULT_IMPORTANCE = 3
    }
}
