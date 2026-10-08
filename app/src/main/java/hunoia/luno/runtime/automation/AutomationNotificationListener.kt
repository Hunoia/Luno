package hunoia.luno.runtime.automation

import android.app.Notification
import android.app.NotificationManager
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import hunoia.luno.runtime.condition.NotificationPulse

/**
 * 自动化通知触发源：只取包名/标题/正文做关键字匹配，匹配完原文即丢；
 * 执行历史不存原文。
 * 常驻通知（ongoing）与同 key 重投去重，不发脉冲。
 */
class AutomationNotificationListener : NotificationListenerService() {

    private var lastKey: String? = null
    private var lastPostMs: Long = 0L

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        val notification = sbn?.notification ?: return
        if ((notification.flags and Notification.FLAG_ONGOING_EVENT) != 0) return
        val key = sbn.key ?: return
        // 同 key 2s 内重投视为更新去重。
        if (key == lastKey && sbn.postTime - lastPostMs < 2_000L) return
        lastKey = key
        lastPostMs = sbn.postTime

        val extras = sbn.notification.extras ?: return
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()
            ?: extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()
            ?: sbn.notification.tickerText?.toString().orEmpty()
        if (title.isBlank() && text.isBlank()) return
        AutomationPulseHub.onNotification?.invoke(
            NotificationPulse(
                packageName = sbn.packageName.orEmpty(),
                title = title,
                text = text,
            )
        )
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        // 移除不发脉冲：v2 只有"到达"语义。
    }

    override fun onInterruptionFilterChanged(interruptionFilter: Int) {
        AutomationPulseHub.onInterruption?.invoke(
            interruptionFilter != NotificationManager.INTERRUPTION_FILTER_ALL
        )
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        // 连接时补一次基线，避免授权后到首次切换之间状态缺失。
        val filter = runCatching { currentInterruptionFilter }.getOrNull()
            ?: NotificationManager.INTERRUPTION_FILTER_ALL
        AutomationPulseHub.onInterruption?.invoke(
            filter != NotificationManager.INTERRUPTION_FILTER_ALL
        )
    }

    companion object {
        /** 用户是否在系统设置里授予了通知访问权限（P3 引导页用同一判断）。 */
        fun isAccessGranted(context: android.content.Context): Boolean {
            val flat = runCatching {
                Settings.Secure.getString(
                    context.contentResolver,
                    "enabled_notification_listeners",
                )
            }.getOrNull().orEmpty()
            if (flat.isBlank()) return false
            return flat.split(":").any { component ->
                component.split("/").firstOrNull() == context.packageName
            }
        }
    }
}
