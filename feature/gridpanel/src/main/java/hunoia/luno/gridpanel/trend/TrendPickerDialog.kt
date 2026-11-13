package hunoia.luno.gridpanel.trend

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import hunoia.luno.config.model.TrendMetric
import hunoia.luno.gridpanel.R
import hunoia.luno.ui.component.PressTextButton
import hunoia.luno.ui.component.sheet.AnchoredDialog
import hunoia.luno.ui.theme.ListSpacing
import hunoia.luno.ui.theme.toColor

/**
 * 趋势指标选择弹窗：给面板「趋势部件」添加时指定目标指标。
 * 选中即回调，由调用方把 metricId 写进 `Kind.Internal.params`（architecture §9.2）。
 */
@Composable
fun TrendPickerDialog(
    metrics: List<TrendMetric>,
    onSelect: (TrendMetric) -> Unit,
    onDismiss: () -> Unit,
) {
    AnchoredDialog(onDismiss = onDismiss) { dismiss ->
        Text(
            text = stringResource(R.string.trend_pick_title),
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
        if (metrics.isEmpty()) {
            Text(
                stringResource(R.string.trend_pick_empty),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 360.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                metrics.forEach { metric ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onSelect(metric)
                                dismiss()
                            }
                            .padding(vertical = 12.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            Modifier
                                .size(16.dp)
                                .clip(CircleShape)
                                .background(metric.colorSpec.toColor()),
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            text = metric.name,
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(ListSpacing),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Spacer(Modifier.weight(1f))
            PressTextButton(onClick = dismiss) {
                Text(stringResource(hunoia.luno.design.R.string.cancel))
            }
        }
    }
}
