package hunoia.luno.ui.automation
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import hunoia.luno.R
import hunoia.luno.domain.automation.AutomationCenter
import hunoia.luno.config.model.AutomationRule
import hunoia.luno.config.model.RulePriority
import hunoia.luno.ui.actionlibrary.IconPalette
import hunoia.luno.ui.actionlibrary.IconPickerSheet
import hunoia.luno.ui.component.TextIconGlyph
import hunoia.luno.ui.theme.CardInnerSpacing
import hunoia.luno.ui.theme.ElevationSmall
import hunoia.luno.ui.theme.FloatingContentBottom
import hunoia.luno.ui.theme.LargeShape
import hunoia.luno.ui.theme.ListSpacing
import hunoia.luno.ui.theme.PageGutter
import hunoia.luno.ui.theme.RowIconSize
import hunoia.luno.ui.component.feedback.EmptyState
import hunoia.luno.ui.component.feedback.LunoIconButton
import hunoia.luno.ui.component.sheet.ConfirmDialog
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.compose.ui.res.stringResource

/**
 * 规则列表本体（Home 自动化 Tab 用）。数据自给：读 [AutomationCenter]，
 * 调用方只给滚动 state、padding 与编辑回调。
 */
@Composable
fun AutomationRulesTabContent(
    listState: LazyListState,
    onNavToEdit: (String) -> Unit,
    contentPadding: PaddingValues,
) {
    val rules by AutomationCenter.rules
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val events by AutomationCenter.log
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val scope = rememberCoroutineScope()
    val timeFormat = rememberTimeFormat()
    fun submit(next: List<AutomationRule>) {
        scope.launch {
            try {
                AutomationCenter.updateRules { next }
            } catch (e: Exception) {
                Log.e("AutomationSettings", "submit failed", e)
            }
        }
    }
    val context = LocalContext.current
    // 展示按优先级降序（与引擎执行顺序一致），同档保持原相对顺序。
    val ordered = rules.sortedByDescending { it.priority }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        state = listState,
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(ListSpacing),
    ) {
        if (rules.isEmpty()) {
            item(key = "empty") {
                EmptyState(message = context.getString(R.string.automation_empty))
            }
        }
        items(ordered, key = { it.id }) { rule ->
            RuleCard(
                rule = rule,
                lastRunText = events.filter { it.wallTimeMs > 0L }.lastOrNull { event -> event.ruleId == rule.id }?.let { event ->
                    timeFormat.format(Date(event.wallTimeMs))
                } ?: context.getString(R.string.rule_never_run),
                fireCount = events.count { event -> event.ruleId == rule.id && event.fired },
                onEnabledChange = { enabled ->
                    submit(rules.map { if (it.id == rule.id) it.copy(enabled = enabled) else it })
                },
                onPriorityChange = { priority ->
                    submit(rules.map { if (it.id == rule.id) it.copy(priority = priority) else it })
                },
                onIconChange = { iconKey ->
                    submit(rules.map { if (it.id == rule.id) it.copy(iconKey = iconKey) else it })
                },
                onEdit = { onNavToEdit(rule.id) },
                onDelete = { submit(rules.filterNot { it.id == rule.id }) },
            )
        }
    }
}
@Composable
private fun RuleCard(
    rule: AutomationRule,
    lastRunText: String,
    fireCount: Int,
    onEnabledChange: (Boolean) -> Unit,
    onPriorityChange: (Int) -> Unit,
    onIconChange: (String?) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val context = LocalContext.current
    var priorityExpanded by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showIconPicker by remember { mutableStateOf(false) }
    Card(
        onClick = onEdit,
        modifier = Modifier.fillMaxWidth(),
        shape = LargeShape,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceBright,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            modifier = Modifier.padding(PageGutter),
            verticalArrangement = Arrangement.spacedBy(ListSpacing),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(CardInnerSpacing),
            ) {
                // M3e 风格：40dp 圆角底色容器 + 24dp 图标（与动作库图标同尺寸）。
                val iconMod = Modifier
                    .size(40.dp)
                    .background(MaterialTheme.colorScheme.primaryContainer, LargeShape)
                    .clickable(
                        indication = null,
                        interactionSource = null,
                        onClick = { showIconPicker = true },
                    )
                val paletteVector = IconPalette.byKey(rule.iconKey)
                Box(modifier = iconMod, contentAlignment = Alignment.Center) {
                    if (paletteVector != null) {
                        Icon(
                            imageVector = paletteVector,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(RowIconSize),
                        )
                    } else if (!rule.iconKey.isNullOrBlank()) {
                        TextIconGlyph(
                            text = rule.iconKey,
                            size = RowIconSize,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Filled.AccountTree,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(RowIconSize),
                        )
                    }
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = rule.name.ifBlank { conditionSummary(context, rule.condition) },
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = lastRunText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = rule.enabled,
                    onCheckedChange = onEnabledChange,
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Box {
                    TextButton(onClick = { priorityExpanded = !priorityExpanded }) {
                        Text(stringResource(priorityLabel(rule.priority)))
                        Icon(
                            imageVector = Icons.Default.ArrowDropDown,
                            contentDescription = null,
                        )
                    }
                    DropdownMenu(
                        expanded = priorityExpanded,
                        onDismissRequest = { priorityExpanded = false },
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        shape = LargeShape,
                        tonalElevation = ElevationSmall,
                    ) {
                        listOf(RulePriority.HIGH, RulePriority.MEDIUM, RulePriority.LOW).forEach { priority ->
                            DropdownMenuItem(
                                text = { Text(stringResource(priorityLabel(priority))) },
                                trailingIcon = {
                                    if (priority == rule.priority) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                        )
                                    }
                                },
                                onClick = {
                                    onPriorityChange(priority)
                                    priorityExpanded = false
                                },
                            )
                        }
                    }
                }
                Text(
                    text = stringResource(R.string.rule_fire_count, fireCount),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
                LunoIconButton(
                    icon = Icons.Default.Delete,
                    contentDescription = stringResource(R.string.delete),
                    onClick = { showDeleteDialog = true },
                )
            }
        }
    }
    if (showIconPicker) {
        IconPickerSheet(
            onDismissRequest = { showIconPicker = false },
            onPick = { iconKey ->
                onIconChange(iconKey)
                showIconPicker = false
            },
            currentKey = rule.iconKey,
            defaultIcon = Icons.Filled.AccountTree,
        )
    }
    if (showDeleteDialog) {
        ConfirmDialog(
            title = stringResource(R.string.automation_delete_title),
            text = rule.name.ifBlank { conditionSummary(context, rule.condition) },
            destructive = true,
            confirmLabel = stringResource(R.string.delete),
            onDismissRequest = { showDeleteDialog = false },
            onConfirm = {
                showDeleteDialog = false
                onDelete()
            },
        )
    }
}
@Composable
private fun rememberTimeFormat(): SimpleDateFormat {
    return remember {
        SimpleDateFormat("MM-dd HH:mm", Locale.getDefault())
    }
}