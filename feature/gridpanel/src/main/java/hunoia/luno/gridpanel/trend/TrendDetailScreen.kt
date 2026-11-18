package hunoia.luno.gridpanel.trend

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
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
import hunoia.luno.ui.component.feedback.EmptyState
import hunoia.luno.ui.component.row.LunoGroup
import hunoia.luno.ui.component.row.LunoGroupItem
import hunoia.luno.ui.component.row.LunoSettingsRow
import hunoia.luno.ui.component.row.segmentedShape
import hunoia.luno.ui.component.screen.LunoScreen
import hunoia.luno.ui.theme.FloatingContentBottom
import hunoia.luno.ui.theme.ListSpacing
import hunoia.luno.ui.theme.PageGutter
import hunoia.luno.ui.theme.toColor
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.launch

/** 详情页最多渲染多少条历史记录；更早的数据仍在配置里，图表也仍会画。 */
private const val TrendRecordPreviewLimit = 200

/**
 * 趋势详情：摘要 + 真实时间轴折线 + 记录列表。
 *
 * [openRecord] 来自面板部件主体点击 / 提醒通知：首次进入自动弹快速记录，
 * 用户关掉后不再重开（局部状态消费一次）。
 *
 * 多读数槽位：每个槽位是一条独立的时间序列，摘要卡逐条统计、图表逐条画线；
 * 指标设了公式时再补一行「本条内计算」的结果。
 */
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
        LunoScreen(title = stringResource(R.string.trend_list_title), onBack = onBack) {
            EmptyState(message = stringResource(R.string.trend_metric_missing))
        }
        return
    }
    val entries = config.entriesOf(metricId)
    val scope = rememberCoroutineScope()
    var rangeName by rememberSaveable { mutableStateOf(TrendRange.DEFAULT.name) }
    val range = remember(rangeName) {
        TrendRange.entries.firstOrNull { it.name == rangeName } ?: TrendRange.DEFAULT
    }
    var showRecord by remember(metricId) { mutableStateOf(openRecord) }
    var editingEntryId by remember(metricId) { mutableStateOf<String?>(null) }
    val editingEntry = editingEntryId?.let { id -> entries.firstOrNull { it.id == id } }

    // 单指标上限 2 万条：统计与系列投影是 O(n) 的，绝不能每次重组重算一遍。
    val slotStats = remember(entries, metric.valueCount, metric.target, metric.goalDirection) {
        List(metric.valueCount) { index ->
            trendStats(seriesEntries(entries, index), metric.target, metric.goalDirection)
        }
    }
    // 指标色在组合态解析一次：`toColor()` 是 @Composable（要读当前主题），不能放进 remember 的 lambda。
    val baseColor = metric.colorSpec.toColor()
    val series = remember(entries, metric.valueCount, baseColor) {
        List(metric.valueCount) { index ->
            TrendSeries(
                entries = seriesEntries(entries, index),
                color = trendSeriesColor(baseColor, index, metric.valueCount),
            )
        }
    }
    val formulaValue = remember(entries, metric.formula, metric.valueCount) {
        trendFormulaValue(metric, entries.lastOrNull())
    }

    LunoScreen(
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
                containerColor = metric.colorSpec.toColor(),
            )
        },
        sheets = {
            if (showRecord) {
                TrendRecordSheet(
                    metric = metric,
                    onDismiss = { showRecord = false },
                    onSave = { readings, atMillis ->
                        scope.launch {
                            val added = TrendCenter.addEntry(
                                metric.id,
                                trendEntry(atMillis, readings),
                            )
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
        SummaryCard(metric = metric, stats = slotStats, formulaValue = formulaValue)
        LunoGroup(title = stringResource(R.string.trend_detail_chart)) {
            LunoGroupItem(shape = segmentedShape(0, if (metric.valueCount > 1) 3 else 2)) {
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
                            onClick = { rangeName = item.name },
                            label = { Text(trendRangeLabel(item)) },
                        )
                    }
                }
            }
            if (metric.valueCount > 1) {
                LunoGroupItem(shape = segmentedShape(1, 3)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = PageGutter, vertical = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(ListSpacing),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        repeat(metric.valueCount) { index ->
                            val color = trendSeriesColor(metric.colorSpec.toColor(), index, metric.valueCount)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    Modifier
                                        .size(10.dp)
                                        .clip(CircleShape)
                                        .background(color),
                                )
                                Text(
                                    text = trendSlotLabel(metric, index),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(start = 6.dp),
                                )
                            }
                        }
                    }
                }
            }
            LunoGroupItem(shape = segmentedShape(if (metric.valueCount > 1) 2 else 1, if (metric.valueCount > 1) 3 else 2)) {
                TrendSeriesChart(
                    series = series,
                    target = metric.target,
                    decimals = metric.safeDecimals,
                    unit = metric.unit,
                    range = range,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .padding(horizontal = PageGutter, vertical = 12.dp),
                )
            }
        }
        LunoGroup(title = stringResource(R.string.trend_detail_records)) {
            if (entries.isEmpty()) {
                LunoGroupItem(shape = segmentedShape(0, 1)) {
                    Text(
                        text = stringResource(R.string.trend_detail_no_entries),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = PageGutter, vertical = 12.dp),
                    )
                }
            } else {
                // 单指标上限 2 万条，全部铺进 Column 会拖垮页面；只渲染最近 200 条。
                val ordered = entries.takeLast(TrendRecordPreviewLimit).asReversed()
                ordered.forEachIndexed { index, entry ->
                    LunoSettingsRow(
                        title = trendEntryTitle(entry, metric),
                        subtitle = formatEntryTime(entry.atMillis),
                        shape = segmentedShape(index, ordered.size),
                        onClick = { editingEntryId = entry.id },
                    )
                }
                if (entries.size > TrendRecordPreviewLimit) {
                    Column(modifier = Modifier.padding(horizontal = PageGutter, vertical = 8.dp)) {
                        Text(
                            text = stringResource(
                                R.string.trend_detail_records_truncated,
                                TrendRecordPreviewLimit,
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

/** 记录行标题：单值显示数值；多值按槽位名逐个显示，让「哪条是哪个读数」一眼可辨。 */
private fun trendEntryTitle(entry: TrendEntry, metric: TrendMetric): String =
    if (metric.valueCount <= 1) {
        formatTrendValue(entry.readings.firstOrNull() ?: 0.0, metric.safeDecimals) + metric.unit
    } else {
        entry.readings.joinToString(" · ") { value ->
            formatTrendValue(value, metric.safeDecimals) + metric.unit
        }
    }

@Composable
private fun SummaryCard(
    metric: TrendMetric,
    stats: List<TrendStats>,
    formulaValue: Double?,
) {
    LunoGroup {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = PageGutter, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            val latest = stats.lastOrNull { it.latest != null }?.latest
            Text(
                text = latest?.let {
                    metric.valueCount.coerceAtMost(it.readings.size)
                        .let { count ->
                            it.readings.take(count).joinToString(" · ") {
                                formatTrendValue(it, metric.safeDecimals) + metric.unit
                            }
                        }
                } ?: stringResource(R.string.trend_detail_no_entries),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = metric.target?.let {
                    stringResource(
                        R.string.trend_summary_target,
                        formatTrendValue(it, metric.safeDecimals) + metric.unit,
                    )
                } ?: stringResource(R.string.trend_summary_no_target),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        // 摘要行的顺序：每个槽位依次 3 行（较上次 / 较 7 天前 / 距目标），末尾是本条内计算。
        // segmentedShape 需要的是扁平序号，不是每组的 0..2，所以统一按 `槽位 × 3 + 行` 编号。
        val hasFormulaRow = formulaValue != null
        val totalRows = stats.size * 3 + if (hasFormulaRow) 1 else 0
        stats.forEachIndexed { index, slotStats ->
            StatRow(
                title = if (metric.valueCount > 1) trendSlotLabel(metric, index)
                    else stringResource(R.string.trend_summary_since_previous),
                value = slotStats.deltaFromPrevious,
                metric = metric,
                shape = segmentedShape(index * 3, totalRows),
            )
            StatRow(
                title = stringResource(R.string.trend_summary_since_seven_days),
                value = slotStats.deltaFromSevenDays,
                metric = metric,
                shape = segmentedShape(index * 3 + 1, totalRows),
            )
            StatRow(
                title = stringResource(R.string.trend_summary_to_target),
                value = slotStats.distanceToTarget,
                metric = metric,
                shape = segmentedShape(index * 3 + 2, totalRows),
                reached = slotStats.goalReached,
            )
        }
        if (hasFormulaRow) {
            StatRow(
                title = stringResource(R.string.trend_summary_formula),
                value = formulaValue,
                metric = metric,
                shape = segmentedShape(stats.size * 3, totalRows),
                isDelta = false,
            )
        }
    }
}

@Composable
private fun StatRow(
    title: String,
    value: Double?,
    metric: TrendMetric,
    shape: Shape,
    reached: Boolean? = null,
    /** false = [value] 是绝对值（本条内计算的结果），不是变化量，不带正负号也不染涨跌色。 */
    isDelta: Boolean = true,
) {
    val text = when {
        value == null -> "—"
        !isDelta -> formatTrendValue(value, metric.safeDecimals) + metric.unit
        reached == true -> stringResource(
            R.string.trend_summary_reached,
            formatTrendDelta(value, metric.safeDecimals) + metric.unit,
        )

        else -> formatTrendDelta(value, metric.safeDecimals) + metric.unit
    }
    val color = when {
        !isDelta -> MaterialTheme.colorScheme.onSurface
        value == null -> MaterialTheme.colorScheme.onSurfaceVariant
        reached != null -> trendDeltaColor(value, metric.goalDirection)
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    LunoSettingsRow(
        title = title,
        trailing = {
            Text(
                text = text,
                style = MaterialTheme.typography.bodyLarge,
                color = color,
            )
        },
        shape = shape,
    )
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

private fun formatEntryTime(atMillis: Long): String =
    Instant.ofEpochMilli(atMillis)
        .atZone(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))