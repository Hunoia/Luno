package hunoia.luno.recognition

import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import hunoia.luno.bridge.ui.showToast
import hunoia.luno.config.model.LinkMatchType
import hunoia.luno.config.model.LinkMissMode
import hunoia.luno.config.model.NotificationMode
import hunoia.luno.config.model.RecognitionBuiltin
import hunoia.luno.config.model.RecognitionDisplay
import hunoia.luno.config.model.RecognitionMatch
import hunoia.luno.config.model.RecognitionOutput
import hunoia.luno.config.model.RecognitionRule
import hunoia.luno.config.model.RecognitionSettings
import hunoia.luno.config.model.RecognitionSource
import hunoia.luno.config.model.removeRule
import hunoia.luno.config.model.upsertRule
import hunoia.luno.core.domain.StoredAction
import hunoia.luno.link.Links
import hunoia.luno.link.R
import hunoia.luno.runtime.recognition.RecognitionInput
import hunoia.luno.runtime.recognition.Recognizer
import hunoia.luno.ui.component.LunoFab
import hunoia.luno.ui.component.PressIconButton
import hunoia.luno.ui.component.feedback.EmptyState
import hunoia.luno.ui.component.row.LunoCardInput
import hunoia.luno.ui.component.row.LunoDropdownRow
import hunoia.luno.ui.component.row.LunoGroup
import hunoia.luno.ui.component.row.LunoGroupItem
import hunoia.luno.ui.component.row.LunoInputRow
import hunoia.luno.ui.component.row.LunoSettingsRow
import hunoia.luno.ui.component.row.LunoSwitchRow
import hunoia.luno.ui.component.row.segmentedShape
import hunoia.luno.ui.component.screen.LunoScreen
import hunoia.luno.ui.component.sheet.AppPickerRequest
import hunoia.luno.ui.component.sheet.AppPickerSlot
import hunoia.luno.ui.component.sheet.ConfirmDialog
import hunoia.luno.ui.facade.LocalFacades
import hunoia.luno.ui.theme.FloatingContentBottom
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * 识别规则编辑页：ruleId 为空 = 新增。
 *
 * 一页写清三段：**输入（来源）→ 匹配（正则 / 链接）→ 输出（内置 / 动作）**。
 * 校验只认识别器自己的 [RecognitionRule.isValid] 与正则可编译性，不另写一套判定。
 */
@Composable
fun RecognitionRuleEditScreen(
    ruleId: String,
    prefillPattern: String = "",
    onBack: () -> Unit,
    /** 应用选择器由 `:app` 注入，避免识别器页面依赖搜索 / 权限模块。 */
    appPicker: AppPickerSlot? = null,
    /**
     * 动作选择器：起选择页并返回本次挑选的 token（null = 起不来）。
     * 选中结果由调用方「监听 token 对应的事件」后回填 [onActionPicked]。
     */
    actionPicker: ((Context) -> String?)? = null,
    /**
     * 订阅「本次挑选结果」的 Composable 钩子：由 `:app` 用 `Events.SubscribeEvent`
     * 实现（`feature:link` 不直接依赖事件总线与选择页 Activity）。
     */
    subscribePickedAction: (@Composable (token: String, onPicked: (StoredAction) -> Unit) -> Unit)? = null,
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val facade = LocalFacades.current.recognitionSettings
    val addMode = ruleId.isBlank()
    var snapshot by remember(ruleId) { mutableStateOf<RecognitionRule?>(null) }
    var missing by remember(ruleId) { mutableStateOf(false) }
    /** 全局浏览器偏好：不传的话「自动」解析可能与实际分发不一致。 */
    var browserPackage by remember(ruleId) { mutableStateOf("") }
    LaunchedEffect(ruleId) {
        val settings = facade.actionSettings.first()
        browserPackage = settings.recognition.browserPackage
        val existing = settings.recognition.rules.firstOrNull { it.id == ruleId }
        if (!addMode && existing == null) {
            missing = true
        } else {
            snapshot = existing ?: RecognitionRule(
                match = RecognitionMatch.Link(
                    matchType = LinkMatchType.CONTAINS,
                    pattern = prefillPattern,
                ),
                output = RecognitionOutput.Builtin(RecognitionBuiltin.OpenLink),
            )
        }
    }

    val initial = snapshot
    if (missing) {
        LunoScreen(
            title = stringResource(R.string.recognition_rule_edit),
            onBack = onBack,
        ) {
            EmptyState(message = stringResource(R.string.recognition_rule_missing))
        }
        return
    }
    if (initial == null) return

    var enabled by remember(ruleId) { mutableStateOf(initial.enabled) }
    var name by remember(ruleId) { mutableStateOf(initial.name) }
    var sources by remember(ruleId) { mutableStateOf(initial.sources) }
    var match by remember(ruleId) { mutableStateOf(initial.match) }
    var output by remember(ruleId) { mutableStateOf(initial.output) }
    var display by remember(ruleId) { mutableStateOf(initial.display) }
    var testText by remember(ruleId) { mutableStateOf("") }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showDiscardConfirm by remember { mutableStateOf(false) }
    var showAppPicker by remember { mutableStateOf(false) }
    /** 本次「挑动作」的 token；非空时订阅事件回填 output。 */
    var pickedToken by remember(ruleId) { mutableStateOf<String?>(null) }

    val draft = initial.copy(
        enabled = enabled,
        name = name.trim(),
        sources = sources,
        match = match,
        output = output,
        display = display,
    )
    val regexError = when (val m = match) {
        is RecognitionMatch.Text -> m.pattern.isNotBlank() &&
            runCatching { Regex(m.pattern) }.isFailure
        is RecognitionMatch.Link -> m.matchType == LinkMatchType.REGEX &&
            m.pattern.isNotBlank() && runCatching { Regex(m.pattern) }.isFailure
    }
    val valid = !regexError && sources.isNotEmpty() && draft.isValid
    val dirty = draft != initial
    val guardedBack = { if (dirty) showDiscardConfirm = true else onBack() }

    fun save() {
        scope.launch {
            facade.updateRecognition { it.upsertRule(draft) }
            onBack()
        }
    }

    // 挑动作的结果回填：token 隔离本次挑选，取到即清空，避免后面同 token 事件重复生效。
    val token = pickedToken
    if (token != null && subscribePickedAction != null) {
        subscribePickedAction(token) { picked ->
            output = RecognitionOutput.Action(picked)
            pickedToken = null
        }
    }

    LunoScreen(
        title = stringResource(
            if (addMode) R.string.recognition_rule_add_title else R.string.recognition_rule_edit,
        ),
        onBack = guardedBack,
        actions = {
            if (!addMode) {
                PressIconButton(onClick = { showDeleteConfirm = true }) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = stringResource(hunoia.luno.design.R.string.delete),
                        tint = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        bottomContentPadding = FloatingContentBottom,
        floatingContent = { _ ->
            LunoFab(
                onClick = { if (valid) save() else showToast(R.string.recognition_rule_invalid) },
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
                    title = stringResource(R.string.recognition_rule_delete_title),
                    text = stringResource(R.string.recognition_rule_delete_desc),
                    onConfirm = {
                        showDeleteConfirm = false
                        scope.launch {
                            facade.updateRecognition { it.removeRule(ruleId) }
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
        sheets = {
            if (showAppPicker) {
                val picker = appPicker
                val linkMatch = match as? RecognitionMatch.Link
                if (picker == null || linkMatch == null) {
                    showAppPicker = false
                } else {
                    picker(
                        AppPickerRequest(
                            selectedPackageNames = listOf(linkMatch.targetPackage),
                            selectedUserId = null,
                            onDismiss = { showAppPicker = false },
                            onConfirm = { packages ->
                                match = linkMatch.copy(
                                    targetPackage = packages.firstOrNull().orEmpty(),
                                )
                                showAppPicker = false
                            },
                            onSelect = null,
                        ),
                    )
                }
            }
        },
    ) {
        LunoGroup {
            LunoSwitchRow(
                title = stringResource(R.string.recognition_rule_enabled),
                checked = enabled,
                onCheckedChange = { enabled = it },
                shape = segmentedShape(0, 2),
            )
            LunoInputRow(
                label = stringResource(R.string.recognition_rule_name),
                value = name,
                onValueChange = { name = it },
                placeholder = stringResource(R.string.recognition_rule_name_hint),
                shape = segmentedShape(1, 2),
            )
        }
        // ---- 输入：来源多选 ----
        LunoGroup(
            title = stringResource(R.string.recognition_rule_input_group),
            subtitle = stringResource(R.string.recognition_rule_input_hint),
        ) {
            LunoGroupItem(shape = segmentedShape(0, 1)) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    val entries = RecognitionSource.entries
                    entries.chunked(3).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            row.forEach { source ->
                                FilterChip(
                                    selected = source in sources,
                                    onClick = {
                                        sources = if (source in sources) sources - source
                                        else sources + source
                                    },
                                    label = { Text(stringResource(source.labelRes())) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor =
                                        MaterialTheme.colorScheme.secondaryContainer,
                                    ),
                                )
                            }
                        }
                    }
                    // 快捷：按规则类型一键取默认（文本规则不含外部 Intent、链接规则不含通知），
                    // 或者全选。手点五个 chip 的体验没必要保留。
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        val defaults = when (match) {
                            is RecognitionMatch.Link -> RecognitionSource.LinkDefault
                            is RecognitionMatch.Text -> RecognitionSource.TextDefault
                        }
                        TextAction(
                            text = stringResource(R.string.recognition_source_defaults),
                            enabled = sources != defaults,
                            onClick = { sources = defaults },
                        )
                        TextAction(
                            text = stringResource(R.string.recognition_source_all),
                            enabled = sources != RecognitionSource.Default,
                            onClick = { sources = RecognitionSource.Default },
                        )
                    }
                }
            }
        }
        // ---- 匹配 ----
        LunoGroup(title = stringResource(R.string.recognition_rule_match_group)) {
            val isLink = match is RecognitionMatch.Link
            // 行数 = 类型下拉 + 分支行（链接分支 4 行，文本分支 1 行）。
            val rows = if (isLink) 5 else 2
            LunoDropdownRow(
                title = stringResource(R.string.recognition_rule_match_kind),
                subtitle = stringResource(R.string.recognition_rule_match_kind_hint),
                options = listOf(
                    stringResource(R.string.recognition_rule_match_kind_text),
                    stringResource(R.string.recognition_rule_match_kind_link),
                ),
                selectedIndex = if (isLink) 1 else 0,
                onSelect = { index ->
                    match = when {
                        index == 1 && !isLink -> RecognitionMatch.Link()
                        index == 0 && isLink -> RecognitionMatch.Text()
                        else -> match
                    }
                },
                shape = segmentedShape(0, rows),
            )
            when (val m = match) {
                is RecognitionMatch.Text -> LunoInputRow(
                    label = stringResource(R.string.recognition_rule_pattern),
                    value = m.pattern,
                    onValueChange = { match = m.copy(pattern = it) },
                    placeholder = stringResource(R.string.recognition_rule_pattern_hint),
                    subtitle = if (regexError) {
                        stringResource(R.string.recognition_rule_regex_invalid)
                    } else {
                        ""
                    },
                    isError = m.pattern.isBlank() || regexError,
                    shape = segmentedShape(1, rows),
                )
                is RecognitionMatch.Link -> {
                    val matchOptions = listOf(
                        LinkMatchType.CONTAINS to stringResource(R.string.recognition_match_contains),
                        LinkMatchType.DOMAIN to stringResource(R.string.recognition_match_domain),
                        LinkMatchType.REGEX to stringResource(R.string.recognition_match_regex),
                        LinkMatchType.EXACT to stringResource(R.string.recognition_match_exact),
                    )
                    LunoDropdownRow(
                        title = stringResource(R.string.recognition_rule_match_type),
                        subtitle = stringResource(R.string.recognition_rule_match_type_hint),
                        options = matchOptions.map { it.second },
                        selectedIndex = matchOptions.indexOfFirst { it.first == m.matchType }
                            .coerceAtLeast(0),
                        onSelect = { index -> match = m.copy(matchType = matchOptions[index].first) },
                        shape = segmentedShape(1, rows),
                    )
                    LunoInputRow(
                        label = stringResource(R.string.recognition_rule_pattern),
                        value = m.pattern,
                        onValueChange = { match = m.copy(pattern = it) },
                        placeholder = stringResource(R.string.recognition_rule_link_pattern_hint),
                        subtitle = if (regexError) {
                            stringResource(R.string.recognition_rule_regex_invalid)
                        } else {
                            ""
                        },
                        isError = regexError,
                        shape = segmentedShape(2, rows),
                    )
                    LunoSwitchRow(
                        title = stringResource(R.string.recognition_rule_bare_domains),
                        subtitle = stringResource(R.string.recognition_rule_bare_domains_hint),
                        checked = m.allowBareDomains,
                        onCheckedChange = { match = m.copy(allowBareDomains = it) },
                        shape = segmentedShape(3, rows),
                    )
                    LunoCardInput(
                        value = m.effectiveExcludes.joinToString("\n"),
                        onValueChange = { text ->
                            match = m.copy(
                                excludes = text.split('\n', ',', '，', ' ')
                                    .map { it.trim() }
                                    .filter { it.isNotEmpty() },
                            )
                        },
                        label = stringResource(R.string.recognition_rule_exclude),
                        placeholder = stringResource(R.string.recognition_rule_exclude_hint),
                        singleLine = false,
                        minLines = 3,
                        shape = segmentedShape(4, rows),
                    )
                }
            }
        }
        // ---- 输出 ----
        LunoGroup(title = stringResource(R.string.recognition_rule_output_group)) {
            // 通知是「命中的事」发生在后台，没有「先显示再点」这一档：隐藏显示档那一行。
            val showDisplay = output !is RecognitionOutput.Notify
            val extraRows = when (output) {
                is RecognitionOutput.Action -> 2
                is RecognitionOutput.Notify -> 2
                is RecognitionOutput.Builtin -> 1
            }
            val rows = extraRows + 1 + (if (showDisplay) 1 else 0)
            val outputOptions = listOf(
                R.string.recognition_output_copy,
                R.string.recognition_output_copy_and_clear,
                R.string.recognition_output_open_link,
                R.string.recognition_output_share,
                R.string.recognition_output_action,
                R.string.recognition_output_notify,
            )
            LunoDropdownRow(
                title = stringResource(R.string.recognition_rule_output_kind),
                options = outputOptions.map { stringResource(it) },
                selectedIndex = when (val o = output) {
                    is RecognitionOutput.Builtin -> when (o.kind) {
                        RecognitionBuiltin.Copy -> 0
                        RecognitionBuiltin.CopyAndClear -> 1
                        RecognitionBuiltin.OpenLink -> 2
                        RecognitionBuiltin.Share -> 3
                    }
                    is RecognitionOutput.Action -> 4
                    is RecognitionOutput.Notify -> 5
                },
                onSelect = { index ->
                    output = when (index) {
                        0 -> RecognitionOutput.Builtin(RecognitionBuiltin.Copy)
                        1 -> RecognitionOutput.Builtin(RecognitionBuiltin.CopyAndClear)
                        2 -> RecognitionOutput.Builtin(RecognitionBuiltin.OpenLink)
                        3 -> RecognitionOutput.Builtin(RecognitionBuiltin.Share)
                        4 -> (output as? RecognitionOutput.Action)
                            ?: RecognitionOutput.Action(StoredAction(typeId = ""))
                        else -> (output as? RecognitionOutput.Notify)
                            ?: RecognitionOutput.Notify()
                    }
                },
                shape = segmentedShape(0, rows),
            )
            when (val o = output) {
                is RecognitionOutput.Builtin -> RecognitionHintRow(
                    text = stringResource(o.kind.hintRes()),
                    shape = segmentedShape(1, rows),
                )
                is RecognitionOutput.Notify -> {
                    // 通知方式：跟随全局（null）或逐规则覆盖。默认跟随通用通知设置。
                    val modeOptions = listOf(
                        null,
                        NotificationMode.STANDARD,
                        NotificationMode.LIVE,
                        NotificationMode.ISLAND,
                    )
                    LunoDropdownRow(
                        title = stringResource(R.string.recognition_notify_mode),
                        subtitle = stringResource(R.string.recognition_notify_mode_hint),
                        options = listOf(
                            stringResource(R.string.recognition_notify_mode_follow),
                            stringResource(R.string.recognition_notify_mode_standard),
                            stringResource(R.string.recognition_notify_mode_live),
                            stringResource(R.string.recognition_notify_mode_island),
                        ),
                        selectedIndex = modeOptions.indexOf(o.spec.mode).coerceAtLeast(0),
                        onSelect = { index ->
                            output = RecognitionOutput.Notify(
                                o.spec.copy(mode = modeOptions.getOrNull(index)),
                            )
                        },
                        shape = segmentedShape(1, rows),
                    )
                    RecognitionHintRow(
                        text = stringResource(R.string.recognition_notify_hint),
                        shape = segmentedShape(2, rows),
                    )
                }
                is RecognitionOutput.Action -> {
                    val actionLabel = if (o.action.typeId.isBlank()) {
                        stringResource(R.string.recognition_rule_action_none)
                    } else {
                        // 用动作库的显示名（含库引用 / 面板名解析），不露 typeId 给用户看。
                        hunoia.luno.ui.component.actionText(o.action)
                    }
                    LunoSettingsRow(
                        title = stringResource(R.string.recognition_rule_action_pick),
                        subtitle = actionLabel,
                        shape = segmentedShape(1, rows),
                        enabled = actionPicker != null,
                        onClick = {
                            val token = actionPicker?.invoke(context)
                            if (token != null) pickedToken = token
                        },
                    )
                    RecognitionHintRow(
                        text = stringResource(R.string.recognition_output_action_hint),
                        shape = segmentedShape(2, rows),
                    )
                }
            }
            // 呈现档：规则属性，不是输出属性。同一种输出两种呈现都可能有人要，
            // 所以放在输出之后、单独一行，而不是塞进输出下拉里。
            if (showDisplay) {
                LunoDropdownRow(
                    title = stringResource(R.string.recognition_rule_display),
                    subtitle = stringResource(R.string.recognition_rule_display_hint),
                    options = listOf(
                        stringResource(R.string.recognition_display_capsule),
                        stringResource(R.string.recognition_display_direct),
                    ),
                    selectedIndex = if (display == RecognitionDisplay.Direct) 1 else 0,
                    onSelect = { index ->
                        display = if (index == 1) RecognitionDisplay.Direct
                        else RecognitionDisplay.Capsule
                    },
                    shape = segmentedShape(extraRows + 1, rows),
                )
            }
        }
        // ---- 链接目标 / 改写 ----
        val linkMatch = match as? RecognitionMatch.Link
        if (linkMatch != null) {
            LunoGroup(title = stringResource(R.string.recognition_rule_target_group)) {
                val target = remember(linkMatch, browserPackage) {
                    Links.resolvedTargetOf(context, linkMatch, browserPackage)
                }
                val appLabel = target?.label
                if (linkMatch.viaBrowser) {
                    // 浏览器目标只列真正的浏览器：用全应用选择器可能选到 B 站这类非浏览器目标。
                    val browsers = remember { Links.browsers(context) }
                    val options = listOf(stringResource(R.string.recognition_rule_browser_auto)) +
                        browsers.map { it.label }
                    val selectedIndex = browsers
                        .indexOfFirst { it.packageName == linkMatch.targetPackage } + 1
                    LunoDropdownRow(
                        title = stringResource(R.string.recognition_rule_browser_target),
                        subtitle = if (linkMatch.targetPackage.isBlank()) {
                            stringResource(R.string.recognition_rule_browser_auto)
                        } else if (appLabel != null && appLabel != linkMatch.targetPackage) {
                            "$appLabel (${linkMatch.targetPackage})"
                        } else {
                            linkMatch.targetPackage
                        },
                        options = options,
                        selectedIndex = selectedIndex.coerceAtLeast(0),
                        onSelect = { index ->
                            match = linkMatch.copy(
                                targetPackage = if (index == 0) "" else browsers[index - 1].packageName,
                            )
                        },
                        shape = segmentedShape(0, 1),
                    )
                } else {
                    LunoSettingsRow(
                        title = stringResource(R.string.recognition_rule_target_app),
                        subtitle = when {
                            linkMatch.targetPackage.isBlank() ->
                                stringResource(hunoia.luno.actions.R.string.action_param_unselected)
                            appLabel != null && appLabel != linkMatch.targetPackage ->
                                "$appLabel (${linkMatch.targetPackage})"
                            else -> linkMatch.targetPackage
                        },
                        subtitleColor = if (linkMatch.targetPackage.isBlank()) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        shape = segmentedShape(0, 2),
                        onClick = { showAppPicker = true },
                    )
                    LunoInputRow(
                        label = stringResource(R.string.recognition_rule_target_activity),
                        value = linkMatch.targetActivity,
                        onValueChange = { match = linkMatch.copy(targetActivity = it) },
                        placeholder = stringResource(R.string.recognition_rule_target_activity_hint),
                        shape = segmentedShape(1, 2),
                    )
                }
            }
            LunoGroup(title = stringResource(R.string.recognition_rule_advanced_group)) {
                LunoSwitchRow(
                    title = stringResource(R.string.recognition_rule_via_browser),
                    subtitle = stringResource(R.string.recognition_rule_via_browser_hint),
                    checked = linkMatch.viaBrowser,
                    onCheckedChange = { match = linkMatch.copy(viaBrowser = it) },
                    shape = segmentedShape(0, 5),
                )
                LunoCardInput(
                    value = linkMatch.template,
                    onValueChange = { match = linkMatch.copy(template = it) },
                    label = stringResource(R.string.recognition_rule_template),
                    placeholder = stringResource(R.string.recognition_rule_template_hint),
                    shape = segmentedShape(1, 5),
                )
                LunoSwitchRow(
                    title = stringResource(R.string.recognition_rule_resolve),
                    subtitle = stringResource(R.string.recognition_rule_resolve_hint),
                    checked = linkMatch.resolveShortLink,
                    onCheckedChange = { match = linkMatch.copy(resolveShortLink = it) },
                    shape = segmentedShape(2, 5),
                )
                LunoSwitchRow(
                    title = stringResource(R.string.recognition_rule_mini_window),
                    checked = linkMatch.miniWindow,
                    onCheckedChange = { match = linkMatch.copy(miniWindow = it) },
                    shape = segmentedShape(3, 5),
                )
                val missOptions = listOf(
                    null to stringResource(R.string.recognition_rule_miss_global),
                    LinkMissMode.SHARE to stringResource(R.string.recognition_rule_miss_share_short),
                    LinkMissMode.BROWSER to stringResource(
                        R.string.recognition_rule_miss_browser_short,
                    ),
                    LinkMissMode.PICKER to stringResource(
                        R.string.recognition_rule_miss_picker_short,
                    ),
                )
                LunoDropdownRow(
                    title = stringResource(R.string.recognition_rule_miss),
                    subtitle = stringResource(R.string.recognition_rule_miss_hint),
                    options = missOptions.map { it.second },
                    selectedIndex = missOptions.indexOfFirst { it.first == linkMatch.missAction }
                        .coerceAtLeast(0),
                    onSelect = { index -> match = linkMatch.copy(missAction = missOptions[index].first) },
                    shape = segmentedShape(4, 5),
                )
            }
        }
        // ---- 测试匹配：用识别器本体跑，不另写测试逻辑 ----
        LunoGroup(
            title = stringResource(R.string.recognition_rule_test),
            subtitle = stringResource(R.string.recognition_rule_test_hint),
        ) {
            val testRows = if (testText.isBlank()) 1 else 2
            LunoInputRow(
                label = stringResource(R.string.recognition_rule_test),
                value = testText,
                onValueChange = { testText = it },
                shape = segmentedShape(0, testRows),
            )
            val hit = remember(testText, draft) {
                if (testText.isBlank() || !draft.isValid) {
                    null
                } else {
                    val source = draft.sources.firstOrNull() ?: RecognitionSource.Clipboard
                    Recognizer
                        .compile(RecognitionSettings(rules = listOf(draft)))
                        .match(RecognitionInput(source, testText))
                        .firstOrNull()
                }
            }
            if (testText.isNotBlank()) {
                RecognitionHintRow(
                    text = if (hit == null) {
                        stringResource(R.string.recognition_rule_test_miss)
                    } else {
                        stringResource(
                            R.string.recognition_rule_test_hit,
                            hit.ruleName.ifBlank { draft.match.pattern },
                            hit.value,
                        )
                    },
                    shape = segmentedShape(1, testRows),
                    highlight = hit != null,
                )
            }
        }
    }
}

/** 组内的纯文字动作（快捷选择）：可点即用，不可点时降级为说明文字。 */
@Composable
private fun TextAction(text: String, enabled: Boolean, onClick: () -> Unit) {
    val color = if (enabled) MaterialTheme.colorScheme.primary
    else MaterialTheme.colorScheme.onSurfaceVariant
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = color,
        modifier = Modifier
            .let { if (enabled) it.clickable(onClick = onClick) else it }
            .padding(vertical = 4.dp),
    )
}

@Composable
private fun RecognitionHintRow(
    text: String,
    shape: androidx.compose.ui.graphics.Shape,
    highlight: Boolean = false,
) {
    LunoGroupItem(shape = shape) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = if (highlight) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
        )
    }
}

private fun RecognitionBuiltin.hintRes(): Int = when (this) {
    RecognitionBuiltin.Copy -> R.string.recognition_output_copy_hint
    RecognitionBuiltin.CopyAndClear -> R.string.recognition_output_copy_and_clear_hint
    RecognitionBuiltin.OpenLink -> R.string.recognition_output_open_link_hint
    RecognitionBuiltin.Share -> R.string.recognition_output_share
}
