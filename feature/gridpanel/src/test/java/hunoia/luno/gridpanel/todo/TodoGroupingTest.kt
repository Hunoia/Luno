package hunoia.luno.gridpanel.todo

import hunoia.luno.config.model.TodoItem
import hunoia.luno.config.model.TodoReminder
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

class TodoGroupingTest {

    private val zone: ZoneId = ZoneOffset.UTC
    // 固定「现在」：2026-11-20T12:00Z，避免跨天 / 时区导致分类漂移。
    private val now: Long = Instant.parse("2026-11-20T12:00:00Z").toEpochMilli()
    private val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate().toEpochDay()
    private val day = 86_400_000L

    private fun todo(id: String, reminder: TodoReminder? = null) = TodoItem(id = id, text = id, reminder = reminder)

    // ---------- urgency ----------

    @Test
    fun `no reminder is always NoReminder`() {
        assertEquals(TodoUrgency.NoReminder, todo("a").urgency(now, zone))
    }

    @Test
    fun `past reminder is Overdue`() {
        val past = TodoReminder(day = today - 1, minuteOfDay = 0)
        assertEquals(TodoUrgency.Overdue, todo("a", past).urgency(now, zone))
    }

    @Test
    fun `today reminder is Today`() {
        val laterToday = TodoReminder(day = today, minuteOfDay = 23 * 60)
        assertEquals(TodoUrgency.Today, todo("a", laterToday).urgency(now, zone))
    }

    @Test
    fun `tomorrow reminder is Upcoming`() {
        val tomorrow = TodoReminder(day = today + 1, minuteOfDay = 9 * 60)
        assertEquals(TodoUrgency.Upcoming, todo("a", tomorrow).urgency(now, zone))
    }

    @Test
    fun `future date reminder is Upcoming`() {
        val later = TodoReminder(day = today + 30, minuteOfDay = 9 * 60)
        assertEquals(TodoUrgency.Upcoming, todo("a", later).urgency(now, zone))
    }

    // ---------- groupPending ----------

    @Test
    fun `groupPending groups in urgency order and drops empties`() {
        val items = listOf(
            todo("no1"),
            todo("today", TodoReminder(today, 23 * 60)),
            todo("overdue", TodoReminder(today - 1, 0)),
            todo("future", TodoReminder(today + 3, 0)),
        )
        val groups = groupPending(items, now, zone)
        assertEquals(
            listOf(TodoUrgency.Overdue, TodoUrgency.Today, TodoUrgency.Upcoming, TodoUrgency.NoReminder),
            groups.map { it.urgency },
        )
        assertEquals(listOf("overdue"), groups[0].todos.map { it.id })
        assertEquals(listOf("today"), groups[1].todos.map { it.id })
        assertEquals(listOf("future"), groups[2].todos.map { it.id })
        assertEquals(listOf("no1"), groups[3].todos.map { it.id })
    }

    @Test
    fun `groupPending with only one urgency returns one group`() {
        val groups = groupPending(listOf(todo("a")), now, zone)
        assertEquals(1, groups.size)
        assertEquals(TodoUrgency.NoReminder, groups.single().urgency)
        assertEquals(listOf("a"), groups.single().todos.map { it.id })
    }

    // ---------- showSectionHeaders ----------

    @Test
    fun `single urgency hides section headers`() {
        val groups = groupPending(listOf(todo("a"), todo("b")), now, zone)
        assertEquals(1, groups.size)
        assertEquals(false, showSectionHeaders(groups))
    }

    @Test
    fun `two or more urgencies show section headers`() {
        val groups = groupPending(
            listOf(todo("a"), todo("b", TodoReminder(today - 1, 0))),
            now,
            zone,
        )
        assertEquals(2, groups.size)
        assertEquals(true, showSectionHeaders(groups))
    }

    // ---------- tone ----------

    @Test
    fun `tone maps label kinds`() {
        assertEquals(
            ReminderTone.Overdue,
            TodoReminder(today - 1, 0).tone(now, zone),
        )
        assertEquals(
            ReminderTone.Soon,
            TodoReminder(today, 23 * 60).tone(now, zone),
        )
        assertEquals(
            ReminderTone.Soon,
            TodoReminder(today + 1, 0).tone(now, zone),
        )
        assertEquals(
            ReminderTone.Neutral,
            TodoReminder(today + 9, 0).tone(now, zone),
        )
    }
}
