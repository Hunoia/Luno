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
 * 常驻通知也发脉冲，是否过滤交给规则条件；自家通知与同 key 重投去重不发。
 */
class AutomationNotificationListener : NotificationListenerService() {

    /**
     * 同 key 重投去重的窗口。以前只记「上一个 key + 时间」，两个应用交替发通知时
     * 每次 key 都在变，去重直接失效 —— 每条通知都要走一遍脉冲 + 全量规则求值。
     * 键值是「postTime + 内容指纹」：同一条通知原地更新（新消息/新文案）不能再被吞掉。
     */
    private data class RecentPost(val postedAt: Long, val signature: String)

    private val recentPosts = object : LinkedHashMap<String, RecentPost>(8, 0.75f, true) {
        override fun removeEldestEntry(
            eldest: MutableMap.MutableEntry<String, RecentPost>?,
        ): Boolean = size > RECENT_KEYS
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        val pulse = sbn?.toPulse() ?: return
        // 自家通知（前台服务常驻等）不进胶囊也不进自动化。
        if (pulse.packageName == packageName) return
        updateActiveCache(add = pulse)
        val key = pulse.key ?: return
        // 常驻通知也要进入自动化：ongoing/clearable/importance 由条件过滤。
        if (pulse.title.isBlank() && pulse.text.isBlank()) return
        // 同 key 重投去重（多 key 各自计时，不再互相冲掉）。仅在「postTime 未推进 + 内容
        // 完全一致」时判重：部分 ROM 在通知原地更新时沿用旧 postTime，只按时间判重会把
        // 同一会话的第二条消息/新文案全部吞掉。
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
        AutomationPulseHub.fireNotification(pulse)
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
        AutomationPulseHub.fireNotificationRemoved(pulse)
    }

    override fun onListenerDisconnected() {
        synchronized(activeLock) { activeCache = emptyList() }
        AutomationPulseHub.fireNotificationStateChanged()
        super.onListenerDisconnected()
    }

    override fun onInterruptionFilterChanged(interruptionFilter: Int) {
        AutomationPulseHub.fireInterruption(
            interruptionFilter != NotificationManager.INTERRUPTION_FILTER_ALL
        )
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        refreshActiveCache()
        AutomationPulseHub.fireNotificationStateChanged()
        // 连接时补一次基线，避免授权后到首次切换之间状态缺失。
        val filter = runCatching { currentInterruptionFilter }.getOrNull()
            ?: NotificationManager.INTERRUPTION_FILTER_ALL
        AutomationPulseHub.fireInterruption(
            filter != NotificationManager.INTERRUPTION_FILTER_ALL
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
            contentIntent = notification.contentIntent,
            flags = notification.flags,
            ongoing = isOngoing,
            clearable = isClearable,
            importance = if (hasRanking) ranking.importance else NotificationManager.IMPORTANCE_DEFAULT,
            key = key,
            groupKey = groupKey,
            channelId = notification.channelId,
            category = notification.category,
        )
    }

    companion object {
        private val activeLock = Any()

        /** 通知栏当前快照（NOTIFICATION_EXISTS 状态条件用），只在监听器已连接时有值。 */
        @Volatile
        private var activeCache: List<NotificationPulse> = emptyList()

        fun activePulses(): List<NotificationPulse> = synchronized(activeLock) {
            activeCache.toList()
        }
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
