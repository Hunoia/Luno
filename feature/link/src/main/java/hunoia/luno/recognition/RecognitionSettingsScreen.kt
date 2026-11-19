package hunoia.luno.recognition

import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.app.role.RoleManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Icon
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import hunoia.luno.bridge.system.ClipboardContent
import hunoia.luno.bridge.ui.showToast
import hunoia.luno.clipboard.ClipboardCenter
import hunoia.luno.shizuku.ShizukuManager
import hunoia.luno.bridge.system.gotoAccessibilitySettings
import hunoia.luno.config.model.ActionSettings
import hunoia.luno.config.model.LinkMissMode
import hunoia.luno.config.model.SceneListMode
import hunoia.luno.config.model.RecognitionBuiltin
import hunoia.luno.config.model.RecognitionDefaults
import hunoia.luno.config.model.RecognitionDisplay
import hunoia.luno.config.model.RecognitionMatch
import hunoia.luno.config.model.RecognitionOutput
import hunoia.luno.config.model.RecognitionRule
import hunoia.luno.config.model.RecognitionSettings
import hunoia.luno.config.model.ToastPresentation
import hunoia.luno.core.domain.JsonSerializer
import hunoia.luno.link.LinkPresets
import hunoia.luno.link.LinkTarget
import hunoia.luno.link.Links
import hunoia.luno.link.R
import hunoia.luno.ui.component.DragReorderHandle
import hunoia.luno.ui.component.LunoFab
import hunoia.luno.ui.component.PressIconButton
import hunoia.luno.ui.component.dragReorderPlacement
import hunoia.luno.ui.component.feedback.EmptyState
import hunoia.luno.ui.component.rememberDragReorderState
import hunoia.luno.ui.component.menu.LunoDropdownMenu
import hunoia.luno.ui.component.menu.LunoDropdownMenuItem
import hunoia.luno.ui.component.row.LunoDropdownRow
import hunoia.luno.ui.component.row.LunoGroup
import hunoia.luno.ui.component.row.LunoGroupItem
import hunoia.luno.ui.component.row.LunoSettingsRow
import hunoia.luno.ui.component.row.LunoSwitchRow
import hunoia.luno.ui.component.row.segmentedShape
import hunoia.luno.ui.component.screen.LunoScreenLazy
import hunoia.luno.ui.component.sheet.ConfirmDialog
import hunoia.luno.ui.facade.LocalFacades
import hunoia.luno.ui.icon.rememberPackageIconBitmap
import hunoia.luno.ui.theme.FloatingContentBottom
import hunoia.luno.ui.theme.RowIconSize
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 导入预览：解析好待并入的规则，确认后才写库。 */
private data class ImportPreview(val rules: List<RecognitionRule>)

/**
 * 分发规则页：**规则 → 输出**的唯一入口。
 *
 * 规则表只有一张：来源、匹配方式、输出、呈现档（直执行 / 显示胶囊）都写在一条规则里。
 * 这里按类型筛选不需要单独页面——规则行自己就写清了这四件事。
 */
@Composable
fun RecognitionSettingsScreen(
    onBack: () -> Unit,
    onEditRule: (String) -> Unit,
    onCreateRule: () -> Unit,
    onNavToSceneList: () -> Unit = {},
    onNavToResultCapsuleSettings: () -> Unit = {},
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val facade = LocalFacades.current.recognitionSettings
    val actionSettings by facade.actionSettings
        .collectAsStateWithLifecycle(initialValue = ActionSettings())
    val settings = actionSettings.recognition
    // 情景识别靠无障碍服务读界面文本；没就绪时在组内提示一行。
    val shizukuStatus by ShizukuManager.statusFlow.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    var importPreview by remember { mutableStateOf<ImportPreview?>(null) }
    var showExportConfirm by remember { mutableStateOf(false) }

    fun update(transform: (RecognitionSettings) -> RecognitionSettings) {
        scope.launch { facade.updateRecognition(transform) }
    }
    fun parseImport(raw: String): ImportPreview? {
        if (raw.isEmpty()) {
            showToast(R.string.recognition_import_empty)
            return null
        }
        val imported = runCatching {
            JsonSerializer.decodeFromString<List<RecognitionRule>>(raw)
        }.getOrNull()
        if (imported.isNullOrEmpty()) {
            showToast(R.string.recognition_import_failed)
            return null
        }
        return ImportPreview(imported)
    }
    fun applyImport(preview: ImportPreview) {
        scope.launch {
            update { current ->
                val existingIds = current.rules.mapTo(mutableSetOf()) { it.id }
                val merged = preview.rules.map { rule ->
                    // id 撞已有时重发：规则按 id 匹配，重复 id 会错乱。
                    if (rule.id in existingIds) rule.copy(id = UUID.randomUUID().toString()) else rule
                }
                current.copy(rules = current.rules + merged)
            }
            showToast(R.string.recognition_import_done)
        }
    }
    val importFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            val raw = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openInputStream(uri)
                        ?.bufferedReader()?.use { it.readText() }
                }.getOrNull()
            }?.trim().orEmpty()
            if (raw.isEmpty()) {
                showToast(R.string.recognition_import_file_empty)
                return@launch
            }
            parseImport(raw)?.let { importPreview = it }
        }
    }
    val fileExportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            val json = JsonSerializer.encodeToString(settings.rules)
            withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openOutputStream(uri)?.use {
                        it.write(json.toByteArray(Charsets.UTF_8))
                    }
                }
            }
            showToast(R.string.recognition_export_file_done)
        }
    }
    // 规则顺序即优先级（第一条命中生效），所以必须能拖；草稿松手才落盘。
    val reorder = rememberDragReorderState<RecognitionRule>(
        current = { settings.rules },
        onCommit = { finalRules -> update { it.copy(rules = finalRules) } },
    )
    LaunchedEffect(settings.rules) { reorder.syncWithCurrent() }
    val rules = reorder.items

    LunoScreenLazy(
        title = stringResource(R.string.recognition_title),
        onBack = onBack,
        state = listState,
        bottomContentPadding = FloatingContentBottom,
        floatingContent = {
            LunoFab(
                onClick = onCreateRule,
                icon = Icons.Default.Add,
                contentDescription = stringResource(R.string.recognition_rule_add),
            )
        },
        actions = {
            var menuOpen by remember { mutableStateOf(false) }
            PressIconButton(onClick = { menuOpen = true }) {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = stringResource(hunoia.luno.design.R.string.more_options),
                )
            }
            LunoDropdownMenu(
                expanded = menuOpen,
                onDismissRequest = { menuOpen = false },
            ) {
                LunoDropdownMenuItem(
                    text = stringResource(R.string.recognition_export_file),
                    leadingIcon = { Icon(Icons.Default.FileDownload, contentDescription = null) },
                    onClick = {
                        menuOpen = false
                        fileExportLauncher.launch("recognition-rules.json")
                    },
                )
                LunoDropdownMenuItem(
                    text = stringResource(R.string.recognition_import_file),
                    leadingIcon = { Icon(Icons.Default.FileOpen, contentDescription = null) },
                    onClick = {
                        menuOpen = false
                        importFileLauncher.launch(
                            arrayOf("application/json", "text/plain", "application/octet-stream"),
                        )
                    },
                )
                LunoDropdownMenuItem(
                    text = stringResource(R.string.recognition_export),
                    leadingIcon = { Icon(Icons.Default.ContentCopy, contentDescription = null) },
                    onClick = {
                        menuOpen = false
                        showExportConfirm = true
                    },
                )
                LunoDropdownMenuItem(
                    text = stringResource(R.string.recognition_import),
                    leadingIcon = { Icon(Icons.Default.ContentPaste, contentDescription = null) },
                    onClick = {
                        menuOpen = false
                        scope.launch {
                            val text = runCatching {
                                (ClipboardCenter.read() as? ClipboardContent.Text)?.text
                            }.getOrNull().orEmpty().trim()
                            importPreview = parseImport(text)
                        }
                    },
                )
            }
        },
        dialogs = {
            importPreview?.let { preview ->
                ConfirmDialog(
                    title = stringResource(R.string.recognition_import_confirm_title),
                    text = stringResource(
                        R.string.recognition_import_confirm_text,
                        preview.rules.size,
                    ),
                    onDismissRequest = { importPreview = null },
                    onConfirm = {
                        importPreview = null
                        applyImport(preview)
                    },
                )
            }
            if (showExportConfirm) {
                ConfirmDialog(
                    title = stringResource(R.string.recognition_export_confirm_title),
                    text = stringResource(
                        R.string.recognition_export_confirm_text,
                        settings.rules.size,
                    ),
                    onDismissRequest = { showExportConfirm = false },
                    onConfirm = {
                        showExportConfirm = false
                        val json = JsonSerializer.encodeToString(settings.rules)
                        ClipboardCenter.writeTextImmediate(json, "luno-recognition-rules")
                        showToast(R.string.recognition_export_done)
                    },
                )
            }
        },
    ) {
        // 默认浏览器：只在 Luno 不是默认浏览器时才显示入口（已是默认就没必要再引导）。
        if (!isLunoDefaultBrowser(context)) {
            item {
                LunoGroup(title = stringResource(R.string.recognition_browser_group)) {
                    LunoSettingsRow(
                        title = stringResource(R.string.recognition_default_browser),
                        shape = segmentedShape(0, 1),
                        onClick = {
                            val launched = runCatching {
                                context.startActivity(
                                    Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)
                                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                                )
                            }
                            if (launched.isFailure) {
                                runCatching {
                                    context.startActivity(
                                        Intent(Settings.ACTION_SETTINGS)
                                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                                    )
                                }
                            }
                        },
                    )
                }
            }
        }
        // 识屏取字参数（OCR 文字体系 / 二维码 / 松手去向）已迁回屏幕识别设置页：
        // v6 单一模型覆盖 50 语言，文字体系无需在此选择。
        item {
            // 浏览器候选只查一次：PM 查询是阻塞 binder，不跟每帧重组。
            val browsers = remember { Links.browsers(context) }
            val browserOptions = listOf(
                stringResource(R.string.recognition_rule_browser_auto),
            ) + browsers.map { it.label }
            val browserIndex = browsers
                .indexOfFirst { it.packageName == settings.browserPackage } + 1
            LunoGroup(title = stringResource(R.string.recognition_global_group)) {
                LunoDropdownRow(
                    title = stringResource(R.string.recognition_fallback),
                    subtitle = stringResource(R.string.recognition_fallback_hint),
                    options = listOf(
                        stringResource(R.string.recognition_miss_share),
                        stringResource(R.string.recognition_miss_browser),
                        stringResource(R.string.recognition_miss_picker),
                    ),
                    selectedIndex = when (settings.fallback) {
                        LinkMissMode.SHARE -> 0
                        LinkMissMode.BROWSER -> 1
                        LinkMissMode.PICKER -> 2
                    },
                    onSelect = { index ->
                        update {
                            it.copy(
                                fallback = when (index) {
                                    0 -> LinkMissMode.SHARE
                                    1 -> LinkMissMode.BROWSER
                                    else -> LinkMissMode.PICKER
                                },
                            )
                        }
                    },
                    shape = segmentedShape(0, 3),
                )
                LunoDropdownRow(
                    title = stringResource(R.string.recognition_browser_choice),
                    subtitle = stringResource(R.string.recognition_browser_choice_hint),
                    options = browserOptions,
                    selectedIndex = browserIndex.coerceAtLeast(0),
                    onSelect = { index ->
                        update {
                            it.copy(
                                browserPackage =
                                if (index == 0) "" else browsers[index - 1].packageName,
                            )
                        }
                    },
                    shape = segmentedShape(1, 3),
                )
                LunoDropdownRow(
                    title = stringResource(R.string.recognition_copy_clear_delay),
                    subtitle = stringResource(R.string.recognition_copy_clear_delay_hint),
                    options = RecognitionDefaults.COPY_CLEAR_DELAY_OPTIONS.map { seconds ->
                        if (seconds == 0) {
                            stringResource(R.string.recognition_copy_clear_off)
                        } else {
                            stringResource(R.string.recognition_copy_clear_seconds, seconds)
                        }
                    },
                    selectedIndex = RecognitionDefaults.COPY_CLEAR_DELAY_OPTIONS
                        .indexOf(settings.copyClearDelaySeconds).coerceAtLeast(0),
                    onSelect = { index ->
                        update {
                            it.copy(
                                copyClearDelaySeconds =
                                RecognitionDefaults.COPY_CLEAR_DELAY_OPTIONS[index],
                            )
                        }
                    },
                    shape = segmentedShape(2, 3),
                )
            }
        }
        // 结果胶囊是分发规则的“显示面”：入口放这里，不再在动作设置里重复一份。
        item(key = "capsule") {
            LunoGroup(title = stringResource(R.string.recognition_capsule_group)) {
                LunoSettingsRow(
                    icon = Icons.Default.Lightbulb,
                    title = stringResource(R.string.recognition_capsule_entry),
                    subtitle = stringResource(R.string.recognition_capsule_entry_hint),
                    shape = segmentedShape(0, 1),
                    onClick = onNavToResultCapsuleSettings,
                )
            }
        }
        // 读取来源：这是「规则读不读这一路输入」的开关，与结果胶囊显示无关
        // （胶囊没有自己的开关，有胶囊档结果就会显示）。情景的开关与名单在下面一组。
        // 组里还带一个通知侧的显示开关（命中规则后要不要留在通知栏），它是「显示」不是「读取」，
        // 所以组副标题只讲读取，避免把两个轴混在一起。
        item {
            LunoGroup(
                title = stringResource(R.string.recognition_read_group),
                subtitle = stringResource(R.string.recognition_read_group_hint),
            ) {
                LunoSwitchRow(
                    title = stringResource(R.string.recognition_read_notification),
                    subtitle = stringResource(R.string.recognition_read_notification_hint),
                    checked = settings.notificationReadEnabled,
                    onCheckedChange = { checked ->
                        update { it.copy(notificationReadEnabled = checked) }
                    },
                    shape = segmentedShape(0, 5),
                )
                LunoSwitchRow(
                    title = stringResource(R.string.recognition_hide_matched_notification),
                    subtitle = stringResource(R.string.recognition_hide_matched_notification_hint),
                    checked = settings.hideMatchedNotification,
                    onCheckedChange = { checked ->
                        update { it.copy(hideMatchedNotification = checked) }
                    },
                    shape = segmentedShape(1, 5),
                )
                LunoSwitchRow(
                    title = stringResource(R.string.recognition_read_clipboard),
                    subtitle = stringResource(R.string.recognition_read_clipboard_hint),
                    checked = settings.clipboardReadEnabled,
                    onCheckedChange = { checked ->
                        update { it.copy(clipboardReadEnabled = checked) }
                    },
                    shape = segmentedShape(2, 5),
                )
                LunoSwitchRow(
                    title = stringResource(R.string.recognition_direct_toast),
                    subtitle = stringResource(R.string.recognition_direct_toast_hint),
                    checked = settings.directToastEnabled,
                    onCheckedChange = { checked ->
                        update { it.copy(directToastEnabled = checked) }
                    },
                    shape = segmentedShape(3, 5),
                )
                LunoDropdownRow(
                    title = stringResource(R.string.recognition_direct_prompt_mode),
                    subtitle = stringResource(R.string.recognition_direct_prompt_mode_hint),
                    options = listOf(
                        stringResource(R.string.recognition_direct_prompt_toast),
                        stringResource(R.string.recognition_direct_prompt_standard),
                        stringResource(R.string.recognition_direct_prompt_live),
                        stringResource(R.string.recognition_direct_prompt_island),
                        stringResource(R.string.recognition_direct_prompt_capsule),
                    ),
                    selectedIndex = settings.directPromptMode.ordinal,
                    onSelect = { index ->
                        update { it.copy(directPromptMode = ToastPresentation.entries[index]) }
                    },
                    shape = segmentedShape(4, 5),
                )
            }
        }
        // 情景识别：识别器的第五个输入源，开关与范围都在这里（不再挂在结果胶囊下）。
        item {
            val sceneReady = sceneA11yEnabled(context)
            val shizukuReady = shizukuStatus.isReady
            val needsA11y = settings.sceneEnabled && !sceneReady
            val needsShizuku = settings.sceneEnabled && !shizukuReady
            val rowCount = 3 + (if (needsA11y) 1 else 0) + (if (needsShizuku) 1 else 0)
            LunoGroup(
                title = stringResource(R.string.recognition_scene_group),
                subtitle = stringResource(R.string.recognition_scene_group_hint),
            ) {
                LunoSwitchRow(
                    title = stringResource(R.string.recognition_scene_enabled),
                    subtitle = stringResource(R.string.recognition_scene_enabled_hint),
                    checked = settings.sceneEnabled,
                    onCheckedChange = { checked -> update { it.copy(sceneEnabled = checked) } },
                    shape = segmentedShape(0, rowCount),
                )
                LunoDropdownRow(
                    title = stringResource(R.string.recognition_scene_list_mode),
                    subtitle = stringResource(R.string.recognition_scene_list_mode_hint),
                    options = listOf(
                        stringResource(R.string.recognition_scene_list_blocklist),
                        stringResource(R.string.recognition_scene_list_allowlist),
                    ),
                    selectedIndex = if (settings.sceneListMode == SceneListMode.Allowlist) 1 else 0,
                    onSelect = { index ->
                        update {
                            it.copy(
                                sceneListMode = if (index == 1) SceneListMode.Allowlist
                                else SceneListMode.Blocklist,
                            )
                        }
                    },
                    shape = segmentedShape(1, rowCount),
                )
                LunoSettingsRow(
                    icon = Icons.Default.Block,
                    title = stringResource(R.string.recognition_scene_list),
                    subtitle = if (settings.scenePackages.isEmpty()) {
                        stringResource(R.string.recognition_scene_list_none)
                    } else {
                        stringResource(
                            R.string.recognition_scene_list_count,
                            settings.scenePackages.size,
                        )
                    },
                    shape = segmentedShape(2, rowCount),
                    onClick = onNavToSceneList,
                )
                if (needsA11y) {
                    LunoSettingsRow(
                        title = stringResource(R.string.recognition_scene_need_a11y),
                        subtitle = stringResource(R.string.recognition_scene_need_a11y_hint),
                        icon = Icons.AutoMirrored.Filled.OpenInNew,
                        shape = segmentedShape(3, rowCount),
                        onClick = { context.gotoAccessibilitySettings() },
                    )
                }
                if (needsShizuku) {
                    LunoSettingsRow(
                        title = stringResource(R.string.recognition_scene_need_shizuku),
                        subtitle = stringResource(R.string.recognition_scene_need_shizuku_hint),
                        icon = Icons.Default.Link,
                        shape = segmentedShape(3 + (if (needsA11y) 1 else 0), rowCount),
                        onClick = { scope.launch { ShizukuManager.autoRequestPermissionIfNeeded() } },
                    )
                }
            }
        }
        item {
            // PM 查询很贵：只在规则表变化时重算，开关/滚动不重复扫包。
            val addable = remember(settings.rules) { addablePresets(context, settings.rules) }
            if (addable.isNotEmpty()) {
                // 逐条添加的提示在组合期按行取好（配置变更能重建），点击回调里不读资源。
                val addedMessages = addable.map { preset ->
                    stringResource(
                        R.string.recognition_presets_added_one,
                        preset.name.ifBlank { preset.match.pattern },
                    )
                }
                LunoGroup(
                    title = stringResource(R.string.recognition_presets_group),
                    subtitle = stringResource(R.string.recognition_presets_hint),
                ) {
                    addable.forEachIndexed { index, preset ->
                        LunoSettingsRow(
                            icon = if (preset.match is RecognitionMatch.Link) {
                                Icons.Default.Link
                            } else {
                                Icons.Default.Lightbulb
                            },
                            title = preset.name.ifBlank { preset.match.pattern },
                            subtitle = presetSummary(preset),
                            shape = segmentedShape(index, addable.size),
                            onClick = {
                                // 逐条添加：一次点一条，比「全部添加」可控（用户往往只要其中一两条）。
                                update { it.copy(rules = it.rules + preset) }
                                showToast(addedMessages[index])
                            },
                        )
                    }
                }
            }
        }
        item {
            val iconPx = with(LocalDensity.current) { RowIconSize.toPx() }.toInt()
            LunoGroup(
                title = stringResource(R.string.recognition_rules_group),
                subtitle = stringResource(R.string.recognition_rules_order),
            ) {
                if (rules.isEmpty()) {
                    LunoGroupItem(shape = segmentedShape(0, 1)) {
                        EmptyState(message = stringResource(R.string.recognition_rules_empty))
                    }
                } else {
                    rules.forEachIndexed { index, rule ->
                        // key 必须带上目标应用/浏览器开关：改完目标返回列表时才能刷新副标题与图标。
                        val target by produceState(
                            initialValue = null as LinkTarget?,
                            rule.id,
                            rule.match,
                            settings.browserPackage,
                        ) {
                            val match = rule.match
                            value = if (match is RecognitionMatch.Link) {
                                Links.resolvedTargetOf(context, match, settings.browserPackage)
                            } else {
                                null
                            }
                        }
                        val targetIcon = target?.packageName?.takeIf { it.isNotEmpty() }
                            ?.let { rememberPackageIconBitmap(it, iconPx) }
                        LunoSettingsRow(
                            icon = when {
                                targetIcon != null -> null
                                rule.match is RecognitionMatch.Link -> Icons.Default.Link
                                rule.output is RecognitionOutput.Action -> Icons.Default.Android
                                else -> Icons.Default.Lightbulb
                            },
                            iconBitmap = targetIcon,
                            title = rule.name.ifBlank { rule.match.pattern.ifBlank { rule.id } },
                            subtitle = recognitionRuleSummary(rule, target?.label),
                            modifier = Modifier.dragReorderPlacement(reorder, index),
                            shape = segmentedShape(index, rules.size),
                            enabled = rule.enabled,
                            onClick = { onEditRule(rule.id) },
                            trailing = {
                                DragReorderHandle(
                                    reorder,
                                    index,
                                    stringResource(R.string.recognition_rule_drag),
                                    settings.rules,
                                )
                            },
                        )
                    }
                }
            }
        }
    }
}

/** 预设行的副标题：说清它会抓什么、送到哪，不用点进去才知道。 */
@Composable
private fun presetSummary(preset: RecognitionRule): String = when (val m = preset.match) {
    is RecognitionMatch.Text -> stringResource(R.string.recognition_preset_text_summary)
    is RecognitionMatch.Link -> if (m.viaBrowser) {
        stringResource(R.string.recognition_preset_browser_summary)
    } else {
        stringResource(R.string.recognition_preset_link_summary, m.targetPackage)
    }
}

/**
 * 可添加的预设：文本类（验证码 / 取件码 / 快递单号 / 订单号）+ 链接类（本机已安装应用）。
 *
 * 按匹配串判重，用户删掉的默认规则也能从这里加回来。文本预设与链接预设同一入口，
 * 否则「添加常用规则」只剩链接一项。
 */
private fun addablePresets(
    context: Context,
    existing: List<RecognitionRule>,
): List<RecognitionRule> {
    val used = existing
        .map { it.match.pattern.trim().lowercase() }
        .filter { it.isNotEmpty() }
        .toMutableSet()
    val text = RecognitionDefaults.textPresets
        .filter { used.add(it.match.pattern.trim().lowercase()) }
        .map { it.copy(id = java.util.UUID.randomUUID().toString()) }
    // 「通知」的变体不受 pattern 判重影响（它和普通预设同正则但输出不同），
    // 按名字判重，避免重复添加。
    val usedNames = existing.map { it.name }.toMutableSet()
    val island = RecognitionDefaults.islandPresets
        .filter { usedNames.add(it.name) }
        .map { it.copy(id = java.util.UUID.randomUUID().toString()) }
    return text + island + LinkPresets.addable(context, existing + text + island)
}

/** 无障碍服务类的简单类名（`SideGestureService`）；与 `:app` 里的声明保持同步。 */
private const val SCENE_A11Y_SERVICE_CLASS = "SideGestureService"

/**
 * 情景识别靠无障碍服务读界面文本，设置页得告诉用户「服务没开」。
 *
 * 读不到服务连接状态：`SideGestureService` 在 `:service` 进程、设置页在主进程，跨进程的
 * 单例槽位在这里永远是 null。改查系统设置 —— `ENABLED_ACCESSIBILITY_SERVICES` 是进程无关的
 * 事实源，权限正常时不再误报「需要无障碍服务」。本模块不能反向依赖 `:app`，类名按字符串比对。
 */
private fun sceneA11yEnabled(context: Context): Boolean =
    hunoia.luno.bridge.system.AccessibilitySettings.enabledComponents(context).any { component ->
        component.packageName == context.packageName &&
            component.className.substringAfterLast('.') == SCENE_A11Y_SERVICE_CLASS
    }

/**
 * Luno 是不是当前默认浏览器。
 *
 * 已是默认浏览器时页面不再显示「设为默认」入口（点了也没事做）。
 * 用浏览器角色判定（`RoleManager.ROLE_BROWSER`，API 29+），与文件里其它平台调用同一口径。
 */
private fun isLunoDefaultBrowser(context: Context): Boolean =
    context.getSystemService(RoleManager::class.java)
        ?.isRoleHeld(RoleManager.ROLE_BROWSER) ?: false

@Composable
internal fun recognitionRuleSummary(rule: RecognitionRule, targetLabel: String?): String {
    // 来源只露前两个并标「+N」：一行副标题塞五个来源会看不清匹配串本身。
    val labels = rule.sources.sortedBy { it.ordinal }.map { source -> stringResource(source.labelRes()) }
    val sources = if (labels.size > 2) {
        labels.take(2).joinToString("/") + "+${labels.size - 2}"
    } else {
        labels.joinToString("/")
    }
    val match = when (val m = rule.match) {
        is RecognitionMatch.Link -> {
            val typeLabel = stringResource(
                when (m.matchType) {
                    hunoia.luno.config.model.LinkMatchType.CONTAINS -> R.string.recognition_match_contains
                    hunoia.luno.config.model.LinkMatchType.DOMAIN -> R.string.recognition_match_domain
                    hunoia.luno.config.model.LinkMatchType.REGEX -> R.string.recognition_match_regex
                    hunoia.luno.config.model.LinkMatchType.EXACT -> R.string.recognition_match_exact
                },
            )
            "$typeLabel · ${m.pattern.ifBlank { "…" }}"
        }
        is RecognitionMatch.Text -> m.pattern
    }
    val output = when (val o = rule.output) {
        is RecognitionOutput.Builtin -> when (o.kind) {
            RecognitionBuiltin.Copy -> stringResource(R.string.recognition_output_copy)
            RecognitionBuiltin.CopyAndClear ->
                stringResource(R.string.recognition_output_copy_and_clear)
            RecognitionBuiltin.OpenLink -> if (targetLabel.isNullOrBlank()) {
                stringResource(R.string.recognition_output_open_link)
            } else {
                targetLabel
            }
            RecognitionBuiltin.Share -> stringResource(R.string.recognition_output_share)
        }
        is RecognitionOutput.Action -> stringResource(R.string.recognition_output_action)
        is RecognitionOutput.Notify -> stringResource(R.string.recognition_output_notify)
    }
    val summary = stringResource(R.string.recognition_rule_summary, sources, match, output)
    val link = rule.match as? RecognitionMatch.Link
    // 直执行档在列表里必须一眼看得见：它与胶囊档的行为差得最远（点了才有 vs 命中就跑）。
    // 通知输出恒为直执行（effectiveDisplay），标签一并显示。
    val directTag = if (rule.effectiveDisplay == RecognitionDisplay.Direct) {
        stringResource(R.string.recognition_rule_direct_tag)
    } else {
        ""
    }
    return buildString {
        append(summary)
        append(directTag)
        if (link?.resolveShortLink == true) {
            append(stringResource(R.string.recognition_rule_resolve_tag))
        }
    }
}

/** 识别来源的显示名资源。 */
internal fun hunoia.luno.config.model.RecognitionSource.labelRes(): Int = when (this) {
    hunoia.luno.config.model.RecognitionSource.Clipboard -> R.string.recognition_source_clipboard
    hunoia.luno.config.model.RecognitionSource.Notification -> R.string.recognition_source_notification
    hunoia.luno.config.model.RecognitionSource.ScreenPick -> R.string.recognition_source_screen_pick
    hunoia.luno.config.model.RecognitionSource.Scene -> R.string.recognition_source_scene
    hunoia.luno.config.model.RecognitionSource.ExternalIntent -> R.string.recognition_source_external
}
