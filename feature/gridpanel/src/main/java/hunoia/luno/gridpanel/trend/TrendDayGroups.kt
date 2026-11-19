package hunoia.luno.gridpanel.trend

import hunoia.luno.config.model.TrendEntry
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** 按本地日期分组后的一天：组内记录按时间升序（与落盘顺序一致）。 */
data class TrendDayGroup(val day: LocalDate, val entries: List<TrendEntry>)

/**
 * 把记录按本地日期分组，返回**最新一天在前**的分组；组内仍按时间升序。
 *
 * [limit] 只取最近若干条再分组（详情页只渲染最近 200 条）；默认取全部。
 * 分组本身是纯函数，时区显式传入，便于单测跨日 / 跨时区边界。
 */
fun trendDayGroups(
    entries: List<TrendEntry>,
    limit: Int = Int.MAX_VALUE,
    zone: ZoneId = ZoneId.systemDefault(),
): List<TrendDayGroup> {
    if (entries.isEmpty() || limit <= 0) return emptyList()
    val recent = if (entries.size <= limit) entries else entries.takeLast(limit)
    val grouped = LinkedHashMap<LocalDate, MutableList<TrendEntry>>()
    recent.forEach { entry ->
        val day = Instant.ofEpochMilli(entry.atMillis).atZone(zone).toLocalDate()
        grouped.getOrPut(day) { mutableListOf() } += entry
    }
    return grouped.entries
        .sortedByDescending { it.key }
        .map { (day, dayEntries) -> TrendDayGroup(day, dayEntries.sortedBy { it.atMillis }) }
}
