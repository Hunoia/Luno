package hunoia.luno.ui.automation

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
import hunoia.luno.config.model.RuleEffect
import hunoia.luno.config.model.RuleEffectType
import hunoia.luno.config.model.RuleScope
import hunoia.luno.config.model.WhenGroup
import hunoia.luno.runtime.condition.hasLeaf
import hunoia.luno.ui.automation.components.RuleScopeSheet
import hunoia.luno.ui.component.MyColumn
import hunoia.luno.ui.component.SegmentedSettingsRow
import hunoia.luno.ui.component.TopBar
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
    var showEntrySheet by remember { mutableStateOf(false) }
    var showTypePicker by remember { mutableStateOf(false) }

    LaunchedEffect(ruleId, loadedRules) {
        if (draft != null) return@LaunchedEffect
        val rules = loadedRules ?: return@LaunchedEffect
        draft = rules.find { it.id == ruleId } ?: AutomationRule(
            id = if (ruleId == NEW_AUTOMATION_RULE_ID) SystemClock.uptimeMillis().toString() else ruleId,
            condition = WhenGroup(),
            effect = RuleEffect(type = RuleEffectType.HIDE_BUTTONS),
        )
    }

    val rule = draft ?: return
    val cooldownText = remember(rule.id) { mutableStateOf(rule.cooldownMs.toString()) }

    fun save() {
        val effective = rule.copy(cooldownMs = parseCooldown(cooldownText.value))
        scope.launch {
            try {
                ConfigProvider.updateAutomationRules { rules ->
                    if (rules.any { it.id == effective.id }) {
                        rules.map { if (it.id == effective.id) effective else it }
                    } else {
                        rules + effective
                    }
                }
                onBack()
            } catch (e: Exception) {
                Log.e("AutomationEdit", "save failed", e)
            }
        }
    }

    val cooldownInvalid = isInvalidCooldown(cooldownText.value)
    val saveEnabled = rule.condition.hasLeaf() &&
        (rule.effect.type != RuleEffectType.RUN_ACTION || rule.effect.entryId.isNotBlank()) &&
        !cooldownInvalid

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
                onAdd = { showTypePicker = true },
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
                    title = stringResource(R.string.automation_entry),
                    subtitle = entryName(library, rule.effect.entryId),
                    onClick = { showEntrySheet = true },
                )
                if (rule.effect.entryId.isBlank()) {
                    Text(
                        text = stringResource(R.string.automation_entry_required),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                OutlinedTextField(
                    value = cooldownText.value,
                    onValueChange = { cooldownText.value = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.automation_cooldown)) },
                    placeholder = { Text(stringResource(R.string.automation_cooldown_hint)) },
                    singleLine = true,
                    isError = cooldownInvalid,
                    supportingText = {
                        if (cooldownInvalid) {
                            Text(stringResource(R.string.automation_cooldown_invalid))
                        }
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    shape = LargeShape,
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

    if (showEntrySheet) {
        EntryPickerSheet(
            entries = library?.entries ?: emptyList(),
            selectedEntryId = rule.effect.entryId,
            onDismissRequest = { showEntrySheet = false },
            onPick = { entryId ->
                draft = rule.copy(effect = rule.effect.copy(entryId = entryId))
                showEntrySheet = false
            },
        )
    }

    if (showTypePicker) {
        ConditionTypePickerSheet(
            onDismissRequest = { showTypePicker = false },
            onPick = { type ->
                draft = rule.copy(
                    condition = rule.condition.copy(
                        items = rule.condition.items + defaultConditionOf(type),
                    ),
                )
                showTypePicker = false
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ConditionGroupEditor(
    group: WhenGroup,
    onChange: (WhenGroup) -> Unit,
    onAdd: () -> Unit,
) {
    val context = LocalContext.current
    SegmentedSettingsRow(
        title = stringResource(R.string.automation_condition),
        subtitle = conditionSummary(context, group),
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
        TextButton(onClick = onAdd) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = null,
                modifier = Modifier.padding(end = ShapeExtraSmall),
            )
            Text(stringResource(R.string.condition_add))
        }
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
private fun entryName(library: NewActionLibrarySettings?, entryId: String): String {
    if (entryId.isBlank()) return stringResource(R.string.automation_entry_empty)
    return library?.entries?.find { it.id == entryId }?.name?.ifBlank { entryId } ?: entryId
}

private fun isInvalidCooldown(text: String): Boolean {
    val value = text.trim().toLongOrNull() ?: return true
    return value < 0 || value > 3600
}

private fun parseCooldown(text: String): Long =
    text.trim().toLongOrNull()?.coerceIn(0, 3600) ?: 0L
