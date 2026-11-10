package hunoia.luno.domain.todo

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import hunoia.luno.core.LOG_TAG
import hunoia.luno.core.LunoLog
import hunoia.luno.domain.gridpanel.TodoCenter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * 待办提醒接收器（主进程，AlarmManager 触发 / 通知按钮）。
 *
 * - ACTION_REMINDER：到点。`goAsync` + 协程读配置；待办已删除 / 已完成 / 提醒已清除则静默收掉。
 *   先清 `scheduled_<id>` 标记再弹（进程被杀也不会在下次重排时重复补弹）。
 * - ACTION_SNOOZE：收起当前通知并挂一次性闹钟；不改原提醒时间。
 * - ACTION_SNOOZE_FIRE：推迟到点；待办已完成 / 已删除 / 提醒被改则不再弹。
 * - ACTION_COMPLETE：通知「完成」→ 直接标记完成（不再二次确认），收起通知与推迟。
 */
class TodoReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext
        val todoId = intent.getStringExtra(TodoReminderScheduler.EXTRA_TODO_ID) ?: return
        when (intent.action) {
            TodoReminderScheduler.ACTION_REMINDER -> onReminder(app, todoId)
            TodoReminderScheduler.ACTION_SNOOZE -> onSnooze(app, todoId)
            TodoReminderScheduler.ACTION_SNOOZE_FIRE -> onSnoozeFire(app, todoId)
            TodoReminderScheduler.ACTION_COMPLETE -> onComplete(app, todoId)
            else -> Unit
        }
    }

    private fun onReminder(context: Context, todoId: String) {
        // 先同步清标记：即使随后进程被杀，也不会在下次重排时重复补弹。
        TodoReminderScheduler.clearScheduled(context, todoId)
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val config = TodoCenter.getTodoConfig()
                val todo = config.todoById(todoId)
                val reminder = todo?.reminder
                if (todo == null || todo.done || reminder == null) {
                    LunoLog.i(LOG_TAG, "todo reminder skipped: todo=$todoId missing/done/no-reminder")
                    TodoReminderNotifier.cancel(context, todoId)
                    TodoReminderScheduler.clearNotified(context, todoId)
                    return@launch
                }
                LunoLog.i(LOG_TAG, "todo reminder fire: todo=$todoId reminder=${reminder.key()}")
                // 到点触发：即使闹钟被系统晚放几秒，正文也按「提醒时间」显示；
                // 「已过期」只用于开机 / 打开应用时的补弹（见 rescheduleAll）。
                val shown = TodoReminderNotifier.show(
                    context = context,
                    todo = todo,
                    reminder = reminder,
                    snoozeMinutes = config.safeReminderSnoozeMinutes(),
                    mode = TodoReminderScheduler.currentMode(),
                    overdue = false,
                    silent = false,
                )
                if (shown) {
                    TodoReminderScheduler.markNotified(context, todoId, reminder.key())
                }
            } catch (e: Exception) {
                LunoLog.w(LOG_TAG, "todo reminder fire failed: ${e.message}")
            } finally {
                pending.finish()
            }
        }
    }

    /** 「推迟 N 分钟」：收起当前提醒并挂一次性闹钟；原提醒时间不变。 */
    private fun onSnooze(context: Context, todoId: String) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val config = TodoCenter.getTodoConfig()
                val todo = config.todoById(todoId)
                val reminder = todo?.reminder
                if (todo == null || todo.done || reminder == null) {
                    TodoReminderNotifier.cancel(context, todoId)
                    TodoReminderScheduler.clearNotified(context, todoId)
                    return@launch
                }
                val minutes = config.safeReminderSnoozeMinutes()
                LunoLog.i(LOG_TAG, "todo reminder snooze: todo=$todoId minutes=$minutes")
                TodoReminderNotifier.cancel(context, todoId)
                TodoReminderScheduler.clearNotified(context, todoId)
                TodoReminderScheduler.scheduleSnooze(context, todoId, reminder.key(), minutes)
            } catch (e: Exception) {
                LunoLog.w(LOG_TAG, "todo reminder snooze failed: ${e.message}")
            } finally {
                pending.finish()
            }
        }
    }

    /** 推迟到点：配置里已无该提醒（完成 / 删除 / 改时间）就静默收掉。 */
    private fun onSnoozeFire(context: Context, todoId: String) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val config = TodoCenter.getTodoConfig()
                val todo = config.todoById(todoId)
                val reminder = todo?.reminder
                if (todo == null || todo.done || reminder == null) {
                    LunoLog.i(LOG_TAG, "todo snooze fire skipped: todo=$todoId missing/done/no-reminder")
                    TodoReminderNotifier.cancel(context, todoId)
                    TodoReminderScheduler.clearNotified(context, todoId)
                    return@launch
                }
                LunoLog.i(LOG_TAG, "todo snooze fire: todo=$todoId reminder=${reminder.key()}")
                val shown = TodoReminderNotifier.show(
                    context = context,
                    todo = todo,
                    reminder = reminder,
                    snoozeMinutes = config.safeReminderSnoozeMinutes(),
                    mode = TodoReminderScheduler.currentMode(),
                    overdue = false,
                    silent = false,
                )
                if (shown) {
                    TodoReminderScheduler.markNotified(context, todoId, reminder.key())
                }
            } catch (e: Exception) {
                LunoLog.w(LOG_TAG, "todo snooze fire failed: ${e.message}")
            } finally {
                pending.finish()
            }
        }
    }

    /** 通知「完成」：直接标记完成，不再二次确认。 */
    private fun onComplete(context: Context, todoId: String) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val todo = TodoCenter.getTodoConfig().todoById(todoId)
                if (todo == null) {
                    TodoReminderNotifier.cancel(context, todoId)
                    return@launch
                }
                if (!todo.done) {
                    TodoCenter.toggleTodo(todoId, true)
                }
                TodoReminderNotifier.cancel(context, todoId)
                TodoReminderScheduler.cancelSnooze(context, todoId)
                TodoReminderScheduler.clearNotified(context, todoId)
                LunoLog.i(LOG_TAG, "todo reminder complete: todo=$todoId")
            } catch (e: Exception) {
                LunoLog.w(LOG_TAG, "todo reminder complete failed: ${e.message}")
            } finally {
                pending.finish()
            }
        }
    }
}
