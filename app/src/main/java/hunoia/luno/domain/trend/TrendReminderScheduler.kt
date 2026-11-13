package hunoia.luno.domain.trend

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import hunoia.luno.config.model.TrendConfig
import hunoia.luno.config.model.TrendMetric
import hunoia.luno.core.LOG_TAG
import hunoia.luno.core.LunoLog
import hunoia.luno.domain.gridpanel.TrendCenter
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * 趋势记录提醒：每个指标每天最多一次。
 *
 * 与习惯 / 待办提醒刻意分开：这里不涉及打卡状态、连续天数、推迟或补弹，
 * 只回答一个问题 ——「今天还没记录这个指标，到点提醒一次」。
 *
 * 口径：
 * - 一次性闹钟，每次触发后立即排下一天；当天已有记录则静默跳过（接收器判断）。
 * - 精确闹钟不可用时降级为 [AlarmManager.setAndAllowWhileIdle]（与习惯 / 待办同一策略）。
 * - 开机、改时间 / 时区、覆盖安装、配置变化后整表重排。
 */
object TrendReminderScheduler {

    const val ACTION_REMINDER = "hunoia.luno.action.TREND_REMINDER"
    const val EXTRA_METRIC_ID = "hunoia.luno.extra.TREND_REMINDER_ID"

    /** 请求码种子，避免与习惯 / 待办闹钟撞车。 */
    private const val REQUEST_MAGIC = 0x54524E44 // "TRND"

    private const val PREFS_NAME = "trend_reminder_state"
    private const val KEY_KNOWN_IDS = "known_metric_ids"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val observing = AtomicBoolean(false)

    private val lock = Any()

    /** 配置变化即时重排（进程内只订阅一次）。 */
    fun startObserving(context: Context) {
        if (!observing.compareAndSet(false, true)) return
        val app = context.applicationContext
        scope.launch {
            TrendCenter.trendConfig.collectLatest { config ->
                scheduleAll(app, config)
            }
        }
    }

    /** 开机 / 更新 / 改时间 / 打开应用时整表重排。 */
    fun rescheduleAll(context: Context) {
        val app = context.applicationContext
        scope.launch {
            val config = runCatching { TrendCenter.getTrendConfig() }.getOrNull()
            if (config == null) {
                LunoLog.w(LOG_TAG, "trend reminder reschedule: config load failed")
                return@launch
            }
            scheduleAll(app, config)
        }
    }

    /**
     * 取消旧闹钟 → 为开启提醒的指标排下一次。
     *
     * 先按上次落盘的 id 集合取消（覆盖已删除 / 已关闭提醒的指标），再排当前集合；
     * 不用在配置里记录调度状态，DataStore 仍是唯一事实来源。
     */
    fun scheduleAll(context: Context, config: TrendConfig) {
        val app = context.applicationContext
        val now = System.currentTimeMillis()
        synchronized(lock) {
            val prefs = app.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val known = prefs.getStringSet(KEY_KNOWN_IDS, emptySet()).orEmpty()
            (known + config.metrics.map { it.id }).forEach { cancelAlarm(app, it) }
            val enabled = config.metrics.filter { it.reminderEnabled }
            enabled.forEach { metric ->
                setAlarm(app, metric.id, nextTrigger(metric, now))
            }
            prefs.edit().putStringSet(KEY_KNOWN_IDS, enabled.map { it.id }.toSet()).apply()
        }
    }

    /** 到点触发后重排下一天。[notBefore] 留一点余量，避免闹钟早放几秒又排回今天。 */
    fun scheduleNext(context: Context, metric: TrendMetric) {
        if (!metric.reminderEnabled) return
        val now = System.currentTimeMillis() + 60_000L
        synchronized(lock) {
            cancelAlarm(context, metric.id)
            setAlarm(context, metric.id, nextTrigger(metric, now))
        }
    }

    fun cancel(context: Context, metricId: String) {
        synchronized(lock) { cancelAlarm(context, metricId) }
    }

    fun notificationId(metricId: String): Int = REQUEST_MAGIC xor metricId.hashCode()

    /** 下一次提醒的绝对时刻：严格晚于 [notBefore] 的最近一个本地墙钟时间。 */
    private fun nextTrigger(metric: TrendMetric, notBefore: Long): Long {
        val zone = ZoneId.systemDefault()
        val now = Instant.ofEpochMilli(notBefore).atZone(zone)
        val time = LocalTime.of(
            metric.reminderHour.coerceIn(0, 23),
            metric.reminderMinute.coerceIn(0, 59),
        )
        var target = now.toLocalDate().atTime(time).atZone(zone)
        if (target.toInstant().toEpochMilli() <= notBefore) {
            target = target.plusDays(1)
        }
        return target.toInstant().toEpochMilli()
    }

    private fun requestCode(metricId: String): Int = REQUEST_MAGIC xor metricId.hashCode()

    private fun pendingIntent(context: Context, metricId: String): PendingIntent {
        val intent = Intent(context, TrendReminderReceiver::class.java).apply {
            action = ACTION_REMINDER
            putExtra(EXTRA_METRIC_ID, metricId)
        }
        return PendingIntent.getBroadcast(
            context,
            requestCode(metricId),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun setAlarm(context: Context, metricId: String, atMillis: Long) {
        val manager = context.getSystemService(AlarmManager::class.java) ?: return
        val pending = pendingIntent(context, metricId)
        try {
            val exact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || manager.canScheduleExactAlarms()
            if (exact) {
                manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, pending)
            } else {
                manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, pending)
            }
            LunoLog.i(LOG_TAG, "trend reminder scheduled: metric=$metricId at=$atMillis exact=$exact")
        } catch (e: SecurityException) {
            LunoLog.w(LOG_TAG, "trend reminder exact alarm denied: ${e.message}")
            runCatching { manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, pending) }
        }
    }

    private fun cancelAlarm(context: Context, metricId: String) {
        val manager = context.getSystemService(AlarmManager::class.java) ?: return
        runCatching { manager.cancel(pendingIntent(context, metricId)) }
    }
}
