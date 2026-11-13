package hunoia.luno.domain.trend

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import hunoia.luno.config.model.epochDayOf
import hunoia.luno.config.model.hasEntryOn
import hunoia.luno.core.LOG_TAG
import hunoia.luno.core.LunoLog
import hunoia.luno.domain.gridpanel.TrendCenter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * 趋势提醒接收器（主进程，AlarmManager 触发）。
 *
 * 每次触发都重新读配置，因此指标被删 / 关闭提醒后不需要依赖「取消闹钟成功」：
 * 即使旧闹钟残留，到点也只会静默跳过。当天已有记录同样跳过；无论发没发通知，
 * 都会排下一天的一次性闹钟。
 */
class TrendReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != TrendReminderScheduler.ACTION_REMINDER) return
        val metricId = intent.getStringExtra(TrendReminderScheduler.EXTRA_METRIC_ID) ?: return
        val app = context.applicationContext
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val config = TrendCenter.getTrendConfig()
                val metric = config.metricById(metricId)
                if (metric == null || !metric.reminderEnabled) {
                    LunoLog.i(LOG_TAG, "trend reminder skipped: metric=$metricId missing/disabled")
                    TrendReminderNotifier.cancel(app, metricId)
                    return@launch
                }
                val today = epochDayOf(System.currentTimeMillis())
                if (hasEntryOn(config.entriesOf(metricId), today)) {
                    LunoLog.i(LOG_TAG, "trend reminder skipped: metric=$metricId already recorded today")
                    TrendReminderNotifier.cancel(app, metricId)
                } else {
                    TrendReminderNotifier.show(app, metric)
                }
                TrendReminderScheduler.scheduleNext(app, metric)
            } catch (e: Exception) {
                LunoLog.w(LOG_TAG, "trend reminder fire failed: ${e.message}")
            } finally {
                pending.finish()
            }
        }
    }
}
