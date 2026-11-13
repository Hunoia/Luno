package hunoia.luno.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import hunoia.luno.core.LOG_TAG
import hunoia.luno.core.LunoLog
import hunoia.luno.domain.habit.HabitReminderScheduler
import hunoia.luno.domain.todo.TodoReminderScheduler
import hunoia.luno.domain.trend.TrendReminderScheduler

/**
 * 系统时间 / 时区变化后重排所有「墙钟」提醒。
 *
 * 习惯与待办提醒都按本地墙钟存储（时分 / 日期），AlarmManager 里挂的是当时换算出的
 * epoch millis。手动改时间或切换时区后，旧闹钟会落在错误的绝对时刻：必须按新时区重算。
 *
 * 只关心 `TIME_SET` / `TIMEZONE_CHANGED`；`DATE_CHANGED`（自然跨天）不改变未来闹钟的
 * 绝对时刻，跨天滚动由各自的调度器负责。
 */
class TimeChangeReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            -> {
                LunoLog.i(LOG_TAG, "time changed (${intent.action}), rescheduling wall-clock reminders")
                HabitReminderScheduler.rescheduleAll(context)
                TodoReminderScheduler.rescheduleAll(context)
                TrendReminderScheduler.rescheduleAll(context)
            }

            else -> Unit
        }
    }
}
