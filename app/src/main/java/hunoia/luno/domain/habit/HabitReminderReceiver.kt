package hunoia.luno.domain.habit

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import hunoia.luno.bridge.ui.showToast
import hunoia.luno.config.model.HabitDayState
import hunoia.luno.config.model.HabitDefaultSnoozeMinutes
import hunoia.luno.config.model.currentStreak
import hunoia.luno.config.model.habitDayState
import hunoia.luno.core.LOG_TAG
import hunoia.luno.core.LunoLog
import hunoia.luno.domain.gridpanel.HabitCenter
import hunoia.luno.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * 习惯提醒接收器（主进程，AlarmManager 触发 / 通知「+1」按钮）。
 *
 * - ACTION_REMINDER：到点。`goAsync` + 协程读配置；应打卡（DUE）、逾期（OVERDUE，仍可补打）
 *   与尚未开始（INACTIVE，从未打卡）才弹通知；已完成 / 跳过不弹；
 *   随后 [HabitReminderScheduler.rescheduleAll] 滚动到下一个应打卡日。
 * - ACTION_EXPIRE：每天 0 点收起昨天仍在屏的提醒，再按新的一天重排。
 * - ACTION_MARK_DONE：通知「+1」→ [HabitCenter.incrementHabit] 加一次；达标才收起通知。
 * - ACTION_SNOOZE：「推迟 N 分钟」→ 收起当前提醒并挂一个一次性闹钟（不动每日调度）。
 * - ACTION_SNOOZE_FIRE：推迟到点 → 重算状态，已完成 / 休整日 / 更晚的提醒槽已弹过则不再弹。
 */
class HabitReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext
        when (intent.action) {
            HabitReminderScheduler.ACTION_REMINDER -> {
                val habitId = intent.getStringExtra(HabitReminderScheduler.EXTRA_HABIT_ID) ?: return
                onReminder(app, habitId, intent)
            }
            HabitReminderScheduler.ACTION_MARK_DONE -> {
                val habitId = intent.getStringExtra(HabitReminderScheduler.EXTRA_HABIT_ID) ?: return
                onMarkDone(app, habitId, intent)
            }
            HabitReminderScheduler.ACTION_SNOOZE -> {
                val habitId = intent.getStringExtra(HabitReminderScheduler.EXTRA_HABIT_ID) ?: return
                onSnooze(app, habitId, intent)
            }
            HabitReminderScheduler.ACTION_SNOOZE_FIRE -> {
                val habitId = intent.getStringExtra(HabitReminderScheduler.EXTRA_HABIT_ID) ?: return
                onSnoozeFire(app, habitId, intent)
            }
            HabitReminderScheduler.ACTION_EXPIRE -> onExpire(app)
            else -> Unit
        }
    }

    private fun onReminder(context: Context, habitId: String, intent: Intent) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val config = HabitCenter.getHabitConfig()
                val habit = config.habitById(habitId)
                if (habit == null || habit.reminderTimes.isEmpty()) return@launch
                val today = LocalDate.now().toEpochDay()
                val checks = config.checks[habitId].orEmpty()
                val state = habitDayState(checks, today, habit.interval, today)
                val slot = intent.getIntExtra(HabitReminderScheduler.EXTRA_SLOT, -1)
                    .takeIf { it >= 0 }
                    ?: habit.reminderTimes.lastOrNull()?.minutesOfDay
                    ?: 0
                LunoLog.i(LOG_TAG, "habit reminder fire: habit=${habit.id} state=$state today=$today slot=$slot")
                // INACTIVE = 从未打卡：与调度口径一致，从今天起每日提醒，直到首次打卡进入周期。
                if (state != HabitDayState.CHECKED && state != HabitDayState.SKIPPED) {
                    HabitReminderNotifier.show(
                        context,
                        habit,
                        today,
                        HabitReminderScheduler.currentMode(),
                        HabitReminderStatus(
                            state = state,
                            streakDays = currentStreak(checks, today, habit.interval),
                            count = config.countOf(habitId, today, habit.safeDailyTarget),
                            target = habit.safeDailyTarget,
                        ),
                        slotMinutes = slot,
                    )
                    HabitReminderScheduler.markNotified(context, habitId, today, slot)
                }
                // 无论是否 DUE 都滚动到下一次（当天已完成 / 时间已过则顺延到下个应打卡日）。
                HabitReminderScheduler.rescheduleAll(context, config)
            } catch (e: Exception) {
                LunoLog.w(LOG_TAG, "habit reminder fire failed: ${e.message}")
            } finally {
                pending.finish()
            }
        }
    }

    private fun onMarkDone(context: Context, habitId: String, intent: Intent) {
        LunoLog.i(LOG_TAG, "habit reminder mark done: habit=$habitId")
        // 测试通知（假 id）：不写任何配置，只给一条轻反馈当「点击链路走通了」的信号。
        if (HabitReminderNotifier.isTestHabit(habitId)) {
            HabitReminderNotifier.cancel(context, habitId)
            showToast(R.string.habit_reminder_test_done)
            return
        }
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // 补卡目标日：通知触发当天；迟几天点「+1」仍补回正确的一天。
                val day = intent.getLongExtra(HabitReminderScheduler.EXTRA_DAY, -1L)
                    .takeIf { it >= 0 } ?: LocalDate.now().toEpochDay()
                val before = HabitCenter.getHabitConfig()
                if (before.habitById(habitId) != null) {
                    HabitCenter.incrementHabit(habitId, day)
                }
                val after = HabitCenter.getHabitConfig()
                val habit = after.habitById(habitId)
                val completed = habit == null || after.isChecked(habitId, day)
                if (completed) {
                    HabitReminderNotifier.cancel(context, habitId)
                    // 达标后不该再被推迟闹钟唤回。
                    HabitReminderScheduler.cancelSnooze(context, habitId)
                } else {
                    // 多次习惯还差几次：保留通知并静默刷新进度，用户可继续点「+1」。
                    val checks = after.checks[habitId].orEmpty()
                    val today = LocalDate.now().toEpochDay()
                    val state = habitDayState(checks, day, habit.interval, today)
                    val slot = intent.getIntExtra(HabitReminderScheduler.EXTRA_SLOT, 0).coerceAtLeast(0)
                    LunoLog.i(
                        LOG_TAG,
                        "habit reminder mark done: partial " +
                            "${after.countOf(habitId, day, habit.safeDailyTarget)}/${habit.safeDailyTarget}",
                    )
                    // 只有「今天」的提醒才刷新正文；跨天补打不覆盖当天的提醒文案。
                    if (day == today) {
                        HabitReminderNotifier.refreshProgress(
                            context,
                            habit,
                            day,
                            HabitReminderStatus(
                                state = state,
                                streakDays = currentStreak(checks, today, habit.interval),
                                count = after.countOf(habitId, day, habit.safeDailyTarget),
                                target = habit.safeDailyTarget,
                            ),
                            slot,
                        )
                    }
                }
            } catch (e: Exception) {
                LunoLog.w(LOG_TAG, "habit reminder mark done failed: ${e.message}")
            } finally {
                pending.finish()
            }
        }
    }

    /** 「推迟 N 分钟」：收起当前提醒并挂一次性闹钟；每日调度不受影响。 */
    private fun onSnooze(context: Context, habitId: String, intent: Intent) {
        val minutes = intent.getIntExtra(HabitReminderScheduler.EXTRA_SNOOZE_MINUTES, 0)
            .takeIf { it > 0 }
            ?: HabitDefaultSnoozeMinutes
        val slot = intent.getIntExtra(HabitReminderScheduler.EXTRA_SLOT, 0).coerceAtLeast(0)
        LunoLog.i(LOG_TAG, "habit reminder snooze: habit=$habitId minutes=$minutes slot=$slot")
        HabitReminderNotifier.cancel(context, habitId)
        HabitReminderScheduler.scheduleSnooze(context, habitId, minutes, slot)
    }

    /** 推迟到点：状态已变（已完成 / 进入休整日）或更晚的提醒槽已弹过就静默收掉。 */
    private fun onSnoozeFire(context: Context, habitId: String, intent: Intent) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val config = HabitCenter.getHabitConfig()
                val habit = config.habitById(habitId)
                if (habit == null || habit.reminderTimes.isEmpty()) return@launch
                val today = LocalDate.now().toEpochDay()
                val checks = config.checks[habitId].orEmpty()
                val state = habitDayState(checks, today, habit.interval, today)
                if (state == HabitDayState.CHECKED || state == HabitDayState.SKIPPED) {
                    LunoLog.i(LOG_TAG, "habit snooze fire skipped: habit=$habitId state=$state")
                    return@launch
                }
                val slot = intent.getIntExtra(HabitReminderScheduler.EXTRA_SLOT, 0).coerceAtLeast(0)
                val (lastDay, lastSlot) = HabitReminderScheduler.lastNotified(context, habitId)
                // 推迟期间更晚的提醒槽已经响过：不再重复弹，避免用户被同一习惯反复打扰。
                if (lastDay == today && lastSlot > slot) {
                    LunoLog.i(LOG_TAG, "habit snooze fire skipped: later slot=$lastSlot > $slot")
                    return@launch
                }
                LunoLog.i(LOG_TAG, "habit snooze fire: habit=$habitId state=$state slot=$slot")
                HabitReminderNotifier.show(
                    context,
                    habit,
                    today,
                    HabitReminderScheduler.currentMode(),
                    HabitReminderStatus(
                        state = state,
                        streakDays = currentStreak(checks, today, habit.interval),
                        count = config.countOf(habitId, today, habit.safeDailyTarget),
                        target = habit.safeDailyTarget,
                    ),
                    slotMinutes = slot,
                )
                HabitReminderScheduler.markNotified(context, habitId, today, slot)
            } catch (e: Exception) {
                LunoLog.w(LOG_TAG, "habit snooze fire failed: ${e.message}")
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
                ids.forEach {
                    HabitReminderNotifier.cancel(context, it)
                    HabitReminderScheduler.cancelSnooze(context, it)
                }
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
