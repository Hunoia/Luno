package hunoia.luno.domain.todo

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import hunoia.luno.config.ConfigProvider
import hunoia.luno.config.model.NotificationKind
import hunoia.luno.config.model.NotificationMode
import hunoia.luno.config.model.TodoConfig
import hunoia.luno.config.model.TodoItem
import hunoia.luno.config.model.TodoMaxSnoozeMinutes
import hunoia.luno.config.model.TodoMinSnoozeMinutes
import hunoia.luno.config.model.TodoReminder
import hunoia.luno.core.LOG_TAG
import hunoia.luno.core.LunoLog
import hunoia.luno.domain.gridpanel.TodoCenter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.time.ZoneId
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 待办提醒调度：每个未完成待办最多一个一次性闹钟。
 *
 * 口径：
 * - 提醒按**本地墙钟**（[TodoReminder.day] + [TodoReminder.minuteOfDay]）重算绝对时刻；
 *   时区 / 手动改时间后由 `TimeChangeReceiver` 触发重排。
 * - 精确闹钟 [AlarmManager.setExactAndAllowWhileIdle]；API 31+ 无权限或 [SecurityException]
 *   时降级 [AlarmManager.setAndAllowWhileIdle]（沿用习惯提醒策略）。
 * - **补弹判据是「排过期但没弹成」**：SharedPreferences 里的 `scheduled_<id>` 标记还在、
 *   提醒时间已过 → 开机 / 打开应用时补弹一次并清标记。这样：
 *   * 关机 / 闹钟被系统清掉 / 应用被强停 → 标记还在 → 补弹；
 *   * 完成待办时提醒还在未来 → 取消并清标记 → 之后撤销完成、时间已过也不会补弹；
 *   * 备份恢复到新设备 → 没有标记 → 旧提醒只显示「已过期」，不会开机炸一堆通知。
 * - 推迟是独立的一次性闹钟，不改原提醒时间；`snooze_<id>` 记录提醒 key + 到点时刻，
 *   提醒被改 / 被删时一并取消。
 */
object TodoReminderScheduler {

    const val ACTION_REMINDER = "hunoia.luno.action.TODO_REMINDER"
    const val ACTION_COMPLETE = "hunoia.luno.action.TODO_REMINDER_COMPLETE"
    const val ACTION_SNOOZE = "hunoia.luno.action.TODO_REMINDER_SNOOZE"
    const val ACTION_SNOOZE_FIRE = "hunoia.luno.action.TODO_REMINDER_SNOOZE_FIRE"

    const val EXTRA_TODO_ID = "hunoia.luno.extra.TODO_REMINDER_ID"

    /** 闹钟 / 通知 / 点击的请求码种子（"TODO" / "TODS" / "TODN" / "TODE"），互不覆盖。 */
    private const val REQUEST_MAGIC = 0x544F444F
    private const val SNOOZE_REQUEST_MAGIC = 0x544F4453
    private const val NOTIFICATION_MAGIC = 0x544F444E
    private const val EDIT_REQUEST_MAGIC = 0x544F4445
    private const val COMPLETE_REQUEST_MAGIC = 0x544F4443
    private const val SNOOZE_ACTION_REQUEST_MAGIC = 0x544F445A

    private const val PREFS_NAME = "todo_reminder_state"
    private const val KEY_KNOWN_IDS = "known_todo_ids"
    private const val KEY_SCHEDULED_PREFIX = "scheduled_"
    private const val KEY_SNOOZE_PREFIX = "snooze_"
    private const val KEY_NOTIFIED_PREFIX = "notified_"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val observing = AtomicBoolean(false)

    /** 配置流与接收器可能并发重排；闹钟 / 通知 / prefs 都按同一把锁串行化。 */
    private val lock = Any()

    /** 通知呈现模式：通用通知设置里 [NotificationKind.TODO] 的最终生效值。 */
    suspend fun currentMode(): NotificationMode =
        runCatching {
            ConfigProvider.getAdvancedSettings().notificationDisplay.modeFor(NotificationKind.TODO)
        }.getOrDefault(NotificationMode.DEFAULT)

    /** 订阅待办配置：增删改 / 完成 / 撤销后按最新配置重排（进程内一次）。 */
    fun startObserving(context: Context) {
        if (!observing.compareAndSet(false, true)) return
        val app = context.applicationContext
        scope.launch {
            TodoCenter.todoConfig.collectLatest { config ->
                rescheduleAll(app, config)
            }
        }
    }

    /** 入口：加载配置后重排（开机 / 应用更新 / 时间变化 / 应用打开）。 */
    fun rescheduleAll(context: Context) {
        val app = context.applicationContext
        scope.launch {
            val config = runCatching { TodoCenter.getTodoConfig() }.getOrNull()
            if (config == null) {
                LunoLog.w(LOG_TAG, "todo reminder reschedule: config load failed")
                return@launch
            }
            rescheduleAll(app, config)
        }
    }

    /**
     * 同步重排 + 锁外补弹：调用方已持有配置（接收器触发后用）。
     * 提醒已过但 `scheduled_<id>` 标记仍在 → 补弹；标记不在 → 只当作已过期。
     */
    suspend fun rescheduleAll(context: Context, config: TodoConfig) {
        val app = context.applicationContext
        val now = System.currentTimeMillis()
        val zone = ZoneId.systemDefault()
        val catchUps = mutableListOf<TodoItem>()
        val snoozeCatchUps = mutableListOf<TodoItem>()
        synchronized(lock) {
            val prefs = app.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val currentIds = config.todos.map { it.id }.toSet()
            val knownIds = prefs.getStringSet(KEY_KNOWN_IDS, emptySet()).orEmpty()
            val scheduledIds = prefs.all.keys
                .filter { it.startsWith(KEY_SCHEDULED_PREFIX) }
                .map { it.removePrefix(KEY_SCHEDULED_PREFIX) }
                .toSet()
            val notifiedIds = prefs.all.keys
                .filter { it.startsWith(KEY_NOTIFIED_PREFIX) }
                .map { it.removePrefix(KEY_NOTIFIED_PREFIX) }
                .toSet()
            val allIds = currentIds + knownIds + scheduledIds + notifiedIds
            // 先读上次标记，再统一取消 / 清理（否则补弹判据会被自己抹掉）。
            val previousScheduled = allIds.associateWith { prefs.getString(scheduledKey(it), null) }
            val previousNotified = allIds.associateWith { prefs.getString(notifiedKey(it), null) }
            val editor = prefs.edit()
            allIds.forEach { todoId ->
                cancelReminderAlarm(app, todoId)
                editor.remove(scheduledKey(todoId))
            }
            // 已删除的待办：配置里查不到它，只能按上次集合补收通知 / 推迟。
            (allIds - currentIds).forEach { todoId ->
                cancelSnoozeAlarm(app, todoId)
                TodoReminderNotifier.cancel(app, todoId)
                editor.remove(snoozeKey(todoId))
                editor.remove(notifiedKey(todoId))
            }
            config.todos.forEach { todo ->
                val reminder = todo.reminder
                if (todo.done || reminder == null) {
                    // 完成 / 删提醒：收起在屏通知与在途推迟，但保留 reminder 数据供撤销完成恢复。
                    cancelSnoozeAlarm(app, todo.id)
                    TodoReminderNotifier.cancel(app, todo.id)
                    editor.remove(snoozeKey(todo.id))
                    editor.remove(notifiedKey(todo.id))
                    return@forEach
                }
                // 提醒时间被改：收起旧提醒的在屏通知（文字编辑不改 key，通知保留）。
                val notified = previousNotified[todo.id]
                if (notified != null && notified != reminder.key()) {
                    TodoReminderNotifier.cancel(app, todo.id)
                    editor.remove(notifiedKey(todo.id))
                }
                // 推迟：提醒被改 / 被删则取消；到点未弹成则补弹；未来则重建闹钟。
                val snooze = parseSnooze(prefs.getString(snoozeKey(todo.id), null))
                when {
                    snooze == null -> Unit
                    snooze.reminderKey != reminder.key() -> {
                        cancelSnoozeAlarm(app, todo.id)
                        editor.remove(snoozeKey(todo.id))
                    }
                    snooze.fireAtMillis > now -> setSnoozeAlarm(app, todo.id, snooze.fireAtMillis)
                    else -> {
                        cancelSnoozeAlarm(app, todo.id)
                        editor.remove(snoozeKey(todo.id))
                        if (!TodoReminderNotifier.isShowing(app, todo.id)) {
                            snoozeCatchUps += todo
                        }
                    }
                }
                // 一次性提醒：未来排期；已过且标记还在 → 补弹；否则只当作已过期。
                if (!reminder.isPast(now, zone)) {
                    setReminderAlarm(app, todo.id, reminder.toEpochMillis(zone))
                    editor.putString(scheduledKey(todo.id), reminder.key())
                } else if (shouldCatchUp(reminder, previousScheduled[todo.id], now, zone) &&
                    !TodoReminderNotifier.isShowing(app, todo.id)
                ) {
                    catchUps += todo
                }
            }
            editor.putStringSet(KEY_KNOWN_IDS, currentIds).commit()
        }
        // 锁外发通知：ISLAND 发布是带校验窗口的挂起事务，不能占着锁。
        val mode = currentMode()
        val snoozeMinutes = config.safeReminderSnoozeMinutes()
        catchUps.forEach { todo ->
            val reminder = todo.reminder ?: return@forEach
            runCatching {
                TodoReminderNotifier.show(
                    context = app,
                    todo = todo,
                    reminder = reminder,
                    snoozeMinutes = snoozeMinutes,
                    mode = mode,
                    overdue = true,
                    silent = true,
                )
            }.onSuccess { shown ->
                if (shown) markNotified(app, todo.id, reminder.key())
            }.onFailure {
                LunoLog.w(LOG_TAG, "todo reminder catch-up failed: todo=${todo.id} ${it.message}")
            }
        }
        snoozeCatchUps.forEach { todo ->
            val reminder = todo.reminder ?: return@forEach
            runCatching {
                TodoReminderNotifier.show(
                    context = app,
                    todo = todo,
                    reminder = reminder,
                    snoozeMinutes = snoozeMinutes,
                    mode = mode,
                    overdue = false,
                    silent = true,
                )
            }.onSuccess { shown ->
                if (shown) markNotified(app, todo.id, reminder.key())
            }.onFailure {
                LunoLog.w(LOG_TAG, "todo snooze catch-up failed: todo=${todo.id} ${it.message}")
            }
        }
    }

    /** 接收器触发前同步清标记：即使随后进程被杀，也不会在下次重排时重复补弹。 */
    fun clearScheduled(context: Context, todoId: String) {
        context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .remove(scheduledKey(todoId))
            .commit()
    }

    /** 记录「当前在屏通知对应哪条提醒」，提醒时间被改时据此收起旧通知。 */
    fun markNotified(context: Context, todoId: String, reminderKey: String) {
        context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(notifiedKey(todoId), reminderKey)
            .commit()
    }

    fun clearNotified(context: Context, todoId: String) {
        context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .remove(notifiedKey(todoId))
            .apply()
    }

    /** 「推迟 N 分钟」：独立一次性闹钟，不改原提醒时间。 */
    fun scheduleSnooze(context: Context, todoId: String, reminderKey: String, minutes: Int) {
        val app = context.applicationContext
        val safeMinutes = minutes.coerceIn(TodoMinSnoozeMinutes, TodoMaxSnoozeMinutes)
        val fireAt = System.currentTimeMillis() + safeMinutes.toLong() * 60_000L
        setSnoozeAlarm(app, todoId, fireAt)
        app.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(snoozeKey(todoId), "$reminderKey|$fireAt")
            .commit()
        LunoLog.i(LOG_TAG, "todo snooze scheduled: todo=$todoId minutes=$safeMinutes fireAt=$fireAt")
    }

    fun cancelSnooze(context: Context, todoId: String) {
        val app = context.applicationContext
        cancelSnoozeAlarm(app, todoId)
        app.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .remove(snoozeKey(todoId))
            .apply()
    }

    /** 同一待办的通知 id；与闹钟 / 点击请求码分离。 */
    fun notificationId(todoId: String): Int = NOTIFICATION_MAGIC xor todoId.hashCode()

    /** 编辑弹窗 PendingIntent 的请求码。 */
    fun editRequestCode(todoId: String): Int = EDIT_REQUEST_MAGIC xor todoId.hashCode()

    /** 通知「完成」按钮的广播请求码。 */
    fun completeRequestCode(todoId: String): Int =
        COMPLETE_REQUEST_MAGIC xor todoId.hashCode()

    /** 通知「推迟」按钮的广播请求码。 */
    fun snoozeActionRequestCode(todoId: String): Int =
        SNOOZE_ACTION_REQUEST_MAGIC xor todoId.hashCode()

    /** 提醒闹钟请求码（每条待办一个）。 */
    fun requestCode(todoId: String): Int = REQUEST_MAGIC xor todoId.hashCode()

    private fun snoozeRequestCode(todoId: String): Int =
        SNOOZE_REQUEST_MAGIC xor todoId.hashCode()

    private fun scheduledKey(todoId: String): String = KEY_SCHEDULED_PREFIX + todoId

    private fun notifiedKey(todoId: String): String = KEY_NOTIFIED_PREFIX + todoId

    private fun snoozeKey(todoId: String): String = KEY_SNOOZE_PREFIX + todoId

    private fun setReminderAlarm(context: Context, todoId: String, triggerMillis: Long) {
        setAlarm(context, triggerMillis, reminderPendingIntent(context, todoId), "todo reminder")
    }

    private fun cancelReminderAlarm(context: Context, todoId: String) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        am.cancel(reminderPendingIntent(context, todoId))
    }

    private fun setSnoozeAlarm(context: Context, todoId: String, triggerMillis: Long) {
        setAlarm(context, triggerMillis, snoozePendingIntent(context, todoId), "todo snooze")
    }

    private fun cancelSnoozeAlarm(context: Context, todoId: String) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        am.cancel(snoozePendingIntent(context, todoId))
    }

    private fun setAlarm(
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
            // 无精确闹钟权限（Android 12+ 用户可关）：降级非精确，宁可晚到也不丢提醒。
            LunoLog.w(LOG_TAG, "$label exact alarm denied, fallback: ${e.message}")
            runCatching {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerMillis, pendingIntent)
            }
        }
        LunoLog.i(LOG_TAG, "$label scheduled: trigger=$triggerMillis")
    }

    private fun reminderPendingIntent(context: Context, todoId: String): PendingIntent {
        val intent = Intent(context, TodoReminderReceiver::class.java).apply {
            action = ACTION_REMINDER
            data = Uri.parse("luno://todo-reminder/$todoId")
            putExtra(EXTRA_TODO_ID, todoId)
        }
        return PendingIntent.getBroadcast(
            context,
            requestCode(todoId),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun snoozePendingIntent(context: Context, todoId: String): PendingIntent {
        val intent = Intent(context, TodoReminderReceiver::class.java).apply {
            action = ACTION_SNOOZE_FIRE
            data = Uri.parse("luno://todo-reminder-snooze/$todoId")
            putExtra(EXTRA_TODO_ID, todoId)
        }
        return PendingIntent.getBroadcast(
            context,
            snoozeRequestCode(todoId),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}

/** 推迟运行态：提醒 key + 到点时刻（prefs 编码为 `key|fireAt`）。 */
internal data class SnoozeState(val reminderKey: String, val fireAtMillis: Long)

internal fun parseSnooze(raw: String?): SnoozeState? {
    if (raw.isNullOrBlank()) return null
    val separator = raw.lastIndexOf('|')
    if (separator <= 0 || separator == raw.lastIndex) return null
    val fireAt = raw.substring(separator + 1).toLongOrNull() ?: return null
    return SnoozeState(raw.substring(0, separator), fireAt)
}

/** 补弹判据：提醒已过、且上次确实为这条提醒排过闹钟（标记匹配）。 */
internal fun shouldCatchUp(
    reminder: TodoReminder,
    previousScheduledKey: String?,
    nowMillis: Long,
    zone: ZoneId = ZoneId.systemDefault(),
): Boolean = reminder.isPast(nowMillis, zone) && previousScheduledKey == reminder.key()
