package hunoia.luno.floatball.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import hunoia.luno.bridge.ui.showToast
import hunoia.luno.config.model.SuggestionRule
import hunoia.luno.config.model.SuggestionRuleAction
import hunoia.luno.config.model.removeRule
import hunoia.luno.config.model.upsertRule
import hunoia.luno.domain.tune.TuneConfig
import hunoia.luno.floatball.R
import hunoia.luno.runtime.floatball.suggestion.SuggestionRuleEngine
import hunoia.luno.ui.component.LabeledTextField
import hunoia.luno.ui.component.LunoFab
import hunoia.luno.ui.component.feedback.EmptyState
import hunoia.luno.ui.component.row.ChoiceRow
import hunoia.luno.ui.component.row.LunoGroup
import hunoia.luno.ui.component.row.LunoSwitchRow
import hunoia.luno.ui.component.row.segmentedShape
import hunoia.luno.ui.component.screen.LunoScreen
import hunoia.luno.ui.component.sheet.ConfirmDialog
import hunoia.luno.ui.theme.FloatingContentBottom
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * 建议规则编辑页：ruleId 为空 = 新增。
 *
 * 保存前拦截非法正则：运行时会静默跳过非法规则，编辑页不拦的话用户只会看到
 * 「规则没用」。测试文本用同一套「第 1 个捕获组、无捕获组取整段」语义。
 */
@Composable
fun SuggestionRuleEditScreen(
    ruleId: String,
    onBack: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val addMode = ruleId.isBlank()
    var snapshot by remember(ruleId) { mutableStateOf<SuggestionRule?>(null) }
    var missing by remember(ruleId) { mutableStateOf(false) }
    LaunchedEffect(ruleId) {
        val settings = TuneConfig.action.first()
        val existing = settings.floatBall.clipboardBall.rules.firstOrNull { it.id == ruleId }
        if (!addMode && existing == null) {
            missing = true
        } else {
            snapshot = existing ?: SuggestionRule()
        }
    }

    val initial = snapshot
    if (missing) {
        LunoScreen(
            title = stringResource(R.string.float_ball_suggestion_rule_edit),
            onBack = onBack,
        ) {
            EmptyState(message = stringResource(R.string.float_ball_suggestion_rule_missing))
        }
        return
    }
    if (initial == null) return

    var enabled by remember(ruleId) { mutableStateOf(initial.enabled) }
    var name by remember(ruleId) { mutableStateOf(initial.name) }
    var pattern by remember(ruleId) { mutableStateOf(initial.pattern) }
    var action by remember(ruleId) { mutableStateOf(initial.action) }
    var testText by remember(ruleId) { mutableStateOf("") }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showDiscardConfirm by remember { mutableStateOf(false) }

    val regexError = pattern.isNotBlank() && runCatching { Regex(pattern) }.isFailure
    val valid = pattern.isNotBlank() && !regexError
    val dirty = enabled != initial.enabled || name != initial.name ||
        pattern != initial.pattern || action != initial.action
    val guardedBack = { if (dirty) showDiscardConfirm = true else onBack() }
    val actions = listOf(
        SuggestionRuleAction.COPY,
        SuggestionRuleAction.OPEN,
        SuggestionRuleAction.SHARE,
    )

    fun save() {
        val rule = initial.copy(
            enabled = enabled,
            name = name.trim(),
            pattern = pattern.trim(),
            action = action,
        )
        scope.launch {
            TuneConfig.updateAction { current ->
                current.copy(floatBall = current.floatBall.copy(
                    clipboardBall = current.floatBall.clipboardBall.upsertRule(rule),
                ))
            }
            onBack()
        }
    }

    LunoScreen(
        title = stringResource(
            if (addMode) R.string.float_ball_suggestion_rule_add_title
            else R.string.float_ball_suggestion_rule_edit,
        ),
        onBack = guardedBack,
        bottomContentPadding = FloatingContentBottom,
        actions = {
            if (!addMode) {
                hunoia.luno.ui.component.PressIconButton(onClick = { showDeleteConfirm = true }) {
                    androidx.compose.material3.Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = stringResource(hunoia.luno.design.R.string.delete),
                        tint = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        floatingContent = { _ ->
            LunoFab(
                onClick = {
                    if (valid) save() else showToast(R.string.float_ball_suggestion_rule_invalid)
                },
                icon = Icons.Default.Save,
                contentDescription = stringResource(hunoia.luno.design.R.string.save),
                containerColor = if (valid) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surfaceContainerHighest,
            )
        },
        dialogs = {
            if (showDeleteConfirm) {
                ConfirmDialog(
                    onDismissRequest = { showDeleteConfirm = false },
                    title = stringResource(R.string.float_ball_suggestion_rule_delete_title),
                    text = stringResource(R.string.float_ball_suggestion_rule_delete_desc),
                    onConfirm = {
                        showDeleteConfirm = false
                        scope.launch {
                            TuneConfig.updateAction { current ->
                                current.copy(floatBall = current.floatBall.copy(
                                    clipboardBall = current.floatBall.clipboardBall.removeRule(ruleId),
                                ))
                            }
                            onBack()
                        }
                    },
                )
            }
            if (showDiscardConfirm) {
                ConfirmDialog(
                    onDismissRequest = { showDiscardConfirm = false },
                    title = stringResource(hunoia.luno.actions.R.string.action_library_discard_title),
                    text = stringResource(hunoia.luno.actions.R.string.action_library_discard_desc),
                    onConfirm = {
                        showDiscardConfirm = false
                        onBack()
                    },
                )
            }
        },
    ) {
        LunoGroup {
            LunoSwitchRow(
                title = stringResource(R.string.float_ball_suggestion_rule_enabled),
                checked = enabled,
                onCheckedChange = { enabled = it },
                shape = segmentedShape(0, 2),
            )
            LabeledTextField(
                value = name,
                onValueChange = { name = it },
                label = stringResource(R.string.float_ball_suggestion_rule_name),
                isError = false,
                singleLine = true,
                placeholder = stringResource(R.string.float_ball_suggestion_rule_name_hint),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        LunoGroup(title = stringResource(R.string.float_ball_suggestion_rules_group)) {
            LabeledTextField(
                value = pattern,
                onValueChange = { pattern = it },
                label = stringResource(R.string.float_ball_suggestion_rule_pattern),
                isError = pattern.isBlank() || regexError,
                singleLine = false,
                placeholder = stringResource(R.string.float_ball_suggestion_rule_pattern_hint),
                modifier = Modifier.fillMaxWidth(),
            )
            if (regexError) {
                Text(
                    text = stringResource(R.string.float_ball_suggestion_rule_regex_invalid),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
            } else {
                Text(
                    text = stringResource(R.string.float_ball_suggestion_rule_group_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }
            ChoiceRow(
                title = stringResource(R.string.float_ball_suggestion_rule_action),
                subtitle = stringResource(R.string.float_ball_suggestion_rule_action_hint),
                options = actions.map { ruleActionLabel(it) },
                selectedIndex = actions.indexOf(action).coerceAtLeast(0),
                onSelect = { index -> action = actions[index] },
                shape = segmentedShape(0, 3),
            )
            LabeledTextField(
                value = testText,
                onValueChange = { testText = it },
                label = stringResource(R.string.float_ball_suggestion_rule_test),
                isError = false,
                singleLine = false,
                placeholder = stringResource(R.string.float_ball_suggestion_rule_test_hint),
                modifier = Modifier.fillMaxWidth(),
            )
            if (testText.isNotBlank()) {
                RuleTestResult(pattern = pattern.trim(), testText = testText)
            }
        }
    }
}

/** 测试预览：与运行时一致，只取第一个匹配的第 1 个捕获组（无捕获组取整段）。 */
@Composable
private fun RuleTestResult(pattern: String, testText: String) {
    val result = remember(pattern, testText) {
        if (pattern.isBlank()) return@remember null
        val regex = runCatching { Regex(pattern) }.getOrNull() ?: return@remember null
        val scan = testText.take(SuggestionRuleEngine.MaxScanChars)
        val match = runCatching { regex.find(scan) }.getOrNull() ?: return@remember null
        val value = if (match.groups.size > 1) match.groups[1]?.value else match.value
        value?.trim()?.takeIf { it.isNotEmpty() }
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        Text(
            text = if (result == null) {
                stringResource(R.string.float_ball_suggestion_rule_test_miss)
            } else {
                stringResource(R.string.float_ball_suggestion_rule_test_hit, result)
            },
            style = MaterialTheme.typography.bodySmall,
            color = if (result == null) MaterialTheme.colorScheme.onSurfaceVariant
            else MaterialTheme.colorScheme.primary,
        )
    }
}

/** 列表/编辑页共用的动作名。 */
@Composable
private fun ruleActionLabel(action: SuggestionRuleAction): String = stringResource(
    when (action) {
        SuggestionRuleAction.COPY -> R.string.float_ball_suggestion_rule_action_copy
        SuggestionRuleAction.OPEN -> R.string.float_ball_suggestion_rule_action_open
        SuggestionRuleAction.SHARE -> R.string.float_ball_suggestion_rule_action_share
    },
)
