package hunoia.luno.ui.automation

import android.content.Context
import android.os.SystemClock
import android.util.Log
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
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
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
import hunoia.luno.action.model.StoredAction
import hunoia.luno.config.ConfigProvider
import hunoia.luno.config.model.AutomationRule
import hunoia.luno.config.model.BooleanMode
import hunoia.luno.config.model.ConditionItem
import hunoia.luno.config.model.ConditionType
import hunoia.luno.config.model.FireMode
import hunoia.luno.config.model.NewActionData
import hunoia.luno.config.model.VALUE_NEW_ACTION
import hunoia.luno.config.model.newActionData
import hunoia.luno.core.JsonSerializer
import hunoia.luno.config.model.RuleEffect
import hunoia.luno.config.model.RuleEffectType
import hunoia.luno.config.model.RuleStep
import hunoia.luno.config.model.RuleStepKind
import hunoia.luno.config.model.ThrottleKind
import hunoia.luno.config.model.ThrottlePolicy
import hunoia.luno.config.model.WhenGroup
import hunoia.luno.config.model.conditionAt
import hunoia.luno.config.model.withConditionAt
import hunoia.luno.runtime.condition.hasLeaf
import hunoia.luno.ui.actionselect.selectedItemLabel
import hunoia.luno.ui.component.MyColumn
import hunoia.luno.ui.component.SegmentedSettingsRow
import hunoia.luno.ui.component.SegmentedSwitchRow
import hunoia.luno.ui.component.TopBar
import hunoia.luno.ui.navigation.ActionSelect
import hunoia.luno.ui.navigation.NEW_AUTOMATION_RULE_ID
import hunoia.luno.ui.theme.CardInnerSpacing
import hunoia.luno.ui.theme.LargeShape
import hunoia.luno.ui.theme.ListSpacing
import hunoia.luno.ui.theme.PageGutter
import hunoia.luno.ui.theme.ShapeExtraSmall
import hunoia.luno.ui.theme.SheetTopShape
import hunoia.luno.ui.theme.liquidGlassBackdrop
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
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
    val library by ConfigProvider.newActionLibrarySettings
        .collectAsStateWithLifecycle(initialValue = null)

    val scope = rememberCoroutineScope()
    var draft by remember { mutableStateOf<AutomationRule?>(null) }
    var conditionSheetPath by remember { mutableStateOf<List<Int>?>(null) }
    var stepSheetPath by remember { mutableStateOf<String?>(null) }
    var stepTypePickerFor by remember { mutableStateOf<String?>(null) }
    var showFireSheet by remember { mutableStateOf(false) }
    var showThrottleSheet by remember { mutableStateOf(false) }

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
                effect = RuleEffect(type = RuleEffectType.RUN_ACTION),
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
    val stepsEmpty = rule.effect.steps.isEmpty()
    val saveEnabled = rule.condition.hasLeaf() && !stepsEmpty && !throttleInvalid

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
                onEditItem = { conditionSheetPath = it },
            )
            if (!rule.condition.hasLeaf()) {
                Text(
                    text = stringResource(R.string.automation_condition_empty),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            SegmentedSettingsRow(
                title = stringResource(R.string.automation_fire_on),
                subtitle = fireModeLabel(rule.fireOn),
                onClick = { showFireSheet = true },
            )

            SegmentedSettingsRow(
                title = stringResource(R.string.automation_steps),
                subtitle = stringResource(R.string.automation_steps),
            )
            StepsEditor(
                steps = rule.effect.steps,
                onChange = { draft = rule.copy(effect = rule.effect.copy(steps = it)) },
                onPickAction = { onPickAction(it) },
                onEditStep = { stepSheetPath = it },
                onAddStep = { stepTypePickerFor = it },
                library = library,
                rule = rule,
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
                onClick = { showThrottleSheet = true },
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

    // 条件参数 sheet：ConditionItemFields 原样搬入，行上只剩删除。
    conditionSheetPath?.let { path ->
        val item = rule.condition.conditionAt(path)
        if (item == null) {
            conditionSheetPath = null
        } else {
            ConditionSheet(
                item = item,
                onDismiss = { conditionSheetPath = null },
                onChange = { new ->
                    draft = rule.copy(condition = rule.condition.withConditionAt(path) { new })
                },
            )
        }
    }

    // 步骤 sheet：按 kind 显示动作选择 / 秒数 / 分支条件。
    stepSheetPath?.let { path ->
        val step = rule.effect.stepAt(path)
        if (step == null) {
            stepSheetPath = null
        } else {
            StepSheet(
                step = step,
                library = library,
                rule = rule,
                onDismiss = { stepSheetPath = null },
                onChange = { new ->
                    draft = rule.copy(effect = rule.effect.withStepAt(path) { new })
                },
                onPickAction = {
                    stepSheetPath = null
                    onPickAction(path)
                },
            )
        }
    }

    // 添加步骤：三选一，选完直接进对应 sheet（动作则跳选择页）。
    stepTypePickerFor?.let { prefix ->
        StepTypeSheet(
            onDismiss = { stepTypePickerFor = null },
            onPick = { kind ->
                val created = when (kind) {
                    RuleStepKind.DELAY -> RuleStep(kind = kind, delayMs = 1000L)
                    else -> RuleStep(kind = kind)
                }
                val newPath = appendStep(rule.effect, prefix, created)
                if (newPath != null) {
                    draft = rule.copy(effect = newPath.second)
                    stepTypePickerFor = null
                    if (kind == RuleStepKind.ACTION) onPickAction(newPath.first)
                    else stepSheetPath = newPath.first
                } else {
                    stepTypePickerFor = null
                }
            },
        )
    }

    if (showFireSheet) {
        FireModeSheet(
            selected = rule.fireOn,
            onDismiss = { showFireSheet = false },
            onPick = {
                draft = rule.copy(fireOn = it)
                showFireSheet = false
            },
        )
    }

    if (showThrottleSheet) {
        ThrottleSheet(
            throttle = rule.throttle,
            onDismiss = { showThrottleSheet = false },
            onChange = { draft = rule.copy(throttle = it) },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StepsEditor(
    steps: List<RuleStep>,
    onChange: (List<RuleStep>) -> Unit,
    onPickAction: (String) -> Unit,
    onEditStep: (String) -> Unit,
    onAddStep: (String) -> Unit,
    library: NewActionLibrarySettings?,
    rule: AutomationRule,
    pathPrefix: String = "",
    numberPrefix: String = "",
    depth: Int = 0,
) {
    Column(verticalArrangement = Arrangement.spacedBy(CardInnerSpacing)) {
        steps.forEachIndexed { index, step ->
            val number = "$numberPrefix${index + 1}"
            StepRow(
                number = number,
                step = step,
                library = library,
                onEdit = { onEditStep("$pathPrefix$index") },
                onDelete = {
                    onChange(steps.toMutableList().also { it.removeAt(index) })
                },
                onMoveUp = if (index > 0) ({
                    onChange(steps.toMutableList().also {
                        val tmp = it[index - 1]
                        it[index - 1] = it[index]
                        it[index] = tmp
                    })
                }) else null,
                onMoveDown = if (index < steps.size - 1) ({
                    onChange(steps.toMutableList().also {
                        val tmp = it[index + 1]
                        it[index + 1] = it[index]
                        it[index] = tmp
                    })
                }) else null,
            )
            if (step.kind == RuleStepKind.IF) {
                // 分支：子列表缩进平铺，各自带添加按钮。
                Column(modifier = Modifier.padding(start = 16.dp)) {
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
                        onEditStep = onEditStep,
                        onAddStep = onAddStep,
                        library = library,
                        rule = rule,
                        pathPrefix = "$pathPrefix$index.then.",
                        numberPrefix = "$number.",
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
                        onEditStep = onEditStep,
                        onAddStep = onAddStep,
                        library = library,
                        rule = rule,
                        pathPrefix = "$pathPrefix$index.else.",
                        numberPrefix = "$number.",
                        depth = depth + 1,
                    )
                }
            }
        }
        TextButton(onClick = { onAddStep(pathPrefix) }) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = null,
                modifier = Modifier.padding(end = ShapeExtraSmall),
            )
            Text(stringResource(R.string.automation_add_step))
        }
    }
}

/** 步骤一行：编号 + 摘要 + 上下箭头 + 删除；点行进 sheet。 */
@Composable
private fun StepRow(
    number: String,
    step: RuleStep,
    library: NewActionLibrarySettings?,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onMoveUp: (() -> Unit)?,
    onMoveDown: (() -> Unit)?,
) {
    val context = LocalContext.current
    SegmentedSettingsRow(
        title = "$number · " + when (step.kind) {
            RuleStepKind.DELAY -> stringResource(R.string.step_delay)
            RuleStepKind.IF -> stringResource(R.string.step_if)
            else -> stringResource(R.string.step_action)
        },
        subtitle = stepSummary(context, library, step),
        onClick = onEdit,
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(enabled = onMoveUp != null, onClick = { onMoveUp?.invoke() }) {
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowUp,
                        contentDescription = null,
                    )
                }
                IconButton(enabled = onMoveDown != null, onClick = { onMoveDown?.invoke() }) {
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = null,
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
    )
}

/**
 * 向 prefix 指向的列表追加步骤，返回（新步骤路径，更新后的 effect）。
 * prefix：""=顶层，"2.then." / "2.then.0.else."=分支。非法返回 null。
 */
private fun appendStep(
    effect: RuleEffect,
    prefix: String,
    created: RuleStep,
): Pair<String, RuleEffect>? {
    if (prefix.isEmpty()) {
        val path = "${effect.steps.size}"
        return path to effect.copy(steps = effect.steps + created)
    }
    val tokens = prefix.split(".").filter { it.isNotEmpty() }
    if (tokens.size < 2) return null
    val branch = tokens.last()
    if (branch != "then" && branch != "else") return null
    val parentPath = tokens.dropLast(1).joinToString(".")
    val parent = effect.stepAt(parentPath) ?: return null
    if (parent.kind != RuleStepKind.IF) return null
    val childIndex = if (branch == "then") parent.thenSteps.size else parent.elseSteps.size
    val updated = effect.withStepAt(parentPath) { step ->
        if (branch == "then") step.copy(thenSteps = step.thenSteps + created)
        else step.copy(elseSteps = step.elseSteps + created)
    }
    return "$parentPath.$branch.$childIndex" to updated
}

private data class ButtonsToggle(val stored: StoredAction, val hide: Boolean)

/** 步骤是否为触钮显隐动作（内联或库引用），是则带出 stored 供配对恢复用。 */
private fun stepButtonsVisibility(
    library: NewActionLibrarySettings?,
    step: RuleStep,
): ButtonsToggle? {
    if (step.kind != RuleStepKind.ACTION) return null
    val stored = step.action?.newActionData()
        ?: library?.entries?.find { it.id == step.entryId }?.storedAction
    if (stored?.typeId != "internal.buttonsVisibility") return null
    val visible = stored.params["visible"]
        ?.jsonPrimitive?.contentOrNull?.toBoolean() ?: true
    return ButtonsToggle(stored, hide = !visible)
}

/** 配对恢复规则：同条件/fireOn EXIT/显示步骤，平铺两条（id 后缀 :show）。 */
private fun buildShowPairRule(
    context: Context,
    parent: AutomationRule,
    library: NewActionLibrarySettings?,
    hideStep: RuleStep,
): AutomationRule {
    val hide = stepButtonsVisibility(library, hideStep)
    val showStored = StoredAction(
        typeId = "internal.buttonsVisibility",
        params = JsonObject(
            (hide?.stored?.params ?: emptyMap()) + ("visible" to JsonPrimitive(true)),
        ),
    )
    val showAction = hunoia.luno.config.model.Action(
        value = VALUE_NEW_ACTION,
        data = JsonSerializer.encodeToString(NewActionData(showStored)),
    )
    val baseName = parent.name.ifBlank { conditionSummary(context, parent.condition) }
    return AutomationRule(
        id = "${parent.id}:show",
        name = context.getString(R.string.automation_pair_show_name, baseName),
        condition = parent.condition,
        effect = RuleEffect(
            type = RuleEffectType.RUN_ACTION,
            steps = listOf(RuleStep(kind = RuleStepKind.ACTION, action = showAction)),
        ),
        fireOn = FireMode.EXIT,
    )
}

private fun stepSummary(
    context: Context,
    library: NewActionLibrarySettings?,
    step: RuleStep,
): String = when (step.kind) {
    RuleStepKind.DELAY -> context.getString(R.string.step_delay_summary, step.delayMs / 1000)
    RuleStepKind.IF -> conditionSummary(context, step.condition) + " · " +
        context.getString(R.string.step_if_counts, step.thenSteps.size, step.elseSteps.size)
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
    onEditItem: (List<Int>) -> Unit,
    basePath: List<Int> = emptyList(),
    depth: Int = 0,
    onDeleteSelf: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    var showPicker by remember { mutableStateOf(false) }
    // 组头一行：摘要 + 且/或小开关 + 删除（顶层组无删除）。
    SegmentedSettingsRow(
        title = stringResource(R.string.automation_condition),
        subtitle = if (group.items.isEmpty()) {
            stringResource(R.string.automation_condition_empty)
        } else {
            "${stringResource(if (group.mode == BooleanMode.AND) R.string.logic_all else R.string.logic_any)} · " +
                stringResource(R.string.condition_group_count, group.items.size)
        },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = {
                    onChange(group.copy(
                        mode = if (group.mode == BooleanMode.AND) BooleanMode.OR else BooleanMode.AND,
                    ))
                }) {
                    Text(stringResource(
                        if (group.mode == BooleanMode.AND) R.string.logic_all else R.string.logic_any,
                    ))
                }
                onDeleteSelf?.let { del ->
                    IconButton(onClick = del) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = stringResource(R.string.delete),
                        )
                    }
                }
            }
        },
    )
    Column(
        modifier = if (depth > 0) Modifier.padding(start = 16.dp) else Modifier,
        verticalArrangement = Arrangement.spacedBy(CardInnerSpacing),
    ) {
        group.items.forEachIndexed { index, item ->
            val path = basePath + index
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
                        onEditItem = onEditItem,
                        basePath = path,
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
                ConditionRow(
                    item = item,
                    onEdit = { onEditItem(path) },
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

/** 条件一行：类型 + 摘要 + 删除；点行进 sheet 配参数。 */
@Composable
private fun ConditionRow(
    item: ConditionItem,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val context = LocalContext.current
    SegmentedSettingsRow(
        title = conditionTypeLabel(context, item.type),
        subtitle = itemSummary(context, item),
        onClick = onEdit,
        trailingContent = {
            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = stringResource(R.string.delete),
                )
            }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ConditionSheet(
    item: ConditionItem,
    onDismiss: () -> Unit,
    onChange: (ConditionItem) -> Unit,
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        shape = SheetTopShape,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(bottom = PageGutter),
            verticalArrangement = Arrangement.spacedBy(CardInnerSpacing),
        ) {
            Text(
                text = conditionTypeLabel(context, item.type),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = PageGutter),
            )
            Column(
                modifier = Modifier.padding(horizontal = PageGutter),
                verticalArrangement = Arrangement.spacedBy(CardInnerSpacing),
            ) {
                SegmentedSwitchRow(
                    title = stringResource(R.string.logic_not),
                    checked = item.negated,
                    onCheckedChange = { onChange(item.copy(negated = it)) },
                )
                ConditionItemFields(item, onChange)
                TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.confirm))
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StepSheet(
    step: RuleStep,
    library: NewActionLibrarySettings?,
    rule: AutomationRule,
    onDismiss: () -> Unit,
    onChange: (RuleStep) -> Unit,
    onPickAction: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        shape = SheetTopShape,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(bottom = PageGutter),
            verticalArrangement = Arrangement.spacedBy(CardInnerSpacing),
        ) {
            Text(
                text = when (step.kind) {
                    RuleStepKind.DELAY -> stringResource(R.string.step_delay)
                    RuleStepKind.IF -> stringResource(R.string.step_if)
                    else -> stringResource(R.string.step_action)
                },
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = PageGutter),
            )
            Column(
                modifier = Modifier.padding(horizontal = PageGutter),
                verticalArrangement = Arrangement.spacedBy(CardInnerSpacing),
            ) {
                when (step.kind) {
                    RuleStepKind.ACTION -> {
                        SegmentedSettingsRow(
                            title = stringResource(R.string.automation_pick_action),
                            subtitle = effectActionLabel(
                                context, library,
                                RuleEffect(
                                    type = RuleEffectType.RUN_ACTION,
                                    action = step.action,
                                    entryId = step.entryId,
                                ),
                            ),
                            onClick = onPickAction,
                        )
                        // 隐藏步骤：一键生成配对恢复规则（同条件/fireOn EXIT/显示，平铺两条）。
                        if (stepButtonsVisibility(library, step)?.hide == true) {
                            OutlinedButton(onClick = {
                                scope.launch {
                                    ConfigProvider.updateAutomationRules { rules ->
                                        if (rules.any { it.id == "${rule.id}:show" }) rules
                                        else rules + buildShowPairRule(context, rule, library, step)
                                    }
                                }
                                onDismiss()
                            }) {
                                Text(stringResource(R.string.automation_pair_show))
                            }
                        }
                    }
                    RuleStepKind.DELAY -> {
                        var text by remember(step.delayMs) {
                            mutableStateOf((step.delayMs / 1000).toString())
                        }
                        OutlinedTextField(
                            value = text,
                            onValueChange = {
                                text = it
                                it.toLongOrNull()?.let { s ->
                                    onChange(step.copy(delayMs = (s * 1000).coerceIn(0L, 60_000L)))
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
                        var nestedPath by remember { mutableStateOf<List<Int>?>(null) }
                        ConditionGroupEditor(
                            group = step.condition,
                            onChange = { onChange(step.copy(condition = it)) },
                            onEditItem = { nestedPath = it },
                        )
                        nestedPath?.let { path ->
                            val nested = step.condition.conditionAt(path)
                            if (nested == null) {
                                nestedPath = null
                            } else {
                                ConditionSheet(
                                    item = nested,
                                    onDismiss = { nestedPath = null },
                                    onChange = { new ->
                                        onChange(step.copy(
                                            condition = step.condition.withConditionAt(path) { new },
                                        ))
                                    },
                                )
                            }
                        }
                    }
                }
                TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.confirm))
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StepTypeSheet(
    onDismiss: () -> Unit,
    onPick: (String) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        shape = SheetTopShape,
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(bottom = PageGutter)) {
            Text(
                text = stringResource(R.string.step_pick_type),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = PageGutter),
            )
            listOf(
                RuleStepKind.ACTION to R.string.step_add_action,
                RuleStepKind.DELAY to R.string.step_add_delay,
                RuleStepKind.IF to R.string.step_add_if,
            ).forEach { (kind, labelRes) ->
                SegmentedSettingsRow(
                    title = stringResource(labelRes),
                    onClick = { onPick(kind) },
                    modifier = Modifier.padding(horizontal = PageGutter),
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FireModeSheet(
    selected: FireMode,
    onDismiss: () -> Unit,
    onPick: (FireMode) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        shape = SheetTopShape,
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(bottom = PageGutter)) {
            Text(
                text = stringResource(R.string.automation_fire_on),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = PageGutter),
            )
            FireMode.entries.forEach { mode ->
                SegmentedSettingsRow(
                    title = fireModeLabel(mode),
                    trailingContent = {
                        if (mode == selected) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                    },
                    onClick = { onPick(mode) },
                    modifier = Modifier.padding(horizontal = PageGutter),
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ThrottleSheet(
    throttle: ThrottlePolicy,
    onDismiss: () -> Unit,
    onChange: (ThrottlePolicy) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        shape = SheetTopShape,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(bottom = PageGutter),
            verticalArrangement = Arrangement.spacedBy(CardInnerSpacing),
        ) {
            Text(
                text = stringResource(R.string.automation_throttle),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = PageGutter),
            )
            Column(
                modifier = Modifier.padding(horizontal = PageGutter),
                verticalArrangement = Arrangement.spacedBy(CardInnerSpacing),
            ) {
                ThrottleEditor(throttle = throttle, onChange = onChange)
                TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.confirm))
                }
            }
        }
    }
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
