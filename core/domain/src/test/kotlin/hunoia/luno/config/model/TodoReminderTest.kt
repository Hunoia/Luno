package hunoia.luno.config.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/** 待办提醒：墙钟换算、过期判定与展示分类。 */
class TodoReminderTest {

    private val zone: ZoneId = ZoneId.of("Asia/Shanghai")
    private val today: LocalDate = LocalDate.of(2026, 11, 10)

    private fun reminder(day: LocalDate, hour: Int, minute: Int): TodoReminder =
        TodoReminder(day = day.toEpochDay(), minuteOfDay = hour * 60 + minute)

    private fun millisAt(dateTime: LocalDateTime): Long =
        dateTime.atZone(zone).toInstant().toEpochMilli()

    @Test
    fun `wall clock converts to epoch in the given zone`() {
        val value = reminder(today, 18, 30)
        assertEquals(millisAt(today.atTime(18, 30)), value.toEpochMillis(zone))
    }

    @Test
    fun `isPast compares against wall clock instant`() {
        val value = reminder(today, 18, 0)
        assertTrue(value.isPast(millisAt(today.atTime(18, 0)), zone))
        assertTrue(value.isPast(millisAt(today.atTime(18, 1)), zone))
        assertFalse(value.isPast(millisAt(today.atTime(17, 59)), zone))
    }

    @Test
    fun `key is stable and includes minute of day`() {
        val value = reminder(today, 9, 5)
        assertEquals("${today.toEpochDay()}:545", value.key())
    }

    @Test
    fun `label classifies today tomorrow date and overdue`() {
        val now = millisAt(today.atTime(12, 0))

        val todayLabel = reminder(today, 18, 0).label(now, zone)
        assertEquals(TodoReminderLabelKind.TODAY, todayLabel.kind)
        assertEquals(18, todayLabel.hour)
        assertEquals(0, todayLabel.minute)

        val tomorrowLabel = reminder(today.plusDays(1), 9, 30).label(now, zone)
        assertEquals(TodoReminderLabelKind.TOMORROW, tomorrowLabel.kind)

        val dateLabel = reminder(today.plusDays(5), 9, 30).label(now, zone)
        assertEquals(TodoReminderLabelKind.DATE, dateLabel.kind)
        assertEquals(11, dateLabel.month)
        assertEquals(15, dateLabel.dayOfMonth)

        val overdueLabel = reminder(today, 11, 59).label(now, zone)
        assertEquals(TodoReminderLabelKind.OVERDUE, overdueLabel.kind)
    }

    @Test
    fun `minute of day is clamped when malformed`() {
        val value = TodoReminder(day = today.toEpochDay(), minuteOfDay = 99_999)
        assertEquals(23, value.hour)
        assertEquals(59, value.minute)
    }
}
