package hunoia.luno.ui.settings.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import hunoia.luno.R
import hunoia.luno.domain.search.SearchCenter
import hunoia.luno.config.model.BuiltinSearchEngineIds
import hunoia.luno.config.model.BuiltinSearchEngines
import hunoia.luno.config.model.DefaultSourceTriggers
import hunoia.luno.config.model.QuickSearchSettings
import hunoia.luno.config.model.SRC_ACTION
import hunoia.luno.config.model.SRC_APP
import hunoia.luno.config.model.SRC_CALL
import hunoia.luno.config.model.SRC_CLIP
import hunoia.luno.config.model.SRC_CALENDAR
import hunoia.luno.config.model.SRC_CONTACT
import hunoia.luno.config.model.SRC_FILE
import hunoia.luno.config.model.SRC_MUSIC
import hunoia.luno.config.model.SRC_SETTINGS
import hunoia.luno.config.model.SRC_SHORTCUT
import hunoia.luno.config.model.SRC_SMS
import hunoia.luno.config.model.SRC_TOGGLE
import hunoia.luno.config.model.SearchEngine
import hunoia.luno.config.model.effectiveEngines
import hunoia.luno.config.model.effectiveTrigger
import kotlinx.coroutines.launch
import hunoia.luno.ui.component.row.LunoGroup
import hunoia.luno.ui.component.row.LunoSelectableRow
import hunoia.luno.ui.component.row.LunoSettingsRow
import hunoia.luno.ui.component.row.LunoSwitchRow
import hunoia.luno.ui.component.row.segmentedShape
import hunoia.luno.ui.component.row.selectedSegmentedShape
import hunoia.luno.ui.component.screen.LunoScreen
import hunoia.luno.ui.component.sheet.ConfirmDialog
import hunoia.luno.ui.component.sheet.TextInputDialog
import hunoia.luno.ui.component.sheet.CustomDialog
import hunoia.luno.ui.component.feedback.LunoIconButton
import hunoia.luno.ui.theme.CardInnerSpacing
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchEngineSettingsScreen(onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val settings by SearchCenter.search.collectAsStateWithLifecycle(initialValue = QuickSearchSettings())
    var showAdd by remember { mutableStateOf(false) }
    var editTarget by remember { mutableStateOf<SearchEngine?>(null) }
    var deleteTarget by remember { mutableStateOf<SearchEngine?>(null) }
    // 来源标识编辑目标（源 key）：行点开 dialog，开关照常切换，两不耽误。
    var triggerTarget by remember { mutableStateOf<String?>(null) }
    val engines = settings.effectiveEngines()
    fun setDefault(id: String) {
        scope.launch { SearchCenter.updateSearch { it.copy(defaultEngineId = id) } }
    }
    LunoScreen(
        title = stringResource(R.string.search_engine_settings),
        onBack = onBack,
        dialogs = {
        if (showAdd || editTarget != null) {
            EngineEditDialog(
                initial = editTarget,
                // 引擎标识也要跟来源标识判重（防把引擎改成 sms 这类）。
                existingTriggers = settings.effectiveEngines()
                    .filter { it.id != editTarget?.id }
                    .map { it.trigger } +
                    DefaultSourceTriggers.keys.map { settings.effectiveTrigger(it) },
                onDismissRequest = { showAdd = false; editTarget = null },
                onConfirm = { name, template, trigger ->
                    scope.launch {
                        SearchCenter.updateSearch { current ->
                            val target = editTarget
                            if (target == null) {
                                current.copy(
                                    customEngines = current.customEngines +
                                        SearchEngine(name = name, urlTemplate = template, trigger = trigger),
                                )
                            } else {
                                current.copy(
                                    customEngines = current.customEngines.map {
                                        if (it.id == target.id) {
                                            it.copy(name = name, urlTemplate = template, trigger = trigger)
                                        } else {
                                            it
                                        }
                                    },
                                )
                            }
                        }
                    }
                    showAdd = false
                    editTarget = null
                },
            )
        }
        triggerTarget?.let { key ->
            SourceTriggerDialog(
                title = sourceTitle(key),
                current = settings.effectiveTrigger(key),
                // 跟引擎标识 + 其他来源标识判重（自己除外）。
                existingTriggers = settings.effectiveEngines().map { it.trigger } +
                    DefaultSourceTriggers.keys
                        .filter { it != key }
                        .map { settings.effectiveTrigger(it) },
                onDismissRequest = { triggerTarget = null },
                onConfirm = { v ->
                    scope.launch {
                        SearchCenter.updateSearch { current ->
                            current.copy(sourceTriggers = current.sourceTriggers + (key to v))
                        }
                    }
                    triggerTarget = null
                },
            )
        }
        deleteTarget?.let { target ->
            ConfirmDialog(
                title = stringResource(R.string.search_engine_delete_title),
                text = target.name,
                confirmLabel = stringResource(R.string.delete),
                destructive = true,
                onDismissRequest = { deleteTarget = null },
                onConfirm = {
                    scope.launch {
                        SearchCenter.updateSearch { current ->
                            current.copy(
                                customEngines = current.customEngines.filterNot { it.id == target.id },
                                defaultEngineId = if (current.defaultEngineId == target.id) {
                                    BuiltinSearchEngines.first().id
                                } else {
                                    current.defaultEngineId
                                },
                            )
                        }
                    }
                },
            )
        }
        },
    ) {
            LunoGroup {
                val engineFlags = engines.map { it.id == settings.defaultEngineId }
                engines.forEachIndexed { index, engine ->
                    val isDefault = engine.id == settings.defaultEngineId
                    val locked = engine.id in BuiltinSearchEngineIds
                    LunoSelectableRow(
                        title = engine.name,
                        subtitle = if (engine.trigger.isNotBlank()) "${engine.trigger} · ${engine.urlTemplate}" else engine.urlTemplate,
                        selected = isDefault,
                        onSelectedChange = { setDefault(engine.id) },
                        singleSelect = true,
                        shape = selectedSegmentedShape(index, engines.size, engineFlags),
                        trailing = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (!locked) {
                                    LunoIconButton(
                                        icon = Icons.Default.Edit,
                                        contentDescription = stringResource(R.string.edit),
                                        onClick = { editTarget = engine },
                                    )
                                    LunoIconButton(
                                        icon = Icons.Default.Delete,
                                        contentDescription = stringResource(R.string.delete),
                                        onClick = { deleteTarget = engine },
                                    )
                                }
                            }
                        },
                    )
                }
            }
            LunoGroup {
                LunoSettingsRow(
                    icon = Icons.Default.Add,
                    title = stringResource(R.string.search_engine_add),
                    onClick = { showAdd = true },
                )
            }
            // 内容源：P1 剪贴板，P2 联系人，P3 音乐/文件，日历/开关/短信/通话记录。
            // 剪贴板历史、最近文件复用剪贴板/文件开关，不另加。
            LunoGroup {
                LunoSwitchRow(
                    title = stringResource(R.string.quick_search_clipboard),
                    checked = settings.searchClipboard,
                    subtitle = settings.effectiveTrigger(SRC_CLIP)
                        .ifBlank { stringResource(R.string.quick_search_trigger_closed) },
                    onClick = { triggerTarget = SRC_CLIP },
                    onCheckedChange = { v ->
                        scope.launch {
                            SearchCenter.updateSearch { it.copy(searchClipboard = v) }
                        }
                    },
                )
                LunoSwitchRow(
                    title = stringResource(R.string.quick_search_contacts),
                    checked = settings.searchContacts,
                    subtitle = settings.effectiveTrigger(SRC_CONTACT)
                        .ifBlank { stringResource(R.string.quick_search_trigger_closed) },
                    onClick = { triggerTarget = SRC_CONTACT },
                    onCheckedChange = { v ->
                        scope.launch {
                            SearchCenter.updateSearch { it.copy(searchContacts = v) }
                        }
                    },
                )
                LunoSwitchRow(
                    title = stringResource(R.string.quick_search_music),
                    checked = settings.searchMusic,
                    subtitle = settings.effectiveTrigger(SRC_MUSIC)
                        .ifBlank { stringResource(R.string.quick_search_trigger_closed) },
                    onClick = { triggerTarget = SRC_MUSIC },
                    onCheckedChange = { v ->
                        scope.launch {
                            SearchCenter.updateSearch { it.copy(searchMusic = v) }
                        }
                    },
                )
                LunoSwitchRow(
                    title = stringResource(R.string.quick_search_files),
                    checked = settings.searchFiles,
                    subtitle = settings.effectiveTrigger(SRC_FILE)
                        .ifBlank { stringResource(R.string.quick_search_trigger_closed) },
                    onClick = { triggerTarget = SRC_FILE },
                    onCheckedChange = { v ->
                        scope.launch {
                            SearchCenter.updateSearch { it.copy(searchFiles = v) }
                        }
                    },
                )
                LunoSwitchRow(
                    title = stringResource(R.string.quick_search_calendar),
                    checked = settings.searchCalendar,
                    subtitle = settings.effectiveTrigger(SRC_CALENDAR)
                        .ifBlank { stringResource(R.string.quick_search_trigger_closed) },
                    onClick = { triggerTarget = SRC_CALENDAR },
                    onCheckedChange = { v ->
                        scope.launch {
                            SearchCenter.updateSearch { it.copy(searchCalendar = v) }
                        }
                    },
                )
                LunoSwitchRow(
                    title = stringResource(R.string.quick_search_toggles),
                    checked = settings.searchToggles,
                    subtitle = settings.effectiveTrigger(SRC_TOGGLE)
                        .ifBlank { stringResource(R.string.quick_search_trigger_closed) },
                    onClick = { triggerTarget = SRC_TOGGLE },
                    onCheckedChange = { v ->
                        scope.launch {
                            SearchCenter.updateSearch { it.copy(searchToggles = v) }
                        }
                    },
                )
                LunoSwitchRow(
                    title = stringResource(R.string.quick_search_sms),
                    checked = settings.searchSms,
                    subtitle = settings.effectiveTrigger(SRC_SMS)
                        .ifBlank { stringResource(R.string.quick_search_trigger_closed) },
                    onClick = { triggerTarget = SRC_SMS },
                    onCheckedChange = { v ->
                        scope.launch {
                            SearchCenter.updateSearch { it.copy(searchSms = v) }
                        }
                    },
                )
                LunoSwitchRow(
                    title = stringResource(R.string.quick_search_calls),
                    checked = settings.searchCalls,
                    subtitle = settings.effectiveTrigger(SRC_CALL)
                        .ifBlank { stringResource(R.string.quick_search_trigger_closed) },
                    onClick = { triggerTarget = SRC_CALL },
                    onCheckedChange = { v ->
                        scope.launch {
                            SearchCenter.updateSearch { it.copy(searchCalls = v) }
                        }
                    },
                )
            }
            // 无开关的来源（应用/快捷方式/动作/设置页常开）：只改标识，副标题显示当前值。
            LunoGroup {
                val sourceKeys = listOf(SRC_APP, SRC_SHORTCUT, SRC_ACTION, SRC_SETTINGS)
                sourceKeys.forEachIndexed { index, key ->
                    val trigger = settings.effectiveTrigger(key)
                    LunoSettingsRow(
                        title = sourceTitle(key),
                        subtitle = trigger.ifBlank { stringResource(R.string.quick_search_trigger_closed) },
                        onClick = { triggerTarget = key },
                        shape = segmentedShape(index, sourceKeys.size),
                    )
                }
            }
        }
}

@Composable
private fun EngineEditDialog(
    initial: SearchEngine?,
    existingTriggers: List<String>,
    onDismissRequest: () -> Unit,
    onConfirm: (name: String, template: String, trigger: String) -> Unit,
) {
    var name by remember(initial) { mutableStateOf(initial?.name.orEmpty()) }
    var template by remember(initial) { mutableStateOf(initial?.urlTemplate.orEmpty()) }
    var trigger by remember(initial) { mutableStateOf(initial?.trigger.orEmpty()) }
    val templateError = template.isNotEmpty() && !template.contains("%s")
    // 标识：空 = 不启用直达；否则不能含空白、不能与其它引擎重复（大小写不敏感）。
    val triggerClean = trigger.trim()
    val triggerError: Int? = when {
        triggerClean.any { it.isWhitespace() } -> R.string.search_engine_trigger_space
        triggerClean.isNotEmpty() && existingTriggers.any { it.equals(triggerClean, ignoreCase = true) } ->
            R.string.search_engine_trigger_duplicate
        else -> null
    }
    val templateValid = name.isNotBlank() && template.contains("%s")
    val valid = templateValid && triggerError == null
    CustomDialog(
        title = stringResource(
            if (initial == null) R.string.search_engine_add else R.string.search_engine_edit,
        ),
        onDismissRequest = onDismissRequest,
        onConfirm = { if (valid) onConfirm(name.trim(), template.trim(), triggerClean) },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(CardInnerSpacing)) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text(stringResource(R.string.search_engine_name)) },
                    )
                    OutlinedTextField(
                        value = template,
                        onValueChange = { template = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text(stringResource(R.string.search_engine_url)) },
                        placeholder = {
                            Text(
                                "https://www.bing.com/search?q=%s",
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                            )
                        },
                        isError = templateError,
                        // supportingText 常驻占位：无错时空字符串，避免错误出现/消失时弹窗高度跳动。
                        supportingText = {
                            Text(
                                text = if (templateError) stringResource(R.string.search_engine_url_error) else "",
                            )
                        },
                    )
                    OutlinedTextField(
                        value = trigger,
                        onValueChange = { trigger = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text(stringResource(R.string.search_engine_trigger)) },
                        placeholder = { Text("bb") },
                        isError = triggerError != null,
                        supportingText = {
                            Text(text = triggerError?.let { stringResource(it) } ?: "")
                        },
                    )
        }
    }
}
/** 来源 key → 显示名：复用已有文案，无开关的四个用分组名。面板提示列表共用。 */
@Composable
internal fun sourceTitle(key: String): String = when (key) {
    SRC_APP -> stringResource(R.string.quick_search_group_apps)
    SRC_SHORTCUT -> stringResource(R.string.quick_search_group_shortcuts)
    SRC_ACTION -> stringResource(R.string.quick_search_group_actions)
    SRC_SETTINGS -> stringResource(R.string.quick_search_group_settings)
    SRC_CONTACT -> stringResource(R.string.quick_search_contacts)
    SRC_MUSIC -> stringResource(R.string.quick_search_music)
    SRC_FILE -> stringResource(R.string.quick_search_files)
    SRC_CALENDAR -> stringResource(R.string.quick_search_calendar)
    SRC_SMS -> stringResource(R.string.quick_search_sms)
    SRC_CALL -> stringResource(R.string.quick_search_calls)
    SRC_TOGGLE -> stringResource(R.string.quick_search_toggles)
    SRC_CLIP -> stringResource(R.string.quick_search_clipboard)
    else -> key
}
/**
 * 来源标识编辑：单输入框，判重规则跟引擎 dialog 同款（空=关闭该源标识，
 * 不能含空白、不能撞引擎/其他来源）。确认键只在合法时可用，不用 toast。
 */
@Composable
private fun SourceTriggerDialog(
    title: String,
    current: String,
    existingTriggers: List<String>,
    onDismissRequest: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var trigger by remember(current) { mutableStateOf(current) }
    val triggerClean = trigger.trim().lowercase()
    val triggerError: Int? = when {
        triggerClean.any { it.isWhitespace() } -> R.string.search_engine_trigger_space
        triggerClean.isNotEmpty() && existingTriggers.any { it.equals(triggerClean, ignoreCase = true) } ->
            R.string.search_engine_trigger_duplicate
        else -> null
    }
    TextInputDialog(
        title = title,
        label = stringResource(R.string.search_engine_trigger),
        initialValue = current,
        isError = triggerError != null,
        supportingText = triggerError?.let { stringResource(it) }
            ?: stringResource(R.string.quick_search_trigger_off),
        confirmEnabled = triggerError == null,
        onDismissRequest = onDismissRequest,
        onConfirm = { onConfirm(it.trim().lowercase()) },
    )
}