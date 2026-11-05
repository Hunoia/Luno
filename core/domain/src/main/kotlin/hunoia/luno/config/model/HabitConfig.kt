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
 * 一个习惯。打卡频率按「每 [interval] 日」滑动式判定（见 [currentStreak] / [isDueDay]）。
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
    val type: HabitType = HabitType.BOOLEAN,
    val interval: Int = 1,
    val createdAt: Long = 0L,
)

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

    /**
     * 部件层面可否打卡：只打卡不可取消——今天已打就不能再点；
     * 取消 / 改历史走管理页（[setChecked]）。
     */
    fun canCheckToday(habitId: String, today: Long): Boolean = !isChecked(habitId, today)

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
 * 连续自然天数（习惯当前 streak）。算法见方案文档 §1.3：
 *
 * - [checks] 为空 → 0；
 * - 今天未打且距最近打卡日已超过 [interval] 天（错过即断）→ 0；
 * - 否则从最近打卡日往前找「相邻打卡间隔 ≤ [interval]」的最长连续链，
 *   连续自然天数 = 今天 − 链起点 + 1（今天未打但在截止覆盖内仍计，今天尚未结束）。
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
 * [day] 是否是「应打卡日」：∃ 打卡日 c 使 `0 ≤ day − c ≤ interval`（打卡日向后覆盖一个周期）。
 * 用于近 N 天点阵的「弱灰 = 非 due」标注。
 */
fun isDueDay(checks: Set<Long>, day: Long, interval: Int): Boolean {
    val safeInterval = interval.coerceAtLeast(1)
    return checks.any { day - it in 0..safeInterval }
}

/** 点阵里一个格子的状态。 */
@Keep
data class HabitDot(
    val day: Long,
    val checked: Boolean,
    /** 是否处于某个打卡日后的覆盖周期内；false 表示「非 due」弱灰。 */
    val due: Boolean,
)

/** 最近 [n] 个自然日（含今天）的点阵，旧 → 新。 */
fun lastNDaysDots(checks: Set<Long>, today: Long, interval: Int, n: Int): List<HabitDot> {
    val count = n.coerceAtLeast(1)
    return (count - 1 downTo 0).map { off ->
        val day = today - off
        HabitDot(
            day = day,
            checked = day in checks,
            due = isDueDay(checks, day, interval),
        )
    }
}
