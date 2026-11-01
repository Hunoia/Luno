package hunoia.luno.runtime.floatball.notification

import android.app.Notification
import android.app.NotificationManager
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import hunoia.luno.core.domain.NotificationPulse
import hunoia.luno.runtime.notification.NotificationPulseHub

/**
 * 通知监听：供通知胶囊使用，取包名 / 标题 / 正文 / 标记，附带 contentIntent 供点击直达。
 *
 * 自家通知与同 key 重投去重不发；通知栏快照供胶囊确认「延迟后仍在通知栏」。
 * 不承担任何自动化条件语义。
 */
class NotificationPulseListener : NotificationListenerService() {

    /**
     * 同 key 重投去重的窗口。键值是「postTime + 内容指纹」：同一条通知原地更新
     * （新消息 / 新文案）不能被吞掉，但完全相同的重投要跳过。
     */
    private data class RecentPost(val postedAt: Long, val signature: String)

    private val recentPosts = object : LinkedHashMap<String, RecentPost>(8, 0.75f, true) {
        override fun removeEldestEntry(
            eldest: MutableMap.MutableEntry<String, RecentPost>?,
        ): Boolean = size > RECENT_KEYS
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        val pulse = sbn?.toPulse() ?: return
        // 自家通知（前台服务常驻等）不进胶囊。
        if (pulse.packageName == packageName) return
        updateActiveCache(add = pulse)
        val key = pulse.key
        // 无标题 / 正文的通知仍会改变「当前是否存在」，胶囊不弹但要刷新快照。
        if (key == null || (pulse.title.isBlank() && pulse.text.isBlank())) {
            NotificationPulseHub.fireStateChanged()
            return
        }
        val postedAt = sbn.postTime
        val signature = "${pulse.title}\u0000${pulse.text}"
        synchronized(recentPosts) {
            val last = recentPosts[key]
            if (last != null &&
                last.signature == signature &&
                postedAt - last.postedAt in 0L until DEDUPE_WINDOW_MS
            ) {
                return
            }
            recentPosts[key] = RecentPost(postedAt, signature)
        }
        NotificationPulseHub.firePosted(pulse)
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
        val pulse = sbn?.toPulse() ?: return
        if (pulse.packageName == packageName) return
        updateActiveCache(removeKey = pulse.key)
        NotificationContentIntents.forget(pulse.key)
        NotificationPulseHub.fireRemoved(pulse)
    }

    override fun onListenerDisconnected() {
        synchronized(activeLock) { activeCache = emptyList() }
        NotificationPulseHub.fireStateChanged()
        super.onListenerDisconnected()
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        refreshActiveCache()
        NotificationPulseHub.fireStateChanged()
        val filter = runCatching { currentInterruptionFilter }.getOrNull()
            ?: NotificationManager.INTERRUPTION_FILTER_ALL
        NotificationPulseHub.fireInterruption(filter != NotificationManager.INTERRUPTION_FILTER_ALL)
    }

    override fun onInterruptionFilterChanged(interruptionFilter: Int) {
        NotificationPulseHub.fireInterruption(
            interruptionFilter != NotificationManager.INTERRUPTION_FILTER_ALL,
        )
    }

    private fun refreshActiveCache() {
        val current = runCatching { activeNotifications?.mapNotNull { it.toPulse() }.orEmpty() }
            .getOrDefault(emptyList())
            .filter { it.packageName != packageName }
        synchronized(activeLock) { activeCache = current }
    }

    private fun updateActiveCache(add: NotificationPulse? = null, removeKey: String? = null) {
        synchronized(activeLock) {
            var next = activeCache
            if (add != null) {
                next = next.filterNot { it.key != null && it.key == add.key } + add
            }
            if (removeKey != null) {
                next = next.filterNot { it.key == removeKey }
            }
            activeCache = next
        }
    }

    private fun StatusBarNotification.toPulse(): NotificationPulse {
        val extras = notification.extras
        val title = extras?.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val text = extras?.getCharSequence(Notification.EXTRA_TEXT)?.toString()
            ?: extras?.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()
            ?: ""
        val ranking = Ranking()
        val hasRanking = runCatching { currentRanking?.getRanking(key, ranking) }
            .getOrNull() ?: false
        return NotificationPulse(
            packageName = packageName.orEmpty(),
            title = title,
            text = text,
            flags = notification.flags,
            ongoing = isOngoing,
            clearable = isClearable,
            importance = if (hasRanking) ranking.importance else NotificationPulse.DEFAULT_IMPORTANCE,
            key = key,
            groupKey = groupKey,
            channelId = notification.channelId,
            category = notification.category,
        ).also { pulse ->
            NotificationContentIntents.remember(pulse.key, notification.contentIntent)
        }
    }

    companion object {
        private val activeLock = Any()

        /** 通知栏当前快照，只在监听器已连接时有值。 */
        @Volatile
        private var activeCache: List<NotificationPulse> = emptyList()

        fun activePulses(): List<NotificationPulse> = synchronized(activeLock) {
            activeCache.toList()
        }

        /** 当前连接中的监听器实例：胶囊用它确认通知在延迟后仍留在通知栏。 */
        @Volatile
        private var instance: NotificationPulseListener? = null

        /** [key] 对应的通知现在仍在通知栏；实例未连接时保守返回 true。 */
        fun isPosted(key: String?): Boolean {
            if (key == null) return true
            val live = instance ?: return true
            return runCatching {
                live.activeNotifications?.any { it.key == key } == true
            }.getOrDefault(true)
        }

        /** 同 key 重投的去重窗口（ms）。 */
        private const val DEDUPE_WINDOW_MS = 2_000L

        /** 同时跟踪的 key 数上限。 */
        private const val RECENT_KEYS = 16
    }
}
