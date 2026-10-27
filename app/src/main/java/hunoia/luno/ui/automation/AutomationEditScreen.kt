package hunoia.luno.ui.automation
import android.os.SystemClock
import android.util.Log
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import hunoia.luno.R
import hunoia.luno.domain.action.ActionLibrary
import hunoia.luno.domain.automation.AutomationCenter
import hunoia.luno.domain.automation.RuleTestResult
import hunoia.luno.bridge.ui.showToast
import hunoia.luno.config.model.AutomationRule
import hunoia.luno.config.model.FireMode
import hunoia.luno.config.model.RuleEffect
import hunoia.luno.config.model.RuleStep
import hunoia.luno.config.model.RuleStepKind
import hunoia.luno.config.model.WhenGroup
import hunoia.luno.config.model.conditionAt
import hunoia.luno.config.model.withConditionAt
import hunoia.luno.core.domain.runRuleId
import hunoia.luno.core.domain.JsonSerializer
import hunoia.luno.ui.navigation.ActionSelect
import hunoia.luno.ui.navigation.NEW_AUTOMATION_RULE_ID
import hunoia.luno.ui.theme.CardInnerSpacing
import hunoia.luno.ui.theme.FloatingContentBottom
import hunoia.luno.ui.theme.LargeShape
import kotlinx.coroutines.launch
import hunoia.luno.ui.component.LunoFab
import hunoia.luno.ui.component.screen.LunoScreen
import hunoia.luno.ui.component.feedback.LunoIconButton
import hunoia.luno.ui.component.row.LunoGroup
import hunoia.luno.ui.component.sheet.ConfirmDialog
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
    /** 当前条件 sheet 编辑的是触发条件还是约束条件（决定写回哪棵树）。 */
    var conditionIsConstraint by remember { mutableStateOf(false) }
    var stepSheetPath by remember { mutableStateOf<String?>(null) }
    var stepClipboard by remember { mutableStateOf<RuleStep?>(null) }
    var stepTypePickerFor by remember { mutableStateOf<String?>(null) }
    var showBlockedDialog by remember { mutableStateOf(false) }
    var showDiscardDialog by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var testRunning by remember { mutableStateOf(false) }
    var testMenuExpanded by remember { mutableStateOf(false) }
    var testResult by remember { mutableStateOf<RuleTestResult?>(null) }
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
        draft = rules.find { it.id == ruleId } ?: AutomationRule(
            id = if (ruleId == NEW_AUTOMATION_RULE_ID) SystemClock.uptimeMillis().toString() else ruleId,
            condition = WhenGroup(),
            effect = RuleEffect(),
            createdAtMs = System.currentTimeMillis(),
        )
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
    val capabilityReport = remember(rule, loadedRules, library, resumeTick) {
        AutomationCenter.capabilityReport(
            context,
            rule,
            (loadedRules ?: emptyList()).filterNot { it.id == rule.id } + rule,
            library,
        )
    }
    /** 条件树含状态叶：决定规则级进入/退出是否可用（纯事件树发生即触发）。 */
    val conditionHasState = rule.condition.hasStateCondition()
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
                // 纯事件树没有持续边沿，触发方式统一落 ENTER（引擎同样归一化，双保险）。
                val toSave = if (conditionHasState) rule else rule.copy(fireOn = FireMode.ENTER)
                AutomationCenter.updateRules { rules ->
                    if (rules.any { it.id == toSave.id }) {
                        rules.map { if (it.id == toSave.id) toSave else it }
                    } else {
                        rules + toSave
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
    fun testRun(executeSteps: Boolean = true) {
        if (testRunning) return
        testRunning = true
        val started = AutomationCenter.runRuleTest(rule, executeSteps) { result ->
            testRunning = false
            if (result == null) {
                showToast(R.string.automation_test_unavailable)
            } else {
                testResult = result
            }
        }
        if (!started) {
            testRunning = false
            showToast(R.string.automation_test_unavailable)
        }
    }
    val conditionIncomplete = rule.condition.hasIncompleteCondition() ||
        rule.constraints.hasIncompleteCondition() ||
        rule.effect.steps.hasIncompleteStepCondition()
    val paramsIncomplete = rule.params.any { !isValidVarName(it.name) } ||
        rule.params.map { it.name }.size != rule.params.map { it.name }.distinct().size
    val stepsEmpty = rule.effect.steps.isEmpty()
    val throttleInvalid = isInvalidThrottle(rule.throttle)
    val canSave = hasCondition && !conditionIncomplete && !paramsIncomplete &&
        !stepsEmpty && !throttleInvalid
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
            Box {
                LunoIconButton(
                    icon = Icons.Default.PlayArrow,
                    contentDescription = stringResource(R.string.automation_test_run),
                    enabled = !testRunning,
                    onClick = { testMenuExpanded = true },
                )
                DropdownMenu(
                    expanded = testMenuExpanded,
                    onDismissRequest = { testMenuExpanded = false },
                ) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.automation_test_run_once)) },
                        onClick = {
                            testMenuExpanded = false
                            testRun(executeSteps = true)
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.automation_test_condition_only)) },
                        onClick = {
                            testMenuExpanded = false
                            testRun(executeSteps = false)
                        },
                    )
                }
            }
            LunoIconButton(
                icon = Icons.Default.History,
                contentDescription = stringResource(R.string.automation_history),
                onClick = { onNavToHistory(rule.id) },
            )
            // 删除从规则列表页移到编辑页右上角：带确认，删后返回。
            LunoIconButton(
                icon = Icons.Default.Delete,
                contentDescription = stringResource(R.string.delete),
                onClick = { showDeleteConfirm = true },
            )
        },
        bottomContentPadding = FloatingContentBottom,
        floatingContent = { _ ->
            // 标准 M3 FAB：点击直接保存（无效配置弹拦截框）；dirty 时主容器色提示未保存。
            LunoFab(
                onClick = { trySave() },
                icon = Icons.Default.Save,
                contentDescription = stringResource(R.string.save),
                containerColor = if (dirty) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surfaceContainerHighest,
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
            if (showDeleteConfirm) {
                ConfirmDialog(
                    title = stringResource(R.string.automation_delete_title),
                    text = stringResource(R.string.automation_delete_text),
                    destructive = true,
                    onDismissRequest = { showDeleteConfirm = false },
                    onConfirm = {
                        showDeleteConfirm = false
                        scope.launch {
                            AutomationCenter.removeRule(rule.id)
                            AutomationDraftHolder.remove(rule.id)
                            onBack()
                        }
                    },
                )
            }
            if (showDiscardDialog) {
                ConfirmDialog(
                    title = stringResource(R.string.automation_discard_title),
                    text = stringResource(R.string.automation_discard_text),
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
                val inConstraints = conditionIsConstraint
                val owner = if (inConstraints) rule.constraints else rule.condition
                owner.conditionAt(path)?.let { item ->
                    ConditionSheet(
                        item = item,
                        stateOnly = inConstraints,
                        onDismissRequest = { conditionSheetPath = null },
                        onConditionChange = { changed ->
                            update(
                                if (inConstraints) {
                                    rule.copy(constraints = rule.constraints.withConditionAt(path) { changed })
                                } else {
                                    rule.copy(condition = rule.condition.withConditionAt(path) { changed })
                                },
                            )
                        },
                        onDelete = {
                            update(
                                if (inConstraints) {
                                    rule.copy(constraints = removeConditionAt(rule.constraints, path))
                                } else {
                                    rule.copy(condition = removeConditionAt(rule.condition, path))
                                },
                            )
                            conditionSheetPath = null
                        },
                    )
                }
            }
            stepSheetPath?.let { path ->
                rule.effect.stepAt(path)?.let { step ->
                    val runRuleTarget = step.action?.runRuleId()?.let { targetId ->
                        loadedRules?.firstOrNull { it.id == targetId }
                    }
                    StepSheet(
                        step = step,
                        onDismissRequest = { stepSheetPath = null },
                        onStepChange = { changed ->
                            update(rule.copy(effect = rule.effect.withStepAt(path) { changed }))
                        },
                        onDelete = {
                            update(rule.copy(effect = removeStepAt(rule.effect, path)))
                            stepSheetPath = null
                        },
                        onEditIfCondition = {
                            stepSheetPath = null
                            ifConditionStepPath = path
                        },
                        actionSummary = stepSummary(
                            context = context,
                            library = library,
                            step = step,
                            rules = loadedRules ?: emptyList(),
                            includeFailure = false,
                        ),
                        onPickAction = {
                            stepSheetPath = null
                            onPickAction(rule, path)
                        },
                        runRuleTarget = runRuleTarget,
                        onCopy = {
                            stepClipboard = step
                            showToast(R.string.step_copied)
                        },
                        canMoveUp = canMoveStep(rule.effect, path, -1),
                        canMoveDown = canMoveStep(rule.effect, path, 1),
                        onMoveUp = {
                            moveStepAt(rule.effect, path, -1)?.let { moved ->
                                update(rule.copy(effect = moved))
                            }
                            stepSheetPath = null
                        },
                        onMoveDown = {
                            moveStepAt(rule.effect, path, 1)?.let { moved ->
                                update(rule.copy(effect = moved))
                            }
                            stepSheetPath = null
                        },
                    )
                }
            }
            ifConditionStepPath?.let { stepPath ->
                rule.effect.stepAt(stepPath)?.let { step ->
                    if (step.kind == RuleStepKind.IF || step.kind == RuleStepKind.WAIT_UNTIL) {
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
            testResult?.let { result ->
                RuleTestResultSheet(
                    result = result,
                    onDismissRequest = { testResult = null },
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
                onEditItem = {
                    conditionIsConstraint = false
                    conditionSheetPath = it
                },
                fireMode = if (conditionHasState) rule.fireOn else null,
                onFireChange = if (conditionHasState) {
                    { mode: FireMode -> update(rule.copy(fireOn = mode)) }
                } else null,
            )
            // 纯事件条件没有持续边沿：进入/退出无意义，隐藏选择器并说明（空条件组不提示）。
            if (hasCondition && !conditionHasState) {
                Text(
                    stringResource(R.string.condition_event_only_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        LunoGroup(
            title = stringResource(R.string.rule_params),
            badgeText = if (rule.params.isEmpty()) null else {
                stringResource(R.string.condition_group_count, rule.params.size)
            },
            badgeError = paramsIncomplete,
        ) {
            RuleParamsEditor(
                params = rule.params,
                onParamsChange = { update(rule.copy(params = it)) },
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
                onPickAction = { path -> onPickAction(rule, path) },
                onDeleteStep = { path ->
                    update(rule.copy(effect = removeStepAt(rule.effect, path)))
                },
                library = library,
                rules = loadedRules ?: emptyList(),
                clipboard = stepClipboard,
                onPasteStep = { prefix ->
                    stepClipboard?.let { clip ->
                        appendStep(rule.effect, prefix, clip)?.let { (_, newEffect) ->
                            update(rule.copy(effect = newEffect))
                        }
                    }
                },
            )
        }
        LunoGroup(
            title = stringResource(R.string.automation_constraints),
            badgeText = if (rule.constraints.items.isEmpty()) {
                stringResource(R.string.automation_constraints_none)
            } else {
                stringResource(R.string.condition_group_count, rule.constraints.items.size)
            },
            badgeError = rule.constraints.hasIncompleteCondition(),
        ) {
            ConditionGroupEditor(
                group = rule.constraints,
                onGroupChange = { update(rule.copy(constraints = it)) },
                onEditItem = {
                    conditionIsConstraint = true
                    conditionSheetPath = it
                },
                // 约束是「此刻适用性」：只给状态类条件，脉冲条件没有意义。
                stateOnly = true,
                emptyIsValid = true,
            )
        }
        LunoGroup(
            title = stringResource(R.string.automation_throttle),
            // 组内首项是裸输入框，上方没有其他组那种等高容器边，缩小标题下间距使视觉一致。
            titleBottomSpacing = CardInnerSpacing,
            badgeText = stringResource(R.string.throttle_invalid).takeIf { throttleInvalid },
            badgeError = throttleInvalid,
        ) {
            ThrottleEditor(
                throttle = rule.throttle,
                onThrottleChange = { update(rule.copy(throttle = it)) },
            )
            if (conditionUsesNotification(rule.condition) ||
                conditionUsesNotification(rule.constraints) ||
                rule.effect.usesNotificationCondition() ||
                capabilityReport.notificationAccessMissing
            ) {
                NotificationPermissionRow(refreshKey = resumeTick)
            }
        }
        LunoGroup(
            title = stringResource(R.string.automation_run_control),
            titleBottomSpacing = CardInnerSpacing,
            badgeText = if (capabilityReport.hasMissing) {
                stringResource(R.string.permission_precheck_title)
            } else {
                null
            },
            badgeError = capabilityReport.hasMissing,
        ) {
            RunControlEditor(
                rule = rule,
                onRuleChange = { update(it) },
            )
            CapabilityWarningRow(capabilityReport)
        }
    }
}
