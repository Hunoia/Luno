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
import hunoia.luno.config.model.TrendMetric
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
 * 已保存记录的编辑：只允许改数值，时间显示为只读。
 *
 * 删除单独二次确认；确认文案明确「只删这一条，不影响指标」。
 */
@Composable
fun TrendEntryEditSheet(
    metric: TrendMetric,
    entry: TrendEntry,
    onDismiss: () -> Unit,
    onSave: (Double) -> Unit,
    onDelete: () -> Unit,
) {
    var text by remember(entry.id) {
        mutableStateOf(formatTrendValue(entry.value, metric.safeDecimals))
    }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    val timeLabel = remember(entry.atMillis) {
        Instant.ofEpochMilli(entry.atMillis)
            .atZone(ZoneId.systemDefault())
            .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
    }
    val parsed = text.trim().replace(',', '.').toDoubleOrNull()?.takeIf { it.isFinite() }

    fun submit() {
        val value = parsed ?: return
        onSave(roundTrendValue(value, metric.safeDecimals))
        onDismiss()
    }

    LaunchedEffect(entry.id) {
        runCatching { focusRequester.requestFocus() }
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
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                trailingIcon = metric.unit.takeIf { it.isNotBlank() }?.let { unit -> { Text(unit) } },
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
