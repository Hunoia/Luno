package hunoia.luno.ui.automation

import android.content.Context
import android.os.SystemClock
import android.util.Log
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.input.KeyboardType
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import hunoia.luno.R
import hunoia.luno.action.model.NewActionLibrarySettings
import hunoia.luno.config.ConfigProvider
import hunoia.luno.config.model.AutomationRule
import hunoia.luno.config.model.BooleanMode
import hunoia.luno.config.model.ConditionItem
import hunoia.luno.config.model.ConditionType
import hunoia.luno.config.model.FireMode
import hunoia.luno.config.model.RuleEffect
import hunoia.luno.config.model.RuleEffectType
import hunoia.luno.config.model.RuleScope
import hunoia.luno.config.model.RuleStep
import hunoia.luno.config.model.RuleStepKind
import hunoia.luno.config.model.ThrottleKind
import hunoia.luno.config.model.ThrottlePolicy
import hunoia.luno.config.model.WhenGroup
import hunoia.luno.runtime.condition.hasLeaf
import hunoia.luno.ui.actionselect.selectedItemLabel
import hunoia.luno.ui.automation.components.RuleScopeSheet
import hunoia.luno.ui.component.MyColumn
import hunoia.luno.ui.component.SegmentedSettingsRow
import hunoia.luno.ui.component.TopBar
import hunoia.luno.ui.navigation.ActionSelect
import hunoia.luno.ui.navigation.NEW_AUTOMATION_RULE_ID
import hunoia.luno.ui.theme.CardInnerSpacing
import hunoia.luno.ui.theme.LargeShape
import hunoia.luno.ui.theme.ListSpacing
import hunoia.luno.ui.theme.ShapeExtraSmall
import hunoia.luno.ui.theme.liquidGlassBackdrop
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.blur.layerBackdrop

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AutomationEditScreen(
    onBack: () -> Unit,
    ruleId: String,
    onNavToActionSelect: (ActionSelect) -> Unit,
    onNavToHistory: (String) -> Unit,
) {
    val loadedRules by ConfigProvider.automationRules
        .collectAsStateWithLifecycle(initialValue = null)
    val buttons by ConfigProvider.gestureButtons
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val library by ConfigProvider.newActionLibrarySettings
        .collectAsStateWithLifecycle(initialValue = null)

    val scope = rememberCoroutineScope()
    var draft by remember { mutableStateOf<AutomationRule?>(null) }
    var showScopeSheet by remember { mutableStateOf(false) }

    LaunchedEffect(ruleId, loadedRules) {
        if (draft != null) return@LaunchedEffect
        val rules = loadedRules ?: return@LaunchedEffect
        val stored = rules.find { it.id == ruleId }
        draft = if (stored != null) {
            // 编辑态物化：legacy action/entryId→steps、cooldownMs→throttle，存档即迁移。
            stored.copy(
                effect = stored.effect.copy(steps = stored.effect.effectiveSteps()),
                throttle = stored.effectiveThrottle(),
                cooldownMs = 0L,
            )
        } else {
            AutomationRule(
                id = if (ruleId == NEW_AUTOMATION_RULE_ID) SystemClock.uptimeMillis().toString() else ruleId,
                condition = WhenGroup(),
                effect = RuleEffect(type = RuleEffectType.HIDE_BUTTONS),
            )
        }
    }

    LaunchedEffect(loadedRules) {
        val current = draft ?: return@LaunchedEffect
        val rules = loadedRules ?: return@LaunchedEffect
        val stored = rules.find { it.id == current.id } ?: return@LaunchedEffect
        // 动作选择页回填（整条规则级或某步骤级）写回 store 后，同步进 draft。
        if (stored.effect.action != current.effect.action ||
            stored.effect.entryId != current.effect.entryId ||
            stored.effect.steps != current.effect.steps
        ) {
            draft = current.copy(effect = current.effect.copy(
                action = stored.effect.action,
                entryId = stored.effect.entryId,
                steps = stored.effect.steps,
            ))
        }
    }

    val rule = draft ?: return
    val context = LocalContext.current

    fun onPickAction(stepPath: String) {
        scope.launch {
            ConfigProvider.updateAutomationRules { rules ->
                if (rules.any { r -> r.id == rule.id }) {
                    rules.map { r -> if (r.id == rule.id) rule else r }
                } else {
                    rules + rule
                }
            }
            onNavToActionSelect(ActionSelect(
                automationRuleId = rule.id,
                automationStepPath = stepPath,
            ))
        }
    }

    fun save() {
        scope.launch {
            try {
                ConfigProvider.updateAutomationRules { rules ->
                    if (rules.any { it.id == rule.id }) {
                        rules.map { if (it.id == rule.id) rule else it }
                    } else {
                        rules + rule
                    }
                }
                onBack()
            } catch (e: Exception) {
                Log.e("AutomationEdit", "save failed", e)
            }
        }
    }

    val throttleInvalid = isInvalidThrottle(rule.throttle)
    val stepsEmpty = rule.effect.type == RuleEffectType.RUN_ACTION && rule.effect.steps.isEmpty()
    val saveEnabled = rule.condition.hasLeaf() &&
        (rule.effect.type != RuleEffectType.RUN_ACTION || !stepsEmpty) &&
        !throttleInvalid

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val backdrop = liquidGlassBackdrop()
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        contentWindowInsets = WindowInsets(),
        topBar = {
            TopBar(
                onBack = onBack,
                title = stringResource(R.string.automation_edit),
                actions = {
                    TextButton(onClick = { save() }, enabled = saveEnabled) {
                        Text(stringResource(R.string.save))
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
            OutlinedTextField(
                value = rule.name,
                onValueChange = { draft = rule.copy(name = it) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.automation_condition)) },
                placeholder = { Text(stringResource(R.string.automation_name_hint)) },
                singleLine = true,
                shape = LargeShape,
            )

            ConditionGroupEditor(
                group = rule.condition,
                onChange = { draft = rule.copy(condition = it) },
            )
            if (!rule.condition.hasLeaf()) {
                Text(
                    text = stringResource(R.string.automation_condition_empty),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            SegmentedSettingsRow(
                title = stringResource(R.string.condition_effect),
                subtitle = stringResource(effectLabelRes(rule.effect.type)),
            )
            EffectSelector(rule.effect.type) { draft = rule.copy(effect = rule.effect.copy(type = it)) }

            if (rule.effect.type != RuleEffectType.RUN_ACTION) {
                SegmentedSettingsRow(
                    title = stringResource(R.string.condition_scope),
                    subtitle = scopeSubtitle(rule.effect.scope, rule.effect.buttonIds),
                    onClick = { showScopeSheet = true },
                )
            }

            if (rule.effect.type == RuleEffectType.RUN_ACTION) {
                SegmentedSettingsRow(
                    title = stringResource(R.string.automation_fire_on),
                    subtitle = fireModeLabel(rule.fireOn),
                )
                FireModeSelector(rule.fireOn) { draft = rule.copy(fireOn = it) }

                SegmentedSettingsRow(
                    title = stringResource(R.string.automation_steps),
                    subtitle = stringResource(R.string.automation_steps),
                )
                StepsEditor(
                    steps = rule.effect.steps,
                    onChange = { draft = rule.copy(effect = rule.effect.copy(steps = it)) },
                    onPickAction = { onPickAction(it) },
                    library = library,
                )
                if (stepsEmpty) {
                    Text(
                        text = stringResource(R.string.automation_steps_empty),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }

                SegmentedSettingsRow(
                    title = stringResource(R.string.automation_throttle),
                    subtitle = throttleSummary(rule.throttle),
                )
                ThrottleEditor(
                    throttle = rule.throttle,
                    onChange = { draft = rule.copy(throttle = it) },
                )
                if (throttleInvalid) {
                    Text(
                        text = stringResource(R.string.throttle_invalid),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }

                if (conditionUsesNotification(rule.condition)) {
                    NotificationPermissionRow()
                }

                SegmentedSettingsRow(
                    title = stringResource(R.string.automation_history),
                    onClick = { onNavToHistory(rule.id) },
                )
            }
        }
    }

    if (showScopeSheet) {
        RuleScopeSheet(
            buttons = buttons,
            scope = rule.effect.scope,
            buttonIds = rule.effect.buttonIds,
            onDismissRequest = { showScopeSheet = false },
            onConfirm = { newScope, ids ->
                draft = rule.copy(effect = rule.effect.copy(scope = newScope, buttonIds = ids))
                showScopeSheet = false
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StepsEditor(
    steps: List<RuleStep>,
    onChange: (List<RuleStep>) -> Unit,
    onPickAction: (String) -> Unit,
    library: NewActionLibrarySettings?,
    pathPrefix: String = "",
    depth: Int = 0,
) {
    val context = LocalContext.current
    Column(verticalArrangement = Arrangement.spacedBy(CardInnerSpacing)) {
        steps.forEachIndexed { index, step ->
            Column(verticalArrangement = Arrangement.spacedBy(CardInnerSpacing)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(CardInnerSpacing),
                ) {
                    SegmentedSettingsRow(
                        title = when (step.kind) {
                            RuleStepKind.DELAY -> stringResource(R.string.step_delay)
                            RuleStepKind.IF -> stringResource(R.string.step_if)
                            else -> stringResource(R.string.step_action)
                        },
                        subtitle = stepSummary(context, library, step),
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = {
                        onChange(steps.toMutableList().also { it.removeAt(index) })
                    }) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = stringResource(R.string.delete),
                        )
                    }
                }
                when (step.kind) {
                    RuleStepKind.ACTION -> SegmentedSettingsRow(
                        title = stringResource(R.string.automation_pick_action),
                        subtitle = effectActionLabel(
                            context, library,
                            RuleEffect(
                                type = RuleEffectType.RUN_ACTION,
                                action = step.action,
                                entryId = step.entryId,
                            ),
                        ),
                        onClick = { onPickAction("$pathPrefix$index") },
                    )
                    RuleStepKind.DELAY -> {
                        var text by remember(step.delayMs) {
                            mutableStateOf((step.delayMs / 1000).toString())
                        }
                        OutlinedTextField(
                            value = text,
                            onValueChange = {
                                text = it
                                it.toLongOrNull()?.let { s ->
                                    val ms = (s * 1000).coerceIn(0L, 60_000L)
                                    onChange(steps.toMutableList().also { list ->
                                        list[index] = step.copy(delayMs = ms)
                                    })
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text(stringResource(R.string.step_delay_seconds)) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            shape = LargeShape,
                        )
                    }
                    RuleStepKind.IF -> {
                        ConditionGroupEditor(
                            group = step.condition,
                            onChange = { g ->
                                onChange(steps.toMutableList().also { list ->
                                    list[index] = step.copy(condition = g)
                                })
                            },
                            depth = depth + 1,
                        )
                        Text(
                            text = stringResource(R.string.step_if_then),
                            style = MaterialTheme.typography.labelMedium,
                        )
                        StepsEditor(
                            steps = step.thenSteps,
                            onChange = { branch ->
                                onChange(steps.toMutableList().also { list ->
                                    list[index] = step.copy(thenSteps = branch)
                                })
                            },
                            onPickAction = onPickAction,
                            library = library,
                            pathPrefix = "$pathPrefix$index.then.",
                            depth = depth + 1,
                        )
                        Text(
                            text = stringResource(R.string.step_if_else),
                            style = MaterialTheme.typography.labelMedium,
                        )
                        StepsEditor(
                            steps = step.elseSteps,
                            onChange = { branch ->
                                onChange(steps.toMutableList().also { list ->
                                    list[index] = step.copy(elseSteps = branch)
                                })
                            },
                            onPickAction = onPickAction,
                            library = library,
                            pathPrefix = "$pathPrefix$index.else.",
                            depth = depth + 1,
                        )
                    }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(CardInnerSpacing)) {
            TextButton(onClick = {
                onChange(steps + RuleStep(kind = RuleStepKind.ACTION))
            }) {
                Text(stringResource(R.string.step_add_action))
            }
            TextButton(onClick = {
                onChange(steps + RuleStep(kind = RuleStepKind.DELAY, delayMs = 1000L))
            }) {
                Text(stringResource(R.string.step_add_delay))
            }
            // UI 只开两层分支嵌套（引擎上限 5 层），编辑器不做迷宫。
            if (depth < 2) {
                TextButton(onClick = {
                    onChange(steps + RuleStep(kind = RuleStepKind.IF))
                }) {
                    Text(stringResource(R.string.step_add_if))
                }
            }
        }
    }
}

private fun stepSummary(
    context: Context,
    library: NewActionLibrarySettings?,
    step: RuleStep,
): String = when (step.kind) {
    RuleStepKind.DELAY -> context.getString(R.string.step_delay_summary, step.delayMs / 1000)
    RuleStepKind.IF -> conditionSummary(context, step.condition)
    else -> {
        val label = context.selectedItemLabel(
            step.action ?: hunoia.luno.config.model.Action.NONE,
            emptyList(), library?.entries ?: emptyList(),
        )
        label.ifBlank { context.getString(R.string.step_empty_action) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ConditionGroupEditor(
    group: WhenGroup,
    onChange: (WhenGroup) -> Unit,
    depth: Int = 0,
    onDeleteSelf: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    var showPicker by remember { mutableStateOf(false) }
    SegmentedSettingsRow(
        title = stringResource(R.string.automation_condition),
        subtitle = conditionSummary(context, group),
        trailingContent = onDeleteSelf?.let { del ->
            {
                IconButton(onClick = del) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = stringResource(R.string.delete),
                    )
                }
            }
        },
    )
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        SegmentedButton(
            selected = group.mode == BooleanMode.AND,
            onClick = { onChange(group.copy(mode = BooleanMode.AND)) },
            shape = SegmentedButtonDefaults.itemShape(count = 2, index = 0),
        ) {
            Text(stringResource(R.string.logic_all))
        }
        SegmentedButton(
            selected = group.mode == BooleanMode.OR,
            onClick = { onChange(group.copy(mode = BooleanMode.OR)) },
            shape = SegmentedButtonDefaults.itemShape(count = 2, index = 1),
        ) {
            Text(stringResource(R.string.logic_any))
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(CardInnerSpacing)) {
        group.items.forEachIndexed { index, item ->
            if (item.type == ConditionType.GROUP) {
                // 嵌套组：UI 只开两层（引擎不限），再深的存量照跑、编辑器折叠为摘要。
                if (depth < 2) {
                    ConditionGroupEditor(
                        group = item.group ?: WhenGroup(),
                        onChange = { g ->
                            val items = group.items.toMutableList()
                            items[index] = item.copy(group = g)
                            onChange(group.copy(items = items))
                        },
                        depth = depth + 1,
                        onDeleteSelf = {
                            val items = group.items.toMutableList()
                            items.removeAt(index)
                            onChange(group.copy(items = items))
                        },
                    )
                } else {
                    SegmentedSettingsRow(
                        title = conditionTypeLabel(context, item.type),
                        subtitle = itemSummary(context, item),
                    )
                }
            } else {
                ConditionItemBlock(
                    item = item,
                    onChange = { new ->
                        val items = group.items.toMutableList()
                        items[index] = new
                        onChange(group.copy(items = items))
                    },
                    onDelete = {
                        val items = group.items.toMutableList()
                        items.removeAt(index)
                        onChange(group.copy(items = items))
                    },
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(CardInnerSpacing)) {
            TextButton(onClick = { showPicker = true }) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = null,
                    modifier = Modifier.padding(end = ShapeExtraSmall),
                )
                Text(stringResource(R.string.condition_add))
            }
            if (depth < 2) {
                TextButton(onClick = {
                    onChange(group.copy(items = group.items + defaultConditionOf(ConditionType.GROUP)))
                }) {
                    Text(stringResource(R.string.condition_logic_group))
                }
            }
        }
    }
    if (showPicker) {
        ConditionTypePickerSheet(
            onDismissRequest = { showPicker = false },
            onPick = { type ->
                onChange(group.copy(items = group.items + defaultConditionOf(type)))
                showPicker = false
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ConditionItemBlock(
    item: ConditionItem,
    onChange: (ConditionItem) -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(CardInnerSpacing),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(CardInnerSpacing),
        ) {
            var menuExpanded by remember { mutableStateOf(false) }
            Box(modifier = Modifier.weight(1f)) {
                SegmentedSettingsRow(
                    title = conditionTypeLabel(context, item.type),
                    subtitle = itemSummary(context, item),
                    onClick = { menuExpanded = !menuExpanded },
                    modifier = Modifier.fillMaxWidth(),
                )
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false },
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    shape = LargeShape,
                    tonalElevation = 3.dp,
                ) {
                    CONDITION_TYPES.forEach { spec ->
                        DropdownMenuItem(
                            text = { Text(stringResource(spec.labelRes)) },
                            trailingIcon = {
                                if (spec.type == item.type) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                    )
                                }
                            },
                            onClick = {
                                onChange(defaultConditionOf(spec.type).copy(negated = item.negated))
                                menuExpanded = false
                            },
                        )
                    }
                }
            }
            Text(
                text = stringResource(R.string.logic_not),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Switch(
                checked = item.negated,
                onCheckedChange = { onChange(item.copy(negated = it)) },
            )
            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = stringResource(R.string.delete),
                )
            }
        }
        ConditionItemFields(item, onChange)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EffectSelector(selected: RuleEffectType, onChange: (RuleEffectType) -> Unit) {
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        RuleEffectType.entries.forEachIndexed { index, type ->
            SegmentedButton(
                selected = selected == type,
                onClick = { onChange(type) },
                shape = SegmentedButtonDefaults.itemShape(count = RuleEffectType.entries.size, index = index),
            ) {
                Text(stringResource(effectLabelRes(type)), maxLines = 1)
            }
        }
    }
}

@Composable
fun scopeSubtitle(scope: RuleScope, buttonIds: List<String>): String = when (scope) {
    RuleScope.ALL -> stringResource(R.string.scope_all)
    RuleScope.EXCEPT -> stringResource(R.string.scope_except_count, buttonIds.size)
    RuleScope.ONLY -> stringResource(R.string.scope_only_count, buttonIds.size)
}

@Composable
private fun effectActionLabel(
    context: Context,
    library: NewActionLibrarySettings?,
    effect: RuleEffect,
): String {
    val action = effect.action
    if (action != null) {
        return context.selectedItemLabel(action, emptyList(), library?.entries ?: emptyList())
    }
    return entryName(library, effect.entryId)
}

@Composable
private fun entryName(library: NewActionLibrarySettings?, entryId: String): String {
    if (entryId.isBlank()) return stringResource(R.string.automation_entry_empty)
    return library?.entries?.find { it.id == entryId }?.name?.ifBlank { entryId } ?: entryId
}

@Composable
private fun fireModeLabel(mode: FireMode): String = stringResource(
    when (mode) {
        FireMode.ENTER -> R.string.fire_enter
        FireMode.EXIT -> R.string.fire_exit
        FireMode.BOTH -> R.string.fire_both
    },
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FireModeSelector(selected: FireMode, onChange: (FireMode) -> Unit) {
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        FireMode.entries.forEachIndexed { index, mode ->
            SegmentedButton(
                selected = selected == mode,
                onClick = { onChange(mode) },
                shape = SegmentedButtonDefaults.itemShape(count = FireMode.entries.size, index = index),
            ) {
                Text(fireModeLabel(mode), maxLines = 1)
            }
        }
    }
}

@Composable
private fun throttleSummary(throttle: ThrottlePolicy): String = when (throttle.kind) {
    ThrottleKind.COOLDOWN -> "${stringResource(R.string.throttle_cooldown)} ${throttle.cooldownMs / 1000}s"
    ThrottleKind.WINDOW -> "${stringResource(R.string.throttle_window)} ${throttle.maxCount}/${throttle.windowMs / 1000}s"
    else -> stringResource(R.string.throttle_off)
}

private fun isInvalidThrottle(throttle: ThrottlePolicy): Boolean = when (throttle.kind) {
    ThrottleKind.COOLDOWN -> throttle.cooldownMs < 0
    ThrottleKind.WINDOW -> throttle.maxCount < 1 || throttle.windowMs < 1000L
    else -> false
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ThrottleEditor(throttle: ThrottlePolicy, onChange: (ThrottlePolicy) -> Unit) {
    var cooldownText by remember(throttle.cooldownMs) { mutableStateOf((throttle.cooldownMs / 1000).toString()) }
    var countText by remember(throttle.maxCount) { mutableStateOf(throttle.maxCount.toString()) }
    var windowText by remember(throttle.windowMs) { mutableStateOf((throttle.windowMs / 1000).toString()) }
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        val kinds = listOf(ThrottleKind.OFF, ThrottleKind.COOLDOWN, ThrottleKind.WINDOW)
        val labels = listOf(
            stringResource(R.string.throttle_off),
            stringResource(R.string.throttle_cooldown),
            stringResource(R.string.throttle_window),
        )
        kinds.forEachIndexed { index, kind ->
            SegmentedButton(
                selected = throttle.kind == kind,
                onClick = { onChange(throttle.copy(kind = kind)) },
                shape = SegmentedButtonDefaults.itemShape(count = kinds.size, index = index),
            ) {
                Text(labels[index], maxLines = 1)
            }
        }
    }
    when (throttle.kind) {
        ThrottleKind.COOLDOWN -> OutlinedTextField(
            value = cooldownText,
            onValueChange = {
                cooldownText = it
                it.toLongOrNull()?.let { s -> onChange(throttle.copy(cooldownMs = s * 1000)) }
            },
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.throttle_cooldown_hint)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            shape = LargeShape,
        )
        ThrottleKind.WINDOW -> {
            OutlinedTextField(
                value = countText,
                onValueChange = {
                    countText = it
                    it.toIntOrNull()?.let { n -> onChange(throttle.copy(maxCount = n)) }
                },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.throttle_window_count)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                shape = LargeShape,
            )
            OutlinedTextField(
                value = windowText,
                onValueChange = {
                    windowText = it
                    it.toLongOrNull()?.let { s -> onChange(throttle.copy(windowMs = s * 1000)) }
                },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.throttle_window_seconds)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                shape = LargeShape,
            )
        }
        else -> Unit
    }
}

private fun conditionUsesNotification(group: WhenGroup): Boolean = group.items.any { item ->
    when (item.type) {
        ConditionType.NOTIFICATION_POSTED -> true
        ConditionType.GROUP -> item.group?.let { conditionUsesNotification(it) } == true
        else -> false
    }
}

@Composable
private fun NotificationPermissionRow() {
    val context = LocalContext.current
    val granted = remember {
        hunoia.luno.runtime.automation.AutomationNotificationListener.isAccessGranted(context)
    }
    SegmentedSettingsRow(
        title = stringResource(R.string.notify_permission_title),
        subtitle = stringResource(
            if (granted) R.string.notify_permission_granted
            else R.string.notify_permission_missing,
        ) + " · " + stringResource(R.string.notify_permission_privacy),
        onClick = {
            if (!granted) {
                context.startActivity(
                    android.content.Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS")
                        .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            }
        },
    )
}
