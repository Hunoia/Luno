package hunoia.luno.config.model

import androidx.annotation.Keep
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * 一组趋势记录的派生统计。纯函数，供列表 / 详情 / 部件共用，避免同口径在多处解释。
 *
 * 「较 7 天前」按本地日期取：找 `最新日期 − 7` 当天或之前最后一条记录；
 * 不拿最早一条冒充基线，没有就返回 null。
 */
@Keep
data class TrendStats(
    val latest: TrendEntry?,
    val previous: TrendEntry?,
    val deltaFromPrevious: Double?,
    val sevenDayBaseline: TrendEntry?,
    val deltaFromSevenDays: Double?,
    /** `最新值 − 目标值`；没有目标时为 null。 */
    val distanceToTarget: Double?,
    /** 按目标方向是否已达标；没有目标 / 中立方向时为 null。 */
    val goalReached: Boolean?,
) {
    val hasData: Boolean get() = latest != null
}

fun trendStats(
    entries: List<TrendEntry>,
    target: Double?,
    direction: TrendGoalDirection,
    zone: ZoneId = ZoneId.systemDefault(),
): TrendStats {
    val latest = entries.lastOrNull()
    if (latest == null) {
        return TrendStats(
            latest = null,
            previous = null,
            deltaFromPrevious = null,
            sevenDayBaseline = null,
            deltaFromSevenDays = null,
            distanceToTarget = null,
            goalReached = null,
        )
    }
    val previous = entries.getOrNull(entries.lastIndex - 1)
    val latestDay = epochDayOf(latest.atMillis, zone)
    val cutoff = latestDay - 7
    val sevenDayBaseline = entries.lastOrNull { epochDayOf(it.atMillis, zone) <= cutoff }
    val distance = target?.let { latest.value - it }
    val reached = when {
        target == null || direction == TrendGoalDirection.NEUTRAL -> null
        direction == TrendGoalDirection.LOWER_IS_BETTER -> latest.value <= target
        else -> latest.value >= target
    }
    return TrendStats(
        latest = latest,
        previous = previous,
        deltaFromPrevious = previous?.let { latest.value - it.value },
        sevenDayBaseline = sevenDayBaseline,
        deltaFromSevenDays = sevenDayBaseline?.let { latest.value - it.value },
        distanceToTarget = distance,
        goalReached = reached,
    )
}

/** 本地日期（epochDay）；趋势图与「当天是否已记录」判断共用。 */
fun epochDayOf(atMillis: Long, zone: ZoneId = ZoneId.systemDefault()): Long =
    Instant.ofEpochMilli(atMillis).atZone(zone).toLocalDate().toEpochDay()

/** 指定本地日期是否已有记录。 */
fun hasEntryOn(entries: List<TrendEntry>, day: Long, zone: ZoneId = ZoneId.systemDefault()): Boolean =
    entries.any { epochDayOf(it.atMillis, zone) == day }

/** 本地日期 → 当天最后一刻的 epochDay（仅用于编辑 / 展示分组，不做补录）。 */
fun trendEntryDay(entry: TrendEntry, zone: ZoneId = ZoneId.systemDefault()): Long =
    epochDayOf(entry.atMillis, zone)

/** 展示用：把本地日期转成 `yyyy-MM-dd`。 */
fun formatEpochDay(day: Long): String = LocalDate.ofEpochDay(day).toString()
