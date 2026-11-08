package hunoia.luno.domain.habit

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import hunoia.luno.config.ConfigProvider
import hunoia.luno.config.model.Habit
import hunoia.luno.config.model.HabitConfig
import hunoia.luno.config.model.HabitDayState
import hunoia.luno.config.model.NotificationKind
import hunoia.luno.config.model.NotificationMode
import hunoia.luno.config.model.habitDayState
import hunoia.luno.core.LunoLog
import hunoia.luno.core.LOG_TAG
import hunoia.luno.domain.gridpanel.HabitCenter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 习惯提醒调度：到「应打卡日」的 [Habit.reminderHour]:[Habit.reminderMinute] 弹常驻通知。
 *
 * 口径（参照 moting ReminderScheduler）：
 * - 精确闹钟 [AlarmManager.setExactAndAllowWhileIdle]，API 31+ 先查 [AlarmManager.canScheduleExactAlarms]，
 *   无精确权限或 [SecurityException] 时降级 [AlarmManager.setAndAllowWhileIdle]（非精确）。
 * - [rescheduleAll] 幂等：先取消当前配置里所有习惯的闹钟，再按「开了提醒」的重排；
 *   删掉 / 关掉 / 改时间的习惯随之取消或更新。被删习惯不在配置里，用上次落盘的 id 集合
 *   做差集补收它的残留闹钟与在屏通知（否则常驻通知会一直留着）。
 * - 排未来的「应打卡日」（DUE）与「逾期日」（OVERDUE，仍可补打）；跳过日（SKIPPED）是休整日
 *   不提醒，已打卡（CHECKED）日不提醒。逾期后每日提醒（常驻通知按 id 刷新），直到补打回到周期；
 *   未开始（无打卡记录）的习惯从今天起每日提醒，直到首次打卡进入正常周期。
 * - 补弹：当天提醒时间已过且仍未打卡时，立即补弹一次；同一习惯同一天只补一次（落盘 lastNotifiedDay）。
 * - 跨日：每天 0 点统一收起昨天的在屏通知，再按新的一天重排，常驻通知不跨天。
 */
object HabitReminderScheduler {

    const val ACTION_REMINDER = "hunoia.luno.action.HABIT_REMINDER"

    /** 通知 / 岛上的「完成」：直接打卡（幂等）。 */
    const val ACTION_MARK_DONE = "hunoia.luno.action.HABIT_REMINDER_DONE"

    /** 通知 / 岛上的「推迟」请求：收起当前提醒，并在 N 分钟后重弹（N 来自习惯设置）。 */
    const val ACTION_SNOOZE = "hunoia.luno.action.HABIT_REMINDER_SNOOZE"

    /** 推迟到点：重算状态后重弹（已打卡 / 休整日不再弹）。 */
    const val ACTION_SNOOZE_FIRE = "hunoia.luno.action.HABIT_REMINDER_SNOOZE_FIRE"

    /** 每日 0 点：收起前一天仍在屏的提醒，避免常驻通知跨到第二天。 */
    const val ACTION_EXPIRE = "hunoia.luno.action.HABIT_REMINDER_EXPIRE"
    const val EXTRA_HABIT_ID = "hunoia.luno.extra.HABIT_REMINDER_ID"
    const val EXTRA_DAY = "hunoia.luno.extra.HABIT_REMINDER_DAY"
    const val EXTRA_SNOOZE_MINUTES = "hunoia.luno.extra.HABIT_REMINDER_SNOOZE_MINUTES"

    /** 与通知 id 同源的请求码种子（"HABT"）；与 habitId.hashCode() 异或避免跨习惯碰撞。 */
    private const val REQUEST_MAGIC = 0x48414254

    /**
     * 通知 id 单独用另一套种子（"HBRT"）：早期版本按闹钟请求码发通知，那个 id 可能被
     * 「固定通知隐藏」snooze 过（系统会静默一年且重新 post 不会解除）。换 id 可绕过旧 snooze，
     * 又保留旧闹钟请求码，避免升级后残留闹钟。
     */
    private const val NOTIFICATION_MAGIC = 0x48425254

    /** 跨日收口闹钟的固定请求码（"HABE"），与习惯请求码空间错开。 */
    private const val EXPIRY_REQUEST_CODE = 0x48414245

    /** 推迟闹钟请求码种子（"HABS"）：与每日提醒、通知 id、跨日收口都错开。 */
    private const val SNOOZE_REQUEST_MAGIC = 0x48414253

    /** 补弹闹钟给接收器留的缓冲，避免 setExact 的 now 已过期。 */
    private const val CATCH_UP_DELAY_MS = 500L

    /** 未来应打卡日的搜索上界：interval 上限 30，60 天内必能命中下一个应打卡日。 */
    private const val LOOKAHEAD_DAYS = 60L

    /** 推迟时长上限（一天）；UI 档位远小于它，这里只防脏数据。 */
    private const val MAX_SNOOZE_MINUTES = 24 * 60

    /** 记录上次参与调度的习惯 id：删习惯后配置里查不到它，只能靠差集取消残留闹钟 / 通知。 */
    private const val PREFS_NAME = "habit_reminder_state"
    private const val KEY_KNOWN_IDS = "known_habit_ids"

    /** 每个习惯最后一次真正弹过通知的 epochDay + 当时提醒时间槽（分钟）；同一时间同一天不重复补弹。 */
    private const val KEY_LAST_NOTIFIED_PREFIX = "last_notified_day_"
    private const val KEY_LAST_NOTIFIED_SLOT_PREFIX = "last_notified_slot_"

    /** 入口：读配置 + 高级设置（通用通知显示设置），在接收器里用最终生效模式展示。 */
    suspend fun currentMode(): NotificationMode =
        runCatching {
            ConfigProvider.getAdvancedSettings().notificationDisplay.modeFor(NotificationKind.HABIT)
        }.getOrDefault(NotificationMode.DEFAULT)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val observing = AtomicBoolean(false)

    /** 配置流与接收器可能并发重排；闹钟 / 通知 / 已调度集合都按同一把锁串行化。 */
    private val lock = Any()

    /**
     * 订阅习惯配置：任何增删改 / 打卡写入后按最新配置重排全部提醒（进程内一次）。
     * 覆盖编辑页保存、部件打卡、通知「完成」等所有写入口，gridpanel 无需反向依赖 app。
     */
    fun startObserving(context: Context) {
        if (!observing.compareAndSet(false, true)) return
        val app = context.applicationContext
        scope.launch {
            HabitCenter.habitConfig.collectLatest { config ->
                rescheduleAll(app, config)
            }
        }
    }

    /** 入口：加载配置后重排全部提醒（增删改 / 开关 / 开机 / 应用打开 / 提醒触发后）。 */
    fun rescheduleAll(context: Context) {
        val app = context.applicationContext
        scope.launch {
            val config = runCatching { HabitCenter.getHabitConfig() }.getOrNull()
            if (config == null) {
                LunoLog.w(LOG_TAG, "habit reminder reschedule: config load failed")
                return@launch
            }
            rescheduleAll(app, config)
        }
    }

    /** 同步重排：调用方已持有配置（接收器触发后滚动到下一次用）。 */
    fun rescheduleAll(context: Context, config: HabitConfig) {
        val app = context.applicationContext
        val now = System.currentTimeMillis()
        val today = LocalDate.now().toEpochDay()
        synchronized(lock) {
            val prefs = app.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val currentIds = config.habits.map { it.id }.toSet()
            // 已删除的习惯：配置里已经没有它，无法在下面的遍历里取消，只能按上次集合补收。
            val removedIds = prefs.getStringSet(KEY_KNOWN_IDS, emptySet()).orEmpty() - currentIds
            removedIds.forEach { habitId ->
                cancelAlarm(app, habitId)
                cancelSnooze(app, habitId)
                HabitReminderNotifier.cancel(app, habitId)
                prefs.edit()
                    .remove(lastNotifiedKey(habitId))
                    .remove(lastNotifiedSlotKey(habitId))
                    .apply()
            }
            config.habits.forEach { cancelAlarm(app, it.id) }
            var anyReminder = false
            config.habits.forEach { habit ->
                val checks = config.checks[habit.id].orEmpty()
                val state = habitDayState(checks, today, habit.interval, today)
                // 今天已打卡 / 今天是跳过日：在屏的提醒通知应随之收起。
                if (state == HabitDayState.CHECKED || state == HabitDayState.SKIPPED) {
                    HabitReminderNotifier.cancel(app, habit.id)
                    cancelSnooze(app, habit.id)
                }
                if (!habit.reminderEnabled) {
                    // 用户关掉提醒：仍在屏的常驻通知与在途的推迟闹钟一并收起。
                    HabitReminderNotifier.cancel(app, habit.id)
                    cancelSnooze(app, habit.id)
                    return@forEach
                }
                anyReminder = true
                // 当天提醒时间已过且仍未打卡：立即补弹一次（同一时间槽同一天只补一次）。
                // 已在通知栏时不重复补弹，避免配置流重排时连续 post。
                val lastNotified = prefs.getLong(lastNotifiedKey(habit.id), Long.MIN_VALUE)
                val lastSlot = prefs.getInt(lastNotifiedSlotKey(habit.id), Int.MIN_VALUE)
                val catchUpDay = if (HabitReminderNotifier.isShowing(app, habit.id)) {
                    null
                } else {
                    catchUpReminderDay(habit, checks, today, now, lastNotified, lastSlot)
                }
                val trigger = if (catchUpDay != null) {
                    now + CATCH_UP_DELAY_MS
                } else {
                    nextTriggerMillis(habit, checks, today, now)
                }
                if (trigger == null) return@forEach
                setAlarm(app, habit.id, trigger)
                LunoLog.i(
                    LOG_TAG,
                    "habit reminder scheduled: habit=${habit.id} trigger=$trigger catchUp=$catchUpDay " +
                        "(${java.time.Instant.ofEpochMilli(trigger)})",
                )
            }
            if (anyReminder) {
                // 每天 0 点统一收起昨天的提醒：常驻通知不跨天。
                setExpiryAlarm(app)
            } else {
                cancelExpiryAlarm(app)
            }
            prefs.edit().putStringSet(KEY_KNOWN_IDS, currentIds).apply()
        }
    }

    /** 下一次提醒触发时刻；无未来应打卡日返回 null。 */
    fun nextTriggerMillis(
        habit: Habit,
        checks: Set<Long>,
        todayEpochDay: Long,
        now: Long,
    ): Long? {
        val zone = ZoneId.systemDefault()
        if (checks.isEmpty()) {
            val todayTrigger = triggerAtMillis(habit, todayEpochDay, zone)
            return if (todayTrigger > now) todayTrigger else triggerAtMillis(habit, todayEpochDay + 1, zone)
        }
        val interval = habit.interval.coerceAtLeast(1)
        var day = todayEpochDay
        while (day <= todayEpochDay + LOOKAHEAD_DAYS) {
            // 应打卡日（DUE）与逾期日（OVERDUE，仍可补打）都要提醒；跳过日（SKIPPED）是休整日不提醒。
            // 逾期后每天都提醒（常驻通知按 id 刷新），直到用户补打回到正常周期。
            when (habitDayState(checks, day, interval, todayEpochDay)) {
                HabitDayState.DUE, HabitDayState.OVERDUE -> {
                    val trigger = triggerAtMillis(habit, day, zone)
                    if (trigger > now) return trigger
                }
                else -> Unit
            }
            day++
        }
        return null
    }

    /**
     * 当天提醒时间已过、习惯仍未打卡（或从未开始）时，返回今天（需要立即补弹）；否则 null。
     * 只有「同一天 + 同一个提醒时间槽」已经弹过才抑制，避免重复调度时反复弹；
     * 用户当天把时间改晚 / 改早后，槽不同，仍可补弹一次。
     */
    fun catchUpReminderDay(
        habit: Habit,
        checks: Set<Long>,
        todayEpochDay: Long,
        now: Long,
        lastNotifiedDay: Long,
        lastNotifiedSlot: Int = Int.MIN_VALUE,
    ): Long? {
        if (!habit.reminderEnabled) return null
        if (lastNotifiedDay == todayEpochDay && lastNotifiedSlot == reminderSlot(habit)) return null
        val interval = habit.interval.coerceAtLeast(1)
        val state = habitDayState(checks, todayEpochDay, interval, todayEpochDay)
        if (state == HabitDayState.CHECKED || state == HabitDayState.SKIPPED) return null
        val trigger = triggerAtMillis(habit, todayEpochDay, ZoneId.systemDefault())
        return if (trigger <= now) todayEpochDay else null
    }

    /** 记录某习惯某天已弹过通知（接收器真正展示后调用），同时记下当时的提醒时间槽。 */
    fun markNotified(context: Context, habit: Habit, day: Long) {
        val slot = reminderSlot(habit)
        val committed = context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putLong(lastNotifiedKey(habit.id), day)
            .putInt(lastNotifiedSlotKey(habit.id), slot)
            // 用 commit：接收器紧接着可能结束进程，apply 的异步落盘会丢。
            .commit()
        LunoLog.i(LOG_TAG, "habit reminder notified: habit=${habit.id} day=$day slot=$slot persisted=$committed")
    }

    /** 上次参与调度的习惯 id（接收器跨日收起通知用）。 */
    fun knownHabitIds(context: Context): Set<String> =
        context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getStringSet(KEY_KNOWN_IDS, emptySet())
            .orEmpty()

    private fun lastNotifiedKey(habitId: String): String = KEY_LAST_NOTIFIED_PREFIX + habitId

    private fun lastNotifiedSlotKey(habitId: String): String = KEY_LAST_NOTIFIED_SLOT_PREFIX + habitId

    private fun reminderSlot(habit: Habit): Int =
        habit.reminderHour.coerceIn(0, 23) * 60 + habit.reminderMinute.coerceIn(0, 59)

    private fun triggerAtMillis(habit: Habit, day: Long, zone: ZoneId): Long {
        val date = LocalDate.ofEpochDay(day)
        return LocalDateTime.of(
            date.year, date.monthValue, date.dayOfMonth,
            habit.reminderHour.coerceIn(0, 23), habit.reminderMinute.coerceIn(0, 59),
        ).atZone(zone).toInstant().toEpochMilli()
    }

    private fun setAlarm(context: Context, habitId: String, triggerMillis: Long) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val pi = reminderPendingIntent(context, habitId)
        val type = AlarmManager.RTC_WAKEUP
        try {
            val canExact = Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()
            if (canExact) {
                am.setExactAndAllowWhileIdle(type, triggerMillis, pi)
            } else {
                am.setAndAllowWhileIdle(type, triggerMillis, pi)
            }
        } catch (e: SecurityException) {
            // 无精确闹钟权限（Android 12+ 用户可关）：降级非精确，宁可晚到也不丢提醒。
            LunoLog.w(LOG_TAG, "habit reminder exact alarm denied, fallback: ${e.message}")
            runCatching { am.setAndAllowWhileIdle(type, triggerMillis, pi) }
        }
    }

    private fun cancelAlarm(context: Context, habitId: String) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        am.cancel(reminderPendingIntent(context, habitId))
    }

    /** 「推迟」到点重弹：独立闹钟，不动每日调度。 */
    fun scheduleSnooze(context: Context, habitId: String, minutes: Int) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val trigger = System.currentTimeMillis() +
            minutes.coerceIn(1, MAX_SNOOZE_MINUTES).toLong() * 60_000L
        val pi = snoozePendingIntent(context, habitId)
        try {
            val canExact = Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()
            if (canExact) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pi)
            } else {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pi)
            }
        } catch (e: SecurityException) {
            LunoLog.w(LOG_TAG, "habit snooze exact alarm denied, fallback: ${e.message}")
            runCatching { am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pi) }
        }
        LunoLog.i(LOG_TAG, "habit snooze scheduled: habit=$habitId minutes=$minutes")
    }

    fun cancelSnooze(context: Context, habitId: String) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        am.cancel(snoozePendingIntent(context, habitId))
    }

    private fun snoozePendingIntent(context: Context, habitId: String): PendingIntent {
        val intent = Intent(context, HabitReminderReceiver::class.java).apply {
            action = ACTION_SNOOZE_FIRE
            putExtra(EXTRA_HABIT_ID, habitId)
        }
        return PendingIntent.getBroadcast(
            context,
            SNOOZE_REQUEST_MAGIC xor habitId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    /** 下一个本地 0 点：通知只活到当天，跨日统一收起。 */
    private fun setExpiryAlarm(context: Context) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val trigger = LocalDate.now().plusDays(1)
            .atStartOfDay(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()
        val pi = expiryPendingIntent(context)
        try {
            val canExact = Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()
            if (canExact) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pi)
            } else {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pi)
            }
        } catch (e: SecurityException) {
            LunoLog.w(LOG_TAG, "habit reminder expiry exact alarm denied, fallback: ${e.message}")
            runCatching { am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pi) }
        }
    }

    private fun cancelExpiryAlarm(context: Context) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        am.cancel(expiryPendingIntent(context))
    }

    private fun expiryPendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, HabitReminderReceiver::class.java).apply {
            action = ACTION_EXPIRE
        }
        return PendingIntent.getBroadcast(
            context,
            EXPIRY_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun reminderPendingIntent(context: Context, habitId: String): PendingIntent {
        val intent = Intent(context, HabitReminderReceiver::class.java).apply {
            action = ACTION_REMINDER
            putExtra(EXTRA_HABIT_ID, habitId)
        }
        return PendingIntent.getBroadcast(
            context,
            requestCode(habitId),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    /** 同一习惯的提醒闹钟与通知共用一个请求码，跨习惯靠 hashCode 区分。 */
    fun requestCode(habitId: String): Int = REQUEST_MAGIC xor habitId.hashCode()

    /** 通知 id（与闹钟请求码分离，规避旧版本遗留的 snooze）。 */
    fun notificationId(habitId: String): Int = NOTIFICATION_MAGIC xor habitId.hashCode()
}
