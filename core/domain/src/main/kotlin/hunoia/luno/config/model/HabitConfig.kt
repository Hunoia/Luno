package hunoia.luno.config.model

import androidx.annotation.Keep
import kotlinx.serialization.Serializable
import java.util.UUID

/** 打卡记录保留天数：早于 `today − retention` 的打卡日写时惰性裁剪，避免无限增长。 */
const val HabitHistoryRetentionDays = 90

/** 「推迟」默认时长：30 分钟。 */
const val HabitDefaultSnoozeMinutes = 30

/** 「推迟」可选范围（分钟）：1 分钟 – 6 小时。 */
const val HabitMinSnoozeMinutes = 1
const val HabitMaxSnoozeMinutes = 6 * 60

/** 单个习惯每日目标次数上限。 */
const val HabitMaxDailyTarget = 20

/** 单个习惯最多提醒时间数。 */
const val HabitMaxReminders = 6

/**
 * 习惯类型。v1 只开放 [BOOLEAN]；枚举预留，数值习惯后续再开。
 */
@Keep
enum class HabitType { BOOLEAN }

/** 一个提醒时间（本地时区，24 小时制）。 */
@Serializable
@Keep
data class HabitReminderTime(
    val hour: Int = 20,
    val minute: Int = 0,
) {
    /** 当天分钟数，便于排序 / 去重 / 调度。 */
    val minutesOfDay: Int
        get() = hour.coerceIn(0, 23) * 60 + minute.coerceIn(0, 59)
}

/** 排序 + 去重 + 限流到 [HabitMaxReminders]；编辑页、调度与通知共用同一口径。 */
fun normalizeReminderTimes(times: List<HabitReminderTime>): List<HabitReminderTime> = times
    .map { HabitReminderTime(it.hour.coerceIn(0, 23), it.minute.coerceIn(0, 59)) }
    .distinctBy { it.minutesOfDay }
    .sortedBy { it.minutesOfDay }
    .take(HabitMaxReminders)

/**
 * 一个习惯。打卡频率按「每 [interval] 日」滑动式判定（见 [habitDayState] / [currentStreak]）：
 * 上次打卡日之后，隔满 [interval] 日才是下一个应打卡日，中间是跳过日。
 *
 * 字段：
 * - [color]：ARGB 整数，来自 UI 的 `ColorSelection`（Theme 预设 / Custom）。
 * - [interval]：每 x 日，x ≥ 1；默认 1（每日）。
 * - [dailyTarget]：应打卡日需要完成的次数，1..[HabitMaxDailyTarget]；与 [interval] 正交。
 * - [reminders]：一天内的提醒时间，最多 [HabitMaxReminders] 个；[reminderEnabled] 是总开关。
 * - [createdAt]：创建时刻（epoch millis），仅展示用，不参与算法。
 */
@Serializable
@Keep
data class Habit(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "",
    val color: Int = 0xFF4CAF50.toInt(),
    /** 颜色来源：自定义 / 跟随主题；与 [color] 一起持久化。 */
    val colorSource: ColorSource = ColorSource.Custom,
    val colorThemeKey: ThemeColorKey = ThemeColorKey.Primary,
    val type: HabitType = HabitType.BOOLEAN,
    val interval: Int = 1,
    val dailyTarget: Int = 1,
    /** 提醒总开关；关闭时保留 [reminders] 列表，重新打开即恢复。 */
    val reminderEnabled: Boolean = false,
    val reminders: List<HabitReminderTime> = emptyList(),
    /** 提醒被「推迟」时重弹的间隔（分钟），范围 [HabitMinSnoozeMinutes]..[HabitMaxSnoozeMinutes]。 */
    val snoozeMinutes: Int = HabitDefaultSnoozeMinutes,
    val createdAt: Long = 0L,
) {
    /** 渲染端统一入口：按 [colorSource] 解析，主题切换时实时跟随。 */
    val colorSpec: ColorSpec
        get() = ColorSpec(colorSource, colorThemeKey, color)

    /** 实际参与调度的提醒时间（已排序 / 去重 / 限流）；总开关关闭时为空。 */
    val reminderTimes: List<HabitReminderTime>
        get() = if (reminderEnabled) normalizeReminderTimes(reminders) else emptyList()

    /** 目标次数钳到合法范围。 */
    val safeDailyTarget: Int
        get() = dailyTarget.coerceIn(1, HabitMaxDailyTarget)
}

/**
 * 习惯配置域：习惯列表 + 打卡记录。
 *
 * 键用 `epochDay`（`LocalDate.toEpochDay()`），每日 0 点边界，跨时区不歧义。
 * - [checks]：`habitId -> 已完成 epochDay 集合`（达到当日目标）。
 * - [counts]：`habitId -> epochDay -> 部分次数`；只存 `0 < count < target` 的进度，
 *   达标后移入 [checks]。旧数据没有该字段时按 0 处理。
 *
 * 数据流：网格面板部件与管理页都经 `:core:data` 的 `ConfigProvider` 门面读写，
 * 不直接进 `ActionRequest`（应用内状态变更，非可执行 Action）。
 */
@Serializable
@Keep
data class HabitConfig(
    val habits: List<Habit> = emptyList(),
    val checks: Map<String, Set<Long>> = emptyMap(),
    val counts: Map<String, Map<Long, Int>> = emptyMap(),
) {

    val hasHabits: Boolean get() = habits.isNotEmpty()

    fun habitById(id: String): Habit? = habits.firstOrNull { it.id == id }

    /** 某习惯是否已在 [day] 完成全部目标次数。 */
    fun isChecked(habitId: String, day: Long): Boolean = checks[habitId]?.contains(day) == true

    /** 某习惯在 [day] 已完成的次数；已完成日直接返回目标次数。 */
    fun countOf(habitId: String, day: Long, target: Int): Int {
        val safeTarget = target.coerceIn(1, HabitMaxDailyTarget)
        if (isChecked(habitId, day)) return safeTarget
        return (counts[habitId]?.get(day) ?: 0).coerceIn(0, safeTarget)
    }

    /** 写入一次完成（幂等：已完成则原样返回，避免无意义写盘）。 */
    fun withCheck(habitId: String, day: Long): HabitConfig {
        val current = checks[habitId] ?: emptySet()
        val dayCounts = counts[habitId]
        val hadPartial = dayCounts?.containsKey(day) == true
        if (day in current && !hadPartial) return this
        val nextChecks = if (day in current) checks else checks + (habitId to current + day)
        val nextCounts = if (hadPartial) {
            val remaining = dayCounts - day
            if (remaining.isEmpty()) counts - habitId else counts + (habitId to remaining)
        } else {
            counts
        }
        return copy(checks = nextChecks, counts = nextCounts)
    }

    /** 取消 / 改历史：同时清除完成标记与部分次数；没有记录则原样返回。 */
    fun withoutCheck(habitId: String, day: Long): HabitConfig {
        val current = checks[habitId] ?: emptySet()
        val dayCounts = counts[habitId] ?: emptyMap()
        if (day !in current && day !in dayCounts) return this
        val nextChecks = if (day in current) {
            val remaining = current - day
            if (remaining.isEmpty()) checks - habitId else checks + (habitId to remaining)
        } else {
            checks
        }
        val nextCounts = if (day in dayCounts) {
            val remaining = dayCounts - day
            if (remaining.isEmpty()) counts - habitId else counts + (habitId to remaining)
        } else {
            counts
        }
        return copy(checks = nextChecks, counts = nextCounts)
    }

    /** 统一写入 / 清除：管理页补卡与改历史用。写入 = 直接填满当日目标。 */
    fun setChecked(habitId: String, day: Long, checked: Boolean): HabitConfig =
        if (checked) withCheck(habitId, day) else withoutCheck(habitId, day)

    /**
     * 打卡 +1：未达标写入部分次数，达标移入 [checks] 并清掉部分进度。
     * 已完成日 / 不存在习惯由调用方先行判断；这里只做纯数据变换。
     */
    fun withIncrement(habitId: String, day: Long, target: Int): HabitConfig {
        val safeTarget = target.coerceIn(1, HabitMaxDailyTarget)
        if (isChecked(habitId, day)) return this
        val current = counts[habitId]?.get(day) ?: 0
        val next = (current + 1).coerceAtMost(safeTarget)
        if (next >= safeTarget) return withCheck(habitId, day)
        val dayCounts = (counts[habitId] ?: emptyMap()) + (day to next)
        return copy(counts = counts + (habitId to dayCounts))
    }

    /**
     * 惰性裁剪：丢掉早于 `today − retentionDays` 的打卡与部分进度（含习惯条目清空即移除键）。
     * 无变化原样返回（避免无意义写盘）。写时调用，存储有界（见 [HabitHistoryRetentionDays]）。
     */
    fun pruneChecks(today: Long, retentionDays: Int): HabitConfig {
        val cutoff = today - retentionDays.coerceAtLeast(0)
        val prunedChecks = checks
            .mapValues { (_, days) -> days.filter { it >= cutoff }.toSet() }
            .filterValues { it.isNotEmpty() }
        val prunedCounts = counts
            .mapValues { (_, days) -> days.filterKeys { it >= cutoff } }
            .filterValues { it.isNotEmpty() }
        return if (prunedChecks == checks && prunedCounts == counts) {
            this
        } else {
            copy(checks = prunedChecks, counts = prunedCounts)
        }
    }

    fun addHabit(habit: Habit): HabitConfig = copy(habits = habits + habit)

    fun updateHabit(habit: Habit): HabitConfig =
        copy(habits = habits.map { if (it.id == habit.id) habit else it })

    /** 删除习惯并连带清理其打卡记录与部分进度。 */
    fun deleteHabit(habitId: String): HabitConfig = copy(
        habits = habits.filterNot { it.id == habitId },
        checks = checks - habitId,
        counts = counts - habitId,
    )
}

/**
 * 面板部件参数：习惯部件（`internal.habitCheck` / `internal.habitHistory`）指向的具体习惯。
 *
 * 经 `GridPanelItem.Kind.Internal.params`（JSON 串）落盘，见 architecture §9.2。
 */
@Serializable
@Keep
data class HabitWidgetParams(val habitId: String)

/**
 * 连续自然天数（习惯当前 streak）。与 [habitDayState] 同一套「每 x 日」语义：
 *
 * - [checks] 为空 → 0；
 * - 今天未打且已过最近打卡日 c 的应打卡日 c + x（即 today > c + x，逾期）→ streak 断为 0；
 * - 否则从最近打卡日往前找「相邻打卡间隔 ≤ x」的最长连续链，
 *   连续自然天数 = 今天 − 链起点 + 1（今天未打但尚未逾期仍计，今天尚未结束）。
 *
 * 逾期日仍可补打，但补打前这一段的 streak 已归零。
 */
fun currentStreak(checks: Set<Long>, today: Long, interval: Int): Long {
    if (checks.isEmpty()) return 0
    val safeInterval = interval.coerceAtLeast(1)
    val d0 = checks.max()
    if (today !in checks && d0 + safeInterval < today) return 0
    var start = d0
    var prev = d0
    for (d in checks.sortedDescending().drop(1)) {
        if (prev - d <= safeInterval) {
            start = d
            prev = d
        } else {
            break
        }
    }
    return (today - start + 1).coerceAtLeast(0)
}

/**
 * 习惯日历里一天的状态。由 [habitDayState] 唯一派生；部件打卡圆、历史点阵与编辑页
 * 近 30 天日历都消费它，避免同一语义在多处被反向解释（ui-guidelines §1.1）。
 *
 * 「每 x 日」= 以最近一次打卡日 c 为锚：c+1 .. c+x−1 是跳过日，c+x 是下一个应打卡日；
 * 过了 c+x 仍没打就是逾期（可补打，但 streak 已断）。
 */
@Keep
enum class HabitDayState {
    /** 当天已完成全部目标次数。 */
    CHECKED,

    /** 两个应打卡日之间的休整日：主色半透明，部件不可点。 */
    SKIPPED,

    /** 应打卡日：空心主色描边，部件可点。 */
    DUE,

    /** 错过应打卡日：弱灰 + 断签描边，仍可补打，但 streak 断。 */
    OVERDUE,

    /** 该日之前没有任何打卡记录（习惯尚未开始）。部件上视为可随时开始。 */
    INACTIVE,
}

/**
 * [day] 的状态：取 `≤ day` 的最近完成日 c，按 `day − c` 与 [interval] 比较。
 * 只向后看，因为历史可以补卡，未来的记录不该倒推历史；[today] 用于把「已过去的应打卡日」
 * 判为逾期（那天没打就是断签，不该在点阵里继续显示成待打卡）。
 */
fun habitDayState(checks: Set<Long>, day: Long, interval: Int, today: Long): HabitDayState {
    val safeInterval = interval.coerceAtLeast(1).toLong()
    var anchor: Long? = null
    for (c in checks) {
        if (c <= day && (anchor == null || c > anchor)) anchor = c
    }
    val last = anchor ?: return HabitDayState.INACTIVE
    val delta = day - last
    return when {
        delta == 0L -> HabitDayState.CHECKED
        delta < safeInterval -> HabitDayState.SKIPPED
        delta == safeInterval && day >= today -> HabitDayState.DUE
        else -> HabitDayState.OVERDUE
    }
}

/**
 * 部件层面某日可否打卡：已完成 / 跳过日（提前打卡）不可点；应打卡、逾期（补打）与尚未开始可点。
 * 部分完成仍处于应打卡 / 逾期状态，因此可以继续 +1。
 */
fun canCheck(checks: Set<Long>, day: Long, interval: Int, today: Long): Boolean =
    when (habitDayState(checks, day, interval, today)) {
        HabitDayState.DUE, HabitDayState.OVERDUE, HabitDayState.INACTIVE -> true
        HabitDayState.CHECKED, HabitDayState.SKIPPED -> false
    }

/** 点阵里一个格子的状态。 */
@Keep
data class HabitDot(
    val day: Long,
    val state: HabitDayState,
    /** 当日已完成次数；已完成日 = [target]。 */
    val count: Int = 0,
    /** 当日目标次数；1 表示单次习惯。 */
    val target: Int = 1,
) {
    /** 部分完成（未达标但已至少完成一次）。 */
    val isPartial: Boolean
        get() = target > 1 && count in 1 until target

    /** 完成进度 0..1；已完成日 = 1。 */
    val progress: Float
        get() = if (target <= 0) 0f else (count.toFloat() / target).coerceIn(0f, 1f)
}

/**
 * 最近 [n] 个自然日（含今天）的点阵，旧 → 新。
 * [counts] / [target] 用于渲染多次习惯的进度弧；单次习惯保持默认值即可。
 */
fun lastNDaysDots(
    checks: Set<Long>,
    today: Long,
    interval: Int,
    n: Int,
    counts: Map<Long, Int> = emptyMap(),
    target: Int = 1,
): List<HabitDot> {
    val count = n.coerceAtLeast(1)
    val safeTarget = target.coerceIn(1, HabitMaxDailyTarget)
    return (count - 1 downTo 0).map { off ->
        val day = today - off
        val done = day in checks
        HabitDot(
            day = day,
            state = habitDayState(checks, day, interval, today),
            count = if (done) safeTarget else (counts[day] ?: 0).coerceIn(0, safeTarget),
            target = safeTarget,
        )
    }
}
