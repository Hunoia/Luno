package hunoia.luno.domain.habit

import hunoia.luno.config.model.Habit
import hunoia.luno.config.model.HabitReminderTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

/** [HabitReminderScheduler] 的纯逻辑（无 Android 依赖）：多提醒槽位 + 补弹。 */
class HabitReminderSchedulerTest {

    private val zone: ZoneId = ZoneId.systemDefault()
    private val today: Long = LocalDate.of(2026, 11, 8).toEpochDay()

    private fun millis(day: Long, hour: Int, minute: Int): Long =
        LocalDate.ofEpochDay(day).atTime(hour, minute).atZone(zone).toInstant().toEpochMilli()

    private fun now(day: Long, hour: Int, minute: Int): Long = millis(day, hour, minute)

    /** 单提醒习惯（默认 20:00）；多提醒用 [reminders]。 */
    private fun habit(
        interval: Int = 1,
        hour: Int = 20,
        minute: Int = 0,
        reminders: List<HabitReminderTime> = listOf(HabitReminderTime(hour, minute)),
    ): Habit = Habit(
        id = "h",
        name = "跑步",
        interval = interval,
        reminderEnabled = true,
        reminders = reminders,
    )

    private fun slot(hour: Int, minute: Int): Int = hour * 60 + minute

    // ---------- nextTriggerMillis：单槽 ----------

    @Test
    fun `no checks - fires today if time in future`() {
        val trigger = HabitReminderScheduler.nextTriggerMillis(
            habit(), emptySet(), today, now(today, 10, 0), slot(20, 0),
        )
        assertEquals(millis(today, 20, 0), trigger)
    }

    @Test
    fun `no checks - fires tomorrow if today time passed`() {
        val trigger = HabitReminderScheduler.nextTriggerMillis(
            habit(), emptySet(), today, now(today, 22, 0), slot(20, 0),
        )
        assertEquals(millis(today + 1, 20, 0), trigger)
    }

    @Test
    fun `due today - fires today at reminder time`() {
        val checks = setOf(today - 1) // interval=1，昨天打卡 → 今天 DUE
        val trigger = HabitReminderScheduler.nextTriggerMillis(
            habit(interval = 1), checks, today, now(today, 10, 0), slot(20, 0),
        )
        assertEquals(millis(today, 20, 0), trigger)
    }

    @Test
    fun `due today but time passed - rolls to next due day`() {
        val checks = setOf(today - 1) // 今天 DUE 但 20:00 已过
        val trigger = HabitReminderScheduler.nextTriggerMillis(
            habit(interval = 1), checks, today, now(today, 22, 0), slot(20, 0),
        )
        assertEquals(millis(today + 1, 20, 0), trigger)
    }

    @Test
    fun `checked today - next due after interval`() {
        val checks = setOf(today) // interval=2，今天已打 → CHECKED
        val trigger = HabitReminderScheduler.nextTriggerMillis(
            habit(interval = 2), checks, today, now(today, 10, 0), slot(20, 0),
        )
        assertEquals(millis(today + 2, 20, 0), trigger)
    }

    @Test
    fun `skipped today - next due is last check plus interval`() {
        val checks = setOf(today - 1) // interval=2，昨天打卡 → 今天 SKIPPED
        val trigger = HabitReminderScheduler.nextTriggerMillis(
            habit(interval = 2), checks, today, now(today, 10, 0), slot(20, 0),
        )
        assertEquals(millis(today + 1, 20, 0), trigger)
    }

    @Test
    fun `overdue today - still reminds`() {
        val checks = setOf(today - 3) // interval=2，3 天前打卡 → 今天 OVERDUE（仍可补打）
        val trigger = HabitReminderScheduler.nextTriggerMillis(
            habit(interval = 2), checks, today, now(today, 10, 0), slot(20, 0),
        )
        assertEquals(millis(today, 20, 0), trigger)
    }

    @Test
    fun `overdue and time passed - reminds tomorrow`() {
        val checks = setOf(today - 3) // 今天 OVERDUE 且 20:00 已过
        val trigger = HabitReminderScheduler.nextTriggerMillis(
            habit(interval = 2), checks, today, now(today, 22, 0), slot(20, 0),
        )
        assertEquals(millis(today + 1, 20, 0), trigger)
    }

    @Test
    fun `slot beyond midnight clamps to 23 59`() {
        val trigger = HabitReminderScheduler.nextTriggerMillis(
            habit(), emptySet(), today, now(today, 6, 0), 24 * 60 + 30,
        )
        assertEquals(millis(today, 23, 59), trigger)
        assertNotNull(trigger)
    }

    // ---------- nextTriggerMillis：多槽 ----------

    @Test
    fun `multiple slots each fire at their own time today`() {
        val reminders = listOf(
            HabitReminderTime(8, 0),
            HabitReminderTime(13, 30),
            HabitReminderTime(20, 0),
        )
        val habit = habit(reminders = reminders)
        val checks = setOf(today - 1)
        assertEquals(
            millis(today, 8, 0),
            HabitReminderScheduler.nextTriggerMillis(habit, checks, today, now(today, 7, 0), slot(8, 0)),
        )
        assertEquals(
            millis(today, 13, 30),
            HabitReminderScheduler.nextTriggerMillis(habit, checks, today, now(today, 9, 0), slot(13, 30)),
        )
        assertEquals(
            millis(today, 20, 0),
            HabitReminderScheduler.nextTriggerMillis(habit, checks, today, now(today, 14, 0), slot(20, 0)),
        )
    }

    @Test
    fun `multiple slots already passed roll to tomorrow individually`() {
        val habit = habit(reminders = listOf(HabitReminderTime(8, 0), HabitReminderTime(20, 0)))
        val checks = setOf(today - 1)
        assertEquals(
            millis(today + 1, 8, 0),
            HabitReminderScheduler.nextTriggerMillis(habit, checks, today, now(today, 12, 0), slot(8, 0)),
        )
        assertEquals(
            millis(today, 20, 0),
            HabitReminderScheduler.nextTriggerMillis(habit, checks, today, now(today, 12, 0), slot(20, 0)),
        )
    }

    // ---------- latestCatchUpSlot ----------

    @Test
    fun `catch up - no checks and time passed returns latest past slot`() {
        val habit = habit(reminders = listOf(HabitReminderTime(8, 0), HabitReminderTime(20, 0)))
        val result = HabitReminderScheduler.latestCatchUpSlot(
            habit, emptySet(), today, now(today, 22, 0), Long.MIN_VALUE,
        )
        assertEquals(slot(20, 0), result)
    }

    @Test
    fun `catch up - only latest past slot not all missed`() {
        val habit = habit(
            reminders = listOf(
                HabitReminderTime(8, 0),
                HabitReminderTime(12, 0),
                HabitReminderTime(20, 0),
            ),
        )
        val result = HabitReminderScheduler.latestCatchUpSlot(
            habit, emptySet(), today, now(today, 13, 0), Long.MIN_VALUE,
        )
        assertEquals(slot(12, 0), result)
    }

    @Test
    fun `catch up - time not reached yet does not pop`() {
        val result = HabitReminderScheduler.latestCatchUpSlot(
            habit(), emptySet(), today, now(today, 10, 0), Long.MIN_VALUE,
        )
        assertNull(result)
    }

    @Test
    fun `catch up - only once per same time slot`() {
        val result = HabitReminderScheduler.latestCatchUpSlot(
            habit(), emptySet(), today, now(today, 22, 0), today, slot(20, 0),
        )
        assertNull(result)
    }

    @Test
    fun `catch up - reminder time changed after earlier notification pops again`() {
        val result = HabitReminderScheduler.latestCatchUpSlot(
            habit(hour = 21, minute = 30), emptySet(), today, now(today, 22, 0), today, slot(20, 0),
        )
        assertEquals(slot(21, 30), result)
    }

    @Test
    fun `catch up - later slot already notified suppresses earlier snoozed slot`() {
        // 推迟的是 20:00，但 21:30 的提醒已弹过：不再用旧槽补弹。
        val result = HabitReminderScheduler.latestCatchUpSlot(
            habit(reminders = listOf(HabitReminderTime(20, 0), HabitReminderTime(21, 30))),
            emptySet(),
            today,
            now(today, 22, 0),
            today,
            slot(21, 30),
        )
        assertNull(result)
    }

    @Test
    fun `catch up - checked today does not pop`() {
        val result = HabitReminderScheduler.latestCatchUpSlot(
            habit(), setOf(today), today, now(today, 22, 0), Long.MIN_VALUE,
        )
        assertNull(result)
    }

    @Test
    fun `catch up - overdue pops today`() {
        val result = HabitReminderScheduler.latestCatchUpSlot(
            habit(interval = 2), setOf(today - 3), today, now(today, 22, 0), Long.MIN_VALUE,
        )
        assertEquals(slot(20, 0), result)
    }

    @Test
    fun `catch up - skipped rest day does not pop`() {
        val result = HabitReminderScheduler.latestCatchUpSlot(
            habit(interval = 2), setOf(today - 1), today, now(today, 22, 0), Long.MIN_VALUE,
        )
        assertNull(result)
    }

    @Test
    fun `catch up - reminders disabled does not pop`() {
        val habit = habit().copy(reminderEnabled = false)
        val result = HabitReminderScheduler.latestCatchUpSlot(
            habit, emptySet(), today, now(today, 22, 0), Long.MIN_VALUE,
        )
        assertNull(result)
    }
}
