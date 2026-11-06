package hunoia.luno.config.model

import androidx.annotation.Keep
import kotlinx.serialization.Serializable
import java.util.UUID

/** 打卡记录保留天数：早于 `today − retention` 的打卡日写时惰性裁剪，避免无限增长。 */
const val HabitHistoryRetentionDays = 90

/**
 * 习惯类型。v1 只开放 [BOOLEAN]；枚举预留，数值习惯后续再开。
 */
@Keep
enum class HabitType { BOOLEAN }

/**
 * 一个习惯。打卡频率按「每 [interval] 日」滑动式判定（见 [habitDayState] / [currentStreak]）：
 * 上次打卡日之后，隔满 [interval] 日才是下一个应打卡日，中间是跳过日。
 *
 * 字段：
 * - [color]：ARGB 整数，来自 UI 的 `ColorSelection`（Theme 预设 / Custom）。
 * - [interval]：每 x 日，x ≥ 1；默认 1（每日）。
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
    val createdAt: Long = 0L,
) {
    /** 渲染端统一入口：按 [colorSource] 解析，主题切换时实时跟随。 */
    val colorSpec: ColorSpec
        get() = ColorSpec(colorSource, colorThemeKey, color)
}

/**
 * 习惯配置域：习惯列表 + 打卡记录（布尔）。
 *
 * 键用 `epochDay`（`LocalDate.toEpochDay()`），每日 0 点边界，跨时区不歧义。
 * [checks] 是 `habitId -> 已打卡 epochDay 集合`；数值习惯的 `values` 留待后续扩展。
 *
 * 数据流：网格面板部件与管理页都经 `:core:data` 的 `ConfigProvider` 门面读写，
 * 不直接进 `ActionRequest`（应用内状态变更，非可执行 Action）。
 */
@Serializable
@Keep
data class HabitConfig(
    val habits: List<Habit> = emptyList(),
    val checks: Map<String, Set<Long>> = emptyMap(),
) {

    val hasHabits: Boolean get() = habits.isNotEmpty()

    fun habitById(id: String): Habit? = habits.firstOrNull { it.id == id }

    /** 某习惯是否已在 [day] 打卡。 */
    fun isChecked(habitId: String, day: Long): Boolean = checks[habitId]?.contains(day) == true

    /** 写入一次打卡（幂等：已在集合则原样返回，避免无意义写盘）。 */
    fun withCheck(habitId: String, day: Long): HabitConfig {
        val current = checks[habitId] ?: emptySet()
        if (day in current) return this
        return copy(checks = checks + (habitId to current + day))
    }

    /** 取消 / 改历史（管理页近 7 天）：未打卡则原样返回。 */
    fun withoutCheck(habitId: String, day: Long): HabitConfig {
        val current = checks[habitId] ?: return this
        if (day !in current) return this
        val next = current - day
        return copy(checks = if (next.isEmpty()) checks - habitId else checks + (habitId to next))
    }

    /** 统一写入 / 清除：管理页补卡与改历史用。 */
    fun setChecked(habitId: String, day: Long, checked: Boolean): HabitConfig =
        if (checked) withCheck(habitId, day) else withoutCheck(habitId, day)

    /**
     * 惰性裁剪：丢掉早于 `today − retentionDays` 的打卡记录（含习惯条目清空即移除键）。
     * 无变化原样返回（避免无意义写盘）。写时调用，存储有界（见 [HabitHistoryRetentionDays]）。
     */
    fun pruneChecks(today: Long, retentionDays: Int): HabitConfig {
        val cutoff = today - retentionDays.coerceAtLeast(0)
        val pruned = checks
            .mapValues { (_, days) -> days.filter { it >= cutoff }.toSet() }
            .filterValues { it.isNotEmpty() }
        return if (pruned == checks) this else copy(checks = pruned)
    }

    fun addHabit(habit: Habit): HabitConfig = copy(habits = habits + habit)

    fun updateHabit(habit: Habit): HabitConfig =
        copy(habits = habits.map { if (it.id == habit.id) habit else it })

    /** 删除习惯并连带清理其打卡记录。 */
    fun deleteHabit(habitId: String): HabitConfig = copy(
        habits = habits.filterNot { it.id == habitId },
        checks = checks - habitId,
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
    /** 当天已打卡。 */
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
 * [day] 的状态：取 `≤ day` 的最近打卡日 c，按 `day − c` 与 [interval] 比较。
 * 只向后看，因为历史可以补卡，未来的记录不该倒推历史；[today] 用于把「已过去的应打卡日」
 * 判为逾期（那天没打就是断签，不该在点阵里继续显示成待打卡）。
 */
fun habitDayState(checks: Set<Long>, day: Long, interval: Int, today: Long): HabitDayState {
    val safeInterval = interval.coerceAtLeast(1).toLong()
    var anchor: Long? = null
    for (c in checks) {
        if (c <= day && (anchor == null || c > anchor!!)) anchor = c
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
 * 部件层面某日可否打卡：已打 / 跳过日（提前打卡）不可点；应打卡、逾期（补打）与尚未开始可点。
 * 取消打卡与改历史不走这里，统一走编辑页（[HabitConfig.setChecked]）。
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
)

/** 最近 [n] 个自然日（含今天）的点阵，旧 → 新。 */
fun lastNDaysDots(checks: Set<Long>, today: Long, interval: Int, n: Int): List<HabitDot> {
    val count = n.coerceAtLeast(1)
    return (count - 1 downTo 0).map { off ->
        val day = today - off
        HabitDot(
            day = day,
            state = habitDayState(checks, day, interval, today),
        )
    }
}
