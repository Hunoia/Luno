package hunoia.luno.ui.automation

import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import hunoia.luno.R
import hunoia.luno.config.ConfigProvider
import hunoia.luno.config.model.AutomationRule
import hunoia.luno.config.model.RuleEffectType
import hunoia.luno.config.model.RuleScope
import hunoia.luno.ui.component.EmptyState
import hunoia.luno.ui.component.MyColumn
import hunoia.luno.ui.component.SegmentedGroup
import hunoia.luno.ui.component.SegmentedSettingsRow
import hunoia.luno.ui.component.SegmentedSwitchRow
import hunoia.luno.ui.component.TopBar
import hunoia.luno.ui.component.segmentedShape
import hunoia.luno.ui.navigation.NEW_AUTOMATION_RULE_ID
import hunoia.luno.ui.theme.ListSpacing
import hunoia.luno.ui.theme.liquidGlassBackdrop
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.blur.layerBackdrop

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AutomationSettingsScreen(
    onBack: () -> Unit,
    onNavToEdit: (String) -> Unit,
) {
    val rules by ConfigProvider.automationRules
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val scope = rememberCoroutineScope()

    fun submit(next: List<AutomationRule>) {
        scope.launch {
            try {
                ConfigProvider.updateAutomationRules { next }
            } catch (e: Exception) {
                Log.e("AutomationSettings", "submit failed", e)
            }
        }
    }

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val backdrop = liquidGlassBackdrop()
    val context = LocalContext.current
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        contentWindowInsets = WindowInsets(),
        topBar = {
            TopBar(
                onBack = onBack,
                title = context.getString(R.string.automation_settings),
                actions = {
                    IconButton(onClick = { onNavToEdit(NEW_AUTOMATION_RULE_ID) }) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = context.getString(R.string.automation_new),
                        )
                    }
                },
                scrollBehavior = scrollBehavior,
                backdrop = backdrop,
            )
        },
    ) { padding ->
        MyColumn(
            modifier = Modifier
                .fillMaxSize()
                .layerBackdrop(backdrop)
                .nestedScroll(scrollBehavior.nestedScrollConnection),
            topPadding = padding.calculateTopPadding(),
            verticalArrangement = Arrangement.spacedBy(ListSpacing),
        ) {
            if (rules.isEmpty()) {
                EmptyState(message = context.getString(R.string.automation_empty))
            }
            rules.forEachIndexed { index, rule ->
                RuleCard(
                    rule = rule,
                    index = index,
                    total = rules.size,
                    onEnabledChange = { enabled ->
                        submit(rules.map { if (it.id == rule.id) it.copy(enabled = enabled) else it })
                    },
                    onMoveUp = {
                        if (index > 0) {
                            val mutable = rules.toMutableList()
                            val item = mutable.removeAt(index)
                            mutable.add(index - 1, item)
                            submit(mutable)
                        }
                    },
                    onMoveDown = {
                        if (index < rules.size - 1) {
                            val mutable = rules.toMutableList()
                            val item = mutable.removeAt(index)
                            mutable.add(index + 1, item)
                            submit(mutable)
                        }
                    },
                    onEdit = { onNavToEdit(rule.id) },
                    onDelete = { submit(rules.filterNot { it.id == rule.id }) },
                )
            }
        }
    }
}

@Composable
private fun RuleCard(
    rule: AutomationRule,
    index: Int,
    total: Int,
    onEnabledChange: (Boolean) -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val context = LocalContext.current
    val effectLabel = context.getString(effectLabelRes(rule.effect.type))
    val scopeLabel = when (rule.effect.scope) {
        RuleScope.ALL -> context.getString(R.string.scope_all)
        RuleScope.EXCEPT -> context.getString(R.string.scope_except_count, rule.effect.buttonIds.size)
        RuleScope.ONLY -> context.getString(R.string.scope_only_count, rule.effect.buttonIds.size)
    }
    SegmentedGroup {
        SegmentedSwitchRow(
            title = rule.name.ifBlank { conditionSummary(context, rule.condition) },
            subtitle = "${conditionSummary(context, rule.condition)} · $effectLabel · $scopeLabel",
            checked = rule.enabled,
            onCheckedChange = onEnabledChange,
            shape = segmentedShape(0, 2),
        )
        SegmentedSettingsRow(
            title = "",
            trailingContent = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.End,
                ) {
                    IconButton(
                        enabled = index > 0,
                        onClick = onMoveUp,
                    ) {
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowUp,
                            contentDescription = context.getString(R.string.rule_move_up),
                        )
                    }
                    IconButton(
                        enabled = index < total - 1,
                        onClick = onMoveDown,
                    ) {
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowDown,
                            contentDescription = context.getString(R.string.rule_move_down),
                        )
                    }
                    IconButton(onClick = onEdit) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = context.getString(R.string.rule_edit),
                        )
                    }
                    IconButton(onClick = onDelete) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = context.getString(R.string.delete),
                        )
                    }
                }
            },
            shape = segmentedShape(1, 2),
        )
    }
}

fun effectLabelRes(type: RuleEffectType): Int = when (type) {
    RuleEffectType.SHOW_BUTTONS -> R.string.effect_show
    RuleEffectType.HIDE_BUTTONS -> R.string.effect_hide
    RuleEffectType.RUN_ACTION -> R.string.automation_effect_run_action
}
