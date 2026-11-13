package hunoia.luno.gridpanel.trend

import hunoia.luno.config.model.TrendEntry
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.round
import kotlin.math.roundToInt

/** 详情页可选的时间范围；`null` = 全部。 */
enum class TrendRange(val days: Int?) {
    DAYS_7(7),
    DAYS_30(30),
    DAYS_90(90),
    YEAR(365),
    ALL(null),
    ;

    companion object {
        val DEFAULT = DAYS_30
    }
}

private const val DayMillis = 24L * 60L * 60L * 1000L

/**
 * 视窗 `[startMillis, endMillis]`。
 *
 * - 固定范围以「现在」为右端，避免最近一次记录很旧时右侧留白一大截；
 *   用户手动把系统时间调早导致记录「在未来」时，右端跟着记录走。
 * - 「全部」以第一条 / 最后一条记录为边界，不把空白历史算进来。
 * - 单点（首尾相同）向两侧各扩半天，保证 x 归一化不除零、点落在中间。
 */
fun trendWindow(
    entries: List<TrendEntry>,
    range: TrendRange,
    now: Long,
): LongRange {
    val first = entries.firstOrNull()?.atMillis
    val last = entries.lastOrNull()?.atMillis
    val end = if (range.days == null) {
        last ?: now
    } else {
        max(now, last ?: now)
    }
    val start = when {
        range.days != null -> end - range.days * DayMillis
        first != null -> first
        else -> end - TrendRange.DEFAULT.days!! * DayMillis
    }
    return if (end > start) {
        start..end
    } else {
        (start - DayMillis / 2)..(end + DayMillis / 2)
    }
}

/** 图表上的一个点：坐标是 0..1 的比例，渲染端再乘画布尺寸。 */
data class TrendPlotPoint(
    val fractionX: Float,
    val fractionY: Float,
    val value: Double,
    val atMillis: Long,
)

/** X 轴刻度：`fraction` 是 0..1 的位置。 */
data class TrendAxisTick(val fraction: Float, val label: String)

/**
 * 一张图的绘制数据（纯数据，可单测）。
 *
 * [points] 已按像素桶降采样，但仍保持时间升序；降采样只影响画线，不写回配置。
 * [valueMin] / [valueMax] 是 Y 轴边界，已包含目标值并留出上下留白。
 */
data class TrendChartData(
    val points: List<TrendPlotPoint>,
    val valueMin: Double,
    val valueMax: Double,
    val targetFractionY: Float?,
    val axisTicks: List<TrendAxisTick>,
    val visibleCount: Int,
    val spanMillis: Long,
) {
    val hasData: Boolean get() = points.isNotEmpty()
    val isSinglePoint: Boolean get() = visibleCount == 1
}

/** 只截取视窗内的记录（输入按时间升序）。 */
fun entriesInWindow(entries: List<TrendEntry>, window: LongRange): List<TrendEntry> =
    entries.filter { it.atMillis in window.first..window.last }

/**
 * 按像素桶做 min/max 降采样：每个桶保留最早的最小值点与最大值点（按时间排序）。
 *
 * 为什么是 min/max 而不是等距抽样：等距抽样会漏掉尖峰（例如一天内体重先掉后弹），
 * min/max 能把每个像素列的真实上下边界都保住。首尾点始终保留，保证线不断在视窗外。
 */
fun downsampleForWidth(
    entries: List<TrendEntry>,
    window: LongRange,
    widthPx: Float,
    maxPoints: Int = 600,
): List<TrendEntry> {
    if (entries.size <= maxPoints) return entries
    val span = (window.last - window.first).coerceAtLeast(1L)
    val buckets = widthPx.roundToInt().coerceIn(1, maxPoints / 2)
    val grouped = LinkedHashMap<Int, MutableList<TrendEntry>>()
    entries.forEach { entry ->
        val index = (((entry.atMillis - window.first).toDouble() / span) * buckets)
            .toInt()
            .coerceIn(0, buckets - 1)
        grouped.getOrPut(index) { mutableListOf() } += entry
    }
    val result = ArrayList<TrendEntry>(grouped.size * 2)
    grouped.values.forEach { bucket ->
        var minEntry = bucket.first()
        var maxEntry = bucket.first()
        bucket.forEach { entry ->
            if (entry.value < minEntry.value) minEntry = entry
            if (entry.value > maxEntry.value) maxEntry = entry
        }
        val picked = when {
            minEntry === maxEntry -> listOf(minEntry)
            minEntry.atMillis <= maxEntry.atMillis -> listOf(minEntry, maxEntry)
            else -> listOf(maxEntry, minEntry)
        }
        picked.forEach { if (result.lastOrNull()?.id != it.id) result += it }
    }
    // 桶边界可能把首尾切掉（视窗端点没有记录时不会发生，但时钟漂移后仍可能），显式补齐。
    val first = entries.first()
    val last = entries.last()
    if (result.firstOrNull()?.id != first.id) result.add(0, first)
    if (result.lastOrNull()?.id != last.id) result += last
    return result
}

/**
 * 构建图表数据。
 *
 * @param widthPx 画布宽度，用于决定降采样桶数；传 0 时按 [defaultWidthPx] 处理。
 */
fun buildTrendChart(
    entries: List<TrendEntry>,
    range: TrendRange,
    now: Long,
    target: Double?,
    widthPx: Float,
    zone: ZoneId = ZoneId.systemDefault(),
    defaultWidthPx: Float = 360f,
): TrendChartData {
    val window = trendWindow(entries, range, now)
    val span = (window.last - window.first).coerceAtLeast(1L)
    val visible = entriesInWindow(entries, window)
    val ticks = trendAxisTicks(window, zone)
    if (visible.isEmpty()) {
        return TrendChartData(
            points = emptyList(),
            valueMin = 0.0,
            valueMax = 1.0,
            targetFractionY = null,
            axisTicks = ticks,
            visibleCount = 0,
            spanMillis = span,
        )
    }
    var minValue = visible.minOf { it.value }
    var maxValue = visible.maxOf { it.value }
    if (target != null) {
        minValue = min(minValue, target)
        maxValue = max(maxValue, target)
    }
    if (minValue == maxValue) {
        // 全平的一组值 / 只有一个点：给一个对称的展示区间，避免除零。
        val margin = max(abs(minValue) * 0.05, 1.0)
        minValue -= margin
        maxValue += margin
    } else {
        val margin = (maxValue - minValue) * 0.08
        minValue -= margin
        maxValue += margin
    }
    val valueSpan = maxValue - minValue
    val sampled = downsampleForWidth(
        entries = visible,
        window = window,
        widthPx = if (widthPx > 0f) widthPx else defaultWidthPx,
    )
    val points = sampled.map { entry ->
        TrendPlotPoint(
            fractionX = ((entry.atMillis - window.first).toDouble() / span).toFloat().coerceIn(0f, 1f),
            fractionY = ((maxValue - entry.value) / valueSpan).toFloat().coerceIn(0f, 1f),
            value = entry.value,
            atMillis = entry.atMillis,
        )
    }
    return TrendChartData(
        points = points,
        valueMin = minValue,
        valueMax = maxValue,
        targetFractionY = target?.let { ((maxValue - it) / valueSpan).toFloat().coerceIn(0f, 1f) },
        axisTicks = ticks,
        visibleCount = visible.size,
        spanMillis = span,
    )
}

/** X 轴 4 个刻度（含两端）。跨度短到同一天时显示时刻，跨年时显示年月。 */
fun trendAxisTicks(
    window: LongRange,
    zone: ZoneId = ZoneId.systemDefault(),
): List<TrendAxisTick> {
    val span = window.last - window.first
    val formatter = when {
        span <= 36L * 60 * 60 * 1000 -> DateTimeFormatter.ofPattern("HH:mm")
        span <= 120 * DayMillis -> DateTimeFormatter.ofPattern("MM-dd")
        else -> DateTimeFormatter.ofPattern("yyyy-MM")
    }
    val fractions = listOf(0f, 1f / 3f, 2f / 3f, 1f)
    return fractions.map { fraction ->
        val millis = window.first + (span * fraction).toLong()
        val label = Instant.ofEpochMilli(millis).atZone(zone).format(formatter)
        TrendAxisTick(fraction, label)
    }
}

// ---------- 展示格式 ----------

/** 按小数位格式化数值；四舍五入用于展示，不改写已存记录。 */
fun formatTrendValue(value: Double, decimals: Int, locale: java.util.Locale = java.util.Locale.getDefault()): String {
    val digits = decimals.coerceIn(0, 4)
    return String.format(locale, "%.${digits}f", value)
}

/**
 * 变化值：正数带 `+`，四舍五入后为 0 时统一显示 `0`（避免 `-0.0`）。
 */
fun formatTrendDelta(delta: Double, decimals: Int, locale: java.util.Locale = java.util.Locale.getDefault()): String {
    val digits = decimals.coerceIn(0, 4)
    val factor = Math.pow(10.0, digits.toDouble())
    val rounded = round(delta * factor) / factor
    val text = formatTrendValue(abs(rounded), digits, locale)
    val sign = when {
        rounded > 0 -> "+"
        rounded < 0 -> "-"
        else -> ""
    }
    return sign + text
}

/**
 * 保存时按指标小数位四舍五入。之后再展示 / 统计都用这个值，
 * 避免「输入 70.04、界面显示 70.0、目标判断却按 70.04」的口径分裂。
 */
fun roundTrendValue(value: Double, decimals: Int): Double {
    val digits = decimals.coerceIn(0, 4)
    val factor = Math.pow(10.0, digits.toDouble())
    return round(value * factor) / factor
}
