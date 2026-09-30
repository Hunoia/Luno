package hunoia.luno.ui.condition

import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.ui.input.nestedscroll.nestedScroll
import hunoia.luno.ui.theme.ListSpacing
import hunoia.luno.ui.theme.liquidGlassBackdrop
import top.yukonga.miuix.kmp.blur.layerBackdrop
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import hunoia.luno.R
import hunoia.luno.config.ConfigProvider
import hunoia.luno.config.model.AdvancedSettings
import hunoia.luno.config.model.Condition
import hunoia.luno.config.model.RuleEffect
import hunoia.luno.config.model.RuleScope
import hunoia.luno.config.model.ScreenType
import hunoia.luno.config.model.VisibilityRule
import hunoia.luno.runtime.condition.formatTimeOfDay
import hunoia.luno.ui.component.EmptyState
import hunoia.luno.ui.component.SegmentedGroup
import hunoia.luno.ui.component.SegmentedSettingsRow
import hunoia.luno.ui.component.SegmentedSwitchRow
import hunoia.luno.ui.component.MyColumn
import hunoia.luno.ui.component.TopBar
import hunoia.luno.ui.component.segmentedShape
import hunoia.luno.ui.navigation.NEW_CONDITION_RULE_ID
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
@OptIn(ExperimentalMaterial3Api::class)

@Composable
fun ConditionSettingsScreen(
    onBack: () -> Unit,
    onNavToEdit: (String) -> Unit,
) {
    val advancedSettings by ConfigProvider.advancedSettings
        .collectAsStateWithLifecycle(initialValue = AdvancedSettings())
    val scope = rememberCoroutineScope()
    val rules = advancedSettings.conditionRules

    fun submit(next: List<VisibilityRule>) {
        scope.launch {
            try {
                ConfigProvider.updateAdvancedSettings { it.copy(conditionRules = next) }
            } catch (e: Exception) {
                Log.e("ConditionSettings", "submit failed", e)
            }
        }
    }

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val backdrop = liquidGlassBackdrop()
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        contentWindowInsets = WindowInsets(),
        topBar = {
            TopBar(
                onBack = onBack,
                title = stringResource(R.string.condition_settings),
                actions = {
                    IconButton(onClick = { onNavToEdit(NEW_CONDITION_RULE_ID) }) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = stringResource(R.string.condition_new),
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
                EmptyState(message = stringResource(R.string.condition_empty))
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
                        val mutable = rules.toMutableList()
                        val item = mutable.removeAt(index)
                        mutable.add(index - 1, item)
                        submit(mutable)
                    },
                    onMoveDown = {
                        val mutable = rules.toMutableList()
                        val item = mutable.removeAt(index)
                        mutable.add(index + 1, item)
                        submit(mutable)
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
    rule: VisibilityRule,
    index: Int,
    total: Int,
    onEnabledChange: (Boolean) -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val effectLabel = stringResource(
        if (rule.effect == RuleEffect.SHOW) R.string.effect_show else R.string.effect_hide,
    )
    val scopeLabel = when (rule.scope) {
        RuleScope.ALL -> stringResource(R.string.scope_all)
        RuleScope.EXCEPT -> stringResource(R.string.scope_except_count, rule.buttonIds.size)
        RuleScope.ONLY -> stringResource(R.string.scope_only_count, rule.buttonIds.size)
    }
    SegmentedGroup(
        title = conditionSummary(rule.condition),
        subtitle = "$effectLabel · $scopeLabel",
    ) {
        SegmentedSwitchRow(
            title = stringResource(R.string.condition_enabled),
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
                            contentDescription = stringResource(R.string.rule_move_up),
                        )
                    }
                    IconButton(
                        enabled = index < total - 1,
                        onClick = onMoveDown,
                    ) {
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowDown,
                            contentDescription = stringResource(R.string.rule_move_down),
                        )
                    }
                    IconButton(onClick = onEdit) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = stringResource(R.string.rule_edit),
                        )
                    }
                    IconButton(onClick = onDelete) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = stringResource(R.string.delete),
                        )
                    }
                }
            },
            shape = segmentedShape(1, 2),
        )
    }
}

@Composable
internal fun conditionSummary(condition: Condition): String = when (condition) {
    is Condition.All -> condition.items
        .map { conditionSummary(it) }
        .joinToString(" ${stringResource(R.string.logic_all)} ")
        .ifEmpty { stringResource(R.string.condition_logic_group) }

    is Condition.Any -> condition.items
        .map { conditionSummary(it) }
        .joinToString(" ${stringResource(R.string.logic_any)} ")
        .ifEmpty { stringResource(R.string.condition_logic_group) }

    is Condition.Not -> "${stringResource(R.string.logic_not)} ${conditionSummary(condition.inner)}"

    is Condition.ForegroundApp ->
        if (condition.packageNames.isEmpty()) {
            stringResource(R.string.condition_apps_empty)
        } else {
            val shown = condition.packageNames.take(3).joinToString(", ")
            val extra = condition.packageNames.size - 3
            stringResource(
                R.string.condition_app_in,
                if (extra > 0) "$shown, +$extra" else shown,
            )
        }

    is Condition.Screen -> stringResource(
        when (condition.screenType) {
            ScreenType.LOCK_SCREEN -> R.string.lock_screen
            ScreenType.LAUNCHER -> R.string.launcher
            ScreenType.LANDSCAPE -> R.string.landscape
            ScreenType.PORTRAIT -> R.string.condition_portrait
            ScreenType.KEYBOARD_INPUT -> R.string.condition_keyboard_input
        },
    )

    is Condition.Battery -> {
        val parts = mutableListOf<String>()
        if (condition.charging == true) parts += stringResource(R.string.condition_charging)
        if (condition.levelMin != null || condition.levelMax != null) {
            parts += stringResource(
                R.string.condition_battery_summary,
                condition.levelMin ?: 0,
                condition.levelMax ?: 100,
            )
        }
        parts.joinToString(" · ").ifEmpty { stringResource(R.string.condition_battery) }
    }

    is Condition.TimeRange -> stringResource(
        R.string.condition_time_summary,
        formatTimeOfDay(condition.startMinute),
        formatTimeOfDay(condition.endMinute),
    )
}
