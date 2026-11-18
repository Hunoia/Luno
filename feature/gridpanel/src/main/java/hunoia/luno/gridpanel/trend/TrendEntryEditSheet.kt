package hunoia.luno.gridpanel.trend

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
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
import hunoia.luno.config.model.TrendEntry
import hunoia.luno.config.model.TrendFormulaResult
import hunoia.luno.config.model.TrendMetric
import hunoia.luno.config.model.evaluateTrendFormula
import hunoia.luno.gridpanel.R
import hunoia.luno.ui.component.PressButton
import hunoia.luno.ui.component.PressIconButton
import hunoia.luno.ui.component.PressTextButton
import hunoia.luno.ui.component.sheet.ConfirmDialog
import hunoia.luno.ui.component.sheet.LunoSheet
import hunoia.luno.ui.theme.ListSpacing
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * 已保存记录的编辑：只允许改读数，时间显示为只读。
 *
 * 删除单独二次确认；确认文案明确「只删这一条，不影响指标」。
 *
 * 行数取 `max(当前槽位数, 该条已有的读数个数)`：指标后来减了槽位时，旧记录多出的读数仍然
 * 看得见、改得掉，不会被静默裁掉。
 */
@Composable
fun TrendEntryEditSheet(
    metric: TrendMetric,
    entry: TrendEntry,
    onDismiss: () -> Unit,
    onSave: (readings: List<Double>) -> Unit,
    onDelete: () -> Unit,
) {
    val existing = entry.readings
    val rowCount = maxOf(metric.valueCount, existing.size)
    val showSlotLabel = rowCount > 1
    var texts by remember(entry.id, rowCount) {
        mutableStateOf(
            List(rowCount) { index ->
                existing.getOrNull(index)?.let { formatTrendValue(it, metric.safeDecimals) } ?: ""
            },
        )
    }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    val focusRequesters = remember(rowCount) { List(rowCount) { FocusRequester() } }
    val timeLabel = remember(entry.atMillis) {
        Instant.ofEpochMilli(entry.atMillis)
            .atZone(ZoneId.systemDefault())
            .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
    }
    val readings = parseTrendReadings(texts, metric.safeDecimals)
    val formula = remember(readings) {
        if (metric.formula.isBlank()) null else evaluateTrendFormula(readings ?: emptyList(), metric.formula, rowCount)
    }

    fun submit() {
        val values = readings ?: return
        onSave(values)
        onDismiss()
    }

    LaunchedEffect(entry.id) {
        runCatching { focusRequesters.firstOrNull()?.requestFocus() }
    }

    LunoSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding(),
            verticalArrangement = Arrangement.spacedBy(ListSpacing),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.trend_entry_edit_title, metric.name),
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                PressIconButton(onClick = { showDeleteConfirm = true }) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = stringResource(R.string.trend_entry_delete),
                    )
                }
            }
            Text(
                text = stringResource(R.string.trend_entry_time, timeLabel),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            texts.forEachIndexed { index, text ->
                val isLast = index == rowCount - 1
                // 与记录面板同一口径：单值指标用指标名做标签，多值指标用槽位名。
                val slotLabel = if (showSlotLabel) trendSlotLabel(metric, index) else metric.name
                OutlinedTextField(
                    value = text,
                    onValueChange = { newValue ->
                        texts = texts.toMutableList().also { it[index] = newValue }
                    },
                    singleLine = true,
                    label = { Text(slotLabel) },
                    trailingIcon = metric.unit.takeIf { it.isNotBlank() }?.let { unit -> { Text(unit) } },
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

    if (showDeleteConfirm) {
        ConfirmDialog(
            title = stringResource(R.string.trend_entry_delete_confirm_title),
            text = stringResource(R.string.trend_entry_delete_confirm_text, timeLabel),
            confirmLabel = stringResource(R.string.trend_entry_delete),
            destructive = true,
            onDismissRequest = { showDeleteConfirm = false },
            onConfirm = {
                showDeleteConfirm = false
                onDelete()
                onDismiss()
            },
        )
    }
}