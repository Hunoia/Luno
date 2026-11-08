package hunoia.luno.domain.habit

import hunoia.luno.config.model.Habit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/** [HabitReminderScheduler.nextTriggerMillis] 的纯逻辑（无 Android 依赖）。 */
class HabitReminderSchedulerTest {

    private val zone: ZoneId = ZoneId.systemDefault()
    private val today: Long = LocalDate.of(2026, 11, 8).toEpochDay()

    private fun millis(day: Long, hour: Int, minute: Int): Long =
        LocalDate.ofEpochDay(day).atTime(hour, minute).atZone(zone).toInstant().toEpochMilli()

    private fun now(day: Long, hour: Int, minute: Int): Long = millis(day, hour, minute)

    private fun habit(interval: Int = 1, hour: Int = 20, minute: Int = 0): Habit =
        Habit(id = "h", name = "跑步", interval = interval, reminderEnabled = true, reminderHour = hour, reminderMinute = minute)

    @Test
    fun `no checks - fires today if time in future`() {
        val trigger = HabitReminderScheduler.nextTriggerMillis(habit(), emptySet(), today, now(today, 10, 0))
        assertEquals(millis(today, 20, 0), trigger)
    }

    @Test
    fun `no checks - fires tomorrow if today time passed`() {
        val trigger = HabitReminderScheduler.nextTriggerMillis(habit(), emptySet(), today, now(today, 22, 0))
        assertEquals(millis(today + 1, 20, 0), trigger)
    }

    @Test
    fun `due today - fires today at reminder time`() {
        val checks = setOf(today - 1) // interval=1，昨天打卡 → 今天 DUE
        val trigger = HabitReminderScheduler.nextTriggerMillis(habit(interval = 1), checks, today, now(today, 10, 0))
        assertEquals(millis(today, 20, 0), trigger)
    }

    @Test
    fun `due today but time passed - rolls to next due day`() {
        val checks = setOf(today - 1) // 今天 DUE 但 20:00 已过
        val trigger = HabitReminderScheduler.nextTriggerMillis(habit(interval = 1), checks, today, now(today, 22, 0))
        assertEquals(millis(today + 1, 20, 0), trigger)
    }

    @Test
    fun `checked today - next due after interval`() {
        val checks = setOf(today) // interval=2，今天已打 → CHECKED
        val trigger = HabitReminderScheduler.nextTriggerMillis(habit(interval = 2), checks, today, now(today, 10, 0))
        assertEquals(millis(today + 2, 20, 0), trigger)
    }

    @Test
    fun `skipped today - next due is last check plus interval`() {
        val checks = setOf(today - 1) // interval=2，昨天打卡 → 今天 SKIPPED
        val trigger = HabitReminderScheduler.nextTriggerMillis(habit(interval = 2), checks, today, now(today, 10, 0))
        assertEquals(millis(today + 1, 20, 0), trigger)
    }

    @Test
    fun `overdue today - still reminds`() {
        val checks = setOf(today - 3) // interval=2，3 天前打卡 → 今天 OVERDUE（仍可补打）
        val trigger = HabitReminderScheduler.nextTriggerMillis(habit(interval = 2), checks, today, now(today, 10, 0))
        assertEquals(millis(today, 20, 0), trigger)
    }

    @Test
    fun `overdue and time passed - reminds tomorrow`() {
        val checks = setOf(today - 3) // 今天 OVERDUE 且 20:00 已过
        val trigger = HabitReminderScheduler.nextTriggerMillis(habit(interval = 2), checks, today, now(today, 22, 0))
        assertEquals(millis(today + 1, 20, 0), trigger)
    }

    @Test
    fun `minute clamped within 0-59`() {
        val trigger = HabitReminderScheduler.nextTriggerMillis(
            habit(hour = 8, minute = 99), emptySet(), today, now(today, 6, 0),
        )
        assertEquals(millis(today, 8, 59), trigger)
        assertNotNull(trigger)
    }

    @Test
    fun `catch up - no checks and time passed pops today`() {
        val day = HabitReminderScheduler.catchUpReminderDay(
            habit(), emptySet(), today, now(today, 22, 0), Long.MIN_VALUE,
        )
        assertEquals(today, day)
    }

    @Test
    fun `catch up - time not reached yet does not pop`() {
        val day = HabitReminderScheduler.catchUpReminderDay(
            habit(), emptySet(), today, now(today, 10, 0), Long.MIN_VALUE,
        )
        assertEquals(null, day)
    }

    @Test
    fun `catch up - only once per same time slot`() {
        val day = HabitReminderScheduler.catchUpReminderDay(
            habit(), emptySet(), today, now(today, 22, 0), today, 20 * 60,
        )
        assertEquals(null, day)
    }

    @Test
    fun `catch up - reminder time changed after earlier notification pops again`() {
        val day = HabitReminderScheduler.catchUpReminderDay(
            habit(hour = 21, minute = 30), emptySet(), today, now(today, 22, 0), today, 20 * 60,
        )
        assertEquals(today, day)
    }

    @Test
    fun `catch up - checked today does not pop`() {
        val day = HabitReminderScheduler.catchUpReminderDay(
            habit(), setOf(today), today, now(today, 22, 0), Long.MIN_VALUE,
        )
        assertEquals(null, day)
    }

    @Test
    fun `catch up - overdue pops today`() {
        val day = HabitReminderScheduler.catchUpReminderDay(
            habit(interval = 2), setOf(today - 3), today, now(today, 22, 0), Long.MIN_VALUE,
        )
        assertEquals(today, day)
    }

    @Test
    fun `catch up - skipped rest day does not pop`() {
        val day = HabitReminderScheduler.catchUpReminderDay(
            habit(interval = 2), setOf(today - 1), today, now(today, 22, 0), Long.MIN_VALUE,
        )
        assertEquals(null, day)
    }
}
