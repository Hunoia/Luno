package hunoia.luno.gridpanel.internal

import hunoia.luno.bridge.system.AppProcessUsage
import hunoia.luno.config.model.SysMonitorWidgetParams
import hunoia.luno.core.domain.JsonSerializer
import java.util.Locale
import kotlin.math.roundToInt

/** 应用列表排序口径。 */
internal enum class SysSort(val key: String) {
    MEM(SysMonitorWidgetParams.SORT_MEM),
    CPU(SysMonitorWidgetParams.SORT_CPU),
    NAME(SysMonitorWidgetParams.SORT_NAME),
    ;

    companion object {
        fun of(key: String?): SysSort = entries.firstOrNull { it.key == key } ?: MEM
    }
}

internal fun parseSysMonitorParams(params: String?): SysMonitorWidgetParams =
    params?.takeIf { it.isNotBlank() }
        ?.let { runCatching { JsonSerializer.decodeFromString<SysMonitorWidgetParams>(it) }.getOrNull() }
        ?.normalized()
        ?: SysMonitorWidgetParams()

internal fun encodeSysMonitorParams(sort: SysSort, includeSystem: Boolean): String =
    JsonSerializer.encodeToString(SysMonitorWidgetParams(sort = sort.key, includeSystem = includeSystem))

/** 迷你曲线的采样点数（内存 1s 一帧，CPU 2s 一帧，窗口约 40s / 80s）。 */
internal const val SysHistorySize = 40

/** 追加一个 0..1 的采样点，超出窗口丢最旧的。非有限值直接忽略（不污染曲线）。 */
internal fun pushSample(history: List<Float>, value: Float): List<Float> {
    if (!value.isFinite()) return history
    val next = ArrayList<Float>(history.size + 1)
    next.addAll(history)
    next.add(value.coerceIn(0f, 1f))
    val overflow = next.size - SysHistorySize
    return if (overflow > 0) next.subList(overflow, next.size).toList() else next
}

/**
 * 应用列表排序。
 *
 * 内存 / CPU 按「档位」而非精确值排序：内存 0.5% 总内存一档，CPU 2% 一档；
 * 同档内沿用上一帧的相对顺序。数值在档内抖动时行不跳，跨档才换位——
 * 每帧按精确值重排会让列表每 2s 整体洗牌，用户按不到、也读不稳。
 */
internal fun sortAppRows(
    rows: List<AppProcessUsage>,
    sort: SysSort,
    totalMemKb: Long,
    previousOrder: Map<Int, Int>,
): List<AppProcessUsage> = when (sort) {
    SysSort.NAME -> rows.sortedWith(
        compareBy<AppProcessUsage> { it.label.lowercase(Locale.ROOT) }
            .thenBy { it.packageName },
    )
    SysSort.MEM -> rows.sortedWith(
        compareByDescending<AppProcessUsage> { memBucket(it, totalMemKb) }
            .thenBy { previousOrder[it.uid] ?: Int.MAX_VALUE }
            .thenBy { it.label.lowercase(Locale.ROOT) },
    )
    SysSort.CPU -> rows.sortedWith(
        compareByDescending<AppProcessUsage> { cpuBucket(it) }
            .thenBy { previousOrder[it.uid] ?: Int.MAX_VALUE }
            .thenBy { it.label.lowercase(Locale.ROOT) },
    )
}

/** 占系统总内存的 0.5% 为一档。 */
internal fun memBucket(row: AppProcessUsage, totalMemKb: Long): Int =
    (row.memKb * 200f / totalMemKb.coerceAtLeast(1L)).toInt()

/** 2% CPU 为一档。 */
internal fun cpuBucket(row: AppProcessUsage): Int = (row.cpuPct / 2f).toInt()

/** kB → 可读尺寸：≥1G 用 G（一位小数），≥10M 取整 M，否则一位小数 / K。 */
internal fun formatKb(kb: Long): String {
    val mb = kb / 1024f
    return when {
        mb >= 1024f -> String.format(Locale.US, "%.1fG", mb / 1024f)
        mb >= 10f -> "${mb.roundToInt()}M"
        mb >= 1f -> String.format(Locale.US, "%.1fM", mb)
        else -> "${kb}K"
    }
}

/** 每秒字节数 → `320K` / `1.2M`。 */
internal fun formatBytesPerSec(bytes: Long): String {
    if (bytes < 1024L) return "${bytes}B"
    val kb = bytes / 1024f
    if (kb < 1024f) return "${kb.roundToInt()}K"
    val mb = kb / 1024f
    return if (mb >= 10f) "${mb.roundToInt()}M" else String.format(Locale.US, "%.1fM", mb)
}

/** 当前频率（MHz）→ `2.4GHz` / `800MHz`。 */
internal fun formatFreqMhz(mhz: Long): String =
    if (mhz >= 1000L) {
        String.format(Locale.US, "%.1fGHz", mhz / 1000f)
    } else {
        "${mhz}MHz"
    }
