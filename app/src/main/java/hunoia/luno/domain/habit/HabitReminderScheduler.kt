package hunoia.luno.domain.habit

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import hunoia.luno.config.ConfigProvider
import hunoia.luno.config.model.Habit
import hunoia.luno.config.model.HabitConfig
import hunoia.luno.config.model.HabitDayState
import hunoia.luno.config.model.HabitMaxSnoozeMinutes
import hunoia.luno.config.model.NotificationKind
import hunoia.luno.config.model.NotificationMode
import hunoia.luno.config.model.habitDayState
import hunoia.luno.core.LOG_TAG
import hunoia.luno.core.LunoLog
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
 * 习惯提醒调度：到「应打卡日」的每个提醒时间槽弹常驻通知。
 *
 * 口径（参照 moting ReminderScheduler）：
 * - 精确闹钟 [AlarmManager.setExactAndAllowWhileIdle]，API 31+ 先查 [AlarmManager.canScheduleExactAlarms]，
 *   无精确权限或 [SecurityException] 时降级 [AlarmManager.setAndAllowWhileIdle]（非精确）。
 * - **每习惯 × 每提醒时间槽一个闹钟**；请求码含 habitId + 槽位，互不覆盖。
 * - 重排幂等：先按上次落盘的「已调度槽位」逐个取消，再按最新配置重排。
 *   删习惯 / 删时间 / 改时间 / 关提醒都不会留下旧闹钟。
 * - 排未来的「应打卡日」（DUE）与「逾期日」（OVERDUE，仍可补打）；跳过日（SKIPPED）是休整日
 *   不提醒，已完成（CHECKED）日不提醒。逾期后每日提醒（常驻通知按 id 刷新），直到补打回到周期；
 *   未开始（无打卡记录）的习惯从今天起每日提醒，直到首次打卡进入正常周期。
 * - 补弹：当天最近一个已过的提醒槽仍未完成时立即补弹一次；同一习惯同一天同一槽只补一次
 *   （落盘 lastNotifiedDay / lastNotifiedSlot）。
 * - 跨日：每天 0 点统一收起昨天的在屏通知，再按新的一天重排，常驻通知不跨天。
 */
object HabitReminderScheduler {

    const val ACTION_REMINDER = "hunoia.luno.action.HABIT_REMINDER"

    /** 通知 / 岛上的「+1」：给当天进度加一次（幂等）。 */
    const val ACTION_MARK_DONE = "hunoia.luno.action.HABIT_REMINDER_DONE"

    /** 通知 / 岛上的「推迟」请求：收起当前提醒，并在 N 分钟后重弹（N 来自习惯设置）。 */
    const val ACTION_SNOOZE = "hunoia.luno.action.HABIT_REMINDER_SNOOZE"

    /** 推迟到点：重算状态后重弹（已完成 / 休整日不再弹）。 */
    const val ACTION_SNOOZE_FIRE = "hunoia.luno.action.HABIT_REMINDER_SNOOZE_FIRE"

    /** 每日 0 点：收起前一天仍在屏的提醒，避免常驻通知跨到第二天。 */
    const val ACTION_EXPIRE = "hunoia.luno.action.HABIT_REMINDER_EXPIRE"
    const val EXTRA_HABIT_ID = "hunoia.luno.extra.HABIT_REMINDER_ID"
    const val EXTRA_DAY = "hunoia.luno.extra.HABIT_REMINDER_DAY"
    const val EXTRA_SNOOZE_MINUTES = "hunoia.luno.extra.HABIT_REMINDER_SNOOZE_MINUTES"

    /** 提醒时间槽（当天分钟数）；「+1」/ 推迟 / 补弹都带上它，便于判断「更晚的槽是否已弹过」。 */
    const val EXTRA_SLOT = "hunoia.luno.extra.HABIT_REMINDER_SLOT"

    /** 与通知 id 同源的请求码种子（"HABT"）；与 habitId + 槽位异或避免跨习惯 / 跨槽碰撞。 */
    private const val REQUEST_MAGIC = 0x48414254

    /**
     * 通知 id 单独用另一套种子（"HBRT"）：早期版本按闹钟请求码发通知，那个 id 可能被
     * 「固定通知隐藏」snooze 过（系统会静默一年且重新 post 不会解除）。换 id 可绕过旧 snooze。
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

    /** 记录上次参与调度的习惯 id：删习惯后配置里查不到它，只能靠差集取消残留闹钟 / 通知。 */
    private const val PREFS_NAME = "habit_reminder_state"
    private const val KEY_KNOWN_IDS = "known_habit_ids"

    /** 每习惯上次调度的槽位集合（分钟数）；重排前逐个取消，避免改时间 / 删时间后残留。 */
    private const val KEY_SCHEDULED_SLOTS_PREFIX = "scheduled_slots_"

    /** 每个习惯最后一次真正弹过通知的 epochDay + 当时提醒时间槽（分钟）；同一槽同一天不重复补弹。 */
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
     * 覆盖编辑页保存、部件打卡、通知「+1」等所有写入口，gridpanel 无需反向依赖 app。
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
            val knownIds = prefs.getStringSet(KEY_KNOWN_IDS, emptySet()).orEmpty()
            val scheduledIds = prefs.all.keys
                .filter { it.startsWith(KEY_SCHEDULED_SLOTS_PREFIX) }
                .map { it.removePrefix(KEY_SCHEDULED_SLOTS_PREFIX) }
                .toSet()
            // 上次调度过的所有槽位先取消；即使习惯已删 / 时间已改也收得干净。
            (currentIds + knownIds + scheduledIds).forEach { habitId ->
                scheduledSlots(prefs, habitId).forEach { slot -> cancelAlarm(app, habitId, slot) }
                prefs.edit().remove(scheduledKey(habitId)).apply()
            }
            // 已删除的习惯：配置里已经没有它，只能按上次集合补收通知 / 推迟。
            (knownIds - currentIds).forEach { habitId ->
                cancelSnooze(app, habitId)
                HabitReminderNotifier.cancel(app, habitId)
                prefs.edit()
                    .remove(lastNotifiedKey(habitId))
                    .remove(lastNotifiedSlotKey(habitId))
                    .apply()
            }
            var anyReminder = false
            config.habits.forEach { habit ->
                val checks = config.checks[habit.id].orEmpty()
                val state = habitDayState(checks, today, habit.interval, today)
                // 今天已完成 / 今天是跳过日：在屏的提醒通知应随之收起。
                if (state == HabitDayState.CHECKED || state == HabitDayState.SKIPPED) {
                    HabitReminderNotifier.cancel(app, habit.id)
                    cancelSnooze(app, habit.id)
                }
                val slots = habit.reminderTimes
                if (slots.isEmpty()) {
                    // 用户关掉提醒 / 没设时间：仍在屏的常驻通知与在途的推迟闹钟一并收起。
                    HabitReminderNotifier.cancel(app, habit.id)
                    cancelSnooze(app, habit.id)
                    return@forEach
                }
                anyReminder = true
                val lastNotified = prefs.getLong(lastNotifiedKey(habit.id), Long.MIN_VALUE)
                val lastSlot = prefs.getInt(lastNotifiedSlotKey(habit.id), Int.MIN_VALUE)
                val catchUpSlot = if (HabitReminderNotifier.isShowing(app, habit.id)) {
                    null
                } else {
                    latestCatchUpSlot(habit, checks, today, now, lastNotified, lastSlot)
                }
                val scheduled = mutableSetOf<Int>()
                slots.forEach { reminder ->
                    val slot = reminder.minutesOfDay
                    val trigger = if (slot == catchUpSlot) {
                        now + CATCH_UP_DELAY_MS
                    } else {
                        nextTriggerMillis(habit, checks, today, now, slot)
                    }
                    if (trigger == null) return@forEach
                    setAlarm(app, habit.id, slot, trigger)
                    scheduled += slot
                    LunoLog.i(
                        LOG_TAG,
                        "habit reminder scheduled: habit=${habit.id} slot=$slot trigger=$trigger " +
                            "catchUp=${slot == catchUpSlot} (${java.time.Instant.ofEpochMilli(trigger)})",
                    )
                }
                prefs.edit()
                    .putStringSet(scheduledKey(habit.id), scheduled.map { it.toString() }.toSet())
                    .commit()
            }
            if (anyReminder) {
                // 每天 0 点统一收起昨天的提醒：常驻通知不跨天。
                setExpiryAlarm(app)
            } else {
                cancelExpiryAlarm(app)
            }
            prefs.edit().putStringSet(KEY_KNOWN_IDS, currentIds).commit()
        }
    }

    /** 某提醒槽下一次触发时刻；无未来应打卡日返回 null。 */
    fun nextTriggerMillis(
        habit: Habit,
        checks: Set<Long>,
        todayEpochDay: Long,
        now: Long,
        slotMinutes: Int,
    ): Long? {
        val zone = ZoneId.systemDefault()
        if (checks.isEmpty()) {
            val todayTrigger = triggerAtMillis(todayEpochDay, slotMinutes, zone)
            return if (todayTrigger > now) {
                todayTrigger
            } else {
                triggerAtMillis(todayEpochDay + 1, slotMinutes, zone)
            }
        }
        val interval = habit.interval.coerceAtLeast(1)
        var day = todayEpochDay
        while (day <= todayEpochDay + LOOKAHEAD_DAYS) {
            // 应打卡日（DUE）与逾期日（OVERDUE，仍可补打）都要提醒；跳过日（SKIPPED）是休整日不提醒。
            // 逾期后每天都提醒（常驻通知按 id 刷新），直到用户补打回到正常周期。
            when (habitDayState(checks, day, interval, todayEpochDay)) {
                HabitDayState.DUE, HabitDayState.OVERDUE -> {
                    val trigger = triggerAtMillis(day, slotMinutes, zone)
                    if (trigger > now) return trigger
                }
                else -> Unit
            }
            day++
        }
        return null
    }

    /**
     * 当天最近一个已过的提醒槽、且该槽还没补弹过时，返回该槽（分钟）；否则 null。
     * 只看最近一个已过槽：用户下午打开应用时，不会把上午所有错过的提醒一次弹完。
     */
    fun latestCatchUpSlot(
        habit: Habit,
        checks: Set<Long>,
        todayEpochDay: Long,
        now: Long,
        lastNotifiedDay: Long,
        lastNotifiedSlot: Int = Int.MIN_VALUE,
    ): Int? {
        val slots = habit.reminderTimes
        if (slots.isEmpty()) return null
        val state = habitDayState(
            checks,
            todayEpochDay,
            habit.interval.coerceAtLeast(1),
            todayEpochDay,
        )
        if (state == HabitDayState.CHECKED || state == HabitDayState.SKIPPED) return null
        val zone = ZoneId.systemDefault()
        val latestPast = slots.lastOrNull { reminder ->
            triggerAtMillis(todayEpochDay, reminder.minutesOfDay, zone) <= now
        } ?: return null
        val slot = latestPast.minutesOfDay
        return if (lastNotifiedDay == todayEpochDay && lastNotifiedSlot == slot) null else slot
    }

    /** 记录某习惯某天某槽已弹过通知（接收器真正展示后调用）。 */
    fun markNotified(context: Context, habitId: String, day: Long, slot: Int) {
        val committed = context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putLong(lastNotifiedKey(habitId), day)
            .putInt(lastNotifiedSlotKey(habitId), slot)
            // 用 commit：接收器紧接着可能结束进程，apply 的异步落盘会丢。
            .commit()
        LunoLog.i(LOG_TAG, "habit reminder notified: habit=$habitId day=$day slot=$slot persisted=$committed")
    }

    /** 上次真正弹过通知的 (epochDay, 槽位)；没有记录返回 (MIN, MIN)。 */
    fun lastNotified(context: Context, habitId: String): Pair<Long, Int> {
        val prefs = context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getLong(lastNotifiedKey(habitId), Long.MIN_VALUE) to
            prefs.getInt(lastNotifiedSlotKey(habitId), Int.MIN_VALUE)
    }

    /** 上次参与调度的习惯 id（接收器跨日收起通知用）。 */
    fun knownHabitIds(context: Context): Set<String> =
        context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getStringSet(KEY_KNOWN_IDS, emptySet())
            .orEmpty()

    private fun lastNotifiedKey(habitId: String): String = KEY_LAST_NOTIFIED_PREFIX + habitId

    private fun lastNotifiedSlotKey(habitId: String): String = KEY_LAST_NOTIFIED_SLOT_PREFIX + habitId

    private fun scheduledKey(habitId: String): String = KEY_SCHEDULED_SLOTS_PREFIX + habitId

    private fun scheduledSlots(prefs: SharedPreferences, habitId: String): Set<Int> =
        prefs.getStringSet(scheduledKey(habitId), emptySet())
            .orEmpty()
            .mapNotNull { it.toIntOrNull() }
            .toSet()

    private fun triggerAtMillis(day: Long, slotMinutes: Int, zone: ZoneId): Long {
        val date = LocalDate.ofEpochDay(day)
        val safeSlot = slotMinutes.coerceIn(0, 23 * 60 + 59)
        return LocalDateTime.of(
            date.year, date.monthValue, date.dayOfMonth,
            safeSlot / 60, safeSlot % 60,
        ).atZone(zone).toInstant().toEpochMilli()
    }

    private fun setAlarm(context: Context, habitId: String, slot: Int, triggerMillis: Long) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val pi = reminderPendingIntent(context, habitId, slot)
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

    private fun cancelAlarm(context: Context, habitId: String, slot: Int) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        am.cancel(reminderPendingIntent(context, habitId, slot))
    }

    /** 「推迟」到点重弹：独立闹钟，不动每日调度。 */
    fun scheduleSnooze(context: Context, habitId: String, minutes: Int, slot: Int) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val safeMinutes = minutes.coerceIn(1, HabitMaxSnoozeMinutes)
        val trigger = System.currentTimeMillis() + safeMinutes.toLong() * 60_000L
        val pi = snoozePendingIntent(context, habitId, slot)
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
        LunoLog.i(LOG_TAG, "habit snooze scheduled: habit=$habitId minutes=$safeMinutes slot=$slot")
    }

    fun cancelSnooze(context: Context, habitId: String) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        // 同一习惯同一时刻只有一个在途的推迟闹钟：请求码只按 habitId 区分，槽位放在 extra 里。
        am.cancel(snoozePendingIntent(context, habitId, slot = 0))
    }

    private fun snoozePendingIntent(context: Context, habitId: String, slot: Int): PendingIntent {
        val intent = Intent(context, HabitReminderReceiver::class.java).apply {
            action = ACTION_SNOOZE_FIRE
            putExtra(EXTRA_HABIT_ID, habitId)
            putExtra(EXTRA_SLOT, slot)
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

    private fun reminderPendingIntent(context: Context, habitId: String, slot: Int): PendingIntent {
        val intent = Intent(context, HabitReminderReceiver::class.java).apply {
            action = ACTION_REMINDER
            putExtra(EXTRA_HABIT_ID, habitId)
            putExtra(EXTRA_SLOT, slot)
        }
        return PendingIntent.getBroadcast(
            context,
            requestCode(habitId, slot),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    /** 同一习惯的每个提醒槽一个请求码；跨习惯 / 跨槽靠 hash 区分。 */
    fun requestCode(habitId: String, slot: Int): Int =
        REQUEST_MAGIC xor (habitId + ":" + slot).hashCode()

    /** 通知 id（与闹钟请求码分离，规避旧版本遗留的 snooze）。 */
    fun notificationId(habitId: String): Int = NOTIFICATION_MAGIC xor habitId.hashCode()
}
