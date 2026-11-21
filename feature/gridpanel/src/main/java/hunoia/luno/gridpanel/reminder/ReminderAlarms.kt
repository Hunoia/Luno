package hunoia.luno.gridpanel.reminder

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.os.Build
import hunoia.luno.core.LOG_TAG
import hunoia.luno.core.LunoLog

/**
 * 待办 / 习惯 / 趋势三个提醒调度器共用的 AlarmManager 机械层。
 *
 * 只收口与「策略」无关的样板：精确闹钟档位（API 31+ 查权限、SecurityException 降级
 * 非精确）与取消。何时触发、补弹、推迟状态等域逻辑仍归各 Scheduler。
 */
internal object ReminderAlarms {

    /**
     * 精确闹钟优先：API 31+ 无 `SCHEDULE_EXACT_ALARM` 权限或抛 [SecurityException] 时
     * 降级 [AlarmManager.setAndAllowWhileIdle]——宁可晚到也不丢提醒。
     */
    fun setExactFallback(
        context: Context,
        triggerMillis: Long,
        pendingIntent: PendingIntent,
        label: String,
    ) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        try {
            val canExact = Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()
            if (canExact) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerMillis, pendingIntent)
            } else {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerMillis, pendingIntent)
            }
        } catch (e: SecurityException) {
            LunoLog.w(LOG_TAG, "$label exact alarm denied, fallback: ${e.message}")
            runCatching {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerMillis, pendingIntent)
            }
        }
    }

    /** 取消闹钟；PendingIntent 未挂过时系统 API 本身幂等，这里只兜服务获取失败。 */
    fun cancel(context: Context, pendingIntent: PendingIntent) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        runCatching { am.cancel(pendingIntent) }
    }
}
