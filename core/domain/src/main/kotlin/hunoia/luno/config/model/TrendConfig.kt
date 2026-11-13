package hunoia.luno.config.model

import androidx.annotation.Keep
import kotlinx.serialization.Serializable
import java.util.UUID

/** 单个指标最多保留的记录数：手动录入场景下 2 万条已足够覆盖多年。 */
const val TrendMaxEntriesPerMetric = 20_000

/** 单指标小数位上限。 */
const val TrendMaxDecimals = 4

/** 目标方向只影响涨跌颜色与「是否达标」，不影响曲线本身。 */
@Serializable
@Keep
enum class TrendGoalDirection {
    LOWER_IS_BETTER,
    HIGHER_IS_BETTER,
    NEUTRAL,
}

/**
 * 一个趋势指标（体重 / 腰围 / 体脂……）。
 *
 * 与 [Habit] 刻意分开：习惯是「应打卡日 + 连续天数」的日历语义，趋势是
 * 「时间戳 + 数值」的时间序列语义。两者共用面板部件体系，但不共用数据模型。
 */
@Serializable
@Keep
data class TrendMetric(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "",
    /** 用户自定义单位（kg / 斤 / cm / %……），空串表示只显示数字。 */
    val unit: String = "",
    /** 展示小数位，0..[TrendMaxDecimals]。 */
    val decimals: Int = 1,
    val colorSource: ColorSource = ColorSource.Custom,
    val colorThemeKey: ThemeColorKey = ThemeColorKey.Primary,
    val color: Int = 0xFF4CAF50.toInt(),
    /** 目标线；null = 没设目标，不画线。 */
    val target: Double? = null,
    val goalDirection: TrendGoalDirection = TrendGoalDirection.NEUTRAL,
    /** 每日提醒开关；当天已有记录时调度器会跳过。 */
    val reminderEnabled: Boolean = false,
    val reminderHour: Int = 8,
    val reminderMinute: Int = 0,
    val createdAt: Long = 0L,
) {
    /** 渲染端统一入口：按 [colorSource] 解析，主题切换时实时跟随。 */
    val colorSpec: ColorSpec
        get() = ColorSpec(colorSource, colorThemeKey, color)

    /** 小数位钳到合法范围。 */
    val safeDecimals: Int
        get() = decimals.coerceIn(0, TrendMaxDecimals)

    /** 提醒时间（当天分钟数），便于调度与展示。 */
    val reminderMinutesOfDay: Int
        get() = reminderHour.coerceIn(0, 23) * 60 + reminderMinute.coerceIn(0, 59)
}

/**
 * 面板部件参数：`internal.trend` 指向的具体指标。
 *
 * 经 `GridPanelItem.Kind.Internal.params`（JSON 串）落盘，见 architecture §9.2。
 */
@Serializable
@Keep
data class TrendWidgetParams(val metricId: String)

/**
 * 一条趋势记录。时间只由保存那一刻决定，不支持补录；编辑只允许改 [value]。
 */
@Serializable
@Keep
data class TrendEntry(
    val id: String = UUID.randomUUID().toString(),
    /** epoch millis。 */
    val atMillis: Long,
    val value: Double,
)

/**
 * 趋势配置域：指标列表 + 按指标分组的记录。
 *
 * [entries] 的 key 是 metricId，value 按 [TrendEntry.atMillis] 升序保存；
 * 删除指标时连带删除其记录。数据流与习惯 / 待办一致：部件与管理页都经
 * `:core:data` 的 `ConfigProvider` 门面读写，不进入 `ActionRequest`。
 */
@Serializable
@Keep
data class TrendConfig(
    val metrics: List<TrendMetric> = emptyList(),
    val entries: Map<String, List<TrendEntry>> = emptyMap(),
) {

    val hasMetrics: Boolean get() = metrics.isNotEmpty()

    fun metricById(id: String): TrendMetric? = metrics.firstOrNull { it.id == id }

    fun entriesOf(metricId: String): List<TrendEntry> = entries[metricId].orEmpty()

    fun latestOf(metricId: String): TrendEntry? = entriesOf(metricId).lastOrNull()

    fun addMetric(metric: TrendMetric): TrendConfig = copy(metrics = metrics + metric)

    fun updateMetric(metric: TrendMetric): TrendConfig =
        copy(metrics = metrics.map { if (it.id == metric.id) metric else it })

    /** 删除指标并连带清理记录。 */
    fun deleteMetric(metricId: String): TrendConfig = copy(
        metrics = metrics.filterNot { it.id == metricId },
        entries = entries - metricId,
    )

    /**
     * 追加一条记录并保持时间升序。
     *
     * 返回值：写入后的配置；指标不存在或达到 [TrendMaxEntriesPerMetric] 时返回 null，
     * 由 UI 给出提示。时间升序插入而不是直接追加：用户手动改系统时间后记录仍能正确排序。
     */
    fun addEntry(metricId: String, entry: TrendEntry): TrendConfig? {
        if (metricById(metricId) == null) return null
        val current = entriesOf(metricId)
        if (current.size >= TrendMaxEntriesPerMetric) return null
        val index = current.indexOfFirst { it.atMillis > entry.atMillis }
        val next = if (index < 0) {
            current + entry
        } else {
            current.toMutableList().also { it.add(index, entry) }
        }
        return copy(entries = entries + (metricId to next))
    }

    /** 只改数值，时间与 id 保持不变。 */
    fun updateEntryValue(metricId: String, entryId: String, value: Double): TrendConfig {
        val current = entriesOf(metricId)
        if (current.none { it.id == entryId }) return this
        return copy(
            entries = entries + (
                metricId to current.map { if (it.id == entryId) it.copy(value = value) else it }
                ),
        )
    }

    fun deleteEntry(metricId: String, entryId: String): TrendConfig {
        val current = entriesOf(metricId)
        if (current.none { it.id == entryId }) return this
        val next = current.filterNot { it.id == entryId }
        return copy(
            entries = if (next.isEmpty()) entries - metricId else entries + (metricId to next),
        )
    }
}
