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
import hunoia.luno.config.model.TrendMetric
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
 */
@Composable
fun TrendRecordSheet(
    metric: TrendMetric,
    onDismiss: () -> Unit,
    onSave: (Double) -> Unit,
) {
    var text by remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }
    val now = remember { System.currentTimeMillis() }
    val timeLabel = remember(now) {
        Instant.ofEpochMilli(now)
            .atZone(ZoneId.systemDefault())
            .format(DateTimeFormatter.ofPattern("HH:mm"))
    }
    val parsed = text.trim().replace(',', '.').toDoubleOrNull()?.takeIf { it.isFinite() }

    fun submit() {
        val value = parsed ?: return
        onSave(roundTrendValue(value, metric.safeDecimals))
        onDismiss()
    }

    LaunchedEffect(Unit) {
        runCatching { focusRequester.requestFocus() }
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
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                trailingIcon = metric.unit.takeIf { it.isNotBlank() }?.let { unit -> { Text(unit) } },
                placeholder = { Text(stringResource(R.string.trend_record_hint)) },
                shape = MaterialTheme.shapes.large,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Decimal,
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(onDone = { submit() }),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(ListSpacing),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Spacer(Modifier.weight(1f))
                PressTextButton(onClick = onDismiss) {
                    Text(stringResource(hunoia.luno.design.R.string.cancel))
                }
                PressButton(onClick = ::submit, enabled = parsed != null) {
                    Text(stringResource(R.string.trend_record_save))
                }
            }
        }
    }
}
