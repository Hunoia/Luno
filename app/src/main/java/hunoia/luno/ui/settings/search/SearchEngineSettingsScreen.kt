package hunoia.luno.ui.settings.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import hunoia.luno.R
import hunoia.luno.bridge.system.copyText
import hunoia.luno.bridge.system.readClipboardText
import hunoia.luno.bridge.ui.showToast
import hunoia.luno.core.JsonSerializer
import hunoia.luno.domain.search.SearchCenter
import hunoia.luno.config.model.QuickSearchSettings
import hunoia.luno.config.model.SearchEngine
import hunoia.luno.config.model.SearchLaunch
import hunoia.luno.config.model.allEngines
import hunoia.luno.config.model.badgeText
import hunoia.luno.config.model.effectiveEngines
import hunoia.luno.config.model.effectiveLaunch
import hunoia.luno.config.model.effectiveTrigger
import hunoia.luno.quicklaunch.query.SearchSourceRegistry
import kotlinx.coroutines.launch
import hunoia.luno.ui.component.TextIcon
import hunoia.luno.ui.component.feedback.EmptyState
import hunoia.luno.ui.component.row.LunoGroup
import hunoia.luno.ui.component.row.LunoSettingsRow
import hunoia.luno.ui.component.row.LunoSwitchRow
import hunoia.luno.ui.component.row.segmentedShape
import hunoia.luno.ui.component.screen.LunoScreenLazy
import hunoia.luno.ui.component.sheet.ConfirmDialog
import hunoia.luno.ui.theme.FloatingContentBottom
import hunoia.luno.ui.theme.ListSpacing
import hunoia.luno.ui.theme.RowIconSize
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
    onNavToEdit: (String) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val density = LocalDensity.current
    val settings by SearchCenter.search.collectAsStateWithLifecycle(initialValue = QuickSearchSettings())
    var deleteTarget by remember { mutableStateOf<SearchEngine?>(null) }
    var importPreview by remember { mutableStateOf<ImportPreview?>(null) }
    val configEngines = settings.effectiveEngines()
    fun update(transform: (QuickSearchSettings) -> QuickSearchSettings) {
        scope.launch { SearchCenter.updateSearch(transform) }
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
    // 拖动排序草稿（与动作面板编辑页同一套）：null = 跟随配置，非 null = 拖动中的本地顺序。
    var pendingEngines by remember { mutableStateOf<List<SearchEngine>?>(null) }
    var draggingIndex by remember { mutableStateOf<Int?>(null) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    var dragRowHeight by remember { mutableFloatStateOf(0f) }
    val itemHeights = remember { mutableStateMapOf<Int, Float>() }
    val engines = pendingEngines ?: configEngines
    // 配置到位（拖拽已结束）后草稿退回跟随，避免编辑/删除/导入后残留旧顺序。
    LaunchedEffect(configEngines) {
        if (draggingIndex == null) pendingEngines = null
    }
    /** 松手落盘：把草稿的完整 id 顺序写入 engineOrder，主引擎 = 第一位。 */
    fun finishDrag() {
        val finalEngines = pendingEngines
        draggingIndex = null
        dragOffset = 0f
        if (finalEngines != null && finalEngines != configEngines) {
            update { current ->
                current.copy(engineOrder = finalEngines.map { it.id })
            }
        }
    }
    fun cancelDrag() {
        draggingIndex = null
        dragOffset = 0f
        pendingEngines = null
    }
    // FAB 与网格面板页同一套：64dp、primaryContainer、无 ripple、避让导航栏。
    val addFab: @Composable () -> Unit = {
        Box(modifier = Modifier.fillMaxSize()) {
            CompositionLocalProvider(LocalRippleConfiguration provides null) {
                FloatingActionButton(
                    onClick = { onNavToEdit("") },
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
    ) {
        item(key = "engines") {
            LunoGroup(title = stringResource(R.string.search_engine_group_title)) {
                if (engines.isEmpty()) {
                    EmptyState(message = stringResource(R.string.search_engine_empty_hint))
                } else {
                    // 引擎行（与动作面板编辑页同一套交互）：点行进编辑页，行尾删除按钮，
                    // 按住手柄只改本地草稿顺序（半行高阈值换位 + 跟手位移），松手才落盘。
                    engines.forEachIndexed { index, engine ->
                        val isDragging = draggingIndex == index
                        val dragHandleInteraction = remember { MutableInteractionSource() }
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
                            modifier = Modifier
                                .zIndex(if (isDragging) 1f else 0f)
                                .graphicsLayer {
                                    translationY = if (isDragging) dragOffset else 0f
                                }
                                .onGloballyPositioned { itemHeights[index] = it.size.height.toFloat() },
                            onClick = { onNavToEdit(engine.id) },
                            trailing = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(onClick = { deleteTarget = engine }) {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = stringResource(R.string.delete),
                                            tint = MaterialTheme.colorScheme.error,
                                            modifier = Modifier.size(RowIconSize),
                                        )
                                    }
                                    Box(
                                        modifier = Modifier
                                            .size(48.dp)
                                            .pointerInput(configEngines) {
                                                detectDragGestures(
                                                    onDragStart = {
                                                        draggingIndex = index
                                                        dragOffset = 0f
                                                        dragRowHeight = itemHeights[index]
                                                            ?: with(density) { 72.dp.toPx() }
                                                    },
                                                    onDrag = { change, dragAmount ->
                                                        change.consume()
                                                        var idx = draggingIndex
                                                            ?: return@detectDragGestures
                                                        dragOffset += dragAmount.y
                                                        val rowHeight =
                                                            dragRowHeight.coerceAtLeast(1f)
                                                        while (
                                                            dragOffset > rowHeight / 2f &&
                                                            idx < (pendingEngines ?: configEngines).lastIndex
                                                        ) {
                                                            val list = (pendingEngines ?: configEngines)
                                                                .toMutableList()
                                                            list.add(idx + 1, list.removeAt(idx))
                                                            pendingEngines = list
                                                            dragOffset -= rowHeight
                                                            idx += 1
                                                        }
                                                        while (
                                                            dragOffset < -rowHeight / 2f &&
                                                            idx > 0
                                                        ) {
                                                            val list = (pendingEngines ?: configEngines)
                                                                .toMutableList()
                                                            list.add(idx - 1, list.removeAt(idx))
                                                            pendingEngines = list
                                                            dragOffset += rowHeight
                                                            idx -= 1
                                                        }
                                                        draggingIndex = idx
                                                    },
                                                    onDragEnd = { finishDrag() },
                                                    onDragCancel = { cancelDrag() },
                                                )
                                            }
                                            // 吞掉点击：按手柄不应触发整行的「进入编辑」。
                                            .clickable(
                                                interactionSource = dragHandleInteraction,
                                                indication = null,
                                                onClick = {},
                                            ),
                                    contentAlignment = Alignment.Center,
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.DragHandle,
                                            contentDescription = stringResource(R.string.search_engine_drag),
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(RowIconSize),
                                        )
                                    }
                                }
                            },
                            shape = segmentedShape(index, engines.size),
                        )
                    }
                }
            }
        }
        item(key = "gap-data") { GroupGap() }
        // 内容源入口与导入/导出合一组：数据进出都在这。
        item(key = "data") {
            LunoGroup(title = stringResource(R.string.search_engine_data_group)) {
                LunoSettingsRow(
                    title = stringResource(R.string.search_source_settings),
                    trailing = { ArrowHint() },
                    shape = segmentedShape(0, 3),
                    onClick = onNavToSources,
                )
                LunoSettingsRow(
                    title = stringResource(R.string.search_engine_export),
                    shape = segmentedShape(1, 3),
                    onClick = {
                        val json = JsonSerializer.encodeToString(settings.customEngines)
                        copyText(context, "luno-search-engines", json)
                        showToast(R.string.search_engine_export_done)
                    },
                )
                LunoSettingsRow(
                    title = stringResource(R.string.search_engine_import),
                    shape = segmentedShape(2, 3),
                    onClick = { importPreview = parseImport() },
                )
            }
        }
        item(key = "gap-experience") { GroupGap() }
        // 体验组（原「结果治理与体验」子页平铺）。
        item(key = "experience") {
            LunoGroup(title = stringResource(R.string.search_experience_group)) {
                LunoSwitchRow(
                    title = stringResource(R.string.search_restore_last_query),
                    checked = settings.restoreLastQuery,
                    onCheckedChange = { v ->
                        update { it.copy(restoreLastQuery = v) }
                    },
                    shape = segmentedShape(0, 2),
                )
                LunoSwitchRow(
                    title = stringResource(R.string.search_ascii_enter),
                    checked = settings.asciiEnterSearch,
                    onCheckedChange = { v ->
                        update { it.copy(asciiEnterSearch = v) }
                    },
                    shape = segmentedShape(1, 2),
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
