package hunoia.luno.gridpanel.todo

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import hunoia.luno.domain.gridpanel.TodoCenter
import hunoia.luno.gridpanel.R
import hunoia.luno.gridpanel.ui.SnoozeDurationDialog
import hunoia.luno.gridpanel.ui.snoozeDurationLabel
import hunoia.luno.ui.component.row.LunoGroup
import hunoia.luno.ui.component.row.LunoSelectableRow
import hunoia.luno.ui.component.row.LunoSettingsRow
import hunoia.luno.ui.component.row.segmentedShape
import hunoia.luno.ui.component.row.group
import hunoia.luno.ui.component.sheet.ConfirmDialog
import hunoia.luno.ui.component.sheet.LunoSheet
import hunoia.luno.ui.theme.ContentBottom
import kotlinx.coroutines.launch

/**
 * 待办设置页：通知（推迟时长）+ 已完成（保留天数 / 清空已完成）。
 *
 * 与执行面（[TodoListContent]）分开；所有设置写回走 [TodoCenter] 门面。
 * 降低保留天数会立即裁剪旧数据，裁剪数量 > 0 时先确认（避免误删）。
 */
@Composable
fun TodoSettingsScreen(
    onBack: () -> Unit,
) {
    val config by TodoCenter.todoConfig.collectAsStateWithLifecycle(initialValue = TodoCenter.latestTodoConfig())
    val scope = rememberCoroutineScope()

    var showSnooze by remember { mutableStateOf(false) }
    var showRetentionSheet by remember { mutableStateOf(false) }
    // 待确认的降档（天数 + 会被裁掉的条数一起冻结）；null = 无待确认裁剪。
    // 条数在点选那一刻算一次并随弹层走，弹层文案不会因重组 / 时间流逝而变。
    var pruneConfirm by remember { mutableStateOf<PruneConfirm?>(null) }
    var confirmClear by remember { mutableStateOf(false) }

    fun applyRetention(days: Int) {
        val toDelete = config.countPrunable(System.currentTimeMillis(), days)
        if (toDelete > 0) {
            pruneConfirm = PruneConfirm(days = days, count = toDelete)
        } else {
            scope.launch { TodoCenter.setDoneRetentionDays(days) }
        }
    }

    hunoia.luno.ui.component.page.LunoPage(title = stringResource(R.string.todo_settings_title),
onBack = onBack,
dialogs = {
            if (showSnooze) {
                SnoozeDurationDialog(
                    initialMinutes = config.reminderSnoozeMinutes,
                    onDismiss = { showSnooze = false },
                    onConfirm = { minutes ->
                        scope.launch { TodoCenter.setReminderSnoozeMinutes(minutes) }
                        showSnooze = false
                    },
                )
            }
            pruneConfirm?.let { pending ->
                ConfirmDialog(
                    title = stringResource(R.string.todo_done_retention),
                    text = stringResource(R.string.todo_prune_confirm, pending.count),
                    confirmLabel = stringResource(R.string.todo_prune_confirm_action),
                    destructive = true,
                    onDismissRequest = { pruneConfirm = null },
                    onConfirm = {
                        scope.launch { TodoCenter.setDoneRetentionDays(pending.days) }
                        pruneConfirm = null
                    },
                )
            }
            if (confirmClear) {
                ConfirmDialog(
                    title = stringResource(R.string.todo_clear_done),
                    text = stringResource(R.string.todo_clear_done_confirm),
                    confirmLabel = stringResource(R.string.todo_clear_done_action),
                    destructive = true,
                    onDismissRequest = { confirmClear = false },
                    onConfirm = {
                        scope.launch { TodoCenter.clearDoneTodos() }
                        confirmClear = false
                    },
                )
            }
        },
sheets = {
            if (showRetentionSheet) {
                LunoSheet(onDismissRequest = { showRetentionSheet = false }) {
                    Text(
                        text = stringResource(R.string.todo_done_retention),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Column(modifier = Modifier.fillMaxWidth()) {
                        RetentionOptions.forEach { days ->
                            LunoSelectableRow(
                                title = stringResource(R.string.todo_done_retention_value, days),
                                selected = config.doneRetentionDays == days,
                                singleSelect = true,
                                shape = segmentedShape(RetentionOptions.indexOf(days), RetentionOptions.size),
                                onSelectedChange = {
                                    showRetentionSheet = false
                                    applyRetention(days)
                                },
                            )
                        }
                    }
                }
            }
        }) {
hunoia.luno.ui.component.page.LunoPageColumn(bottomContentPadding = ContentBottom) {
    block {
        androidx.compose.foundation.layout.Column(
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(hunoia.luno.ui.theme.ListSpacing),
        ) {

        group(title = stringResource(R.string.todo_settings_group_notify)) {
row(
title = stringResource(R.string.todo_reminder_setting),
subtitle = snoozeDurationLabel(config.reminderSnoozeMinutes),
onClick = { showSnooze = true },

)        }
        group(title = stringResource(R.string.todo_settings_group_done)) {
row(
title = stringResource(R.string.todo_done_retention),
subtitle = stringResource(R.string.todo_done_retention_value, config.doneRetentionDays),
onClick = { showRetentionSheet = true },

)
row(
title = stringResource(R.string.todo_clear_done),
enabled = config.doneCount > 0,
onClick = { confirmClear = true },

)        }

        }
    }
}
}
}

/** 待确认的降档：天数 + 会被裁掉的条数（点选那一刻冻结）。 */
private data class PruneConfirm(val days: Int, val count: Int)

/** 保留天数档位（与计划定稿一致）；区间由 domain 的 [hunoia.luno.config.model.TodoDoneRetentionMinDays]
 * ..[hunoia.luno.config.model.TodoDoneRetentionMaxDays] 兜底。 */
private val RetentionOptions = listOf(1, 3, 7, 14, 30)
