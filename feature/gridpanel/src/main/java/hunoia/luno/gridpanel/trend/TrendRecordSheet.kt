package hunoia.luno.gridpanel.trend

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
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
import hunoia.luno.ui.component.PressButton
import hunoia.luno.ui.component.PressTextButton
import hunoia.luno.ui.component.sheet.LunoSheet
import hunoia.luno.ui.theme.ListSpacing
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * 快速记录：只输数值，时间固定为「点保存那一刻」。
 *
 * 不提供日期 / 时间选择器 —— 趋势记录是「此刻的实测值」，补录会让曲线失真；
 * 录错只能编辑数值，时间不可改。
 *
 * 一条记录是一组读数（见 [TrendMetric.valueNames]）：读数必须是槽位的**前缀**，
 * 中间留空而后面有值时不可保存（否则 `#2` 这种占位符会指错槽位）。
 *
 * [onSave] 回传的 [atMillis] 由本面板在**打开时**捕获，与顶部显示的时间标签同源：
 * 调用方不得自己再取一次 `System.currentTimeMillis()`，否则面板开着慢慢输的几分钟
 * 会让「显示 09:00、落盘 09:07」，图表上的点也偏到真实记录时间之后。
 */
@Composable
fun TrendRecordSheet(
    metric: TrendMetric,
    onDismiss: () -> Unit,
    onSave: (readings: List<Double>, atMillis: Long) -> Unit,
) {
    val slotCount = metric.valueCount
    val showSlotLabel = slotCount > 1
    val atMillis = remember { System.currentTimeMillis() }
    val texts = remember(metric.id, slotCount) {
        mutableStateOf(List(slotCount) { "" })
    }
    val focusRequesters = remember(slotCount) { List(slotCount) { FocusRequester() } }
    val timeLabel = remember(atMillis) {
        Instant.ofEpochMilli(atMillis)
            .atZone(ZoneId.systemDefault())
            .format(DateTimeFormatter.ofPattern("HH:mm"))
    }
    val readings = parseTrendReadings(texts.value, metric.safeDecimals)
    val formula = remember(readings) {
        if (metric.formula.isBlank()) null else evaluateTrendFormula(readings ?: emptyList(), metric.formula, metric.valueCount)
    }

    fun submit() {
        val values = readings ?: return
        onSave(values, atMillis)
        onDismiss()
    }

    LaunchedEffect(Unit) {
        runCatching { focusRequesters.first().requestFocus() }
    }

    LunoSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding(),
            verticalArrangement = Arrangement.spacedBy(ListSpacing),
        ) {
            Text(
                text = stringResource(R.string.trend_record_title, metric.name),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = stringResource(R.string.trend_record_now, timeLabel),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            texts.value.forEachIndexed { index, text ->
                val isLast = index == slotCount - 1
                // 单值指标没有槽位名，label 用指标名：输入框永远有语义标签，placeholder 才留给提示。
                val slotLabel = if (showSlotLabel) trendSlotLabel(metric, index) else metric.name
                OutlinedTextField(
                    value = text,
                    onValueChange = { newValue ->
                        texts.value = texts.value.toMutableList().also { it[index] = newValue }
                    },
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
                        onDone = { submit() },
                        onNext = {
                            if (!isLast) runCatching { focusRequesters[index + 1].requestFocus() }
                        },
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequesters[index]),
                )
            }
            if (readings == null) {
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(ListSpacing),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Spacer(Modifier.weight(1f))
                PressTextButton(onClick = onDismiss) {
                    Text(stringResource(hunoia.luno.design.R.string.cancel))
                }
                PressButton(onClick = ::submit, enabled = readings != null) {
                    Text(stringResource(R.string.trend_record_save))
                }
            }
        }
    }
}

/** 第 [index] 个读数槽位的展示名；未命名时回落「值 N」。单值指标返回空串（不需要标签）。 */
fun trendSlotLabel(metric: TrendMetric, index: Int): String {
    if (metric.valueCount <= 1) return ""
    return metric.valueNames.getOrNull(index)?.trim()?.takeIf { it.isNotEmpty() } ?: "值 ${index + 1}"
}

/**
 * 把各槽位文本解析成**前缀**读数。
 *
 * - 全空 → null（没有可保存的内容）。
 * - 末尾连续空槽位 → 裁掉，返回更短的读数列表。
 * - 中间空、后面有值 → null（不可保存：`#n` 占位符会指错槽位）。
 *
 * 解析成功的读数一律按指标小数位四舍五入，与保存口径一致。
 */
fun parseTrendReadings(texts: List<String>, decimals: Int): List<Double>? {
    val parsed = texts.map { text ->
        text.trim().replace(',', '.').toDoubleOrNull()?.takeIf { it.isFinite() }
    }
    val lastNonNull = parsed.indexOfLast { it != null }
    if (lastNonNull < 0) return null
    for (i in 0..lastNonNull) {
        if (parsed[i] == null) return null
    }
    return parsed.take(lastNonNull + 1).map { roundTrendValue(it!!, decimals) }
}