package hunoia.luno.runtime.floatball.notification

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import hunoia.luno.bridge.system.PersistentNotificationShell
import hunoia.luno.config.model.HABIT_REMINDER_CHANNEL_ID
import hunoia.luno.config.model.PersistentNotificationHidingSettings
import hunoia.luno.config.model.shouldHide
import hunoia.luno.core.domain.NotificationPulse
import hunoia.luno.domain.floatball.FloatBallSettingsCenter
import hunoia.luno.runtime.notification.NotificationPulseHub
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

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

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Volatile
    private var hiding = PersistentNotificationHidingSettings()

    @Volatile
    private var hidingLoaded = false

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        sbn ?: return
        // 自家通知（前台服务常驻等）不进胶囊，但命中隐藏配置时要续 snooze。
        if (sbn.packageName == packageName) {
            if (shouldHide(sbn)) snooze(sbn.key)
            return
        }
        val pulse = sbn.toPulse()
        if (shouldHide(sbn)) {
            snooze(sbn.key)
            return
        }
        updateActiveCache(add = pulse)
        val key = pulse.key
        // 无标题 / 正文的通知仍会改变「当前是否存在」，胶囊不弹。
        if (key == null || (pulse.title.isBlank() && pulse.text.isBlank())) {
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
        scope.launch {
            FloatBallSettingsCenter.advancedSettings.collect { advanced ->
                val next = advanced.persistentNotificationHiding
                val previous = hiding
                val first = !hidingLoaded
                if (!first && next == previous) return@collect
                hiding = next
                hidingLoaded = true
                if (first) reapplyHiding() else applyHidingChange(previous, next)
            }
        }
    }

    override fun onDestroy() {
        scope.cancel()
        if (instance === this) instance = null
        NotificationContentIntents.clear()
        super.onDestroy()
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        sbn ?: return
        if (sbn.packageName == packageName) return
        val pulse = sbn.toPulse()
        updateActiveCache(removeKey = pulse.key)
        NotificationContentIntents.forget(pulse.key)
        NotificationPulseHub.fireRemoved(pulse)
    }

    override fun onListenerDisconnected() {
        synchronized(activeLock) { activeCache = emptyList() }
        NotificationContentIntents.clear()
        super.onListenerDisconnected()
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        refreshActiveCache()
        reapplyHiding()
    }

    private suspend fun applyHidingChange(
        previous: PersistentNotificationHidingSettings,
        next: PersistentNotificationHidingSettings,
    ) {
        if (!next.enabled) {
            unsnooze(previous)
            return
        }
        if (previous.hideLunoNotifications && !next.hideLunoNotifications) {
            unsnoozeSnoozedOwn()
        }
        previous.hiddenTargets
            .filterNot { it in next.hiddenTargets }
            .forEach { unsnoozeSnoozedTarget(it) }
        reapplyHiding()
    }

    private fun reapplyHiding() {
        val config = hiding
        if (!config.enabled) return
        val active = runCatching { activeNotifications }.getOrNull() ?: return
        active.forEach { sbn ->
            if (shouldHide(sbn, config)) snooze(sbn.key)
        }
    }

    private suspend fun unsnooze(config: PersistentNotificationHidingSettings) {
        if (config.hideLunoNotifications) unsnoozeSnoozedOwn()
        config.hiddenTargets.forEach { unsnoozeSnoozedTarget(it) }
    }

    private suspend fun unsnoozeSnoozedOwn() {
        val keys = runCatching {
            snoozedNotifications
                ?.filter { it.packageName == packageName }
                ?.map { it.key }
        }.getOrNull().orEmpty()
        keys.forEach { unsnoozeKey(it) }
    }

    private suspend fun unsnoozeSnoozedTarget(
        target: hunoia.luno.config.model.HiddenNotificationTarget,
    ) {
        val key = runCatching {
            snoozedNotifications
                ?.firstOrNull { sbn ->
                    sbn.packageName == target.packageName &&
                        sbn.id == target.notificationId &&
                        sbn.tag == target.tag
                }
                ?.key
        }.getOrNull()
        if (key != null) unsnoozeKey(key)
    }

    /** Shizuku 不可用时用 1ms snooze 让系统自己把通知放回来。 */
    private suspend fun unsnoozeKey(key: String) {
        if (PersistentNotificationShell.unsnooze(key)) return
        runCatching { snoozeNotification(key, 1L) }
    }

    private fun shouldHide(
        sbn: StatusBarNotification,
        config: PersistentNotificationHidingSettings = hiding,
    ): Boolean {
        if (!hidingLoaded) return false
        // 习惯提醒是用户显式开启、面向用户的常驻通知，不属于「固定通知隐藏」范围；
        // 隐藏功能只该处理后台前台服务这类「系统运行痕迹」。
        if (sbn.packageName == packageName && sbn.notification.channelId == HABIT_REMINDER_CHANNEL_ID) {
            return false
        }
        val flags = sbn.notification.flags
        val persistent = sbn.isOngoing ||
            (flags and (Notification.FLAG_ONGOING_EVENT or
                Notification.FLAG_FOREGROUND_SERVICE or
                Notification.FLAG_NO_CLEAR)) != 0
        return config.shouldHide(
            ownPackage = packageName,
            packageName = sbn.packageName.orEmpty(),
            notificationId = sbn.id,
            tag = sbn.tag,
            isPersistent = persistent,
        )
    }

    private fun snooze(key: String) {
        runCatching { snoozeNotification(key, PersistentNotificationShell.SNOOZE_DURATION_MS) }
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

        /** 胶囊滑掉通知项时同步撤销系统通知；失败返回 false，调用方保留收件箱项。 */
        fun cancelPosted(key: String?): Boolean {
            if (key.isNullOrBlank()) return false
            val live = instance ?: return false
            return runCatching {
                live.cancelNotification(key)
                true
            }.getOrDefault(false)
        }

        /** 同 key 重投的去重窗口（ms）。 */
        private const val DEDUPE_WINDOW_MS = 2_000L

        /** 同时跟踪的 key 数上限。 */
        private const val RECENT_KEYS = 16
    }
}
