package hunoia.luno.ui.condition

import android.os.SystemClock
import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import hunoia.luno.R
import hunoia.luno.config.ConfigProvider
import hunoia.luno.config.model.AdvancedSettings
import hunoia.luno.config.model.Condition
import hunoia.luno.config.model.GestureButton
import hunoia.luno.config.model.RuleEffect
import hunoia.luno.config.model.RuleScope
import hunoia.luno.config.model.ScreenType
import hunoia.luno.config.model.VisibilityRule
import hunoia.luno.runtime.condition.formatTimeOfDay
import hunoia.luno.runtime.condition.hasLeaf
import hunoia.luno.ui.component.AppPickerSheet
import hunoia.luno.ui.component.ExpressiveCard
import hunoia.luno.ui.component.ExpressiveSwitchItem
import hunoia.luno.ui.component.MyColumn
import hunoia.luno.ui.component.TopBar
import hunoia.luno.ui.condition.components.RuleScopeSheet
import hunoia.luno.ui.navigation.NEW_CONDITION_RULE_ID
import hunoia.luno.ui.theme.CardShape
import hunoia.luno.ui.component.settings.CompactSettingsRow
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConditionEditScreen(
    onBack: () -> Unit,
    ruleId: String,
) {
    val loadedSettings by ConfigProvider.advancedSettings.collectAsStateWithLifecycle(initialValue = null)
    val buttons by ConfigProvider.gestureButtons.collectAsStateWithLifecycle(initialValue = emptyList())
    val scope = rememberCoroutineScope()

    var draft by remember { mutableStateOf<VisibilityRule?>(null) }
    var showScopeSheet by remember { mutableStateOf(false) }

    LaunchedEffect(ruleId, loadedSettings) {
        val settings = loadedSettings ?: return@LaunchedEffect
        if (draft != null) return@LaunchedEffect
        draft = settings.conditionRules.find { it.id == ruleId } ?: VisibilityRule(
            id = if (ruleId == NEW_CONDITION_RULE_ID) SystemClock.uptimeMillis().toString() else ruleId,
            condition = Condition.All(),
        )
    }

    val rule = draft ?: return

    fun save() {
        scope.launch {
            try {
                ConfigProvider.updateAdvancedSettings { settings ->
                    settings.copy(
                        conditionRules = if (settings.conditionRules.any { it.id == rule.id }) {
                            settings.conditionRules.map { if (it.id == rule.id) rule else it }
                        } else {
                            settings.conditionRules + rule
                        },
                    )
                }
                onBack()
            } catch (e: Exception) {
                Log.e("ConditionEdit", "save failed", e)
            }
        }
    }

    Scaffold(
        topBar = {
            TopBar(
                onBack = onBack,
                title = stringResource(R.string.condition_edit),
                actions = {
                    TextButton(onClick = { save() }, enabled = rule.condition.hasLeaf()) {
                        Text(stringResource(R.string.save))
                    }
                },
            )
        },
    ) { padding ->
        MyColumn(
            modifier = Modifier.padding(padding),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedTextField(
                value = rule.name,
                onValueChange = { draft = rule.copy(name = it) },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text(stringResource(R.string.condition_name_hint)) },
                singleLine = true,
            )

            ExpressiveSwitchItem(
                title = stringResource(R.string.condition_enabled),
                checked = rule.enabled,
                onCheckedChange = { draft = rule.copy(enabled = it) },
            )

            ExpressiveCard(
                title = stringResource(R.string.condition_effect),
                subtitle = stringResource(
                    if (rule.effect == RuleEffect.SHOW) R.string.effect_show else R.string.effect_hide,
                ),
                onClick = {},
            ) {
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    SegmentedButton(
                        selected = rule.effect == RuleEffect.SHOW,
                        onClick = { draft = rule.copy(effect = RuleEffect.SHOW) },
                        shape = SegmentedButtonDefaults.itemShape(count = 2, index = 0),
                    ) {
                        Text(stringResource(R.string.effect_show))
                    }
                    SegmentedButton(
                        selected = rule.effect == RuleEffect.HIDE,
                        onClick = { draft = rule.copy(effect = RuleEffect.HIDE) },
                        shape = SegmentedButtonDefaults.itemShape(count = 2, index = 1),
                    ) {
                        Text(stringResource(R.string.effect_hide))
                    }
                }
            }

            CompactSettingsRow(
                title = stringResource(R.string.condition_scope),
                subtitle = scopeSubtitle(rule),
                onClick = { showScopeSheet = true },
            )

            ExpressiveCard(
                title = stringResource(R.string.condition_home),
                subtitle = conditionSummary(rule.condition),
                onClick = {},
            ) {
                ConditionNodeEditor(
                    node = rule.condition,
                    onChange = { draft = rule.copy(condition = it) },
                    onDelete = null,
                )
                if (!rule.condition.hasLeaf()) {
                    Text(
                        text = stringResource(R.string.condition_needs_leaf),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }

    if (showScopeSheet) {
        RuleScopeSheet(
            buttons = buttons,
            scope = rule.scope,
            buttonIds = rule.buttonIds,
            onDismissRequest = { showScopeSheet = false },
            onConfirm = { newScope, ids ->
                draft = rule.copy(scope = newScope, buttonIds = ids)
                showScopeSheet = false
            },
        )
    }
}

@Composable
internal fun scopeSubtitle(rule: VisibilityRule): String = when (rule.scope) {
    RuleScope.ALL -> stringResource(R.string.scope_all)
    RuleScope.EXCEPT -> stringResource(R.string.scope_except_count, rule.buttonIds.size)
    RuleScope.ONLY -> stringResource(R.string.scope_only_count, rule.buttonIds.size)
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun ConditionNodeEditor(
    node: Condition,
    onChange: (Condition) -> Unit,
    onDelete: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val isNot = node is Condition.Not
    val inner: Condition = if (isNot) (node as Condition.Not).inner else node

    fun wrap(x: Condition): Condition = if (isNot) Condition.Not(x) else x

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            var menuExpanded by remember { mutableStateOf(false) }
            OutlinedButton(onClick = { menuExpanded = true }) {
                Text(typeLabel(inner), maxLines = 1)
                Icon(
                    imageVector = Icons.Default.ArrowDropDown,
                    contentDescription = null,
                )
            }
            DropdownMenu(
                expanded = menuExpanded,
                onDismissRequest = { menuExpanded = false },
            ) {
                NodeType.entries.forEach { type ->
                    DropdownMenuItem(
                        text = { Text(nodeTypeLabel(type)) },
                        onClick = {
                            onChange(wrap(newConditionOf(type, inner)))
                            menuExpanded = false
                        },
                    )
                }
            }
            Spacer(Modifier.weight(1f))
            Text(
                text = stringResource(R.string.logic_not),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Switch(
                checked = isNot,
                onCheckedChange = { on ->
                    onChange(if (on) Condition.Not(inner) else inner)
                },
            )
            if (onDelete != null) {
                IconButton(onClick = onDelete) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = stringResource(R.string.delete),
                    )
                }
            }
        }

        when (inner) {
            is Condition.All -> GroupEditor(
                items = inner.items,
                onUpdate = { onChange(wrap(inner.copy(items = it))) },
            )

            is Condition.Any -> GroupEditor(
                items = inner.items,
                onUpdate = { onChange(wrap(inner.copy(items = it))) },
            )

            is Condition.ForegroundApp -> {
                var showPicker by remember { mutableStateOf(false) }
                Text(
                    text = if (inner.packageNames.isEmpty()) {
                        stringResource(R.string.condition_apps_empty)
                    } else {
                        inner.packageNames.joinToString(", ")
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
                FilledTonalButton(onClick = { showPicker = true }) {
                    Text(stringResource(R.string.condition_app_select_title))
                }
                if (showPicker) {
                    AppPickerSheet(
                        onDismissRequest = { showPicker = false },
                        selectedPackageNames = inner.packageNames,
                        onConfirm = { picked ->
                            onChange(wrap(inner.copy(packageNames = picked)))
                        },
                    )
                }
            }

            is Condition.Screen -> FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ScreenType.entries.forEach { type ->
                    FilterChip(
                        selected = inner.screenType == type,
                        onClick = { onChange(wrap(inner.copy(screenType = type))) },
                        label = { Text(screenTypeLabel(type)) },
                    )
                }
            }

            is Condition.Battery -> BatteryEditor(
                battery = inner,
                onChange = { onChange(wrap(it)) },
            )

            is Condition.TimeRange -> TimeRangeEditor(
                timeRange = inner,
                onChange = { onChange(wrap(it)) },
            )

            is Condition.Not -> Unit
        }
    }
}

@Composable
private fun GroupEditor(
    items: List<Condition>,
    onUpdate: (List<Condition>) -> Unit,
) {
    items.forEachIndexed { index, child ->
        androidx.compose.material3.Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = CardShape,
            color = MaterialTheme.colorScheme.surfaceContainerLow,
        ) {
            ConditionNodeEditor(
                node = child,
                onChange = { newChild ->
                    onUpdate(items.toMutableList().also { it[index] = newChild })
                },
                onDelete = {
                    onUpdate(items.toMutableList().also { it.removeAt(index) })
                },
                modifier = Modifier.padding(8.dp),
            )
        }
    }
    TextButton(
        onClick = { onUpdate(items + Condition.Screen(ScreenType.LOCK_SCREEN)) },
    ) {
        Icon(
            imageVector = Icons.Default.Add,
            contentDescription = null,
            modifier = Modifier.padding(end = 4.dp),
        )
        Text(stringResource(R.string.condition_add))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BatteryEditor(
    battery: Condition.Battery,
    onChange: (Condition.Battery) -> Unit,
) {
    ExpressiveSwitchItem(
        title = stringResource(R.string.condition_charging),
        checked = battery.charging == true,
        onCheckedChange = { on ->
            onChange(battery.copy(charging = if (on) true else null))
        },
    )
    val rangeActive = battery.levelMin != null || battery.levelMax != null
    ExpressiveSwitchItem(
        title = stringResource(R.string.condition_battery_level),
        checked = rangeActive,
        onCheckedChange = { on ->
            onChange(
                if (on) battery.copy(levelMin = 0, levelMax = 100)
                else battery.copy(levelMin = null, levelMax = null),
            )
        },
    )
    if (rangeActive) {
        var sliderValue by remember(battery.levelMin, battery.levelMax) {
            mutableStateOf(
                (battery.levelMin ?: 0).toFloat()..(battery.levelMax ?: 100).toFloat(),
            )
        }
        RangeSlider(
            value = sliderValue,
            onValueChange = { sliderValue = it },
            onValueChangeFinished = {
                onChange(
                    battery.copy(
                        levelMin = sliderValue.start.roundToInt(),
                        levelMax = sliderValue.endInclusive.roundToInt(),
                    ),
                )
            },
            valueRange = 0f..100f,
        )
        Text(
            text = stringResource(
                R.string.condition_battery_summary,
                sliderValue.start.roundToInt(),
                sliderValue.endInclusive.roundToInt(),
            ),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun TimeRangeEditor(
    timeRange: Condition.TimeRange,
    onChange: (Condition.TimeRange) -> Unit,
) {
    var picking by remember { mutableStateOf<Int?>(null) }
    val crossesMidnight = timeRange.startMinute > timeRange.endMinute
    val nextDaySuffix = if (crossesMidnight) {
        " (${stringResource(R.string.condition_next_day)})"
    } else {
        ""
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutlinedButton(onClick = { picking = 0 }) {
            Text(formatTimeOfDay(timeRange.startMinute))
        }
        Text("–")
        OutlinedButton(onClick = { picking = 1 }) {
            Text(formatTimeOfDay(timeRange.endMinute) + nextDaySuffix)
        }
    }

    when (picking) {
        0 -> TimePickerDialog(
            title = stringResource(R.string.condition_time_start),
            initialMinute = timeRange.startMinute,
            onDismiss = { picking = null },
            onConfirm = { minute -> onChange(timeRange.copy(startMinute = minute)) },
        )

        1 -> TimePickerDialog(
            title = stringResource(R.string.condition_time_end),
            initialMinute = timeRange.endMinute,
            onDismiss = { picking = null },
            onConfirm = { minute -> onChange(timeRange.copy(endMinute = minute)) },
        )

        else -> Unit
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimePickerDialog(
    title: String,
    initialMinute: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit,
) {
    val timeState = rememberTimePickerState(
        initialHour = initialMinute / 60,
        initialMinute = initialMinute % 60,
        is24Hour = true,
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { TimePicker(state = timeState) },
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirm(timeState.hour * 60 + timeState.minute)
                    onDismiss()
                },
            ) {
                Text(stringResource(R.string.confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}

private enum class NodeType { ALL, ANY, APP, SCREEN, BATTERY, TIME }

private fun newConditionOf(type: NodeType, old: Condition): Condition = when (type) {
    NodeType.ALL -> Condition.All(items = if (old is Condition.Any) old.items else emptyList())
    NodeType.ANY -> Condition.Any(items = if (old is Condition.All) old.items else emptyList())
    NodeType.APP -> if (old is Condition.ForegroundApp) old else Condition.ForegroundApp()
    NodeType.SCREEN ->
        if (old is Condition.Screen) old else Condition.Screen(ScreenType.LOCK_SCREEN)

    NodeType.BATTERY -> if (old is Condition.Battery) old else Condition.Battery(charging = true)
    NodeType.TIME ->
        if (old is Condition.TimeRange) old else Condition.TimeRange(startMinute = 8 * 60, endMinute = 18 * 60)
}

@Composable
private fun typeLabel(condition: Condition): String = when (condition) {
    is Condition.All -> stringResource(R.string.logic_all)
    is Condition.Any -> stringResource(R.string.logic_any)
    is Condition.ForegroundApp -> stringResource(R.string.condition_foreground_app)
    is Condition.Screen -> screenTypeLabel(condition.screenType)
    is Condition.Battery -> stringResource(R.string.condition_battery)
    is Condition.TimeRange -> stringResource(R.string.condition_time_range)
    is Condition.Not -> typeLabel(condition.inner)
}

@Composable
private fun nodeTypeLabel(type: NodeType): String = when (type) {
    NodeType.ALL -> stringResource(R.string.logic_all)
    NodeType.ANY -> stringResource(R.string.logic_any)
    NodeType.APP -> stringResource(R.string.condition_foreground_app)
    NodeType.SCREEN -> stringResource(R.string.condition_screen)
    NodeType.BATTERY -> stringResource(R.string.condition_battery)
    NodeType.TIME -> stringResource(R.string.condition_time_range)
}

@Composable
private fun screenTypeLabel(type: ScreenType): String = stringResource(
    when (type) {
        ScreenType.LOCK_SCREEN -> R.string.lock_screen
        ScreenType.LAUNCHER -> R.string.launcher
        ScreenType.LANDSCAPE -> R.string.landscape
        ScreenType.PORTRAIT -> R.string.condition_portrait
        ScreenType.KEYBOARD_INPUT -> R.string.condition_keyboard_input
    },
)
