package hunoia.luno.gridpanel.habit

import hunoia.luno.config.model.Habit
import hunoia.luno.config.model.HabitConfig
import hunoia.luno.config.model.HabitDayState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 今日分组是纯函数：主页习惯 tab 的三段（待打卡 / 已完成 / 无需打卡）与打卡按钮
 * 的「+1 / 撤销」语义都从这里派生，规则改动必须在这里钉死。
 */
class HabitGroupsTest {

    private val today = 20_000L

    private fun habit(
        id: String,
        interval: Int = 1,
        target: Int = 1,
    ) = Habit(id = id, name = id, interval = interval, dailyTarget = target)

    @Test
    fun `due checked and skipped are split into three groups`() {
        val cfg = HabitConfig(
            habits = listOf(
                habit("daily"),        // 今天没打 → 待打卡
                habit("checked"),      // 今天已打 → 已完成
                habit("skip", interval = 3), // 2 天前打过 → 今天跳过日
            ),
            checks = mapOf(
                "checked" to setOf(today),
                "skip" to setOf(today - 2),
            ),
        )
        val groups = groupHabitsToday(cfg, today)
        assertEquals(listOf("daily"), groups.due.map { it.habitId })
        assertEquals(listOf("checked"), groups.done.map { it.habitId })
        assertEquals(listOf("skip"), groups.skipped.map { it.habitId })
        assertEquals(HabitDayState.SKIPPED, groups.skipped.single().state)
    }

    @Test
    fun `missed due day goes to due group as overdue`() {
        val cfg = HabitConfig(
            habits = listOf(habit("missed", interval = 3)),
            checks = mapOf("missed" to setOf(today - 4)),
        )
        val row = groupHabitsToday(cfg, today).due.single()
        assertEquals(HabitDayState.OVERDUE, row.state)
        assertFalse(row.checkedToday)
    }

    @Test
    fun `partial progress stays due and offers one more check`() {
        val cfg = HabitConfig(
            habits = listOf(habit("water", target = 3)),
            counts = mapOf("water" to mapOf(today to 1)),
        )
        val row = groupHabitsToday(cfg, today).due.single()
        assertTrue(row.isPartial)
        assertEquals(1, row.count)
        assertEquals(2, row.nextCount)
        assertEquals(1f / 3f, row.progress, 0.001f)
    }

    @Test
    fun `reaching target moves to done and the next tap unchecks`() {
        val cfg = HabitConfig(
            habits = listOf(habit("water", target = 3)),
            checks = mapOf("water" to setOf(today)),
        )
        val row = groupHabitsToday(cfg, today).done.single()
        assertTrue(row.checkedToday)
        assertFalse(row.isPartial)
        assertEquals(3, row.count)
        assertEquals(3, row.nextCount) // 撤销时不使用；这里只保证不会溢出目标
        assertEquals(1f, row.progress, 0.001f)
    }

    @Test
    fun `groups keep config order and are empty when no habits`() {
        val cfg = HabitConfig(habits = listOf(habit("a"), habit("b"), habit("c")))
        assertEquals(listOf("a", "b", "c"), groupHabitsToday(cfg, today).due.map { it.habitId })

        val empty = groupHabitsToday(HabitConfig(), today)
        assertTrue(empty.due.isEmpty() && empty.done.isEmpty() && empty.skipped.isEmpty())
    }

    @Test
    fun `dots window is the last seven days ending today`() {
        val row = groupHabitsToday(HabitConfig(habits = listOf(habit("a"))), today).due.single()
        assertEquals(HabitTodayDotCount, row.dots.size)
        assertEquals(today - (HabitTodayDotCount - 1), row.dots.first().day)
        assertEquals(today, row.dots.last().day)
    }
}
