package hunoia.luno.gridpanel.todo

import hunoia.luno.config.model.TodoItem
import hunoia.luno.config.model.TodoReminder
import hunoia.luno.config.model.TodoReminderLabelKind
import hunoia.luno.config.model.label
import java.time.ZoneId

/**
 * 待办紧迫度（未完成行的分组与排序依据，代办页列表用）。
 *
 * 展示优先级 = [ordinal]：已过期最上，其次今天，再是即将（明天及以后），最后无提醒。
 * 分类复用 `:core:domain` 的 [TodoReminder.label]（已过期 / 今天 / 明天 / 其它日期），
 * 避免与提醒文案的判断分叉。
 */
enum class TodoUrgency {
    Overdue,
    Today,
    Upcoming,
    NoReminder,
}

/** 一组未完成待办：同一紧迫度，保持原创建顺序。 */
data class TodoGroup(
    val urgency: TodoUrgency,
    val todos: List<TodoItem>,
)

/** [todo] 的紧迫度；无提醒恒为 [TodoUrgency.NoReminder]。 */
fun TodoItem.urgency(nowMillis: Long, zone: ZoneId = ZoneId.systemDefault()): TodoUrgency {
    val reminder = reminder ?: return TodoUrgency.NoReminder
    return when (reminder.label(nowMillis, zone).kind) {
        TodoReminderLabelKind.OVERDUE -> TodoUrgency.Overdue
        TodoReminderLabelKind.TODAY -> TodoUrgency.Today
        TodoReminderLabelKind.TOMORROW,
        TodoReminderLabelKind.DATE,
        -> TodoUrgency.Upcoming
    }
}

/**
 * 未完成按紧迫度分组，组序固定为 [TodoUrgency] 声明序（已过期 → 无提醒）；
 * 空组不保留，组内保持原创建顺序。
 */
fun groupPending(
    todos: List<TodoItem>,
    nowMillis: Long,
    zone: ZoneId = ZoneId.systemDefault(),
): List<TodoGroup> =
    TodoUrgency.entries
        .map { urgency -> TodoGroup(urgency, todos.filter { it.urgency(nowMillis, zone) == urgency }) }
        .filter { it.todos.isNotEmpty() }

/**
 * 未完成分区是否要画标题：只有一个非空分区时省掉标题。
 * 少量待办（例如全是无提醒）配一排分区头只会更吵；两个以上分区才需要「已过期 / 今天」的区分。
 */
fun showSectionHeaders(groups: List<TodoGroup>): Boolean = groups.size > 1

/**
 * 行内提醒的色调：过期 = error，今天/明天 = 主色，其它未来日期 = 中性。
 * 无提醒不显示提醒行，不产生色调。
 */
enum class ReminderTone {
    Overdue,
    Soon,
    Neutral,
}

fun TodoReminder.tone(nowMillis: Long, zone: ZoneId = ZoneId.systemDefault()): ReminderTone =
    when (label(nowMillis, zone).kind) {
        TodoReminderLabelKind.OVERDUE -> ReminderTone.Overdue
        TodoReminderLabelKind.TODAY,
        TodoReminderLabelKind.TOMORROW,
        -> ReminderTone.Soon
        TodoReminderLabelKind.DATE -> ReminderTone.Neutral
    }
