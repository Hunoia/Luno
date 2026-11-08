package hunoia.luno.domain.habit

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import hunoia.luno.config.model.HabitDayState
import hunoia.luno.config.model.habitDayState
import hunoia.luno.core.LunoLog
import hunoia.luno.core.LOG_TAG
import hunoia.luno.domain.gridpanel.HabitCenter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * 习惯提醒接收器（主进程，AlarmManager 触发 / 通知「完成」按钮）。
 *
 * - ACTION_REMINDER：到点。`goAsync` + 协程读配置；应打卡（DUE）、逾期（OVERDUE，仍可补打）
 *   与尚未开始（INACTIVE，从未打卡）才弹通知；已打 / 跳过不弹；
 *   随后 [HabitReminderScheduler.rescheduleAll] 滚动到下一个应打卡日。
 * - ACTION_EXPIRE：每天 0 点收起昨天仍在屏的提醒，再按新的一天重排。
 * - ACTION_MARK_DONE：通知「完成」→ [HabitCenter.setHabitCheck] 打卡（幂等）并收起通知。
 */
class HabitReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext
        when (intent.action) {
            HabitReminderScheduler.ACTION_REMINDER -> {
                val habitId = intent.getStringExtra(HabitReminderScheduler.EXTRA_HABIT_ID) ?: return
                onReminder(app, habitId)
            }
            HabitReminderScheduler.ACTION_MARK_DONE -> {
                val habitId = intent.getStringExtra(HabitReminderScheduler.EXTRA_HABIT_ID) ?: return
                onMarkDone(app, habitId, intent)
            }
            HabitReminderScheduler.ACTION_EXPIRE -> onExpire(app)
            else -> Unit
        }
    }

    private fun onReminder(context: Context, habitId: String) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val config = HabitCenter.getHabitConfig()
                val habit = config.habitById(habitId)
                if (habit == null || !habit.reminderEnabled) return@launch
                val today = LocalDate.now().toEpochDay()
                val checks = config.checks[habitId].orEmpty()
                val state = habitDayState(checks, today, habit.interval, today)
                LunoLog.i(LOG_TAG, "habit reminder fire: habit=${habit.id} state=$state today=$today checks=$checks")
                // INACTIVE = 从未打卡：与调度口径一致，从今天起每日提醒，直到首次打卡进入周期。
                if (state != HabitDayState.CHECKED && state != HabitDayState.SKIPPED) {
                    HabitReminderNotifier.show(context, habit, today, HabitReminderScheduler.currentStyle())
                    HabitReminderScheduler.markNotified(context, habit, today)
                }
                // 无论是否 DUE 都滚动到下一次（当天已打 / 时间已过则顺延到下个应打卡日）。
                HabitReminderScheduler.rescheduleAll(context, config)
            } catch (e: Exception) {
                LunoLog.w(LOG_TAG, "habit reminder fire failed: ${e.message}")
            } finally {
                pending.finish()
            }
        }
    }

    private fun onMarkDone(context: Context, habitId: String, intent: Intent) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // 补卡目标日：通知触发当天；迟几天点「完成」仍补回正确的一天。
                val day = intent.getLongExtra(HabitReminderScheduler.EXTRA_DAY, -1L)
                    .takeIf { it >= 0 } ?: LocalDate.now().toEpochDay()
                val config = HabitCenter.getHabitConfig()
                if (config.habitById(habitId) != null) {
                    HabitCenter.setHabitCheck(habitId, day, true)
                }
                HabitReminderNotifier.cancel(context, habitId)
            } catch (e: Exception) {
                LunoLog.w(LOG_TAG, "habit reminder mark done failed: ${e.message}")
            } finally {
                pending.finish()
            }
        }
    }

    /** 跨日：收起昨天所有休息在屏的提醒，再按新的一天重排（含当天已过时间的补弹）。 */
    private fun onExpire(context: Context) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val config = runCatching { HabitCenter.getHabitConfig() }.getOrNull()
                val ids = HabitReminderScheduler.knownHabitIds(context) +
                    config?.habits?.map { it.id }.orEmpty()
                ids.forEach { HabitReminderNotifier.cancel(context, it) }
                if (config != null) {
                    HabitReminderScheduler.rescheduleAll(context, config)
                }
                LunoLog.i(LOG_TAG, "habit reminder expired: cancelled=${ids.size}")
            } catch (e: Exception) {
                LunoLog.w(LOG_TAG, "habit reminder expire failed: ${e.message}")
            } finally {
                pending.finish()
            }
        }
    }
}
