package hunoia.luno.gridpanel.trend

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import hunoia.luno.bridge.ui.showToast
import hunoia.luno.config.model.TrendConfig
import hunoia.luno.config.model.TrendEntry
import hunoia.luno.config.model.TrendMetric
import hunoia.luno.config.model.TrendStats
import hunoia.luno.config.model.trendStats
import hunoia.luno.domain.gridpanel.TrendCenter
import hunoia.luno.gridpanel.R
import hunoia.luno.ui.component.LunoFab
import hunoia.luno.ui.component.PressFilterChip
import hunoia.luno.ui.component.PressIconButton
import hunoia.luno.ui.component.feedback.EmptyState
import hunoia.luno.ui.component.row.LunoGroup
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
    val stats = trendStats(entries, metric.target, metric.goalDirection)
    val scope = rememberCoroutineScope()
    var rangeName by rememberSaveable { mutableStateOf(TrendRange.DEFAULT.name) }
    val range = remember(rangeName) {
        TrendRange.entries.firstOrNull { it.name == rangeName } ?: TrendRange.DEFAULT
    }
    var showRecord by remember(metricId) { mutableStateOf(openRecord) }
    var editingEntryId by remember(metricId) { mutableStateOf<String?>(null) }
    val editingEntry = editingEntryId?.let { id -> entries.firstOrNull { it.id == id } }

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
                    onSave = { value ->
                        scope.launch {
                            val added = TrendCenter.addEntry(
                                metric.id,
                                TrendEntry(atMillis = System.currentTimeMillis(), value = value),
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
                    onSave = { value ->
                        scope.launch { TrendCenter.updateEntryValue(metric.id, entry.id, value) }
                    },
                    onDelete = { scope.launch { TrendCenter.deleteEntry(metric.id, entry.id) } },
                )
            }
        },
    ) {
        SummaryCard(metric = metric, stats = stats)
        LunoGroup(title = stringResource(R.string.trend_detail_chart)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = PageGutter),
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
            TrendChart(
                entries = entries,
                target = metric.target,
                decimals = metric.safeDecimals,
                unit = metric.unit,
                color = metric.colorSpec.toColor(),
                range = range,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .padding(horizontal = PageGutter),
            )
        }
        LunoGroup(title = stringResource(R.string.trend_detail_records)) {
            if (entries.isEmpty()) {
                Column(modifier = Modifier.padding(horizontal = PageGutter)) {
                    Text(
                        text = stringResource(R.string.trend_detail_no_entries),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                // 单指标上限 2 万条，全部铺进 Column 会拖垮页面；只渲染最近 200 条。
                val ordered = entries.takeLast(TrendRecordPreviewLimit).asReversed()
                ordered.forEachIndexed { index, entry ->
                    LunoSettingsRow(
                        title = formatTrendValue(entry.value, metric.safeDecimals) + metric.unit,
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

@Composable
private fun SummaryCard(metric: TrendMetric, stats: TrendStats) {
    LunoGroup {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = PageGutter, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = stats.latest?.let {
                    formatTrendValue(it.value, metric.safeDecimals) + metric.unit
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
        StatRow(
            title = stringResource(R.string.trend_summary_since_previous),
            value = stats.deltaFromPrevious,
            metric = metric,
            shape = segmentedShape(0, 3),
        )
        StatRow(
            title = stringResource(R.string.trend_summary_since_seven_days),
            value = stats.deltaFromSevenDays,
            metric = metric,
            shape = segmentedShape(1, 3),
        )
        StatRow(
            title = stringResource(R.string.trend_summary_to_target),
            value = stats.distanceToTarget,
            metric = metric,
            shape = segmentedShape(2, 3),
            reached = stats.goalReached,
        )
    }
}

@Composable
private fun StatRow(
    title: String,
    value: Double?,
    metric: TrendMetric,
    shape: androidx.compose.ui.graphics.Shape,
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
    val color = when {
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
