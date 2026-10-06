package hunoia.luno.ui.automation
import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import hunoia.luno.R
import hunoia.luno.domain.automation.AutomationCenter
import hunoia.luno.config.model.AutomationRule
import hunoia.luno.config.model.RulePriority
import hunoia.luno.ui.theme.ElevationSmall
import hunoia.luno.ui.theme.LargeShape
import hunoia.luno.ui.component.feedback.EmptyState
import hunoia.luno.ui.component.feedback.LunoIconButton
import hunoia.luno.ui.navigation.NEW_AUTOMATION_RULE_ID
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import hunoia.luno.ui.component.row.LunoGroup
import hunoia.luno.ui.component.row.LunoSettingsRow
import hunoia.luno.ui.component.row.LunoSwitchRow
import hunoia.luno.ui.component.row.segmentedShape
import hunoia.luno.ui.component.screen.LunoScreen
import androidx.compose.ui.res.stringResource
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AutomationSettingsScreen(
    onBack: () -> Unit,
    onNavToEdit: (String) -> Unit,
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
    LunoScreen(
        title = stringResource(R.string.automation_settings),
        onBack = onBack,
        actions = {
            LunoIconButton(
                icon = Icons.Default.Add,
                contentDescription = context.getString(R.string.automation_new),
                onClick = { onNavToEdit(NEW_AUTOMATION_RULE_ID) },
            )
        },
    ) {

            if (rules.isEmpty()) {
                EmptyState(message = context.getString(R.string.automation_empty))
            }
            ordered.forEach { rule ->
                RuleCard(
                    rule = rule,
                    lastRunText = events.lastOrNull { it.ruleId == rule.id }?.let { event ->
                        context.getString(
                            R.string.history_last_run,
                            timeFormat.format(Date(event.timeMs)),
                            historySubtitle(event),
                        )
                    },
                    onEnabledChange = { enabled ->
                        submit(rules.map { if (it.id == rule.id) it.copy(enabled = enabled) else it })
                    },
                    onPriorityChange = { priority ->
                        submit(rules.map { if (it.id == rule.id) it.copy(priority = priority) else it })
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
    lastRunText: String?,
    onEnabledChange: (Boolean) -> Unit,
    onPriorityChange: (Int) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val context = LocalContext.current
    var priorityExpanded by remember { mutableStateOf(false) }
    val subtitle = "${conditionSummary(context, rule.condition)} · ${rule.effect.effectiveSteps().size}步" +
        (lastRunText?.let { " · $it" } ?: "")
    LunoGroup {
        LunoSwitchRow(
            title = rule.name.ifBlank { conditionSummary(context, rule.condition) },
            subtitle = subtitle,
            checked = rule.enabled,
            onCheckedChange = onEnabledChange,
            shape = segmentedShape(0, 2),
        )
        LunoSettingsRow(
            title = "",
            leading = {
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
            },
            trailing = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.End,
                ) {
                    LunoIconButton(
                        icon = Icons.Default.Edit,
                        contentDescription = stringResource(R.string.rule_edit),
                        onClick = onEdit,
                    )
                    LunoIconButton(
                        icon = Icons.Default.Delete,
                        contentDescription = stringResource(R.string.delete),
                        onClick = onDelete,
                    )
                }
            },
            shape = segmentedShape(1, 2),
        )
    }
}
@Composable
private fun rememberTimeFormat(): SimpleDateFormat {
    return remember {
        SimpleDateFormat("MM-dd HH:mm", Locale.getDefault())
    }
}