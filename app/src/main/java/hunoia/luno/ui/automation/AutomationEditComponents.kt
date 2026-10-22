package hunoia.luno.ui.automation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import hunoia.luno.R
import hunoia.luno.action.model.ActionLibrarySettings
import hunoia.luno.domain.automation.AutomationCenter
import hunoia.luno.config.model.AutomationRule
import hunoia.luno.config.model.ConditionItem
import hunoia.luno.config.model.ConditionType
import hunoia.luno.config.model.FireMode
import hunoia.luno.config.model.RuleStep
import hunoia.luno.config.model.RuleStepKind
import hunoia.luno.config.model.ThrottleKind
import hunoia.luno.config.model.ThrottlePolicy
import hunoia.luno.config.model.WhenGroup
import hunoia.luno.ui.actionlibrary.TemplateTextField
import hunoia.luno.ui.theme.CardInnerSpacing
import hunoia.luno.ui.theme.ContainerRadius
import hunoia.luno.ui.theme.ElevationSmall
import hunoia.luno.ui.theme.LargeShape
import hunoia.luno.ui.theme.MinInteractiveSize
import hunoia.luno.ui.theme.PageGutter
import hunoia.luno.ui.component.CheckDropdownMenuItem
import hunoia.luno.ui.component.row.LunoSettingsRow
import hunoia.luno.ui.component.row.LunoSwitchRow
import hunoia.luno.ui.component.sheet.LunoSheet
import hunoia.luno.ui.component.feedback.LunoIconButton
import hunoia.luno.ui.component.sheet.SheetHeader
import hunoia.luno.ui.component.timeline.TimelineBadge
import hunoia.luno.ui.component.timeline.TimelineColumn
import hunoia.luno.ui.component.timeline.TimelineNode
import hunoia.luno.ui.component.timeline.timelineIndent

/** 添加行：图标独占一行，背景/高度/圆角与其他行一致。 */
@Composable
internal fun AddRow(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = MinInteractiveSize)
            .clip(RoundedCornerShape(ContainerRadius))
            .background(MaterialTheme.colorScheme.surfaceBright)
            .clickable(onClick = onClick, role = Role.Button),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Default.Add,
            contentDescription = contentDescription,
        )
    }
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun StepsEditor(
    steps: List<RuleStep>,
    onStepsChange: (List<RuleStep>) -> Unit,
    onEditStep: (String) -> Unit,
    onAddStep: (String) -> Unit,
    onPickAction: (String) -> Unit,
    onDeleteStep: (String) -> Unit,
    library: ActionLibrarySettings?,
    rules: List<AutomationRule> = emptyList(),
    pathPrefix: String = "",
    numberPrefix: String = "",
    depth: Int = 0,
) {
    TimelineColumn {
        steps.forEachIndexed { index, step ->
            val number = "$numberPrefix${index + 1}"
            TimelineNode(badge = { TimelineBadge(number) }) {
                StepRow(
                    step = step,
                    library = library,
                    rules = rules,
                    onEdit = { onEditStep("$pathPrefix$index") },
                    onPickAction = { onPickAction("$pathPrefix$index") },
                    onDelete = { onDeleteStep("$pathPrefix$index") },
                )
            }
            if (step.kind == RuleStepKind.IF) {
                // 分支收进内嵌子卡：各自时间线 + 各自添加按钮。
                Text(
                    text = stringResource(R.string.step_if_then),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = timelineIndent(),
                )
                TimelineColumn(modifier = timelineIndent()) {
                    StepsEditor(
                        steps = step.thenSteps,
                        onStepsChange = { branch ->
                            onStepsChange(steps.toMutableList().also { list ->
                                list[index] = step.copy(thenSteps = branch)
                            })
                        },
                        onEditStep = onEditStep,
                        onAddStep = onAddStep,
                        onPickAction = onPickAction,
                        onDeleteStep = onDeleteStep,
                        library = library,
                        rules = rules,
                        pathPrefix = "$pathPrefix$index.then.",
                        numberPrefix = "$number.",
                        depth = depth + 1,
                    )
                }
                Text(
                    text = stringResource(R.string.step_if_else),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = timelineIndent(),
                )
                TimelineColumn(modifier = timelineIndent()) {
                    StepsEditor(
                        steps = step.elseSteps,
                        onStepsChange = { branch ->
                            onStepsChange(steps.toMutableList().also { list ->
                                list[index] = step.copy(elseSteps = branch)
                            })
                        },
                        onEditStep = onEditStep,
                        onAddStep = onAddStep,
                        onPickAction = onPickAction,
                        onDeleteStep = onDeleteStep,
                        library = library,
                        rules = rules,
                        pathPrefix = "$pathPrefix$index.else.",
                        numberPrefix = "$number.",
                        depth = depth + 1,
                    )
                }
            }
        }
        AddRow(
            onClick = { onAddStep(pathPrefix) },
            modifier = timelineIndent(),
            contentDescription = stringResource(R.string.automation_add_step),
        )
    }
}
/**
 * 步骤一行：动作行整行点击直达动作选择页并在行内删除；其余类型点行进各自 sheet。
 */
@Composable
internal fun StepRow(
    step: RuleStep,
    library: ActionLibrarySettings?,
    rules: List<AutomationRule> = emptyList(),
    onEdit: () -> Unit,
    onPickAction: () -> Unit,
    onDelete: () -> Unit,
) {
    val context = LocalContext.current
    val isAction = step.kind == RuleStepKind.ACTION
    LunoSettingsRow(
        title = when (step.kind) {
            RuleStepKind.DELAY -> stringResource(R.string.step_delay)
            RuleStepKind.IF -> stringResource(R.string.step_if)
            RuleStepKind.SET_VAR -> stringResource(R.string.step_set_var)
            else -> stringResource(R.string.step_action)
        },
        subtitle = stepSummary(context, library, step, rules),
        onClick = if (isAction) onPickAction else onEdit,
        trailing = if (isAction) {
            {
                LunoIconButton(
                    icon = Icons.Default.Delete,
                    contentDescription = stringResource(R.string.delete),
                    onClick = onDelete,
                )
            }
        } else {
            null
        },
    )
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ConditionGroupEditor(
    group: WhenGroup,
    onGroupChange: (WhenGroup) -> Unit,
    onEditItem: (List<Int>) -> Unit,
    basePath: List<Int> = emptyList(),
    depth: Int = 0,
    onDeleteSelf: (() -> Unit)? = null,
    fireMode: FireMode? = null,
    onFireChange: ((FireMode) -> Unit)? = null,
    groupLabel: String? = null,
    // 类型选择器外置（sheet 内嵌编辑器时用）：传 null 走内部 picker，传 host 走外部。
    pickerHost: ((List<Int>) -> Unit)? = null,
    /** 约束条件：只允许状态类条件，隐藏脉冲/手动类型与状态↔变化切换。 */
    stateOnly: Boolean = false,
    /** 顶层约束组允许为空（= 不限制），空组不标红。 */
    emptyIsValid: Boolean = false,
) {
    val context = LocalContext.current
    var showPicker by remember { mutableStateOf(false) }
    var fireExpanded by remember { mutableStateOf(false) }
    // 组头一行：触发时机下拉（仅顶层规则组）+ 且/或切换 + 删除（顶层组无删除）。
    TimelineColumn {
        TimelineNode(
            badge = {
                TimelineBadge(
                    text = group.items.size.toString(),
                    error = group.items.isEmpty() && !emptyIsValid,
                )
            },
        ) {
            LunoSettingsRow(
                title = groupLabel.orEmpty(),
                leading = if (fireMode != null && onFireChange != null) {
                    {
                        Box {
                            TextButton(onClick = { fireExpanded = !fireExpanded }) {
                                Text(fireModeLabel(fireMode))
                                Icon(
                                    imageVector = Icons.Default.ArrowDropDown,
                                    contentDescription = null,
                                )
                            }
                            DropdownMenu(
                                expanded = fireExpanded,
                                onDismissRequest = { fireExpanded = false },
                                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                shape = LargeShape,
                                tonalElevation = ElevationSmall,
                            ) {
                                FireMode.entries.forEach { mode ->
                                    CheckDropdownMenuItem(
                                        label = fireModeLabel(mode),
                                        selected = mode == fireMode,
                                        onClick = {
                                            onFireChange(mode)
                                            fireExpanded = false
                                        },
                                    )
                                }
                            }
                        }
                    }
                } else {
                    null
                },
                trailing = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = {
                            onGroupChange(group.copy(mode = nextGroupMode(group.mode)))
                        }) {
                            Text(stringResource(groupModeLabel(group.mode)))
                        }
                        onDeleteSelf?.let { del ->
                            LunoIconButton(
                                icon = Icons.Default.Delete,
                                contentDescription = stringResource(R.string.delete),
                                onClick = del,
                            )
                        }
                    }
                },
            )
        }
        group.items.forEachIndexed { index, item ->
            val path = basePath + index
            if (item.type == ConditionType.GROUP) {
                // 嵌套组：UI 只开两层（引擎不限），更深的折叠为摘要。
                if (depth < 2) {
                    TimelineNode(
                        badge = { TimelineBadge((item.group?.items?.size ?: 0).toString()) },
                    ) {
                        ConditionGroupEditor(
                            group = item.group ?: WhenGroup(),
                            onGroupChange = { g ->
                                val items = group.items.toMutableList()
                                items[index] = item.copy(group = g)
                                onGroupChange(group.copy(items = items))
                            },
                            onEditItem = onEditItem,
                            basePath = path,
                            depth = depth + 1,
                            groupLabel = context.getString(R.string.condition_group_number, index + 1),
                            stateOnly = stateOnly,
                            onDeleteSelf = {
                                val items = group.items.toMutableList()
                                items.removeAt(index)
                                onGroupChange(group.copy(items = items))
                            },
                        )
                    }
                } else {
                    TimelineNode(badge = { TimelineBadge((index + 1).toString()) }) {
                        LunoSettingsRow(
                            title = conditionTypeLabel(context, item.type),
                            subtitle = itemSummary(context, item),
                        )
                    }
                }
            } else {
                TimelineNode(badge = { TimelineBadge((index + 1).toString()) }) {
                    ConditionRow(
                        item = item,
                        onEdit = { onEditItem(path) },
                    )
                }
            }
        }
        Row(
            modifier = timelineIndent()
                .fillMaxWidth()
                .heightIn(min = MinInteractiveSize)
                .clip(RoundedCornerShape(ContainerRadius))
                .background(MaterialTheme.colorScheme.surfaceBright),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clickable(
                        onClick = {
                            if (pickerHost != null) pickerHost(basePath)
                            else showPicker = true
                        },
                        role = Role.Button,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = stringResource(R.string.condition_add),
                )
            }
            if (depth < 2) {
                VerticalDivider(
                    modifier = Modifier
                        .fillMaxHeight()
                        .padding(vertical = CardInnerSpacing),
                    color = MaterialTheme.colorScheme.outlineVariant,
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clickable(
                            onClick = {
                                onGroupChange(group.copy(items = group.items + defaultConditionOf(ConditionType.GROUP)))
                            },
                            role = Role.Button,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Default.AccountTree,
                        contentDescription = stringResource(R.string.condition_logic_group),
                    )
                }
            }
        }
    }
    if (showPicker) {
        ConditionTypePickerSheet(
            onDismissRequest = { showPicker = false },
            onPick = { type ->
                onGroupChange(group.copy(items = group.items + defaultConditionOf(type)))
                showPicker = false
            },
            includeManual = !stateOnly,
            stateOnly = stateOnly,
        )
    }
}
@Composable
internal fun ConditionRow(
    item: ConditionItem,
    onEdit: () -> Unit,
) {
    val context = LocalContext.current
    LunoSettingsRow(
        title = conditionTypeLabel(context, item.type),
        subtitle = itemSummary(context, item),
        onClick = onEdit,
    )
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ConditionSheet(
    item: ConditionItem,
    onDismissRequest: () -> Unit,
    onConditionChange: (ConditionItem) -> Unit,
    onDelete: () -> Unit,
    /** 约束条件只允许状态类，不提供「状态/变化」切换。 */
    stateOnly: Boolean = false,
) {
    val context = LocalContext.current
    LunoSheet(
        onDismissRequest = onDismissRequest,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(bottom = PageGutter),
            verticalArrangement = Arrangement.spacedBy(CardInnerSpacing),
        ) {
            SheetHeader(title = conditionTypeLabel(context, item.type))
            Column(
                modifier = Modifier.padding(horizontal = PageGutter),
                verticalArrangement = Arrangement.spacedBy(CardInnerSpacing),
            ) {
                // 成对条件（充电/耳机/音乐/手电/勿扰/前台应用）：状态↔变化切换。
                val isPaired = !stateOnly &&
                    (item.type in PAIRED_CONDITION_TYPES || item.type in PAIRED_STATE_TYPES)
                if (isPaired) {
                    val isChange = item.type in PAIRED_CONDITION_TYPES.values
                    ConditionModeToggle(
                        isChange = isChange,
                        onModeChange = { toChange ->
                            onConditionChange(item.switchConditionMode(toChange))
                        },
                    )
                    Text(
                        stringResource(
                            if (isChange) R.string.condition_mode_change_hint
                            else R.string.condition_mode_state_hint,
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                // MANUAL 取反恒真（永远触发），不给开关；求值层同样无视取反。
                if (item.type != ConditionType.MANUAL) {
                    LunoSwitchRow(
                        title = stringResource(R.string.logic_not),
                        checked = item.negated,
                        onCheckedChange = { onConditionChange(item.copy(negated = it)) },
                    )
                }
                ConditionItemFields(item, onConditionChange)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = onDelete) {
                        Text(
                            stringResource(R.string.delete),
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                    TextButton(onClick = onDismissRequest) {
                        Text(stringResource(R.string.confirm))
                    }
                }
            }
        }
    }
}
/**
 * 步骤 sheet：只有 DELAY / IF 会进来；动作行直达选择页、删除在行内，没有中间 sheet。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun StepSheet(
    step: RuleStep,
    onDismissRequest: () -> Unit,
    onStepChange: (RuleStep) -> Unit,
    onDelete: () -> Unit,
    onEditIfCondition: () -> Unit,
) {
    val context = LocalContext.current
    LunoSheet(
        onDismissRequest = onDismissRequest,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(bottom = PageGutter),
            verticalArrangement = Arrangement.spacedBy(CardInnerSpacing),
        ) {
            SheetHeader(
                title = when (step.kind) {
                    RuleStepKind.DELAY -> stringResource(R.string.step_delay)
                    RuleStepKind.SET_VAR -> stringResource(R.string.step_set_var)
                    else -> stringResource(R.string.step_if)
                },
            )
            Column(
                modifier = Modifier.padding(horizontal = PageGutter),
                verticalArrangement = Arrangement.spacedBy(CardInnerSpacing),
            ) {
                when (step.kind) {
                    RuleStepKind.DELAY -> {
                        var text by remember(step.delayMs) {
                            mutableStateOf((step.delayMs / 1000).toString())
                        }
                        OutlinedTextField(
                            value = text,
                            onValueChange = {
                                text = it
                                it.toLongOrNull()?.let { s ->
                                    onStepChange(step.copy(delayMs = (s * 1000).coerceIn(0L, 60_000L)))
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text(stringResource(R.string.step_delay_seconds)) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            shape = LargeShape,
                        )
                    }
                    RuleStepKind.SET_VAR -> {
                        var name by remember(step.varName) { mutableStateOf(step.varName) }
                        OutlinedTextField(
                            value = name,
                            onValueChange = {
                                name = it
                                onStepChange(step.copy(varName = it))
                            },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text(stringResource(R.string.step_set_var_name)) },
                            singleLine = true,
                            isError = !isValidVarName(name),
                            shape = LargeShape,
                        )
                        var template by remember(step.varTemplate) { mutableStateOf(step.varTemplate) }
                        TemplateTextField(
                            value = template,
                            onValueChange = {
                                template = it
                                onStepChange(step.copy(varTemplate = it))
                            },
                            label = stringResource(R.string.step_set_var_value),
                            isError = false,
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Text(
                            text = stringResource(R.string.step_set_var_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    else -> {
                        LunoSettingsRow(
                            title = stringResource(R.string.step_if),
                            subtitle = conditionSummary(context, step.condition),
                            onClick = onEditIfCondition,
                        )
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = onDelete) {
                        Text(
                            stringResource(R.string.delete),
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                    TextButton(onClick = onDismissRequest) {
                        Text(stringResource(R.string.confirm))
                    }
                }
            }
        }
    }
}
/**
 * IF 步骤的条件编辑（屏幕层 sheet）。不走步骤 sheet 内嵌：弹层不支持嵌套，
 * 编辑/添加/删除全部走屏幕层通道。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun IfConditionSheet(
    condition: WhenGroup,
    onConditionChange: (WhenGroup) -> Unit,
    onDismissRequest: () -> Unit,
    onEditItem: (List<Int>) -> Unit,
    onOpenPicker: (List<Int>) -> Unit,
) {
    LunoSheet(
        onDismissRequest = onDismissRequest,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(bottom = PageGutter),
            verticalArrangement = Arrangement.spacedBy(CardInnerSpacing),
        ) {
            SheetHeader(title = stringResource(R.string.step_if))
            Column(
                modifier = Modifier.padding(horizontal = PageGutter),
                verticalArrangement = Arrangement.spacedBy(CardInnerSpacing),
            ) {
                ConditionGroupEditor(
                    group = condition,
                    onGroupChange = onConditionChange,
                    onEditItem = onEditItem,
                    pickerHost = onOpenPicker,
                )
                TextButton(onClick = onDismissRequest, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.confirm))
                }
            }
        }
    }
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun StepTypeSheet(
    onDismissRequest: () -> Unit,
    onPick: (String) -> Unit,
) {
    LunoSheet(
        onDismissRequest = onDismissRequest,
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(bottom = PageGutter)) {
            SheetHeader(title = stringResource(R.string.step_pick_type))
            listOf(
                RuleStepKind.ACTION to R.string.step_add_action,
                RuleStepKind.DELAY to R.string.step_add_delay,
                RuleStepKind.IF to R.string.step_add_if,
                RuleStepKind.SET_VAR to R.string.step_add_set_var,
            ).forEach { (kind, labelRes) ->
                LunoSettingsRow(
                    title = stringResource(labelRes),
                    onClick = { onPick(kind) },
                    modifier = Modifier.padding(horizontal = PageGutter),
                )
            }
        }
    }
}
@Composable
internal fun fireModeLabel(mode: FireMode): String = stringResource(
    when (mode) {
        FireMode.ENTER -> R.string.fire_enter
        FireMode.EXIT -> R.string.fire_exit
        FireMode.BOTH -> R.string.fire_both
    },
)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ThrottleEditor(throttle: ThrottlePolicy, onThrottleChange: (ThrottlePolicy) -> Unit) {
    // 旧数据里只填了冷却（cooldownMs）的规则，等价于「窗口内最多 1 次」。
    val initialCount = when {
        throttle.maxCount > 0 -> throttle.maxCount
        throttle.cooldownMs > 0 -> 1
        else -> 0
    }
    val initialWindowMs = when {
        throttle.windowMs > 0 -> throttle.windowMs
        throttle.cooldownMs > 0 -> throttle.cooldownMs
        else -> 0L
    }
    var countText by remember(throttle.maxCount, throttle.cooldownMs) {
        mutableStateOf(if (initialCount > 0) initialCount.toString() else "")
    }
    var windowText by remember(throttle.windowMs, throttle.cooldownMs) {
        mutableStateOf(if (initialWindowMs > 0) (initialWindowMs / 1000).toString() else "")
    }
    fun submit(maxCount: Int, windowMs: Long) {
        val kind = when {
            maxCount <= 0 || windowMs <= 0 -> ThrottleKind.OFF
            maxCount == 1 -> ThrottleKind.COOLDOWN
            else -> ThrottleKind.WINDOW
        }
        onThrottleChange(
            ThrottlePolicy(
                kind = kind,
                // 1 次即冷却：运行时继续走 cooldown 分支，语义与窗口一致。
                cooldownMs = if (maxCount == 1 && windowMs > 0) windowMs else 0L,
                maxCount = maxCount,
                windowMs = windowMs,
            ),
        )
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(CardInnerSpacing),
    ) {
        OutlinedTextField(
            value = windowText,
            onValueChange = {
                windowText = it
                val count = countText.toIntOrNull() ?: 0
                submit(count, it.toLongOrNull()?.times(1000) ?: 0L)
            },
            modifier = Modifier.weight(1f),
            label = { Text(stringResource(R.string.throttle_window_seconds)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            shape = LargeShape,
        )
        OutlinedTextField(
            value = countText,
            onValueChange = {
                countText = it
                val windowMs = (windowText.toLongOrNull() ?: 0L) * 1000L
                submit(it.toIntOrNull() ?: 0, windowMs)
            },
            modifier = Modifier.weight(1f),
            label = { Text(stringResource(R.string.throttle_window_count)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            shape = LargeShape,
        )
    }
}
@Composable
internal fun NotificationPermissionRow() {
    val context = LocalContext.current
    val granted = remember {
        AutomationCenter.isNotificationAccessGranted(context)
    }
    LunoSettingsRow(
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
