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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import hunoia.luno.R
import hunoia.luno.action.model.NewActionLibrarySettings
import hunoia.luno.domain.action.ActionLibrary
import hunoia.luno.domain.automation.AutomationCenter
import hunoia.luno.config.model.AutomationRule
import hunoia.luno.config.model.ConditionItem
import hunoia.luno.config.model.ConditionType
import hunoia.luno.config.model.FireMode
import hunoia.luno.config.model.RuleEffect
import hunoia.luno.config.model.RuleEffectType
import hunoia.luno.config.model.RuleStep
import hunoia.luno.config.model.RuleStepKind
import hunoia.luno.config.model.ThrottlePolicy
import hunoia.luno.config.model.WhenGroup
import hunoia.luno.config.model.conditionAt
import hunoia.luno.config.model.withConditionAt
import hunoia.luno.core.JsonSerializer
import hunoia.luno.ui.actionselect.selectedItemLabel
import hunoia.luno.ui.navigation.ActionSelect
import hunoia.luno.ui.navigation.NEW_AUTOMATION_RULE_ID
import hunoia.luno.ui.theme.BottomBarPadding
import hunoia.luno.ui.theme.CardInnerSpacing
import hunoia.luno.ui.theme.ContainerRadius
import hunoia.luno.ui.theme.ElevationSmall
import hunoia.luno.ui.theme.FloatingContentBottom
import hunoia.luno.ui.theme.LargeShape
import hunoia.luno.ui.theme.MinInteractiveSize
import hunoia.luno.ui.theme.PageGutter
import hunoia.luno.ui.theme.appHazeEffect
import kotlinx.coroutines.launch
import hunoia.luno.ui.component.CheckDropdownMenuItem
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
/**
 * 编辑草稿跨页面存活：进动作选择页时编辑页会离开组合，普通 remember 的 draft
 * 回来就没了（新建规则表现为内容被清空）。经 JSON 走 SaveableStateRegistry，
 * 往返选择页/进程死亡重建都能恢复；同一 ruleId 复用同 key，pop 后自动清理。
 */
private val AutomationRuleStateSaver = Saver<MutableState<AutomationRule?>, String>(
    save = { state -> state.value?.let { JsonSerializer.encodeToString(it) }.orEmpty() },
    restore = { json ->
        mutableStateOf(
            json.takeIf { it.isNotEmpty() }?.let {
                runCatching { JsonSerializer.decodeFromString<AutomationRule>(it) }.getOrNull()
            },
        )
    },
)

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
    var draft by rememberSaveable(ruleId, saver = AutomationRuleStateSaver) { mutableStateOf<AutomationRule?>(null) }
    var dirty by rememberSaveable(ruleId) { mutableStateOf(ruleId == NEW_AUTOMATION_RULE_ID) }
    var conditionSheetPath by remember { mutableStateOf<List<Int>?>(null) }
    var stepSheetPath by remember { mutableStateOf<String?>(null) }
    var stepTypePickerFor by remember { mutableStateOf<String?>(null) }
    var showBlockedDialog by remember { mutableStateOf(false) }
    var showDiscardDialog by remember { mutableStateOf(false) }
    // IF 步骤条件编辑（屏幕层 sheet，不嵌套）：目标步骤路径 + 叶路径 + 待选类型落点。
    var ifConditionStepPath by remember { mutableStateOf<String?>(null) }
    var ifNestedPath by remember { mutableStateOf<List<Int>?>(null) }
    var ifPickerTarget by remember { mutableStateOf<Pair<String, List<Int>>?>(null) }
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
    val lifecycleOwner = LocalLifecycleOwner.current
    var resumeTick by remember { mutableStateOf(0) }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) resumeTick++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(resumeTick) {
        if (resumeTick == 0) return@LaunchedEffect
        val current = draft ?: return@LaunchedEffect
        // 动作选择页回填（整条规则级或某步骤级）只写内存暂存，取回并入 draft。
        // 回填也是未保存改动：置 dirty，否则直接返回会静默丢弃且不弹丢弃确认。
        AutomationDraftHolder.take(current.id)?.let { staged ->
            if (staged.effect != current.effect) {
                draft = current.copy(effect = staged.effect)
                dirty = true
            }
        }
    }
    val rule = draft ?: return
    val context = LocalContext.current
    /**
     * 跳动作选择页：草稿只暂存内存（[AutomationDraftHolder]），不写库——
     * 未保存的半成品不进列表、不进引擎；选择页读写都经 holder 回写暂存。
     * 必须显式传入要暂存的规则：调用方刚改完 draft 时重组还没发生，
     * 直接读闭包里的 rule 会拿到旧快照。
     */
    fun onPickAction(ruleToPersist: AutomationRule, stepPath: String) {
        AutomationDraftHolder.stage(ruleToPersist)
        onNavToActionSelect(ActionSelect(
            automationRuleId = ruleToPersist.id,
            automationStepPath = stepPath,
        ))
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
                AutomationDraftHolder.remove(rule.id)
                dirty = false
                onBack()
            } catch (e: Exception) {
                Log.e("AutomationEdit", "save failed", e)
            }
        }
    }
    val hasCondition = AutomationCenter.hasLeafOf(rule.condition)
    val conditionIncomplete = rule.condition.hasIncompleteCondition() ||
        rule.effect.steps.hasIncompleteStepCondition()
    val stepsEmpty = rule.effect.steps.isEmpty()
    val throttleInvalid = isInvalidThrottle(rule.throttle)
    val canSave = hasCondition && !conditionIncomplete && !stepsEmpty && !throttleInvalid
    fun trySave() {
        if (canSave) save() else showBlockedDialog = true
    }
    fun tryBack() {
        if (dirty) showDiscardDialog = true
        else {
            AutomationDraftHolder.remove(rule.id)
            onBack()
        }
    }
    LunoScreen(
        title = stringResource(R.string.automation_edit),
        onBack = { tryBack() },
        actions = {
            LunoIconButton(
                icon = Icons.Default.History,
                contentDescription = stringResource(R.string.automation_history),
                onClick = { onNavToHistory(rule.id) },
            )
        },
        bottomContentPadding = FloatingContentBottom,
        floatingContent = { hazeState ->
            SavePill(
                hazeState = hazeState,
                dirty = dirty,
                onClick = { trySave() },
            )
        },
        dialogs = {
            if (showBlockedDialog) {
                val reasons = listOfNotNull(
                    stringResource(R.string.automation_condition_empty).takeIf { !hasCondition },
                    stringResource(R.string.automation_condition_incomplete).takeIf { conditionIncomplete },
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
            if (showDiscardDialog) {
                ConfirmDialog(
                    title = stringResource(R.string.automation_discard_title),
                    text = stringResource(R.string.automation_discard_text),
                    confirmLabel = stringResource(R.string.automation_discard),
                    onDismissRequest = { showDiscardDialog = false },
                    onConfirm = {
                        showDiscardDialog = false
                        AutomationDraftHolder.remove(rule.id)
                        onBack()
                    },
                )
            }
        },
        sheets = {
            conditionSheetPath?.let { path ->
                rule.condition.conditionAt(path)?.let { item ->
                    ConditionSheet(
                        item = item,
                        onDismissRequest = { conditionSheetPath = null },
                        onConditionChange = { changed ->
                            update(rule.copy(condition = rule.condition.withConditionAt(path) { changed }))
                        },
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
                        rules = loadedRules ?: emptyList(),
                        onDismissRequest = { stepSheetPath = null },
                        onStepChange = { update(rule.copy(effect = rule.effect.withStepAt(path) { it })) },
                        onPickAction = { onPickAction(rule, path) },
                        onDelete = {
                            update(rule.copy(effect = removeStepAt(rule.effect, path)))
                            stepSheetPath = null
                        },
                        onEditIfCondition = {
                            stepSheetPath = null
                            ifConditionStepPath = path
                        },
                    )
                }
            }
            ifConditionStepPath?.let { stepPath ->
                rule.effect.stepAt(stepPath)?.let { step ->
                    if (step.kind == RuleStepKind.IF) {
                        IfConditionSheet(
                            condition = step.condition,
                            onConditionChange = { newCond ->
                                update(rule.copy(effect = rule.effect.withStepAt(stepPath) {
                                    it.copy(condition = newCond)
                                }))
                            },
                            onDismissRequest = {
                                ifConditionStepPath = null
                                ifNestedPath = null
                                ifPickerTarget = null
                            },
                            onEditItem = { ifNestedPath = it },
                            onOpenPicker = { groupPath -> ifPickerTarget = stepPath to groupPath },
                        )
                    }
                }
            }
            ifNestedPath?.let { nested ->
                ifConditionStepPath?.let { stepPath ->
                    rule.effect.stepAt(stepPath)?.let { step ->
                        step.condition.conditionAt(nested)?.let { item ->
                            ConditionSheet(
                                item = item,
                                onDismissRequest = { ifNestedPath = null },
                                onConditionChange = { new ->
                                    update(rule.copy(effect = rule.effect.withStepAt(stepPath) {
                                        it.copy(condition = it.condition.withConditionAt(nested) { new })
                                    }))
                                },
                                onDelete = {
                                    update(rule.copy(effect = rule.effect.withStepAt(stepPath) {
                                        it.copy(condition = removeConditionAt(it.condition, nested))
                                    }))
                                    ifNestedPath = null
                                },
                            )
                        }
                    }
                }
            }
            ifPickerTarget?.let { (stepPath, groupPath) ->
                ConditionTypePickerSheet(
                    onDismissRequest = { ifPickerTarget = null },
                    onPick = { type ->
                        update(rule.copy(effect = rule.effect.withStepAt(stepPath) {
                            it.copy(condition = appendConditionAt(it.condition, groupPath, defaultConditionOf(type)))
                        }))
                        ifPickerTarget = null
                    },
                    // IF 条件里的 MANUAL 恒假（无手势调用语义），不提供。
                    includeManual = false,
                )
            }
            stepTypePickerFor?.let { prefix ->
                StepTypeSheet(
                    onDismissRequest = { stepTypePickerFor = null },
                    onPick = { kind ->
                        val appended = appendStep(rule.effect, prefix, RuleStep(kind = kind))
                        stepTypePickerFor = null
                        appended?.let { (newPath, newEffect) ->
                            val newRule = rule.copy(effect = newEffect)
                            update(newRule)
                            if (kind == RuleStepKind.ACTION) {
                                // 动作直达选择页，不经过中间步骤 sheet。
                                onPickAction(newRule, newPath)
                            } else {
                                stepSheetPath = newPath
                            }
                        }
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
            badgeError = !hasCondition || conditionIncomplete,
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
                rules = loadedRules ?: emptyList(),
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
            if (conditionUsesNotification(rule.condition) || rule.effect.usesNotificationCondition()) {
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
                        library = library,
                        rule = rule,
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
/** 步骤一行：类型 + 摘要 + 上下箭头；点行进 sheet，删除在 sheet 里。 */
@Composable
private fun StepRow(
    step: RuleStep,
    library: NewActionLibrarySettings?,
    rules: List<AutomationRule> = emptyList(),
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
        subtitle = stepSummary(context, library, step, rules),
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
    groupLabel: String? = null,
    // 类型选择器外置（sheet 内嵌编辑器时用）：传 null 走内部 picker，传 host 走外部。
    pickerHost: ((List<Int>) -> Unit)? = null,
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
                            groupLabel = context.getString(R.string.condition_group_number, index + 1),
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
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StepSheet(
    step: RuleStep,
    library: NewActionLibrarySettings?,
    rule: AutomationRule,
    rules: List<AutomationRule> = emptyList(),
    onDismissRequest: () -> Unit,
    onStepChange: (RuleStep) -> Unit,
    onPickAction: () -> Unit,
    onDelete: () -> Unit,
    onEditIfCondition: () -> Unit,
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
                                rules,
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
 * IF 步骤的条件编辑（屏幕层 sheet）。
 * 不复用步骤 sheet 内嵌——弹层不支持嵌套，编辑/添加/删除全部走屏幕层通道。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun IfConditionSheet(
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
    rules: List<AutomationRule> = emptyList(),
): String {
    val action = effect.action
    if (action != null) {
        return context.selectedItemLabel(action, emptyList(), library?.entries ?: emptyList(), rules)
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
private fun ThrottleEditor(throttle: ThrottlePolicy, onThrottleChange: (ThrottlePolicy) -> Unit) {
    var cooldownText by remember(throttle.cooldownMs) {
        mutableStateOf(if (throttle.cooldownMs > 0) (throttle.cooldownMs / 1000).toString() else "")
    }
    var countText by remember(throttle.maxCount) {
        mutableStateOf(if (throttle.maxCount > 0) throttle.maxCount.toString() else "")
    }
    var windowText by remember(throttle.windowMs) {
        mutableStateOf(if (throttle.windowMs > 0) (throttle.windowMs / 1000).toString() else "")
    }
    fun submit(cooldownMs: Long = throttle.cooldownMs, maxCount: Int = throttle.maxCount, windowMs: Long = throttle.windowMs) {
        onThrottleChange(throttle.copy(
            kind = deriveThrottleKind(cooldownMs, maxCount, windowMs),
            cooldownMs = cooldownMs,
            maxCount = maxCount,
            windowMs = windowMs,
        ))
    }
    OutlinedTextField(
        value = cooldownText,
        onValueChange = {
            cooldownText = it
            if (it.isBlank()) submit(cooldownMs = 0L)
            else it.toLongOrNull()?.let { s -> submit(cooldownMs = s * 1000) }
        },
        modifier = Modifier.fillMaxWidth(),
        label = { Text(stringResource(R.string.throttle_cooldown_hint)) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        shape = LargeShape,
    )
        OutlinedTextField(
            value = countText,
            onValueChange = {
                countText = it
                if (it.isBlank()) submit(maxCount = 0)
                else it.toIntOrNull()?.let { n -> submit(maxCount = n) }
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
                if (it.isBlank()) submit(windowMs = 0L)
                else it.toLongOrNull()?.let { s -> submit(windowMs = s * 1000) }
            },
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.throttle_window_seconds)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            shape = LargeShape,
        )
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
}/** 右下角悬浮保存键：M3 圆形 + Haze 背后模糊，有未保存改动时高亮。 */
@Composable
private fun BoxScope.SavePill(
    hazeState: dev.chrisbanes.haze.HazeState?,
    dirty: Boolean,
    onClick: () -> Unit,
) {
    val contentColor = if (dirty) MaterialTheme.colorScheme.primary
    else MaterialTheme.colorScheme.onSurfaceVariant
    val containerColor = if (dirty) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.85f)
    else MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.65f)
    Box(
        modifier = Modifier
            .align(Alignment.BottomEnd)
            .padding(end = PageGutter, bottom = BottomBarPadding)
            .windowInsetsPadding(WindowInsets.navigationBars)
            .clip(CircleShape)
            .appHazeEffect(hazeState, containerColor)
            .clickable(onClick = onClick, role = Role.Button)
            // 对标底栏 + 号键 64 正圆。
            .size(64.dp),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Default.Save,
            contentDescription = stringResource(R.string.save),
            tint = contentColor,
        )
    }
}
