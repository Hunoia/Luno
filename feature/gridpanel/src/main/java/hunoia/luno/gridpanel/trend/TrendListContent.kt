package hunoia.luno.gridpanel.trend

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import hunoia.luno.config.model.TrendConfig
import hunoia.luno.config.model.TrendGoalDirection
import hunoia.luno.config.model.TrendMetric
import hunoia.luno.config.model.trendStats
import hunoia.luno.domain.gridpanel.TrendCenter
import hunoia.luno.gridpanel.R
import hunoia.luno.ui.component.feedback.EmptyState
import hunoia.luno.ui.component.row.LunoGroup
import hunoia.luno.ui.component.row.LunoSettingsRow
import hunoia.luno.ui.component.row.segmentedShape
import hunoia.luno.ui.theme.FloatingContentBottom
import hunoia.luno.ui.theme.ListSpacing
import hunoia.luno.ui.theme.PageGutter
import hunoia.luno.ui.theme.toColor

/**
 * 趋势列表内容：主页「趋势」tab 嵌入用。
 *
 * 共享主页外壳的顶栏，没有自己的顶栏 / 返回键 / FAB；新建指标入口是底栏的功能球，
 * 由调用方接 `onNewMetric`（导航到指标编辑页）。
 * 数据源 [TrendCenter.trendConfig]；增删改写走 domain 门面（配置写入，不走 Action）。
 */
@Composable
fun TrendListContent(
    topPadding: Dp,
    onOpenMetric: (metricId: String) -> Unit,
    modifier: Modifier = Modifier,
    state: LazyListState = rememberLazyListState(),
) {
    val config by TrendCenter.trendConfig.collectAsStateWithLifecycle(initialValue = TrendConfig())
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        state = state,
        contentPadding = PaddingValues(
            start = PageGutter,
            top = topPadding,
            end = PageGutter,
            bottom = FloatingContentBottom,
        ),
        verticalArrangement = Arrangement.spacedBy(ListSpacing),
    ) {
        if (config.metrics.isEmpty()) {
            item {
                EmptyState(message = stringResource(R.string.trend_empty))
            }
        } else {
            item {
                LunoGroup(title = stringResource(R.string.trend_list_group)) {
                    config.metrics.forEachIndexed { index, metric ->
                        TrendMetricRow(
                            metric = metric,
                            entries = config.entriesOf(metric.id),
                            shape = segmentedShape(index, config.metrics.size),
                            onClick = { onOpenMetric(metric.id) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TrendMetricRow(
    metric: TrendMetric,
    entries: List<hunoia.luno.config.model.TrendEntry>,
    shape: androidx.compose.ui.graphics.Shape,
    onClick: () -> Unit,
) {
    val latest = entries.lastOrNull()
    val stats = trendStats(entries, metric.target, metric.goalDirection)
    val unit = metric.unit
    val subtitle = if (latest == null) {
        stringResource(R.string.trend_detail_no_entries)
    } else {
        val value = formatTrendValue(latest.value, metric.safeDecimals) + unit
        val delta = stats.deltaFromPrevious?.let {
            stringResource(
                R.string.trend_delta_from_previous,
                formatTrendDelta(it, metric.safeDecimals) + unit,
            )
        }
        listOfNotNull(value, delta).joinToString(" · ")
    }
    LunoSettingsRow(
        title = metric.name,
        subtitle = subtitle,
        subtitleColor = trendDeltaColor(stats.deltaFromPrevious, metric.goalDirection),
        leading = {
            Box(
                Modifier
                    .size(16.dp)
                    .clip(CircleShape)
                    .background(metric.colorSpec.toColor()),
            )
        },
        trailing = if (entries.isEmpty()) {
            null
        } else {
            {
                TrendMiniChart(
                    entries = entries,
                    target = metric.target,
                    color = metric.colorSpec.toColor(),
                    modifier = Modifier
                        .width(64.dp)
                        .height(28.dp),
                )
            }
        },
        shape = shape,
        onClick = onClick,
    )
}

/** 涨跌颜色：朝目标方向是好（主色），反方向是坏（错误色），中立/无变化用次级文字色。 */
@Composable
fun trendDeltaColor(delta: Double?, direction: TrendGoalDirection): Color {
    if (delta == null || delta == 0.0 || direction == TrendGoalDirection.NEUTRAL) {
        return androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant
    }
    val good = when (direction) {
        TrendGoalDirection.LOWER_IS_BETTER -> delta < 0
        TrendGoalDirection.HIGHER_IS_BETTER -> delta > 0
        TrendGoalDirection.NEUTRAL -> true
    }
    return if (good) {
        androidx.compose.material3.MaterialTheme.colorScheme.primary
    } else {
        androidx.compose.material3.MaterialTheme.colorScheme.error
    }
}
