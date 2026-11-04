package hunoia.luno.config.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HabitConfigTest {

    // 纯数字 epochDay：算法不关心真实日期，只要相邻关系。
    private val today = 1000L

    // ---------- currentStreak ----------

    @Test
    fun `empty checks is zero streak`() {
        assertEquals(0L, currentStreak(emptySet(), today, 1))
    }

    @Test
    fun `daily habit consecutive days`() {
        // 昨天、前天都打了，今天没打：今天仍在覆盖内（x=1），连续 = 今天 - 前天 + 1 = 3
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
        // x=3：11/1 与 11/4 间隔 3 ≤ 3 → 连续；今天未打但仍在 d0+3 覆盖内
        val checks = setOf(today - 3, today)
        // 今天打了（today in checks）：链 11/1..11/4，连续自然天数 = today - (today-3) + 1 = 4
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
        // x=3：today-2 打卡，今天未打，d0+3 = today+1 ≥ today → 未断，连续 = today - (today-2) + 1 = 3
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

    // ---------- isDueDay / dots ----------

    @Test
    fun `due day is within interval after a check`() {
        val checks = setOf(today - 5)
        assertTrue(isDueDay(checks, today - 5, 3)) // 打卡日当天
        assertTrue(isDueDay(checks, today - 4, 3)) // 覆盖内
        assertTrue(isDueDay(checks, today - 2, 3)) // 边界 day-c == interval
        assertFalse(isDueDay(checks, today - 1, 3)) // 超出周期 day-c == interval+1
        assertFalse(isDueDay(checks, today - 9, 3)) // 覆盖前
    }

    @Test
    fun `dots reflect check and due`() {
        val checks = setOf(today - 1)
        val dots = lastNDaysDots(checks, today, 1, 4)
        assertEquals(4, dots.size)
        // 旧→新：today-3, today-2, today-1, today
        assertFalse(dots[0].checked); assertFalse(dots[0].due)
        assertFalse(dots[1].checked); assertFalse(dots[1].due)
        assertTrue(dots[2].checked); assertTrue(dots[2].due)
        assertFalse(dots[3].checked); assertTrue(dots[3].due) // 今天未打但在覆盖内
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
}
