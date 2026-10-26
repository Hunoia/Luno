package hunoia.luno.runtime.automation

import android.app.Notification
import android.app.NotificationManager
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import hunoia.luno.runtime.condition.NotificationPulse

/**
 * 自动化通知触发源：取包名/标题/正文做关键字匹配（匹配完原文即丢，执行历史不存原文），
 * 附带 contentIntent 供通知胶囊点击直达。
 * 常驻通知（ongoing）、自家通知与同 key 重投去重，不发脉冲。
 */
class AutomationNotificationListener : NotificationListenerService() {

    /**
     * 同 key 重投去重的窗口。以前只记「上一个 key + 时间」，两个应用交替发通知时
     * 每次 key 都在变，去重直接失效 —— 每条通知都要走一遍脉冲 + 全量规则求值。
     */
    private val recentPosts = object : LinkedHashMap<String, Long>(8, 0.75f, true) {
        override fun removeEldestEntry(
            eldest: MutableMap.MutableEntry<String, Long>?,
        ): Boolean = size > RECENT_KEYS
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        val notification = sbn?.notification ?: return
        if ((notification.flags and Notification.FLAG_ONGOING_EVENT) != 0) return
        // 自家通知（前台服务常驻等）不进胶囊也不进自动化。
        if (sbn.packageName == packageName) return
        val key = sbn.key ?: return
        // 同 key 在窗口内重投视为更新去重（多 key 各自计时，不再互相冲掉）。
        val postedAt = sbn.postTime
        synchronized(recentPosts) {
            val last = recentPosts[key]
            if (last != null && postedAt - last in 0L until DEDUPE_WINDOW_MS) return
            recentPosts[key] = postedAt
        }

        val extras = sbn.notification.extras ?: return
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()
            ?: extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()
            ?: ""
        if (title.isBlank() && text.isBlank()) return
        // importance 只在胶囊层用（自动化语义不变）：MIN 在状态栏不显示、NONE 完全不出。
        val ranking = Ranking()
        val hasRanking = runCatching { currentRanking?.getRanking(key, ranking) }
            .getOrNull() ?: false
        AutomationPulseHub.fireNotification(
            NotificationPulse(
                packageName = sbn.packageName.orEmpty(),
                title = title,
                text = text,
                contentIntent = notification.contentIntent,
                flags = notification.flags,
                ongoing = sbn.isOngoing,
                clearable = sbn.isClearable,
                importance = if (hasRanking) {
                    ranking.importance
                } else {
                    NotificationManager.IMPORTANCE_DEFAULT
                },
                key = key,
            )
        )
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
    }

    override fun onDestroy() {
        if (instance === this) instance = null
        super.onDestroy()
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        // 移除不发脉冲：v2 只有"到达"语义。
    }

    override fun onInterruptionFilterChanged(interruptionFilter: Int) {
        AutomationPulseHub.fireInterruption(
            interruptionFilter != NotificationManager.INTERRUPTION_FILTER_ALL
        )
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        // 连接时补一次基线，避免授权后到首次切换之间状态缺失。
        val filter = runCatching { currentInterruptionFilter }.getOrNull()
            ?: NotificationManager.INTERRUPTION_FILTER_ALL
        AutomationPulseHub.fireInterruption(
            filter != NotificationManager.INTERRUPTION_FILTER_ALL
        )
    }

    companion object {
        /** 当前连接中的监听器实例：胶囊用它确认通知在延迟后仍留在通知栏。 */
        @Volatile
        private var instance: AutomationNotificationListener? = null

        /** [key] 对应的通知现在仍在通知栏；实例未连接时保守返回 true（不拦自动化/胶囊）。 */
        fun isPosted(key: String?): Boolean {
            if (key == null) return true
            val live = instance ?: return true
            return runCatching {
                live.activeNotifications?.any { it.key == key } == true
            }.getOrDefault(true)
        }

        /** 同 key 重投的去重窗口（ms）。 */
        private const val DEDUPE_WINDOW_MS = 2_000L

        /** 同时跟踪的 key 数上限：够覆盖「几个应用交替推送」的场景，且不会无限增长。 */
        private const val RECENT_KEYS = 16

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
