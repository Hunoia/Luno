package hunoia.luno.gridpanel.internal

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import hunoia.luno.config.model.TrendConfig
import hunoia.luno.config.model.TrendWidgetParams
import hunoia.luno.config.model.trendStats
import hunoia.luno.core.domain.JsonSerializer
import hunoia.luno.domain.gridpanel.TrendCenter
import hunoia.luno.gridpanel.LocalGridPanelClose
import hunoia.luno.gridpanel.R
import hunoia.luno.gridpanel.TrendExtras
import hunoia.luno.gridpanel.geometry.ItemCorner
import hunoia.luno.gridpanel.trend.TrendMiniChart
import hunoia.luno.gridpanel.trend.formatTrendDelta
import hunoia.luno.gridpanel.trend.formatTrendValue
import hunoia.luno.gridpanel.ui.gridPanelOnCardColor
import hunoia.luno.ui.component.pressClickable
import hunoia.luno.ui.theme.containerShape
import hunoia.luno.ui.theme.toColor

internal fun parseTrendWidgetParams(params: String?): TrendWidgetParams? {
    if (params.isNullOrBlank()) return null
    return runCatching { JsonSerializer.decodeFromString<TrendWidgetParams>(params) }.getOrNull()
}

/**
 * 趋势面板部件：标题进详情，主体直接快速记录。
 *
 * 一个实例绑定一个指标（params 存 [TrendWidgetParams]）；指标被删 / 未配置时
 * 显示空态，面板里还有其他指标则可点击原位重选（与习惯部件同一套 reconfigure 流程）。
 */
@Composable
internal fun TrendWidgetCell(
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    params: String? = null,
    onReconfigure: (() -> Unit)? = null,
) {
    val config by TrendCenter.trendConfig.collectAsState(initial = TrendConfig())
    val metric = config.metricById(parseTrendWidgetParams(params)?.metricId.orEmpty())
    val onCard = gridPanelOnCardColor
    if (metric == null) {
        EmptyTrendCell(
            modifier = modifier,
            configHasMetrics = config.hasMetrics,
            enabled = enabled,
            onReconfigure = onReconfigure,
        )
        return
    }
    val entries = config.entriesOf(metric.id)
    val stats = trendStats(entries, metric.target, metric.goalDirection)
    val color = metric.colorSpec.toColor()
    val context = LocalContext.current
    val closePanel = LocalGridPanelClose.current
    val recordHint = stringResource(R.string.grid_panel_trend_record_hint)

    Column(
        modifier = modifier
            .fillMaxSize()
            .clip(containerShape(ItemCorner))
            .padding(WidgetInnerPadding),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (enabled) {
                        Modifier.pressClickable {
                            context.startActivity(
                                TrendExtras.openDetailIntent(context, metric.id, openRecord = false),
                            )
                            closePanel()
                        }
                    } else {
                        Modifier
                    },
                ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(9.dp)
                    .clip(CircleShape)
                    .background(color),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = metric.name,
                style = MaterialTheme.typography.titleSmall,
                color = onCard,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.height(4.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .semantics {
                    contentDescription = recordHint
                }
                .then(
                    if (enabled) {
                        Modifier.pressClickable {
                            context.startActivity(
                                TrendExtras.openDetailIntent(context, metric.id, openRecord = true),
                            )
                            closePanel()
                        }
                    } else {
                        Modifier
                    },
                ),
        ) {
            Column(Modifier.fillMaxSize()) {
                val latest = stats.latest
                if (latest == null) {
                    Text(
                        text = stringResource(R.string.trend_detail_no_entries),
                        style = MaterialTheme.typography.bodySmall,
                        color = onCard.copy(alpha = 0.7f),
                    )
                } else {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            text = formatTrendValue(latest.value, metric.safeDecimals),
                            style = MaterialTheme.typography.headlineSmall,
                            color = onCard,
                            maxLines = 1,
                        )
                        if (metric.unit.isNotBlank()) {
                            Spacer(Modifier.width(3.dp))
                            Text(
                                text = metric.unit,
                                style = MaterialTheme.typography.bodySmall,
                                color = onCard.copy(alpha = 0.75f),
                            )
                        }
                    }
                    stats.deltaFromPrevious?.let { delta ->
                        Text(
                            text = stringResource(
                                R.string.trend_delta_from_previous,
                                formatTrendDelta(delta, metric.safeDecimals) + metric.unit,
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = onCard.copy(alpha = 0.65f),
                            maxLines = 1,
                        )
                    }
                }
                if (entries.isNotEmpty()) {
                    TrendMiniChart(
                        entries = entries,
                        target = metric.target,
                        color = color,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .padding(top = 4.dp),
                    )
                }
            }
        }
    }
}

/**
 * 空态：无指标或指向指标被删。
 *
 * 指向指标被删但还有其他指标、且非编辑态时**可点击**：走面板层「原位重选」，
 * 新部件替换本项（位置不变）。
 */
@Composable
private fun EmptyTrendCell(
    modifier: Modifier,
    configHasMetrics: Boolean,
    enabled: Boolean,
    onReconfigure: (() -> Unit)?,
) {
    val onCard = gridPanelOnCardColor
    val clickable = enabled && configHasMetrics && onReconfigure != null
    val hint = stringResource(R.string.grid_panel_trend_missing_reselect)
    Box(
        modifier = modifier
            .fillMaxSize()
            .clip(containerShape(ItemCorner))
            .alpha(if (clickable) 0.65f else 0.5f)
            .then(
                if (clickable) {
                    Modifier
                        .pressClickable { onReconfigure?.invoke() }
                        .semantics { contentDescription = hint }
                } else {
                    Modifier
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = stringResource(
                when {
                    clickable -> R.string.grid_panel_trend_missing_reselect
                    configHasMetrics -> R.string.grid_panel_trend_missing
                    else -> R.string.grid_panel_trend_empty
                },
            ),
            style = MaterialTheme.typography.bodySmall,
            color = onCard,
            textAlign = TextAlign.Center,
        )
    }
}
