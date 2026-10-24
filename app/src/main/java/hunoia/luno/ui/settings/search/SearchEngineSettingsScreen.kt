package hunoia.luno.ui.settings.search

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import hunoia.luno.R
import hunoia.luno.bridge.system.copyText
import hunoia.luno.bridge.system.readClipboardText
import hunoia.luno.bridge.ui.showToast
import hunoia.luno.core.JsonSerializer
import hunoia.luno.domain.search.SearchCenter
import hunoia.luno.config.model.BuiltinSearchEngineIds
import hunoia.luno.config.model.QuickSearchSettings
import hunoia.luno.config.model.SearchEngine
import hunoia.luno.config.model.SearchLaunch
import hunoia.luno.config.model.allEngines
import hunoia.luno.config.model.badgeText
import hunoia.luno.config.model.effectiveEngines
import hunoia.luno.config.model.effectiveLaunch
import hunoia.luno.config.model.effectiveTrigger
import hunoia.luno.config.model.moveEngineBetween
import hunoia.luno.quicklaunch.query.SearchSourceRegistry
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import kotlinx.coroutines.launch
import hunoia.luno.ui.component.TextIcon
import hunoia.luno.ui.component.feedback.LunoIconButton
import hunoia.luno.ui.component.row.LunoGroup
import hunoia.luno.ui.component.row.LunoSettingsRow
import hunoia.luno.ui.component.row.segmentedShape
import hunoia.luno.ui.component.screen.LunoScreenLazy
import hunoia.luno.ui.component.sheet.ConfirmDialog
import hunoia.luno.ui.component.sheet.AppPickerSheet
import hunoia.luno.ui.theme.FloatingContentBottom
import hunoia.luno.ui.theme.ListSpacing
import hunoia.luno.ui.theme.PageGutter
import hunoia.luno.ui.theme.SegmentedGap

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
    onNavToGovernance: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val settings by SearchCenter.search.collectAsStateWithLifecycle(initialValue = QuickSearchSettings())
    var editSheetTarget by remember { mutableStateOf<SearchEngine?>(null) }
    var addSheetOpen by remember { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<SearchEngine?>(null) }
    var importPreview by remember { mutableStateOf<ImportPreview?>(null) }
    // 引擎编辑（Sheet 里）的目标应用：由 AppPickerSheet 回填，编辑器只读。
    var engineTargetPackage by remember { mutableStateOf("") }
    var showEngineAppPicker by remember { mutableStateOf(false) }
    val visibleEngines = settings.effectiveEngines()
    val hiddenEngines = settings.allEngines().filter { it.id in settings.hiddenEngineIds }
    // 标识判重输入：引擎（排除正在编辑的那条）+ 来源标识。
    val existingTriggers = settings.allEngines()
        .filter { it.id != editSheetTarget?.id }
        .map { it.trigger } +
        SearchSourceRegistry.all.map { settings.effectiveTrigger(it.key) }
    fun update(transform: (QuickSearchSettings) -> QuickSearchSettings) {
        scope.launch { SearchCenter.updateSearch(transform) }
    }
    /** 拖拽换位（库的 onMove 按 key 回调）：直接在完整顺序里移动。 */
    fun moveEngineById(fromId: String, toId: String) {
        update { current ->
            current.copy(
                engineOrder = moveEngineBetween(current.allEngines().map { it.id }, fromId, toId),
            )
        }
    }
    fun toggleEngineHidden(engine: SearchEngine) {
        val hidden = engine.id in settings.hiddenEngineIds
        if (!hidden && visibleEngines.size <= 1) {
            showToast(R.string.search_engine_keep_one)
            return
        }
        update { current ->
            val currentHidden = engine.id in current.hiddenEngineIds
            current.copy(
                hiddenEngineIds = if (currentHidden) {
                    current.hiddenEngineIds.filterNot { it == engine.id }
                } else {
                    current.hiddenEngineIds + engine.id
                },
            )
        }
    }
    fun closeEditSheet() {
        addSheetOpen = false
        editSheetTarget = null
        engineTargetPackage = ""
    }
    fun parseImport(): ImportPreview? {
        val raw = readClipboardText(context)?.trim().orEmpty()
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
    val listState = rememberLazyListState()
    val haptic = LocalHapticFeedback.current
    val reorderState = rememberReorderableLazyListState(listState) { from, to ->
        moveEngineById(from.key.toString(), to.key.toString())
        haptic.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
    }
    // FAB 与网格面板页同一套：64dp、primaryContainer、无 ripple、避让导航栏。
    val addFab: @Composable () -> Unit = {
        Box(modifier = Modifier.fillMaxSize()) {
            CompositionLocalProvider(LocalRippleConfiguration provides null) {
                FloatingActionButton(
                    onClick = {
                        engineTargetPackage = ""
                        addSheetOpen = true
                    },
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .windowInsetsPadding(WindowInsets.navigationBars)
                        .padding(end = 16.dp, bottom = 16.dp)
                        .size(64.dp),
                    shape = FloatingActionButtonDefaults.shape,
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = stringResource(R.string.search_engine_add),
                    )
                }
            }
        }
    }
    LunoScreenLazy(
        title = stringResource(R.string.search_engine_settings),
        onBack = onBack,
        state = listState,
        contentSpacing = SegmentedGap,
        bottomContentPadding = FloatingContentBottom,
        floatingContent = addFab,
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
                    confirmLabel = stringResource(R.string.search_engine_import),
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
                    confirmLabel = stringResource(R.string.delete),
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
        },
        sheets = {
            if (showEngineAppPicker) {
                AppPickerSheet(
                    onDismissRequest = { showEngineAppPicker = false },
                    selectedPackageNames = listOf(engineTargetPackage),
                    confirmOnSelect = true,
                    onConfirm = { packages ->
                        engineTargetPackage = packages.firstOrNull().orEmpty()
                        showEngineAppPicker = false
                    },
                )
            }
            if (addSheetOpen || editSheetTarget != null) {
                val target = editSheetTarget
                val builtin = target?.id in BuiltinSearchEngineIds
                val editState = rememberEngineEditState(
                    initial = target,
                    builtin = builtin,
                    existingTriggers = existingTriggers,
                )
                EngineEditSheet(
                    state = editState,
                    builtin = builtin,
                    targetPackage = engineTargetPackage,
                    addMode = target == null,
                    onDismissRequest = { closeEditSheet() },
                    onPickApp = { showEngineAppPicker = true },
                    onConfirm = { result ->
                        if (target == null) {
                            update { current ->
                                current.copy(
                                    customEngines = current.customEngines +
                                        SearchEngine(
                                            name = result.name,
                                            urlTemplate = result.template,
                                            trigger = result.trigger,
                                            launch = result.launch,
                                            iconText = result.iconText,
                                        ),
                                )
                            }
                        } else {
                            scope.launch {
                                SearchCenter.updateSearch { current ->
                                    if (target.id in BuiltinSearchEngineIds) {
                                        // 内置引擎只覆盖打开方式，名称/标识/URL 仍由代码常量控制。
                                        current.copy(
                                            engineLaunchOverrides = current.engineLaunchOverrides +
                                                (target.id to result.launch),
                                        )
                                    } else {
                                        current.copy(
                                            customEngines = current.customEngines.map {
                                                if (it.id == target.id) {
                                                    it.copy(
                                                        name = result.name,
                                                        urlTemplate = result.template,
                                                        trigger = result.trigger,
                                                        launch = result.launch,
                                                        iconText = result.iconText,
                                                    )
                                                } else {
                                                    it
                                                }
                                            },
                                        )
                                    }
                                }
                            }
                        }
                        closeEditSheet()
                    },
                )
            }
        },
    ) {
        // 引擎行：LazyColumn 键控项，拖拽换位交给 sh.calvin.reorderable（手柄在行尾）。
        itemsIndexed(visibleEngines, key = { _, engine -> engine.id }) { index, engine ->
            ReorderableItem(reorderState, key = engine.id) { _ ->
                val locked = engine.id in BuiltinSearchEngineIds
                val launch = engine.effectiveLaunch()
                val template = when (launch) {
                    is SearchLaunch.Url -> launch.template
                    is SearchLaunch.Chooser -> launch.template
                    is SearchLaunch.Share -> launch.textTemplate
                    is SearchLaunch.Action -> launch.copyTemplate
                    is SearchLaunch.ShareImage -> ""
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
                    is SearchLaunch.Action -> ""
                }
                LunoSettingsRow(
                    title = engine.name,
                    textIcon = TextIcon(engine.badgeText),
                    subtitle = listOfNotNull(
                        // 首位即主引擎：给个视觉锚点，拖拽换位就是换主引擎。
                        if (index == 0) stringResource(R.string.search_engine_primary) else null,
                        engine.trigger.takeIf { it.isNotBlank() },
                        launchLabel.takeIf { it.isNotBlank() },
                        template.takeIf { it.isNotBlank() },
                    ).joinToString(" · "),
                    shape = segmentedShape(index, visibleEngines.size),
                    onClick = {},
                    trailing = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            LunoIconButton(
                                icon = Icons.Default.DragHandle,
                                contentDescription = stringResource(R.string.search_engine_drag),
                                onClick = {},
                                modifier = Modifier.draggableHandle(
                                    onDragStarted = {
                                        haptic.performHapticFeedback(
                                            HapticFeedbackType.GestureThresholdActivate,
                                        )
                                    },
                                    onDragStopped = {
                                        haptic.performHapticFeedback(HapticFeedbackType.GestureEnd)
                                    },
                                ),
                            )
                            var menuOpen by remember(engine.id) { mutableStateOf(false) }
                            LunoIconButton(
                                icon = Icons.Default.MoreVert,
                                contentDescription = stringResource(R.string.more),
                                onClick = { menuOpen = true },
                            )
                            DropdownMenu(
                                expanded = menuOpen,
                                onDismissRequest = { menuOpen = false },
                            ) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.edit)) },
                                    onClick = {
                                        menuOpen = false
                                        engineTargetPackage =
                                            (engine.effectiveLaunch() as? SearchLaunch.Url)
                                                ?.packageName
                                                .orEmpty()
                                        editSheetTarget = engine
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.search_engine_hide)) },
                                    onClick = { menuOpen = false; toggleEngineHidden(engine) },
                                )
                                if (!locked) {
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.delete)) },
                                        onClick = { menuOpen = false; deleteTarget = engine },
                                    )
                                }
                            }
                        }
                    },
                )
            }
        }
        item(key = "gap-entries") { GroupGap() }
        item(key = "entries") {
            // 子页入口：内容源/标识与结果治理拆出，主页面只留引擎管理。
            LunoGroup {
                LunoSettingsRow(
                    title = stringResource(R.string.search_source_settings),
                    trailing = { ArrowHint() },
                    onClick = onNavToSources,
                )
                LunoSettingsRow(
                    title = stringResource(R.string.search_governance_settings),
                    trailing = { ArrowHint() },
                    shape = segmentedShape(1, 2),
                    onClick = onNavToGovernance,
                )
            }
        }
        if (hiddenEngines.isNotEmpty()) {
            item(key = "gap-hidden") { GroupGap() }
            // 已隐藏引擎分区：点击恢复，不再混在可见列表里静默无效。
            item(key = "hidden") {
                LunoGroup(title = stringResource(R.string.search_engine_hidden)) {
                    hiddenEngines.forEachIndexed { index, engine ->
                        LunoSettingsRow(
                            title = engine.name,
                            textIcon = TextIcon(engine.badgeText),
                            subtitle = stringResource(R.string.search_engine_hidden_tap_hint),
                            shape = segmentedShape(index, hiddenEngines.size),
                            onClick = { toggleEngineHidden(engine) },
                        )
                    }
                }
            }
        }
        item(key = "gap-io") { GroupGap() }
        // 导入/导出：放最底下，单独一组。
        item(key = "io") {
            LunoGroup {
                LunoSettingsRow(
                    title = stringResource(R.string.search_engine_export),
                    onClick = {
                        val json = JsonSerializer.encodeToString(settings.customEngines)
                        copyText(context, "luno-search-engines", json)
                        showToast(R.string.search_engine_export_done)
                    },
                )
                LunoSettingsRow(
                    title = stringResource(R.string.search_engine_import),
                    shape = segmentedShape(1, 2),
                    onClick = { importPreview = parseImport() },
                )
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
