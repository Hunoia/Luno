package hunoia.luno.gridpanel.trend

import android.text.format.DateFormat
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import hunoia.luno.bridge.ui.showToast
import hunoia.luno.config.model.TrendConfig
import hunoia.luno.config.model.TrendEntry
import hunoia.luno.config.model.TrendMetric
import hunoia.luno.config.model.TrendStats
import hunoia.luno.config.model.seriesEntries
import hunoia.luno.config.model.trendEntry
import hunoia.luno.config.model.trendFormulaValue
import hunoia.luno.config.model.trendStats
import hunoia.luno.domain.gridpanel.TrendCenter
import hunoia.luno.gridpanel.R
import hunoia.luno.ui.component.LunoFab
import hunoia.luno.ui.component.PressFilterChip
import hunoia.luno.ui.component.PressIconButton
import hunoia.luno.ui.component.SheetGroupHeader
import hunoia.luno.ui.component.feedback.EmptyState
import hunoia.luno.ui.component.row.LunoGroup
import hunoia.luno.ui.component.row.LunoGroupItem
import hunoia.luno.ui.component.row.LunoSettingsRow
import hunoia.luno.ui.component.row.segmentedShape
import hunoia.luno.ui.component.screen.LunoScreenLazy
import hunoia.luno.ui.theme.CapsuleShape
import hunoia.luno.ui.theme.CardInnerSpacing
import hunoia.luno.ui.theme.FloatingContentBottom
import hunoia.luno.ui.theme.ListSpacing
import hunoia.luno.ui.theme.PageGutter
import hunoia.luno.ui.theme.TileInnerPadding
import hunoia.luno.ui.theme.toColor
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import kotlinx.coroutines.launch

/** 详情页最多渲染多少条历史记录；更早的数据仍在配置里，图表也仍会画。 */
private const val TrendRecordPreviewLimit = 200

/**
 * 趋势详情：摘要 + 真实时间轴折线 + 按日分组的记录。
 *
 * [openRecord] 来自面板部件主体点击 / 提醒通知：首次进入自动弹快速记录，
 * 用户关掉后不再重开（局部状态消费一次）。
 *
 * 多读数槽位：每个槽位是一条独立的时间序列，摘要逐槽位一行 chip，图表逐条画线；
 * 指标设了公式时再补一枚「本条内计算」chip。范围选择放在进程内 [TrendViewState]，
 * 切走再回来仍然保留。
 */
@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
fun TrendDetailScreen(
    metricId: String,
    openRecord: Boolean,
    onBack: () -> Unit,
    onEditMetric: (metricId: String) -> Unit,
) {
    val config by TrendCenter.trendConfig.collectAsStateWithLifecycle(initialValue = TrendConfig())
    val metric = config.metricById(metricId)
    if (metric == null) {
        LunoScreenLazy(title = stringResource(R.string.trend_list_title), onBack = onBack) {
            item {
                EmptyState(message = stringResource(R.string.trend_metric_missing))
            }
        }
        return
    }
    val entries = config.entriesOf(metricId)
    val scope = rememberCoroutineScope()
    val range by TrendViewState.range.collectAsStateWithLifecycle()
    var showRecord by remember(metricId) { mutableStateOf(openRecord) }
    var editingEntryId by remember(metricId) { mutableStateOf<String?>(null) }
    val editingEntry = editingEntryId?.let { id -> entries.firstOrNull { it.id == id } }
    val zone = remember { ZoneId.systemDefault() }

    // 单指标上限 2 万条：统计与系列投影是 O(n) 的，绝不能每次重组重算一遍。
    val slotStats = remember(entries, metric.valueCount, metric.target, metric.goalDirection, zone) {
        List(metric.valueCount) { index ->
            trendStats(seriesEntries(entries, index), metric.target, metric.goalDirection, zone)
        }
    }
    // 指标色与暗色主题在组合态解析一次：`toColor()` 是 @Composable（要读当前主题），不能放进 remember 的 lambda。
    val baseColor = metric.colorSpec.toColor()
    val dark = isSystemInDarkTheme()
    val series = remember(entries, metric.valueCount, baseColor, dark) {
        List(metric.valueCount) { index ->
            TrendSeries(
                entries = seriesEntries(entries, index),
                color = trendSeriesColor(baseColor, index, metric.valueCount, dark),
            )
        }
    }
    // 槽位名是 @Composable（未命名要取 string resource），只能在组合里显式循环生成。
    val slotLabels = ArrayList<String>(metric.valueCount)
    for (index in 0 until metric.valueCount) {
        slotLabels += if (metric.valueCount > 1) trendSlotLabel(metric, index) else metric.name
    }
    val formulaValue = remember(entries, metric.formula, metric.valueCount) {
        trendFormulaValue(metric, entries.lastOrNull())
    }
    val latestEntry = entries.lastOrNull()
    val today = LocalDate.now(zone)
    val dayGroups = remember(entries, zone) {
        trendDayGroups(entries, TrendRecordPreviewLimit, zone)
    }

    LunoScreenLazy(
        title = metric.name,
        onBack = onBack,
        bottomContentPadding = FloatingContentBottom,
        actions = {
            PressIconButton(onClick = { onEditMetric(metric.id) }) {
                Icon(
                    imageVector = Icons.Default.Edit,
                    contentDescription = stringResource(R.string.trend_edit_title),
                )
            }
        },
        floatingContent = {
            LunoFab(
                onClick = { showRecord = true },
                icon = Icons.Default.Add,
                contentDescription = stringResource(R.string.trend_record_add),
                containerColor = baseColor,
                contentColor = if (baseColor.luminance() > 0.5f) Color.Black else Color.White,
            )
        },
        sheets = {
            if (showRecord) {
                TrendRecordSheet(
                    metric = metric,
                    onDismiss = { showRecord = false },
                    onSave = { readings, atMillis ->
                        scope.launch {
                            val added = TrendCenter.addEntry(metric.id, trendEntry(atMillis, readings))
                            if (!added) showToast(R.string.trend_record_full)
                        }
                    },
                )
            }
            editingEntry?.let { entry ->
                TrendEntryEditSheet(
                    metric = metric,
                    entry = entry,
                    onDismiss = { editingEntryId = null },
                    onSave = { readings ->
                        scope.launch { TrendCenter.updateEntryReadings(metric.id, entry.id, readings) }
                    },
                    onDelete = { scope.launch { TrendCenter.deleteEntry(metric.id, entry.id) } },
                )
            }
        },
    ) {
        item(key = "trend_summary") {
            TrendSummary(
                metric = metric,
                stats = slotStats,
                formulaValue = formulaValue,
                latestEntry = latestEntry,
            )
        }
        item(key = "trend_chart") {
            TrendChartGroup(
                metric = metric,
                series = series,
                labels = slotLabels,
                range = range,
                dark = dark,
            )
        }
        stickyHeader(key = "trend_records_header") {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface),
            ) {
                SheetGroupHeader(stringResource(R.string.trend_detail_records))
            }
        }
        if (entries.isEmpty()) {
            item(key = "trend_records_empty") {
                LunoGroup {
                    LunoGroupItem(shape = segmentedShape(0, 1)) {
                        Text(
                            text = stringResource(R.string.trend_detail_no_entries),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = PageGutter, vertical = 12.dp),
                        )
                    }
                }
            }
        } else {
            dayGroups.forEach { group ->
                item(key = "trend-day-${group.day}") {
                    LunoGroup(title = trendDayTitle(group.day, today)) {
                        // 组内按时间倒序：最新一条在最近的位置。
                        val ordered = group.entries.asReversed()
                        ordered.forEachIndexed { index, entry ->
                            LunoSettingsRow(
                                title = trendEntryTitle(entry, metric, slotLabels),
                                subtitle = trendEntryTime(entry.atMillis, zone),
                                shape = segmentedShape(index, ordered.size),
                                onClick = { editingEntryId = entry.id },
                            )
                        }
                    }
                }
            }
            if (entries.size > TrendRecordPreviewLimit) {
                item(key = "trend_records_truncated") {
                    Text(
                        text = stringResource(
                            R.string.trend_detail_records_truncated,
                            TrendRecordPreviewLimit,
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = PageGutter, vertical = 8.dp),
                    )
                }
            }
        }
    }
}

/**
 * 摘要：最新一条记录的大数值 + 时间 / 目标一行 + 每个读数槽位一行 chip。
 *
 * 多槽位不再一个槽位铺三整行；信息量不降（较上次 / 7 天前 / 距目标都在），只是从纵向堆叠
 * 变成可折行的 chip 行，主值重新成为视觉焦点。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TrendSummary(
    metric: TrendMetric,
    stats: List<TrendStats>,
    formulaValue: Double?,
    latestEntry: TrendEntry?,
) {
    val unit = metric.unit
    LunoGroup {
        LunoGroupItem(shape = segmentedShape(0, 1)) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = PageGutter, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(CardInnerSpacing),
            ) {
                if (latestEntry == null) {
                    Text(
                        text = stringResource(R.string.trend_detail_no_entries),
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    val readings = latestEntry.readings.take(metric.valueCount.coerceAtLeast(1))
                    Text(
                        text = readings.joinToString(" · ") {
                            formatTrendValue(it, metric.safeDecimals)
                        }.let { if (unit.isNotBlank()) "$it $unit" else it },
                        style = if (metric.valueCount <= 1) {
                            MaterialTheme.typography.headlineMedium
                        } else {
                            MaterialTheme.typography.headlineSmall
                        },
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    val targetText = metric.target?.let {
                        stringResource(
                            R.string.trend_summary_target,
                            formatTrendValue(it, metric.safeDecimals) + unit,
                        )
                    } ?: stringResource(R.string.trend_summary_no_target)
                    Text(
                        text = trendMomentLabel(latestEntry.atMillis) + " · " + targetText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    stats.forEachIndexed { index, slotStats ->
                        if (metric.valueCount > 1) {
                            Text(
                                text = trendSlotLabel(metric, index),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(CardInnerSpacing),
                            verticalArrangement = Arrangement.spacedBy(CardInnerSpacing),
                        ) {
                            TrendDeltaChip(
                                label = stringResource(R.string.trend_summary_since_previous),
                                value = slotStats.deltaFromPrevious,
                                metric = metric,
                            )
                            TrendDeltaChip(
                                label = stringResource(R.string.trend_summary_since_seven_days),
                                value = slotStats.deltaFromSevenDays,
                                metric = metric,
                            )
                            TrendDeltaChip(
                                label = stringResource(R.string.trend_summary_to_target),
                                value = slotStats.distanceToTarget,
                                metric = metric,
                                reached = slotStats.goalReached,
                            )
                        }
                    }
                    if (formulaValue != null) {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(CardInnerSpacing),
                            verticalArrangement = Arrangement.spacedBy(CardInnerSpacing),
                        ) {
                            TrendStatChip(
                                label = stringResource(R.string.trend_summary_formula),
                                value = formatTrendValue(formulaValue, metric.safeDecimals) + unit,
                                valueColor = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                }
            }
        }
    }
}

/** 单个变化 chip：`较上次 -0.3 kg`。没有基线时显示 `—`，不假装是 0。 */
@Composable
private fun TrendDeltaChip(
    label: String,
    value: Double?,
    metric: TrendMetric,
    reached: Boolean? = null,
) {
    val text = when {
        value == null -> "—"
        reached == true -> stringResource(
            R.string.trend_summary_reached,
            formatTrendDelta(value, metric.safeDecimals) + metric.unit,
        )

        else -> formatTrendDelta(value, metric.safeDecimals) + metric.unit
    }
    TrendStatChip(
        label = label,
        value = text,
        valueColor = if (value == null) {
            MaterialTheme.colorScheme.onSurfaceVariant
        } else {
            trendDeltaColor(value, metric.goalDirection)
        },
    )
}

/** chip 本体：小号说明 + 带涨跌色的数值；胶囊角色，一行放不下由外部 FlowRow 折行。 */
@Composable
private fun TrendStatChip(
    label: String,
    value: String,
    valueColor: Color,
) {
    Surface(
        shape = CapsuleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.6f),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = CardInnerSpacing, vertical = TileInnerPadding),
            horizontalArrangement = Arrangement.spacedBy(TileInnerPadding),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = value,
                style = MaterialTheme.typography.labelLarge,
                color = valueColor,
            )
        }
    }
}

/** 图表组：范围 chip + 多系列图例（可折行）+ 折线图。 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TrendChartGroup(
    metric: TrendMetric,
    series: List<TrendSeries>,
    labels: List<String>,
    range: TrendRange,
    dark: Boolean,
) {
    val itemCount = if (metric.valueCount > 1) 3 else 2
    val baseColor = metric.colorSpec.toColor()
    LunoGroup(title = stringResource(R.string.trend_detail_chart)) {
        LunoGroupItem(shape = segmentedShape(0, itemCount)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = PageGutter, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(ListSpacing),
            ) {
                TrendRange.entries.forEach { item ->
                    PressFilterChip(
                        selected = item == range,
                        onClick = { TrendViewState.selectRange(item) },
                        label = { Text(trendRangeLabel(item)) },
                    )
                }
            }
        }
        if (metric.valueCount > 1) {
            LunoGroupItem(shape = segmentedShape(1, itemCount)) {
                FlowRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = PageGutter, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(ListSpacing),
                    verticalArrangement = Arrangement.spacedBy(CardInnerSpacing),
                ) {
                    for (index in 0 until metric.valueCount) {
                        val color = trendSeriesColor(baseColor, index, metric.valueCount, dark)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                Modifier
                                    .size(10.dp)
                                    .clip(CircleShape)
                                    .background(color),
                            )
                            Text(
                                text = labels.getOrElse(index) { "#${index + 1}" },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(start = TileInnerPadding),
                            )
                        }
                    }
                }
            }
        }
        LunoGroupItem(shape = segmentedShape(itemCount - 1, itemCount)) {
            TrendSeriesChart(
                series = series,
                target = metric.target,
                decimals = metric.safeDecimals,
                unit = metric.unit,
                range = range,
                labels = labels,
                onShowAll = { TrendViewState.selectRange(TrendRange.ALL) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .padding(horizontal = PageGutter, vertical = 12.dp),
            )
        }
    }
}

/** 记录行标题：单值直接显示数值；多值按槽位名逐个显示，让「哪条是哪个读数」一眼可辨。 */
private fun trendEntryTitle(
    entry: TrendEntry,
    metric: TrendMetric,
    labels: List<String>,
): String {
    val values = entry.readings.map { formatTrendValue(it, metric.safeDecimals) + metric.unit }
    if (metric.valueCount <= 1) return values.joinToString(" · ")
    return values.mapIndexed { index, value ->
        val name = labels.getOrNull(index)?.takeIf { it.isNotBlank() }
        if (name == null) value else "$name $value"
    }.joinToString(" · ")
}

/** 记录行副标题：只给时刻（日期已经在组头上），并按系统区域格式化。 */
private fun trendEntryTime(atMillis: Long, zone: ZoneId): String =
    Instant.ofEpochMilli(atMillis)
        .atZone(zone)
        .format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(Locale.getDefault()))

@Composable
private fun trendMomentLabel(atMillis: Long, zone: ZoneId = ZoneId.systemDefault()): String {
    // LocalConfiguration.current 是配置感知的可观察来源（lint: NonObservableLocale 禁止在
    // composable 里裸读 Locale.getDefault()——配置变化会读到过期区域）。
    val locale = LocalConfiguration.current.locales[0]
    val dateTime = Instant.ofEpochMilli(atMillis).atZone(zone)
    val time = dateTime.format(
        DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale),
    )
    val today = LocalDate.now(zone)
    return when (dateTime.toLocalDate()) {
        today -> stringResource(R.string.trend_time_today, time)
        today.minusDays(1) -> stringResource(R.string.trend_time_yesterday, time)
        else -> trendDateText(dateTime.toLocalDate(), today) + " " + time
    }
}

@Composable
private fun trendDayTitle(day: LocalDate, today: LocalDate): String = when (day) {
    today -> stringResource(R.string.trend_day_today)
    today.minusDays(1) -> stringResource(R.string.trend_day_yesterday)
    else -> trendDateText(day, today)
}

/** 本地化日期：同年省年份，跨年带年份；用系统日期骨架，不硬编码「月日」顺序。 */
private fun trendDateText(day: LocalDate, today: LocalDate): String {
    val locale = Locale.getDefault()
    val skeleton = if (day.year == today.year) "MMMd" else "yMMMd"
    val pattern = DateFormat.getBestDateTimePattern(locale, skeleton)
    return day.format(DateTimeFormatter.ofPattern(pattern, locale))
}

@Composable
private fun trendRangeLabel(range: TrendRange): String = stringResource(
    when (range) {
        TrendRange.DAYS_7 -> R.string.trend_range_7
        TrendRange.DAYS_30 -> R.string.trend_range_30
        TrendRange.DAYS_90 -> R.string.trend_range_90
        TrendRange.YEAR -> R.string.trend_range_year
        TrendRange.ALL -> R.string.trend_range_all
    },
)
