package hunoia.luno.quickaccess.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.chrisbanes.haze.HazeState
import hunoia.luno.quickaccess.R
import hunoia.luno.bridge.system.ClipboardContent
import hunoia.luno.clipboard.ClipboardCenter
import hunoia.luno.bridge.ui.showToast
import hunoia.luno.core.domain.JsonSerializer
import hunoia.luno.domain.search.SearchCenter
import hunoia.luno.config.model.QuickSearchListStyle
import hunoia.luno.config.model.QuickSearchSettings
import hunoia.luno.config.model.SearchEngine
import hunoia.luno.config.model.SearchLaunch
import hunoia.luno.config.model.allEngines
import hunoia.luno.config.model.badgeText
import hunoia.luno.config.model.effectiveLaunch
import hunoia.luno.config.model.effectiveTrigger
import hunoia.luno.quickaccess.query.SearchSourceRegistry
import hunoia.luno.ui.component.LunoActionBar
import hunoia.luno.ui.component.LunoActionBarItem
import hunoia.luno.ui.component.page.LunoPage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import hunoia.luno.ui.component.DragReorderHandle
import hunoia.luno.ui.component.TextIcon
import hunoia.luno.ui.component.dragReorderPlacement
import hunoia.luno.ui.component.rememberDragReorderState
import hunoia.luno.ui.component.feedback.EmptyState
import hunoia.luno.ui.component.menu.LunoDropdownMenu
import hunoia.luno.ui.component.menu.LunoDropdownMenuItem
import hunoia.luno.ui.component.row.LunoGroup
import hunoia.luno.ui.component.row.LunoSettingsRow
import hunoia.luno.ui.component.row.chunkedGroup
import hunoia.luno.ui.component.row.group
import hunoia.luno.ui.component.sheet.ConfirmDialog
import hunoia.luno.ui.theme.ListSpacing
import hunoia.luno.ui.theme.RowIconSize
import hunoia.luno.ui.theme.SegmentedGap
import hunoia.luno.ui.component.PressIconButton

/** 导入预览：解析好待并入的引擎 + 标识冲突条数，确认后才写库。 */
private data class ImportPreview(
    val engines: List<SearchEngine>,
    val conflictTriggers: Int,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchEngineSettingsScreen(
    onBack: () -> Unit,
    onNavToSources: () -> Unit,
    onNavToEdit: (String) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val settings by SearchCenter.search.collectAsStateWithLifecycle(initialValue = SearchCenter.latestSearch())
    var deleteTarget by remember { mutableStateOf<SearchEngine?>(null) }
    var importPreview by remember { mutableStateOf<ImportPreview?>(null) }
    var showExportConfirm by remember { mutableStateOf(false) }
    val configEngines = settings.allEngines()
    fun update(transform: (QuickSearchSettings) -> QuickSearchSettings) {
        scope.launch { SearchCenter.updateSearch(transform) }
    }
    fun parseImport(raw: String): ImportPreview? {
        if (raw.isEmpty()) {
            showToast(R.string.search_engine_import_empty)
            return null
        }
        val imported = runCatching {
            JsonSerializer.decodeFromString<List<SearchEngine>>(raw)
        }.getOrNull()
        if (imported.isNullOrEmpty()) {
            showToast(R.string.search_engine_import_failed)
            return null
        }
        // 标识判重：导入的引擎标识不能撞已有引擎或来源标识，否则会把旧的直达入口顶掉。
        // 撞了的直接清空标识（引擎还在，只是不参与「标识 + 空格」直达）。
        val existingIds = settings.allEngines().mapTo(mutableSetOf()) { it.id }
        val usedTriggers = settings.allEngines()
            .mapTo(mutableSetOf()) { it.trigger.trim().lowercase() }
            .apply { remove("") }
        usedTriggers += SearchSourceRegistry.all
            .mapTo(mutableSetOf()) { settings.effectiveTrigger(it.key).trim().lowercase() }
            .apply { remove("") }
        var cleared = 0
        val merged = imported.map { engine ->
            // id 撞已有或缺失时留空：写入归一化按内容派生稳定 id（同内容重复导入得到同一 id）。
            val id = engine.id.takeIf { it.isNotBlank() && it !in existingIds } ?: ""
            existingIds.add(id)
            val trigger = engine.trigger.trim()
            val cleanTrigger = if (trigger.isEmpty() || usedTriggers.add(trigger.lowercase())) {
                trigger
            } else {
                cleared++
                ""
            }
            engine.copy(id = id, trigger = cleanTrigger)
        }
        return ImportPreview(engines = merged, conflictTriggers = cleared)
    }
    fun applyImport(preview: ImportPreview) {
        scope.launch {
            SearchCenter.updateSearch { current ->
                current.copy(customEngines = current.customEngines + preview.engines)
            }
            showToast(
                if (preview.conflictTriggers > 0) {
                    context.getString(
                        R.string.search_engine_import_trigger_conflict,
                        preview.conflictTriggers,
                    )
                } else {
                    context.getString(R.string.search_engine_import_done)
                },
            )
        }
    }
    // 文件导入：选 JSON 文件，读内容走同一套解析/确认流程。
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
                showToast(R.string.search_engine_import_file_empty)
                return@launch
            }
            parseImport(raw)?.let { importPreview = it }
        }
    }
    // 文件导出：CreateDocument 让用户选保存位置，直接写引擎 JSON。
    // 导出的是排序后的生效列表（主引擎在首位）：导入到空配置时顺序原样回来，主引擎不漂移。
    val fileExportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            val json = JsonSerializer.encodeToString(settings.allEngines())
            withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openOutputStream(uri)?.use {
                        it.write(json.toByteArray(Charsets.UTF_8))
                    }
                }
            }
            showToast(R.string.search_engine_export_file_done)
        }
    }
    val listState = rememberLazyListState()
    // 拖动排序草稿由 DragReorderState 持有（与动作面板编辑页同一套）：null = 跟随配置，松手才落盘。
    val reorder = rememberDragReorderState<SearchEngine>(
        current = { configEngines },
        onCommit = { finalEngines ->
            // 把草稿的完整 id 顺序写入 engineOrder，主引擎 = 第一位。
            update { current -> current.copy(engineOrder = finalEngines.map { it.id }) }
        },
    )
    val engines = reorder.items
    // 配置到位（拖拽已结束）后草稿退回跟随，避免编辑/删除/导入后残留旧顺序。
    LaunchedEffect(configEngines) {
        reorder.syncWithCurrent()
    }
    // 常用引擎预设已删（首装由 DefaultSearchEngines seed）：引擎只有新建与导入两条路，
    // 引擎列表本身就是唯一事实源。
    val actionBar: @Composable BoxScope.(HazeState) -> Unit = { hazeState ->
        LunoActionBar(hazeState) {
            LunoActionBarItem(
                onClick = { onNavToEdit("") },
                icon = Icons.Default.Add,
                contentDescription = stringResource(R.string.search_engine_add),
            )
        }
    }
    LunoPage(title = stringResource(R.string.search_engine_settings),
onBack = onBack,
bottomBar = actionBar,
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
                    text = stringResource(R.string.search_engine_export_file),
                    leadingIcon = { Icon(Icons.Default.FileDownload, contentDescription = null) },
                    onClick = {
                        menuOpen = false
                        fileExportLauncher.launch("search-engines.json")
    },
                )
                LunoDropdownMenuItem(
                    text = stringResource(R.string.search_engine_import_file),
                    leadingIcon = { Icon(Icons.Default.FileOpen, contentDescription = null) },
                    onClick = {
                        menuOpen = false
                        importFileLauncher.launch(
                            arrayOf("application/json", "text/plain", "application/octet-stream"),
                        )
                    },
                )
                LunoDropdownMenuItem(
                    text = stringResource(R.string.search_engine_export),
                    leadingIcon = { Icon(Icons.Default.ContentCopy, contentDescription = null) },
                    onClick = {
                        menuOpen = false
                        showExportConfirm = true
                    },
                )
                LunoDropdownMenuItem(
                    text = stringResource(R.string.search_engine_import),
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
                    title = stringResource(R.string.search_import_confirm_title),
                    text = stringResource(
                        if (preview.conflictTriggers > 0) {
                            R.string.search_import_confirm_text
                        } else {
                            R.string.search_import_confirm_text_no_conflict
                        },
                        preview.engines.size,
                        preview.conflictTriggers,
                    ),
                    onDismissRequest = { importPreview = null },
                    onConfirm = {
                        importPreview = null
                        applyImport(preview)
                    },
                )
            }
            deleteTarget?.let { target ->
                ConfirmDialog(
                    title = stringResource(R.string.search_engine_delete_title),
                    text = target.name,
                    destructive = true,
                    onDismissRequest = { deleteTarget = null },
                    onConfirm = {
                        scope.launch {
                            SearchCenter.updateSearch { current ->
                                current.copy(
                                    customEngines = current.customEngines.filterNot { it.id == target.id },
                                )
                            }
                        }
                    },
                )
            }
            if (showExportConfirm) {
                ConfirmDialog(
                    title = stringResource(R.string.search_export_confirm_title),
                    text = stringResource(
                        R.string.search_export_confirm_text,
                        settings.allEngines().size,
                    ),
                    onDismissRequest = { showExportConfirm = false },
                    onConfirm = {
                        showExportConfirm = false
                        val json = JsonSerializer.encodeToString(settings.allEngines())
                        ClipboardCenter.writeTextImmediate(json, "luno-search-engines")
                        showToast(R.string.search_engine_export_done)
                    },
                )
            }
        }) {
    list(state = listState,
contentSpacing = SegmentedGap) {
        lazy {

        chunkedGroup(
            engines,
            "engines",
            title = { stringResource(R.string.search_engine_group_title) },
            emptyContent = {
                LunoGroup(title = stringResource(R.string.search_engine_group_title)) {
                    EmptyState(message = stringResource(R.string.search_engine_empty_hint))
                }
            },
        ) { shape, position, engine ->
            val launch = engine.effectiveLaunch()
            val template = when (launch) {
                is SearchLaunch.Url -> launch.template
                is SearchLaunch.Chooser -> launch.template
                is SearchLaunch.Share -> launch.textTemplate
                is SearchLaunch.Action -> launch.copyTemplate
                is SearchLaunch.ShareImage -> ""
                is SearchLaunch.Activity -> ""
            }
            val launchLabel = when (launch) {
                is SearchLaunch.Chooser -> stringResource(R.string.search_engine_open_chooser)
                is SearchLaunch.Url -> if (launch.packageName.isNotBlank()) {
                    stringResource(R.string.search_engine_open_app)
                } else {
                    ""
                }
                is SearchLaunch.Share -> stringResource(R.string.search_engine_open_share)
                is SearchLaunch.ShareImage -> stringResource(R.string.search_engine_open_share_image)
                is SearchLaunch.Activity -> stringResource(R.string.search_engine_open_activity)
                is SearchLaunch.Action -> ""
            }
            LunoSettingsRow(
                title = engine.name,
                textIcon = TextIcon(engine.badgeText),
                subtitle = listOfNotNull(
                    // 首位即主引擎：给个视觉锚点，拖拽换位就是换主引擎。
                    if (position == 0) stringResource(R.string.search_engine_primary) else null,
                    engine.trigger.takeIf { it.isNotBlank() },
                    launchLabel.takeIf { it.isNotBlank() },
                    template.takeIf { it.isNotBlank() },
                ).joinToString(" · "),
                modifier = Modifier.dragReorderPlacement(reorder, position),
                onClick = { onNavToEdit(engine.id) },
                trailing = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        PressIconButton(onClick = { deleteTarget = engine }) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = stringResource(hunoia.luno.design.R.string.delete),
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(RowIconSize),
                            )
                        }
                        DragReorderHandle(
                            reorder,
                            position,
                            stringResource(R.string.search_engine_drag),
                            configEngines,
                        )
                    }
                },
                shape = shape,
            )
        }
        item(key = "gap-general") { GroupGap() }
        // 通用组：内容源入口 + 列表样式 + 输入行为开关。
        item(key = "general") {
            group(title = stringResource(R.string.search_general_group)) {
                row(
                    title = stringResource(R.string.search_source_settings),
                    trailing = { ArrowHint() },
                    onClick = onNavToSources,
                )
                dropdownRow(
                    title = stringResource(R.string.search_list_style),
                    options = listOf(
                        stringResource(R.string.search_list_style_flat),
                        stringResource(R.string.search_list_style_cards),
                    ),
                    selectedIndex = settings.listStyle.ordinal,
                    onSelect = { index ->
                        update { it.copy(listStyle = QuickSearchListStyle.entries[index]) }
                    },
                )
                switchRow(
                    title = stringResource(R.string.search_ascii_enter),
                    subtitle = stringResource(R.string.search_ascii_enter_subtitle),
                    checked = settings.asciiEnterSearch,
                    onCheckedChange = { v ->
                        update { it.copy(asciiEnterSearch = v) }
                    },
                )
            }
        }

        }
    }
}
}

/** 组间距补偿：LazyColumn 项间距是 SegmentedGap，组间再补到 ListSpacing。 */
@Composable
private fun GroupGap() {
    Spacer(Modifier.height(ListSpacing - SegmentedGap))
}

/** 子页入口行尾的右箭头：与系统设置页「进入下级」约定一致。 */
@Composable
private fun ArrowHint() {
    Icon(
        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
