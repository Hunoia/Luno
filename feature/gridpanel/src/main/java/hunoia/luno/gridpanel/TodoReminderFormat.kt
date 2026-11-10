package hunoia.luno.gridpanel

import android.content.Context
import hunoia.luno.config.model.TodoMaxSnoozeMinutes
import hunoia.luno.config.model.TodoMinSnoozeMinutes
import hunoia.luno.config.model.TodoReminder
import hunoia.luno.config.model.TodoReminderLabel
import hunoia.luno.config.model.TodoReminderLabelKind
import hunoia.luno.config.model.label
import java.time.LocalDate
import java.time.ZoneId

/**
 * 待办提醒文案收口：应用内列表 / 编辑弹窗、面板部件、桌面部件与通知共用同一套格式。
 *
 * 语义分类（今天 / 明天 / 具体日期 / 已过期）由 `:core:domain` 的 [TodoReminder.label] 决定，
 * 这里只负责渲染字符串，避免各端各写一份「今天 / 明天」判断后逐渐漂移。
 */
object TodoReminderFormat {

    /** 行内徽标（列表 / 面板 / 桌面部件共用）：今天只显示时间，其它日期用紧凑的 `11/12 14:03`。 */
    fun label(
        context: Context,
        reminder: TodoReminder,
        nowMillis: Long = System.currentTimeMillis(),
        zone: ZoneId = ZoneId.systemDefault(),
    ): String {
        val label = reminder.label(nowMillis, zone)
        val time = timeText(context, label)
        return when (label.kind) {
            TodoReminderLabelKind.OVERDUE,
            TodoReminderLabelKind.DATE,
            -> context.getString(
                R.string.todo_reminder_short_date,
                label.month,
                label.dayOfMonth,
                time,
            )

            TodoReminderLabelKind.TODAY -> time
            TodoReminderLabelKind.TOMORROW -> context.getString(R.string.todo_reminder_tomorrow, time)
        }
    }

    /** 通知正文：`提醒时间 · 今天 18:00` / 补弹 `已过期 · 11月12日 09:00`。 */
    fun notificationBody(
        context: Context,
        reminder: TodoReminder,
        overdue: Boolean,
        nowMillis: Long = System.currentTimeMillis(),
        zone: ZoneId = ZoneId.systemDefault(),
    ): String {
        val label = reminder.label(nowMillis, zone)
        val time = timeText(context, label)
        // 到点触发时提醒时刻刚过，不能用 label() 的「已过期」分类：按日期差说「今天 / 明天」。
        // 补弹（overdue）才用绝对日期，方便用户确认错过的是哪一天。
        val whenText = if (overdue) {
            dateTimeText(context, label, time)
        } else {
            when (reminder.day - LocalDate.now(zone).toEpochDay()) {
                0L -> context.getString(R.string.todo_reminder_today, time)
                1L -> context.getString(R.string.todo_reminder_tomorrow, time)
                else -> dateTimeText(context, label, time)
            }
        }
        return context.getString(
            if (overdue) R.string.todo_reminder_overdue_body else R.string.todo_reminder_notification_body,
            whenText,
        )
    }

    /** 编辑弹窗里的状态行：已过期只显示「已过期」，未来时间显示完整提醒时间。 */
    fun dialogStatus(
        context: Context,
        reminder: TodoReminder,
        nowMillis: Long = System.currentTimeMillis(),
        zone: ZoneId = ZoneId.systemDefault(),
    ): String {
        if (reminder.isPast(nowMillis, zone)) {
            return context.getString(R.string.todo_reminder_expired_short)
        }
        val label = reminder.label(nowMillis, zone)
        val time = timeText(context, label)
        val whenText = when (reminder.day - LocalDate.now(zone).toEpochDay()) {
            0L -> context.getString(R.string.todo_reminder_today, time)
            1L -> context.getString(R.string.todo_reminder_tomorrow, time)
            else -> dateTimeText(context, label, time)
        }
        return context.getString(R.string.todo_reminder_dialog_status, whenText)
    }

    /** 推迟时长文案（`30 分钟` / `1 小时 30 分钟`），设置行与通知按钮共用。 */
    fun snoozeDurationLabel(context: Context, minutes: Int): String {
        val safe = minutes.coerceIn(TodoMinSnoozeMinutes, TodoMaxSnoozeMinutes)
        val hours = safe / 60
        val rest = safe % 60
        return when {
            hours == 0 -> context.getString(R.string.snooze_duration_minutes, rest)
            rest == 0 -> context.getString(R.string.snooze_duration_hours, hours)
            else -> context.getString(R.string.snooze_duration_hours_minutes, hours, rest)
        }
    }

    /** 通知按钮文案（`推迟 30 分钟`）。 */
    fun snoozeActionLabel(context: Context, minutes: Int): String {
        val safe = minutes.coerceIn(TodoMinSnoozeMinutes, TodoMaxSnoozeMinutes)
        val hours = safe / 60
        val rest = safe % 60
        return when {
            hours == 0 -> context.getString(R.string.todo_reminder_snooze_minutes, rest)
            rest == 0 -> context.getString(R.string.todo_reminder_snooze_hours, hours)
            else -> context.getString(R.string.todo_reminder_snooze_hours_minutes, hours, rest)
        }
    }

    private fun timeText(context: Context, label: TodoReminderLabel): String =
        context.getString(R.string.todo_reminder_time, label.hour, label.minute)

    private fun dateTimeText(
        context: Context,
        label: TodoReminderLabel,
        time: String,
    ): String = context.getString(R.string.todo_reminder_date, label.month, label.dayOfMonth, time)
}
