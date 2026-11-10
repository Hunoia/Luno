package hunoia.luno.domain.todo

import hunoia.luno.config.model.TodoConfig
import hunoia.luno.config.model.TodoMaxSnoozeMinutes
import hunoia.luno.config.model.TodoMinSnoozeMinutes
import hunoia.luno.config.model.TodoReminder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

/** [TodoReminderScheduler] 的纯逻辑：推迟状态编码与补弹判据。 */
class TodoReminderSchedulerTest {

    private val zone: ZoneId = ZoneId.systemDefault()
    private val today: Long = LocalDate.of(2026, 11, 10).toEpochDay()

    private fun reminder(hour: Int, minute: Int): TodoReminder =
        TodoReminder(day = today, minuteOfDay = hour * 60 + minute)

    private fun now(hour: Int, minute: Int): Long =
        LocalDate.ofEpochDay(today).atTime(hour, minute).atZone(zone).toInstant().toEpochMilli()

    @Test
    fun `parse snooze round trips reminder key and fire time`() {
        val state = parseSnooze("${today}:1080|1762776000000")
        assertEquals("${today}:1080", state?.reminderKey)
        assertEquals(1762776000000L, state?.fireAtMillis)
    }

    @Test
    fun `parse snooze rejects malformed values`() {
        assertNull(parseSnooze(null))
        assertNull(parseSnooze(""))
        assertNull(parseSnooze("no-separator"))
        assertNull(parseSnooze("key|not-a-number"))
        assertNull(parseSnooze("|123"))
    }

    @Test
    fun `catch up only when reminder passed and mark matches`() {
        val value = reminder(18, 0)
        assertTrue(shouldCatchUp(value, value.key(), now(18, 1), zone))
        assertFalse(shouldCatchUp(value, value.key(), now(17, 59), zone))
        assertFalse(shouldCatchUp(value, null, now(18, 1), zone))
        assertFalse(shouldCatchUp(value, "other", now(18, 1), zone))
    }

    @Test
    fun `snooze minutes are clamped to the supported range`() {
        assertEquals(TodoMinSnoozeMinutes, TodoConfig(reminderSnoozeMinutes = -5).safeReminderSnoozeMinutes())
        assertEquals(TodoMaxSnoozeMinutes, TodoConfig(reminderSnoozeMinutes = 9999).safeReminderSnoozeMinutes())
        assertEquals(45, TodoConfig(reminderSnoozeMinutes = 45).safeReminderSnoozeMinutes())
    }
}
