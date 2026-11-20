package hunoia.luno.gridpanel.habit

import hunoia.luno.config.model.Habit
import hunoia.luno.config.model.HabitConfig
import hunoia.luno.config.model.HabitDayState
import hunoia.luno.config.model.HabitDot
import hunoia.luno.config.model.currentStreak
import hunoia.luno.config.model.habitDayState
import hunoia.luno.config.model.lastNDaysDots

/** 行内近 7 天点阵的格数（含今天，旧 → 新）。 */
internal const val HabitTodayDotCount = 7

/**
 * 「今日习惯」一行的派生状态（纯数据，不含文案）。
 *
 * 文案依赖 `stringResource`，留在 UI 层取；这里只留判定，分组与打卡口径可单测
 * （[groupHabitsToday] 的门禁见 `HabitGroupsTest`）。
 */
internal data class HabitToday(
    val habit: Habit,
    val state: HabitDayState,
    val checkedToday: Boolean,
    val count: Int,
    val target: Int,
    val streak: Long,
    val dots: List<HabitDot>,
) {
    val habitId: String get() = habit.id

    val name: String get() = habit.name

    /** 部分完成（未达标但已至少一次）：进度弧只在这时画。 */
    val isPartial: Boolean get() = target > 1 && count in 1 until target

    /** 完成进度 0..1（与 [HabitDot.progress] 同一口径）。 */
    val progress: Float
        get() = if (target <= 0) 0f else (count.toFloat() / target).coerceIn(0f, 1f)

    /** 本次打卡后要达到的次数（确认弹层展示用）；撤销时不使用。 */
    val nextCount: Int get() = if (checkedToday) count else (count + 1).coerceAtMost(target)
}

/**
 * 今日分组：待打卡 / 今日已完成 / 今日无需打卡（跳过）；组内保持配置顺序。
 *
 * 已完成优先于「跳过」：多次习惯在跳过日提前打满时，仍应出现在「今日已完成」。
 */
internal data class HabitTodayGroups(
    val due: List<HabitToday>,
    val done: List<HabitToday>,
    val skipped: List<HabitToday>,
)

internal fun groupHabitsToday(config: HabitConfig, today: Long): HabitTodayGroups {
    val due = mutableListOf<HabitToday>()
    val done = mutableListOf<HabitToday>()
    val skipped = mutableListOf<HabitToday>()
    config.habits.forEach { habit ->
        val row = habitToday(config, habit, today)
        when {
            row.checkedToday -> done += row
            row.state == HabitDayState.SKIPPED -> skipped += row
            else -> due += row
        }
    }
    return HabitTodayGroups(due = due, done = done, skipped = skipped)
}

private fun habitToday(config: HabitConfig, habit: Habit, today: Long): HabitToday {
    val checks = config.checks[habit.id].orEmpty()
    val counts = config.counts[habit.id].orEmpty()
    val target = habit.safeDailyTarget
    return HabitToday(
        habit = habit,
        state = habitDayState(checks, today, habit.interval, today),
        checkedToday = today in checks,
        count = config.countOf(habit.id, today, target),
        target = target,
        streak = currentStreak(checks, today, habit.interval),
        dots = lastNDaysDots(
            checks = checks,
            today = today,
            interval = habit.interval,
            n = HabitTodayDotCount,
            counts = counts,
            target = target,
        ),
    )
}
