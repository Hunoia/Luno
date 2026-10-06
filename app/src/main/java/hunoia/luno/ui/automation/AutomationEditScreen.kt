package hunoia.luno.ui.automation
import android.content.Context
import android.os.SystemClock
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import hunoia.luno.R
import hunoia.luno.action.model.NewActionLibrarySettings
import hunoia.luno.action.model.StoredAction
import hunoia.luno.domain.action.ActionLibrary
import hunoia.luno.domain.automation.AutomationCenter
import hunoia.luno.config.model.AutomationRule
import hunoia.luno.config.model.BooleanMode
import hunoia.luno.config.model.ConditionItem
import hunoia.luno.config.model.ConditionType
import hunoia.luno.config.model.FireMode
import hunoia.luno.config.model.RuleEffect
import hunoia.luno.config.model.RuleEffectType
import hunoia.luno.config.model.RulePriority
import hunoia.luno.config.model.RuleStep
import hunoia.luno.config.model.RuleStepKind
import hunoia.luno.config.model.ThrottleKind
import hunoia.luno.config.model.ThrottlePolicy
import hunoia.luno.config.model.WhenGroup
import hunoia.luno.config.model.conditionAt
import hunoia.luno.config.model.withConditionAt
import hunoia.luno.ui.actionselect.selectedItemLabel
import hunoia.luno.ui.navigation.ActionSelect
import hunoia.luno.ui.navigation.NEW_AUTOMATION_RULE_ID
import hunoia.luno.ui.theme.BottomBarPadding
import hunoia.luno.ui.theme.CardInnerSpacing
import hunoia.luno.ui.theme.ContainerRadius
import hunoia.luno.ui.theme.ElevationSmall
import hunoia.luno.ui.theme.FloatingContentBottom
import hunoia.luno.ui.theme.LargeShape
import hunoia.luno.ui.theme.ListItemVerticalPadding
import hunoia.luno.ui.theme.MinInteractiveSize
import hunoia.luno.ui.theme.PageGutter
import hunoia.luno.ui.theme.liquidGlassBlur
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import hunoia.luno.ui.component.row.LunoSettingsRow
import hunoia.luno.ui.component.row.LunoSwitchRow
import hunoia.luno.ui.component.screen.LunoScreen
import hunoia.luno.ui.component.sheet.LunoSheet
import hunoia.luno.ui.component.feedback.LunoIconButton
import hunoia.luno.ui.component.row.LunoGroup
import hunoia.luno.ui.component.sheet.ConfirmDialog
import hunoia.luno.ui.component.sheet.SheetHeader
import hunoia.luno.ui.component.timeline.TimelineBadge
import hunoia.luno.ui.component.timeline.TimelineColumn
import hunoia.luno.ui.component.timeline.TimelineNode
import hunoia.luno.ui.component.timeline.timelineIndent
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AutomationEditScreen(
    onBack: () -> Unit,
    ruleId: String,
    onNavToActionSelect: (ActionSelect) -> Unit,
    onNavToHistory: (String) -> Unit,
) {
    val loadedRules by AutomationCenter.rules
        .collectAsStateWithLifecycle(initialValue = null)
    val library by ActionLibrary.settings
        .collectAsStateWithLifecycle(initialValue = null)
    val scope = rememberCoroutineScope()
    var draft by remember { mutableStateOf<AutomationRule?>(null) }
    var dirty by remember(ruleId) { mutableStateOf(ruleId == NEW_AUTOMATION_RULE_ID) }
    var conditionSheetPath by remember { mutableStateOf<List<Int>?>(null) }
    var stepSheetPath by remember { mutableStateOf<String?>(null) }
    var stepTypePickerFor by remember { mutableStateOf<String?>(null) }
    var showBlockedDialog by remember { mutableStateOf(false) }
    /** 用户编辑统一入口：写草稿并标脏（store 回填与初始物化不走这里）。 */
    fun update(next: AutomationRule) {
        draft = next
        dirty = true
    }
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
                createdAtMs = System.currentTimeMillis(),
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
    /**
     * 跳动作选择页：必须显式传入要持久化的规则——调用方刚改完 draft 时重组还没发生，
     * 直接读闭包里的 rule 会拿到旧快照（新步骤丢了，选择页回填 path 失效还照样 toast 成功）。
     */
    fun onPickAction(ruleToPersist: AutomationRule, stepPath: String) {
        scope.launch {
            AutomationCenter.updateRules { rules ->
                if (rules.any { r -> r.id == ruleToPersist.id }) {
                    rules.map { r -> if (r.id == ruleToPersist.id) ruleToPersist else r }
                } else {
                    rules + ruleToPersist
                }
            }
            onNavToActionSelect(ActionSelect(
                automationRuleId = ruleToPersist.id,
                automationStepPath = stepPath,
            ))
        }
    }
    fun save() {
        scope.launch {
            try {
                AutomationCenter.updateRules { rules ->
                    if (rules.any { it.id == rule.id }) {
                        rules.map { if (it.id == rule.id) rule else it }
                    } else {
                        rules + rule
                    }
                }
                dirty = false
                onBack()
            } catch (e: Exception) {
                Log.e("AutomationEdit", "save failed", e)
            }
        }
    }
    val hasCondition = AutomationCenter.hasLeafOf(rule.condition)
    val stepsEmpty = rule.effect.steps.isEmpty()
    val throttleInvalid = isInvalidThrottle(rule.throttle)
    val canSave = hasCondition && !stepsEmpty && !throttleInvalid
    fun trySave() {
        if (canSave) save() else showBlockedDialog = true
    }
    LunoScreen(
        title = stringResource(R.string.automation_edit),
        onBack = onBack,
        actions = {
            LunoIconButton(
                icon = Icons.Default.History,
                contentDescription = stringResource(R.string.automation_history),
                onClick = { onNavToHistory(rule.id) },
            )
        },
        bottomContentPadding = FloatingContentBottom,
        floatingContent = { backdrop ->
            SavePill(
                backdrop = backdrop,
                dirty = dirty,
                onClick = { trySave() },
            )
        },
        dialogs = {
            if (showBlockedDialog) {
                val reasons = listOfNotNull(
                    stringResource(R.string.automation_condition_empty).takeIf { !hasCondition },
                    stringResource(R.string.automation_steps_empty).takeIf { stepsEmpty },
                    stringResource(R.string.throttle_invalid).takeIf { throttleInvalid },
                ).joinToString(separator = "\n")
                ConfirmDialog(
                    title = stringResource(R.string.automation_cannot_save),
                    text = reasons,
                    onDismissRequest = { showBlockedDialog = false },
                    onConfirm = { showBlockedDialog = false },
                )
            }
        },
        sheets = {
            conditionSheetPath?.let { path ->
                rule.condition.conditionAt(path)?.let { item ->
                    ConditionSheet(
                        item = item,
                        onDismissRequest = { conditionSheetPath = null },
                        onConditionChange = { update(rule.copy(condition = rule.condition.withConditionAt(path) { it })) },
                        onDelete = {
                            update(rule.copy(condition = removeConditionAt(rule.condition, path)))
                            conditionSheetPath = null
                        },
                    )
                }
            }
            stepSheetPath?.let { path ->
                rule.effect.stepAt(path)?.let { step ->
                    StepSheet(
                        step = step,
                        library = library,
                        rule = rule,
                        onDismissRequest = { stepSheetPath = null },
                        onStepChange = { update(rule.copy(effect = rule.effect.withStepAt(path) { it })) },
                        onPickAction = { onPickAction(rule, path) },
                        onDelete = {
                            update(rule.copy(effect = removeStepAt(rule.effect, path)))
                            stepSheetPath = null
                        },
                    )
                }
            }
            stepTypePickerFor?.let { prefix ->
                StepTypeSheet(
                    onDismissRequest = { stepTypePickerFor = null },
                    onPick = { kind ->
                        appendStep(rule.effect, prefix, RuleStep(kind = kind))?.let { (newPath, newEffect) ->
                            update(rule.copy(effect = newEffect))
                            stepSheetPath = newPath
                        }
                        stepTypePickerFor = null
                    },
                )
            }
        },
    ) {
        OutlinedTextField(
            value = rule.name,
            onValueChange = { update(rule.copy(name = it)) },
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.automation_name)) },
            placeholder = { Text(stringResource(R.string.automation_name_hint)) },
            singleLine = true,
            shape = LargeShape,
        )
        LunoGroup(
            title = stringResource(R.string.automation_condition),
            badgeText = if (hasCondition) {
                stringResource(R.string.condition_group_count, rule.condition.items.size)
            } else {
                stringResource(R.string.automation_condition_empty)
            },
            badgeError = !hasCondition,
        ) {
            ConditionGroupEditor(
                group = rule.condition,
                onGroupChange = { update(rule.copy(condition = it)) },
                onEditItem = { conditionSheetPath = it },
                fireMode = rule.fireOn,
                onFireChange = { update(rule.copy(fireOn = it)) },
            )
        }
        LunoGroup(
            title = stringResource(R.string.automation_steps),
            badgeText = if (stepsEmpty) {
                stringResource(R.string.automation_steps_empty)
            } else {
                stringResource(R.string.condition_group_count, rule.effect.steps.size)
            },
            badgeError = stepsEmpty,
        ) {
            StepsEditor(
                steps = rule.effect.steps,
                onStepsChange = { update(rule.copy(effect = rule.effect.copy(steps = it))) },
                onEditStep = { stepSheetPath = it },
                onAddStep = { stepTypePickerFor = it },
                library = library,
                rule = rule,
            )
        }
        LunoGroup(
            title = stringResource(R.string.automation_throttle),
            badgeText = stringResource(R.string.throttle_invalid).takeIf { throttleInvalid },
            badgeError = throttleInvalid,
        ) {
            ThrottleEditor(
                throttle = rule.throttle,
                onThrottleChange = { update(rule.copy(throttle = it)) },
            )
            if (conditionUsesNotification(rule.condition)) {
                NotificationPermissionRow()
            }
        }
    }
}
/** 添加行：图标独占一行，背景/高度/圆角与其他行一致。 */
@Composable
private fun AddRow(
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
private fun StepsEditor(
    steps: List<RuleStep>,
    onStepsChange: (List<RuleStep>) -> Unit,
    onEditStep: (String) -> Unit,
    onAddStep: (String) -> Unit,
    library: NewActionLibrarySettings?,
    rule: AutomationRule,
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
                    onEdit = { onEditStep("$pathPrefix$index") },
                    onMoveUp = if (index > 0) ({
                        onStepsChange(steps.toMutableList().also {
                            val tmp = it[index - 1]
                            it[index - 1] = it[index]
                            it[index] = tmp
                        })
                    }) else null,
                    onMoveDown = if (index < steps.size - 1) ({
                        onStepsChange(steps.toMutableList().also {
                            val tmp = it[index + 1]
                            it[index + 1] = it[index]
                            it[index] = tmp
                        })
                    }) else null,
                )
            }
            if (step.kind == RuleStepKind.IF) {
                // 分支收进内嵌子卡：各自时间线 + 各自添加按钮，不再裸缩进平铺。
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
                        library = library,
                        rule = rule,
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
                        library = library,
                        rule = rule,
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
/** 步骤一行：类型 + 摘要 + 上下箭头；点行进 sheet，删除在 sheet 里。 */
@Composable
private fun StepRow(
    step: RuleStep,
    library: NewActionLibrarySettings?,
    onEdit: () -> Unit,
    onMoveUp: (() -> Unit)?,
    onMoveDown: (() -> Unit)?,
) {
    val context = LocalContext.current
    LunoSettingsRow(
        title = when (step.kind) {
            RuleStepKind.DELAY -> stringResource(R.string.step_delay)
            RuleStepKind.IF -> stringResource(R.string.step_if)
            else -> stringResource(R.string.step_action)
        },
        subtitle = stepSummary(context, library, step),
        onClick = onEdit,
        trailing = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                LunoIconButton(
                    icon = Icons.Default.KeyboardArrowUp,
                    contentDescription = stringResource(R.string.rule_move_up),
                    onClick = { onMoveUp?.invoke() },
                    enabled = onMoveUp != null,
                )
                LunoIconButton(
                    icon = Icons.Default.KeyboardArrowDown,
                    contentDescription = stringResource(R.string.rule_move_down),
                    onClick = { onMoveDown?.invoke() },
                    enabled = onMoveDown != null,
                )
            }
        },
    )
}
/**
 * 按路径删除条件：path 为条件组内下标链。非法路径原样返回。
 */
private fun removeConditionAt(group: WhenGroup, path: List<Int>): WhenGroup {
    if (path.isEmpty()) return group
    val head = path.first()
    if (head !in group.items.indices) return group
    val items = group.items.toMutableList()
    if (path.size == 1) {
        items.removeAt(head)
        return group.copy(items = items)
    }
    val target = items[head]
    val sub = target.group ?: return group
    items[head] = target.copy(group = removeConditionAt(sub, path.drop(1)))
    return group.copy(items = items)
}
/**
 * 按路径删除步骤：path 为 appendStep 同款字符串路径（"2" / "2.then.0"）。
 * 非法路径原样返回。
 */
private fun removeStepAt(effect: RuleEffect, path: String): RuleEffect {
    val tokens = path.split(".").filter { it.isNotEmpty() }
    if (tokens.isEmpty()) return effect
    fun removeFromList(steps: List<RuleStep>, index: Int): List<RuleStep> =
        steps.toMutableList().also { if (index in it.indices) it.removeAt(index) }
    if (tokens.size == 1) {
        val index = tokens[0].toIntOrNull() ?: return effect
        return effect.copy(steps = removeFromList(effect.steps, index))
    }
    val index = tokens.last().toIntOrNull() ?: return effect
    val branch = tokens[tokens.size - 2]
    if (branch != "then" && branch != "else") return effect
    val parentPath = tokens.dropLast(2).joinToString(".")
    if (parentPath.isEmpty()) return effect
    return effect.withStepAt(parentPath) { step ->
        if (step.kind != RuleStepKind.IF) step
        else if (branch == "then") step.copy(thenSteps = removeFromList(step.thenSteps, index))
        else step.copy(elseSteps = removeFromList(step.elseSteps, index))
    }
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
    val stored = step.action
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
    val baseName = parent.name.ifBlank { conditionSummary(context, parent.condition) }
    return AutomationRule(
        id = "${parent.id}:show",
        name = context.getString(R.string.automation_pair_show_name, baseName),
        condition = parent.condition,
        effect = RuleEffect(
            type = RuleEffectType.RUN_ACTION,
            steps = listOf(RuleStep(kind = RuleStepKind.ACTION, action = showStored)),
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
        val label = step.action?.let {
            context.selectedItemLabel(it, emptyList(), library?.entries ?: emptyList())
        }.orEmpty()
        label.ifBlank { context.getString(R.string.step_empty_action) }
    }
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ConditionGroupEditor(
    group: WhenGroup,
    onGroupChange: (WhenGroup) -> Unit,
    onEditItem: (List<Int>) -> Unit,
    basePath: List<Int> = emptyList(),
    depth: Int = 0,
    onDeleteSelf: (() -> Unit)? = null,
    fireMode: FireMode? = null,
    onFireChange: ((FireMode) -> Unit)? = null,
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
                    error = group.items.isEmpty(),
                )
            },
        ) {
            LunoSettingsRow(
                title = "",
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
                                    DropdownMenuItem(
                                        text = { Text(fireModeLabel(mode)) },
                                        trailingIcon = {
                                            if (mode == fireMode) {
                                                Icon(
                                                    imageVector = Icons.Default.Check,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.primary,
                                                )
                                            }
                                        },
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
                // 嵌套组：UI 只开两层（引擎不限），再深的存量照跑、编辑器折叠为摘要。
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
            modifier = timelineIndent(),
            horizontalArrangement = Arrangement.spacedBy(CardInnerSpacing),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AddRow(
                onClick = { showPicker = true },
                modifier = Modifier.weight(1f),
                contentDescription = stringResource(R.string.condition_add),
            )
            if (depth < 2) {
                TextButton(onClick = {
                    onGroupChange(group.copy(items = group.items + defaultConditionOf(ConditionType.GROUP)))
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
                onGroupChange(group.copy(items = group.items + defaultConditionOf(type)))
                showPicker = false
            },
        )
    }
}
/** 条件一行：类型 + 摘要；点行进 sheet 配参数，删除在 sheet 里。 */
@Composable
private fun ConditionRow(
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
private fun ConditionSheet(
    item: ConditionItem,
    onDismissRequest: () -> Unit,
    onConditionChange: (ConditionItem) -> Unit,
    onDelete: () -> Unit,
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
                LunoSwitchRow(
                    title = stringResource(R.string.logic_not),
                    checked = item.negated,
                    onCheckedChange = { onConditionChange(item.copy(negated = it)) },
                )
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
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StepSheet(
    step: RuleStep,
    library: NewActionLibrarySettings?,
    rule: AutomationRule,
    onDismissRequest: () -> Unit,
    onStepChange: (RuleStep) -> Unit,
    onPickAction: () -> Unit,
    onDelete: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
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
                    RuleStepKind.IF -> stringResource(R.string.step_if)
                    else -> stringResource(R.string.step_action)
                },
            )
            Column(
                modifier = Modifier.padding(horizontal = PageGutter),
                verticalArrangement = Arrangement.spacedBy(CardInnerSpacing),
            ) {
                when (step.kind) {
                    RuleStepKind.ACTION -> {
                        LunoSettingsRow(
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
                                    AutomationCenter.updateRules { rules ->
                                        if (rules.any { it.id == "${rule.id}:show" }) rules
                                        else rules + buildShowPairRule(context, rule, library, step)
                                    }
                                }
                                onDismissRequest()
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
                    RuleStepKind.IF -> {
                        var nestedPath by remember { mutableStateOf<List<Int>?>(null) }
                        ConditionGroupEditor(
                            group = step.condition,
                            onGroupChange = { onStepChange(step.copy(condition = it)) },
                            onEditItem = { nestedPath = it },
                        )
                        nestedPath?.let { path ->
                            val nested = step.condition.conditionAt(path)
                            if (nested == null) {
                                nestedPath = null
                            } else {
                                ConditionSheet(
                                    item = nested,
                                    onDismissRequest = { nestedPath = null },
                                    onConditionChange = { new ->
                                        onStepChange(step.copy(
                                            condition = step.condition.withConditionAt(path) { new },
                                        ))
                                    },
                                    onDelete = {
                                        onStepChange(step.copy(
                                            condition = removeConditionAt(step.condition, path),
                                        ))
                                        nestedPath = null
                                    },
                                )
                            }
                        }
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
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StepTypeSheet(
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
    val entries = library?.entries
    val entry = entries?.find { it.id == entryId } ?: return stringResource(R.string.automation_entry_missing, entryId)
    return entry.name.ifBlank { entryId }
}
private fun groupModeLabel(mode: BooleanMode): Int = when (mode) {
    BooleanMode.AND -> R.string.logic_all
    BooleanMode.OR -> R.string.logic_any
}
private fun nextGroupMode(mode: BooleanMode): BooleanMode = when (mode) {
    BooleanMode.AND -> BooleanMode.OR
    BooleanMode.OR -> BooleanMode.AND
}
internal fun priorityLabel(priority: Int): Int = when (priority) {
    RulePriority.HIGH -> R.string.priority_high
    RulePriority.LOW -> R.string.priority_low
    else -> R.string.priority_medium
}
@Composable
private fun fireModeLabel(mode: FireMode): String = stringResource(
    when (mode) {
        FireMode.ENTER -> R.string.fire_enter
        FireMode.EXIT -> R.string.fire_exit
        FireMode.BOTH -> R.string.fire_both
    },
)
private fun isInvalidThrottle(throttle: ThrottlePolicy): Boolean = when (throttle.kind) {
    ThrottleKind.COOLDOWN -> throttle.cooldownMs < 0
    ThrottleKind.WINDOW -> throttle.maxCount < 1 || throttle.windowMs < 1000L
    else -> false
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ThrottleEditor(throttle: ThrottlePolicy, onThrottleChange: (ThrottlePolicy) -> Unit) {
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
                onClick = { onThrottleChange(throttle.copy(kind = kind)) },
                shape = SegmentedButtonDefaults.itemShape(count = kinds.size, index = index),
                icon = {},
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
                it.toLongOrNull()?.let { s -> onThrottleChange(throttle.copy(cooldownMs = s * 1000)) }
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
                    it.toIntOrNull()?.let { n -> onThrottleChange(throttle.copy(maxCount = n)) }
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
                    it.toLongOrNull()?.let { s -> onThrottleChange(throttle.copy(windowMs = s * 1000)) }
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
}/** 右下角悬浮保存键：主页底栏同款液态玻璃圆形，有未保存改动时高亮。 */
@Composable
private fun BoxScope.SavePill(
    backdrop: LayerBackdrop?,
    dirty: Boolean,
    onClick: () -> Unit,
) {
    val contentColor = if (dirty) MaterialTheme.colorScheme.primary
    else MaterialTheme.colorScheme.onSurfaceVariant
    Box(
        modifier = Modifier
            .align(Alignment.BottomEnd)
            .padding(end = PageGutter, bottom = BottomBarPadding)
            .windowInsetsPadding(WindowInsets.navigationBars)
            .liquidGlassBlur(backdrop, CircleShape)
            .background(
                color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.5f),
                shape = CircleShape,
            )
            .clickable(onClick = onClick, role = Role.Button)
            .padding(ListItemVerticalPadding),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Default.Save,
            contentDescription = stringResource(R.string.save),
            tint = contentColor,
        )
    }
}
