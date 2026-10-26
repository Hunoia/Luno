package hunoia.luno.ui.settings.link

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import hunoia.luno.R
import hunoia.luno.bridge.ui.showToast
import hunoia.luno.config.model.LinkDispatchRule
import hunoia.luno.config.model.LinkMatchType
import hunoia.luno.config.model.remove
import hunoia.luno.config.model.upsert
import hunoia.luno.domain.tune.TuneConfig
import hunoia.luno.ui.actionlibrary.TemplateTextField
import hunoia.luno.ui.component.LunoFab
import hunoia.luno.ui.component.feedback.EmptyState
import hunoia.luno.ui.component.row.LunoGroup
import hunoia.luno.ui.component.row.LunoSettingsRow
import hunoia.luno.ui.component.row.LunoSwitchRow
import hunoia.luno.ui.component.row.segmentedShape
import hunoia.luno.ui.component.screen.LunoScreen
import hunoia.luno.ui.component.sheet.AppPickerSheet
import hunoia.luno.ui.component.sheet.ConfirmDialog
import hunoia.luno.ui.theme.FloatingContentBottom
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * 链接分发规则编辑页：ruleId 为空 = 新增。
 * 校验只看「匹配模式非空模式串」与「选了目标应用」；模板/Activity 都是可选项。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LinkDispatchEditScreen(
    ruleId: String,
    onBack: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val addMode = ruleId.isBlank()
    var snapshot by remember(ruleId) { mutableStateOf<LinkDispatchRule?>(null) }
    var missing by remember(ruleId) { mutableStateOf(false) }
    LaunchedEffect(ruleId) {
        val settings = TuneConfig.action.first()
        val existing = settings.linkDispatch.rules.firstOrNull { it.id == ruleId }
        if (!addMode && existing == null) {
            missing = true
        } else {
            snapshot = existing ?: LinkDispatchRule()
        }
    }

    val initial = snapshot
    if (missing) {
        LunoScreen(title = stringResource(R.string.link_dispatch_rule_edit), onBack = onBack) {
            EmptyState(message = stringResource(R.string.link_dispatch_rule_missing))
        }
        return
    }
    if (initial == null) return

    var name by remember(ruleId) { mutableStateOf(initial.name) }
    var enabled by remember(ruleId) { mutableStateOf(initial.enabled) }
    var matchType by remember(ruleId) { mutableStateOf(initial.matchType) }
    var pattern by remember(ruleId) { mutableStateOf(initial.pattern) }
    var targetPackage by remember(ruleId) { mutableStateOf(initial.targetPackage) }
    var targetActivity by remember(ruleId) { mutableStateOf(initial.targetActivity) }
    var template by remember(ruleId) { mutableStateOf(initial.template) }
    var miniWindow by remember(ruleId) { mutableStateOf(initial.miniWindow) }
    var viaBrowser by remember(ruleId) { mutableStateOf(initial.viaBrowser) }
    var resolveShortLink by remember(ruleId) { mutableStateOf(initial.resolveShortLink) }
    var showAppPicker by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showDiscardConfirm by remember { mutableStateOf(false) }

    val appLabel by produceState(initialValue = null as String?, targetPackage) {
        value = targetPackage.takeIf { it.isNotBlank() }?.let { pkg ->
            runCatching {
                context.packageManager.getApplicationLabel(
                    context.packageManager.getApplicationInfo(pkg, 0),
                ).toString()
            }.getOrNull()
        }
    }
    val valid = pattern.isNotBlank() && (viaBrowser || targetPackage.isNotBlank())
    val dirty = name != initial.name || enabled != initial.enabled ||
        matchType != initial.matchType || pattern != initial.pattern ||
        targetPackage != initial.targetPackage || targetActivity != initial.targetActivity ||
        template != initial.template || miniWindow != initial.miniWindow ||
        viaBrowser != initial.viaBrowser || resolveShortLink != initial.resolveShortLink
    val guardedBack = { if (dirty) showDiscardConfirm = true else onBack() }

    fun save() {
        val rule = initial.copy(
            name = name.trim(),
            enabled = enabled,
            matchType = matchType,
            pattern = pattern.trim(),
            targetPackage = targetPackage.trim(),
            targetActivity = targetActivity.trim(),
            template = template.trim(),
            miniWindow = miniWindow,
            viaBrowser = viaBrowser,
            resolveShortLink = resolveShortLink,
        )
        scope.launch {
            TuneConfig.updateAction { it.copy(linkDispatch = it.linkDispatch.upsert(rule)) }
            onBack()
        }
    }

    LunoScreen(
        title = stringResource(
            if (addMode) R.string.link_dispatch_rule_add else R.string.link_dispatch_rule_edit,
        ),
        onBack = guardedBack,
        bottomContentPadding = FloatingContentBottom,
        actions = {
            if (!addMode) {
                IconButton(onClick = { showDeleteConfirm = true }) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = stringResource(R.string.delete),
                        tint = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        floatingContent = { _ ->
            LunoFab(
                onClick = {
                    if (valid) save() else showToast(R.string.link_dispatch_rule_invalid)
                },
                icon = Icons.Default.Save,
                contentDescription = stringResource(R.string.save),
                containerColor = if (valid) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surfaceContainerHighest,
            )
        },
        dialogs = {
            if (showDeleteConfirm) {
                ConfirmDialog(
                    onDismissRequest = { showDeleteConfirm = false },
                    title = stringResource(R.string.link_dispatch_rule_delete_title),
                    text = stringResource(R.string.link_dispatch_rule_delete_desc),
                    onConfirm = {
                        showDeleteConfirm = false
                        scope.launch {
                            TuneConfig.updateAction {
                                it.copy(linkDispatch = it.linkDispatch.remove(ruleId))
                            }
                            onBack()
                        }
                    },
                )
            }
            if (showDiscardConfirm) {
                ConfirmDialog(
                    onDismissRequest = { showDiscardConfirm = false },
                    title = stringResource(R.string.action_library_discard_title),
                    text = stringResource(R.string.action_library_discard_desc),
                    onConfirm = {
                        showDiscardConfirm = false
                        onBack()
                    },
                )
            }
        },
        sheets = {
            if (showAppPicker) {
                AppPickerSheet(
                    onDismissRequest = { showAppPicker = false },
                    selectedPackageNames = listOf(targetPackage),
                    confirmOnSelect = true,
                    onConfirm = { packages ->
                        targetPackage = packages.firstOrNull().orEmpty()
                        showAppPicker = false
                    },
                )
            }
        },
    ) {
        LunoGroup {
            LunoSwitchRow(
                title = stringResource(R.string.link_dispatch_rule_enabled),
                checked = enabled,
                onCheckedChange = { enabled = it },
                shape = segmentedShape(0, 2),
            )
            TemplateTextField(
                value = name,
                onValueChange = { name = it },
                label = stringResource(R.string.link_dispatch_rule_name),
                isError = false,
                singleLine = true,
                allowTemplates = false,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        LunoGroup(title = stringResource(R.string.link_dispatch_rule_match_group)) {
            MatchTypeRow(
                matchType = matchType,
                onSelect = { matchType = it },
            )
            TemplateTextField(
                value = pattern,
                onValueChange = { pattern = it },
                label = stringResource(R.string.link_dispatch_rule_pattern),
                isError = pattern.isBlank(),
                singleLine = true,
                allowTemplates = false,
                placeholder = stringResource(R.string.link_dispatch_rule_pattern_hint),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        LunoGroup(title = stringResource(R.string.link_dispatch_rule_target_group)) {
            LunoSwitchRow(
                title = stringResource(R.string.link_dispatch_rule_via_browser),
                subtitle = stringResource(R.string.link_dispatch_rule_via_browser_hint),
                checked = viaBrowser,
                onCheckedChange = { viaBrowser = it },
                shape = if (viaBrowser) segmentedShape(0, 3) else segmentedShape(0, 1),
            )
            if (viaBrowser) {
                LunoSettingsRow(
                    title = stringResource(R.string.link_dispatch_rule_browser_target),
                    subtitle = when {
                        targetPackage.isBlank() -> stringResource(R.string.link_dispatch_rule_browser_auto)
                        appLabel != null && appLabel != targetPackage -> "$appLabel ($targetPackage)"
                        else -> targetPackage
                    },
                    shape = segmentedShape(1, 3),
                    onClick = { showAppPicker = true },
                )
                LunoSettingsRow(
                    title = stringResource(R.string.link_dispatch_rule_browser_clear),
                    subtitle = stringResource(R.string.link_dispatch_rule_browser_clear_hint),
                    shape = segmentedShape(2, 3),
                    enabled = targetPackage.isNotBlank(),
                    onClick = { targetPackage = "" },
                )
            } else {
                LunoSwitchRow(
                    title = stringResource(R.string.link_dispatch_rule_resolve),
                    subtitle = stringResource(R.string.link_dispatch_rule_resolve_hint),
                    checked = resolveShortLink,
                    onCheckedChange = { resolveShortLink = it },
                    shape = segmentedShape(1, 3),
                )
                LunoSettingsRow(
                    title = stringResource(R.string.link_dispatch_rule_target_app),
                    subtitle = when {
                        targetPackage.isBlank() -> stringResource(R.string.action_param_unselected)
                        appLabel != null && appLabel != targetPackage -> "$appLabel ($targetPackage)"
                        else -> targetPackage
                    },
                    subtitleColor = if (targetPackage.isBlank()) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    shape = segmentedShape(2, 3),
                    onClick = { showAppPicker = true },
                )
                TemplateTextField(
                    value = targetActivity,
                    onValueChange = { targetActivity = it },
                    label = stringResource(R.string.link_dispatch_rule_target_activity),
                    isError = false,
                    singleLine = true,
                    allowTemplates = false,
                    placeholder = stringResource(R.string.link_dispatch_rule_target_activity_hint),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        LunoGroup(title = stringResource(R.string.link_dispatch_rule_advanced_group)) {
            TemplateTextField(
                value = template,
                onValueChange = { template = it },
                label = stringResource(R.string.link_dispatch_rule_template),
                isError = false,
                singleLine = true,
                allowTemplates = false,
                placeholder = stringResource(R.string.link_dispatch_rule_template_hint),
                modifier = Modifier.fillMaxWidth(),
            )
            LunoSwitchRow(
                title = stringResource(R.string.link_dispatch_rule_mini_window),
                checked = miniWindow,
                onCheckedChange = { miniWindow = it },
                shape = segmentedShape(1, 2),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MatchTypeRow(
    matchType: LinkMatchType,
    onSelect: (LinkMatchType) -> Unit,
) {
    val options = listOf(
        LinkMatchType.CONTAINS to stringResource(R.string.link_dispatch_match_contains),
        LinkMatchType.REGEX to stringResource(R.string.link_dispatch_match_regex),
        LinkMatchType.EXACT to stringResource(R.string.link_dispatch_match_exact),
    )
    LunoSettingsRow(
        title = stringResource(R.string.link_dispatch_rule_match_type),
        shape = segmentedShape(0, 1),
        trailing = {
            SingleChoiceSegmentedButtonRow {
                options.forEachIndexed { index, (type, label) ->
                    SegmentedButton(
                        selected = matchType == type,
                        onClick = { onSelect(type) },
                        shape = SegmentedButtonDefaults.itemShape(
                            count = options.size,
                            index = index,
                        ),
                    ) {
                        Text(text = label, maxLines = 1)
                    }
                }
            }
        },
    )
}
