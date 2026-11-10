package hunoia.luno.config.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HabitConfigTest {

    // 纯数字 epochDay：算法不关心真实日期，只要相邻关系。
    private val today = 1000L

    // ---------- 推迟 ----------

    @Test
    fun `snooze default is within the selectable range`() {
        val habit = Habit(name = "cap")
        assertEquals(HabitDefaultSnoozeMinutes, habit.snoozeMinutes)
        assertTrue(habit.snoozeMinutes in HabitMinSnoozeMinutes..HabitMaxSnoozeMinutes)
    }

    // ---------- currentStreak ----------

    @Test
    fun `empty checks is zero streak`() {
        assertEquals(0L, currentStreak(emptySet(), today, 1))
    }

    @Test
    fun `daily habit consecutive days`() {
        // 昨天、前天都打了，今天没打：今天仍是应打卡日（x=1），连续 = 今天 - 前天 + 1 = 3
        val checks = setOf(today - 1, today - 2)
        assertEquals(3L, currentStreak(checks, today, 1))
    }

    @Test
    fun `daily habit checked today`() {
        val checks = setOf(today, today - 1, today - 2)
        assertEquals(3L, currentStreak(checks, today, 1))
    }

    @Test
    fun `daily habit broken yesterday resets today`() {
        // 前天打了，昨天没打（错过），今天打了 → 重新从 1
        val checks = setOf(today, today - 2)
        assertEquals(1L, currentStreak(checks, today, 1))
    }

    @Test
    fun `daily habit missed deadline breaks`() {
        // 前天打了，昨天今天都没打：d0=today-2，d0+1=today-1 < today → 0
        val checks = setOf(today - 2)
        assertEquals(0L, currentStreak(checks, today, 1))
    }

    @Test
    fun `interval habit within window is consecutive`() {
        // x=3：11/1 与 11/4 间隔 3 ≤ 3 → 连续；今天打了，链 11/1..11/4 = 4 天
        val checks = setOf(today - 3, today)
        assertEquals(4L, currentStreak(checks, today, 3))
    }

    @Test
    fun `interval habit gap beyond x breaks`() {
        // x=3：两次打卡间隔 4 > 3 → 断；今天打了 → 1
        val checks = setOf(today - 4, today)
        assertEquals(1L, currentStreak(checks, today, 3))
    }

    @Test
    fun `interval habit not yet missed deadline`() {
        // x=3：today-2 打卡，今天未打，d0+3 = today+1 ≥ today → 未断，连续 = 3
        val checks = setOf(today - 2)
        assertEquals(3L, currentStreak(checks, today, 3))
    }

    @Test
    fun `interval habit missed deadline breaks`() {
        // x=3：today-4 打卡，今天未打，d0+3 = today-1 < today → 错过即断
        val checks = setOf(today - 4)
        assertEquals(0L, currentStreak(checks, today, 3))
    }

    @Test
    fun `backfill reconnects streak`() {
        // x=1：前天打了、昨天没打、今天打了 → 断。补昨天后接上。
        val before = setOf(today, today - 2)
        assertEquals(1L, currentStreak(before, today, 1))
        val backfilled = before + (today - 1)
        assertEquals(3L, currentStreak(backfilled, today, 1))
    }

    // ---------- habitDayState / canCheck ----------

    @Test
    fun `every two days has skip day then due day`() {
        // 用户报告的场景：x=2，昨天打卡，今天必须是「跳过」，明天才是「应打卡」。
        val checks = setOf(today - 1)
        assertEquals(HabitDayState.CHECKED, habitDayState(checks, today - 1, 2, today))
        assertEquals(HabitDayState.SKIPPED, habitDayState(checks, today, 2, today))
        assertEquals(HabitDayState.DUE, habitDayState(checks, today + 1, 2, today + 1))
        assertFalse(canCheck(checks, today, 2, today))
        assertTrue(canCheck(checks, today + 1, 2, today + 1))
    }

    @Test
    fun `overdue is still checkable but breaks streak`() {
        // 逾期（补打）仍可点，但 currentStreak 已归零。
        val checks = setOf(today - 3)
        assertEquals(HabitDayState.OVERDUE, habitDayState(checks, today, 2, today))
        assertTrue(canCheck(checks, today, 2, today))
        assertEquals(0L, currentStreak(checks, today, 2))
    }

    @Test
    fun `past due day without a check is overdue`() {
        // 点阵里已过去的应打卡日不该继续显示成「待打卡」。
        val checks = setOf(today - 4)
        assertEquals(HabitDayState.SKIPPED, habitDayState(checks, today - 3, 2, today))
        assertEquals(HabitDayState.OVERDUE, habitDayState(checks, today - 2, 2, today))
        assertEquals(HabitDayState.OVERDUE, habitDayState(checks, today, 2, today))
    }

    @Test
    fun `daily habit has no skip days`() {
        val checks = setOf(today - 1)
        assertEquals(HabitDayState.CHECKED, habitDayState(checks, today - 1, 1, today))
        assertEquals(HabitDayState.DUE, habitDayState(checks, today, 1, today))
        assertTrue(canCheck(checks, today, 1, today))
    }

    @Test
    fun `no prior check means inactive and startable`() {
        val checks = emptySet<Long>()
        assertEquals(HabitDayState.INACTIVE, habitDayState(checks, today - 2, 2, today))
        assertEquals(HabitDayState.INACTIVE, habitDayState(checks, today, 2, today))
        assertTrue(canCheck(checks, today, 2, today))
    }

    @Test
    fun `backfill re-anchors the next due day`() {
        // x=3：在跳过日补卡后，周期从补卡日重新起算。
        val checks = setOf(today - 4)
        assertEquals(HabitDayState.SKIPPED, habitDayState(checks, today - 2, 3, today))
        val backfilled = checks + (today - 2)
        assertEquals(HabitDayState.CHECKED, habitDayState(backfilled, today - 2, 3, today))
        assertEquals(HabitDayState.SKIPPED, habitDayState(backfilled, today, 3, today))
        assertEquals(HabitDayState.DUE, habitDayState(backfilled, today + 1, 3, today + 1))
        assertEquals(0L, currentStreak(checks, today, 3))
        // 补卡后链重新连上：today-4 .. today 共 5 个自然日
        assertEquals(5L, currentStreak(backfilled, today, 3))
    }

    @Test
    fun `checked and skipped days are not checkable`() {
        val checks = setOf(today - 1)
        assertFalse(canCheck(checks, today - 1, 2, today))
        assertFalse(canCheck(checks, today, 2, today))
    }

    @Test
    fun `interval is clamped to at least one`() {
        val checks = setOf(today - 1)
        assertEquals(HabitDayState.DUE, habitDayState(checks, today, 0, today))
        assertEquals(HabitDayState.DUE, habitDayState(checks, today, -5, today))
    }

    // ---------- dots ----------

    @Test
    fun `dots reflect the day state in order`() {
        // x=2，昨天打卡：旧→新 = 未开始, 未开始, 已打, 跳过(今天)
        val checks = setOf(today - 1)
        val dots = lastNDaysDots(checks, today, 2, 4)
        assertEquals(listOf(today - 3, today - 2, today - 1, today), dots.map { it.day })
        assertEquals(
            listOf(
                HabitDayState.INACTIVE,
                HabitDayState.INACTIVE,
                HabitDayState.CHECKED,
                HabitDayState.SKIPPED,
            ),
            dots.map { it.state },
        )
    }

    @Test
    fun `dots show today as due when it is the next due day`() {
        // x=2，前天打卡：旧→新 = 已打, 跳过, 待打卡(今天)
        val checks = setOf(today - 2)
        val dots = lastNDaysDots(checks, today, 2, 3)
        assertEquals(
            listOf(HabitDayState.CHECKED, HabitDayState.SKIPPED, HabitDayState.DUE),
            dots.map { it.state },
        )
    }

    // ---------- HabitConfig 操作 ----------

    @Test
    fun `with and without check are idempotent`() {
        val id = "h1"
        val cfg = HabitConfig()
        val checked = cfg.withCheck(id, today)
        assertTrue(checked.isChecked(id, today))
        assertTrue(checked.withCheck(id, today) === checked) // 已存在原样返回

        val cleared = checked.withoutCheck(id, today)
        assertFalse(cleared.isChecked(id, today))
        assertTrue(cleared.withoutCheck(id, today) === cleared) // 不存在原样返回
    }

    @Test
    fun `delete habit removes its checks`() {
        val id = "h1"
        val cfg = HabitConfig(habits = listOf(Habit(id = id, name = "run")))
            .withCheck(id, today)
        val after = cfg.deleteHabit(id)
        assertFalse(after.habits.any { it.id == id })
        assertTrue(after.checks.isEmpty())
    }

    @Test
    fun `update habit replaces in place`() {
        val id = "h1"
        val cfg = HabitConfig(habits = listOf(Habit(id = id, name = "run", interval = 1)))
        val renamed = cfg.updateHabit(Habit(id = id, name = "run2", interval = 3))
        assertEquals(1, renamed.habits.size)
        assertEquals("run2", renamed.habits.first().name)
        assertEquals(3, renamed.habits.first().interval)
    }

    @Test
    fun `set checked unified write and clear`() {
        val id = "h1"
        val cfg = HabitConfig().setChecked(id, today, true)
        assertTrue(cfg.isChecked(id, today))
        val cleared = cfg.setChecked(id, today, false)
        assertFalse(cleared.isChecked(id, today))
    }

    // ---------- pruneChecks ----------

    @Test
    fun `prune keeps recent and drops old within retention`() {
        val id = "h1"
        val cfg = HabitConfig()
            .withCheck(id, today)          // 今天
            .withCheck(id, today - 1)      // 昨天
            .withCheck(id, today - 5)      // 5 天前（保留）
            .withCheck(id, today - 91)     // 91 天前（裁掉，> 90）
        val pruned = cfg.pruneChecks(today, 90)
        assertTrue(pruned.isChecked(id, today))
        assertTrue(pruned.isChecked(id, today - 5))
        assertFalse(pruned.isChecked(id, today - 91))
    }

    @Test
    fun `prune keeps boundary day exactly at cutoff`() {
        val id = "h1"
        val cfg = HabitConfig().withCheck(id, today - 90) // 边界：today - retention
        val pruned = cfg.pruneChecks(today, 90)
        assertTrue(pruned.isChecked(id, today - 90))
    }

    @Test
    fun `prune removes habit key when all checks dropped`() {
        val id = "h1"
        val cfg = HabitConfig().withCheck(id, today - 91)
        val pruned = cfg.pruneChecks(today, 90)
        assertTrue(id !in pruned.checks)
    }

    @Test
    fun `prune is no-op when nothing older than retention`() {
        val id = "h1"
        val cfg = HabitConfig().withCheck(id, today).withCheck(id, today - 3)
        val pruned = cfg.pruneChecks(today, 90)
        assertTrue(pruned === cfg) // 无变化原样返回
    }

    // ---------- daily target / increment ----------

    @Test
    fun `safe daily target is clamped`() {
        assertEquals(1, Habit(dailyTarget = 0).safeDailyTarget)
        assertEquals(HabitMaxDailyTarget, Habit(dailyTarget = 999).safeDailyTarget)
    }

    @Test
    fun `increment accumulates partial counts then completes`() {
        val id = "h1"
        var cfg = HabitConfig()
        cfg = cfg.withIncrement(id, today, 3)
        assertEquals(1, cfg.countOf(id, today, 3))
        assertFalse(cfg.isChecked(id, today))
        cfg = cfg.withIncrement(id, today, 3)
        assertEquals(2, cfg.countOf(id, today, 3))
        cfg = cfg.withIncrement(id, today, 3)
        assertTrue(cfg.isChecked(id, today))
        assertEquals(3, cfg.countOf(id, today, 3))
        // 达标后只保留完成集合，不再留部分进度。
        assertFalse(cfg.counts[id]?.containsKey(today) == true)
    }

    @Test
    fun `increment on completed day is idempotent`() {
        val id = "h1"
        val cfg = HabitConfig().withCheck(id, today)
        assertTrue(cfg.withIncrement(id, today, 3) === cfg)
    }

    @Test
    fun `partial completion is not checked and does not start streak`() {
        val id = "h1"
        val cfg = HabitConfig().withIncrement(id, today, 3)
        assertFalse(cfg.isChecked(id, today))
        assertEquals(0L, currentStreak(cfg.checks[id].orEmpty(), today, 1))
    }

    @Test
    fun `setting checked clears partial progress`() {
        val id = "h1"
        val cfg = HabitConfig().withIncrement(id, today, 3)
        val filled = cfg.setChecked(id, today, true)
        assertTrue(filled.isChecked(id, today))
        assertFalse(filled.counts[id]?.containsKey(today) == true)
    }

    @Test
    fun `without check clears partial progress`() {
        val id = "h1"
        val cfg = HabitConfig().withIncrement(id, today, 3)
        val cleared = cfg.withoutCheck(id, today)
        assertEquals(0, cleared.countOf(id, today, 3))
        assertFalse(cleared.counts[id]?.containsKey(today) == true)
    }

    @Test
    fun `prune drops old partial counts`() {
        val id = "h1"
        val cfg = HabitConfig().withIncrement(id, today - 91, 3)
        val pruned = cfg.pruneChecks(today, 90)
        assertTrue(id !in pruned.counts)
    }

    // ---------- reminders ----------

    @Test
    fun `reminder times are normalized sorted deduped and capped`() {
        val times = (0..8).map { HabitReminderTime(hour = 23 - it, minute = it) }
        val normalized = normalizeReminderTimes(times + HabitReminderTime(23, 0))
        assertEquals(HabitMaxReminders, normalized.size)
        assertEquals(normalized.sortedBy { it.minutesOfDay }, normalized)
        assertEquals(normalized.distinctBy { it.minutesOfDay }.size, normalized.size)
    }

    @Test
    fun `disabled reminders expose no times`() {
        val habit = Habit(
            reminderEnabled = false,
            reminders = listOf(HabitReminderTime(8, 0)),
        )
        assertTrue(habit.reminderTimes.isEmpty())
        assertTrue(habit.copy(reminderEnabled = true).reminderTimes.isNotEmpty())
    }

    // ---------- dots with progress ----------

    @Test
    fun `dots carry count and target for multi target habits`() {
        val checks = setOf(today - 1)
        val counts = mapOf(today to 1)
        val dots = lastNDaysDots(checks, today, 1, 2, counts = counts, target = 3)
        val todayDot = dots.last()
        assertEquals(1, todayDot.count)
        assertEquals(3, todayDot.target)
        assertTrue(todayDot.isPartial)
        assertEquals(1f / 3f, todayDot.progress, 0.0001f)
        val yesterdayDot = dots.first()
        assertEquals(3, yesterdayDot.count)
        assertFalse(yesterdayDot.isPartial)
        assertEquals(1f, yesterdayDot.progress, 0.0001f)
    }

    // ---------- 序列化 ----------

    @Test
    fun `config with counts and reminders round trips through json`() {
        val habit = Habit(
            id = "h1",
            name = "喝水",
            dailyTarget = 3,
            reminderEnabled = true,
            reminders = listOf(HabitReminderTime(8, 0), HabitReminderTime(20, 30)),
        )
        val config = HabitConfig(habits = listOf(habit))
            .withIncrement("h1", today, 3)
            .withCheck("h1", today - 1)
        val json = kotlinx.serialization.json.Json.encodeToString(HabitConfig.serializer(), config)
        val restored = kotlinx.serialization.json.Json.decodeFromString(HabitConfig.serializer(), json)
        assertEquals(config, restored)
        assertEquals(1, restored.countOf("h1", today, 3))
        assertEquals(
            listOf(HabitReminderTime(8, 0), HabitReminderTime(20, 30)),
            restored.habits.first().reminderTimes,
        )
    }
}
