package hunoia.luno.gridpanel.trend

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import hunoia.luno.config.model.TrendFormulaResult
import hunoia.luno.config.model.TrendMetric
import hunoia.luno.config.model.evaluateTrendFormula
import hunoia.luno.gridpanel.R
import hunoia.luno.ui.theme.ListSpacing

/**
 * 记录 / 编辑两个面板共用的读数输入区：逐槽位一个输入框 + 公式预览 + 漏填提示。
 *
 * 状态留在调用方（保存按钮要用解析结果），这里只负责呈现：
 * - 漏填提示只在「用户已经输入过」之后出现，空表单不再一打开就报红；
 * - 单值指标用指标名做输入框标签，多值指标用槽位名（未命名回落「值 N」）。
 */
@Composable
internal fun TrendReadingsForm(
    metric: TrendMetric,
    texts: List<String>,
    onTextChange: (index: Int, text: String) -> Unit,
    focusRequesters: List<FocusRequester>,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val rowCount = texts.size
    val showSlotLabel = rowCount > 1
    val readings = parseTrendReadings(texts, metric.safeDecimals)
    val hasTyped = texts.any { it.isNotBlank() }
    val formula = remember(readings, metric.formula, rowCount) {
        if (metric.formula.isBlank()) {
            null
        } else {
            evaluateTrendFormula(readings ?: emptyList(), metric.formula, rowCount)
        }
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(ListSpacing),
    ) {
        texts.forEachIndexed { index, text ->
            val isLast = index == rowCount - 1
            val slotLabel = if (showSlotLabel) trendSlotLabel(metric, index) else metric.name
            OutlinedTextField(
                value = text,
                onValueChange = { newValue -> onTextChange(index, newValue) },
                singleLine = true,
                label = { Text(slotLabel) },
                trailingIcon = metric.unit.takeIf { it.isNotBlank() }?.let { unit -> { Text(unit) } },
                placeholder = { Text(stringResource(R.string.trend_record_hint)) },
                shape = MaterialTheme.shapes.large,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Decimal,
                    imeAction = if (isLast) ImeAction.Done else ImeAction.Next,
                ),
                keyboardActions = KeyboardActions(
                    onDone = { onDone() },
                    onNext = {
                        if (!isLast) runCatching { focusRequesters[index + 1].requestFocus() }
                    },
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequesters[index]),
            )
        }
        if (hasTyped && readings == null) {
            Text(
                text = stringResource(R.string.trend_record_missing_slots),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
        if (formula != null) {
            val formulaText = when (formula) {
                is TrendFormulaResult.Ok -> formatTrendValue(formula.value, metric.safeDecimals) + metric.unit
                else -> "—"
            }
            Text(
                text = stringResource(R.string.trend_formula_preview, metric.formula.trim(), formulaText),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
