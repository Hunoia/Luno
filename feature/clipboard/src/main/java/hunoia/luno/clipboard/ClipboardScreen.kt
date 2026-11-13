package hunoia.luno.clipboard

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import coil3.imageLoader
import hunoia.luno.bridge.system.FileOutput
import hunoia.luno.bridge.ui.showToast
import hunoia.luno.config.model.ClipKind
import hunoia.luno.config.model.ClipboardEntry
import hunoia.luno.config.model.ClipboardMaxEntriesRange
import hunoia.luno.ui.component.AppSearchBar
import hunoia.luno.ui.component.feedback.EmptyState
import hunoia.luno.ui.component.row.LunoGroup
import hunoia.luno.ui.component.row.LunoSettingsRow
import hunoia.luno.ui.component.row.LunoSwitchRow
import hunoia.luno.ui.component.row.segmentedShape
import hunoia.luno.ui.component.screen.LunoScreenLazy
import hunoia.luno.ui.component.sheet.ConfirmDialog
import hunoia.luno.ui.facade.LocalFacades
import hunoia.luno.ui.theme.CapsuleShape
import hunoia.luno.ui.theme.FloatingContentBottom
import hunoia.luno.ui.theme.PageGutter
import hunoia.luno.ui.theme.SmallShape
import kotlinx.coroutines.launch
import hunoia.luno.ui.component.PressIconButton

/**
 * 剪贴板管理页：历史唯一来源 + 固定 / 删除 / 清空 / 搜索 / 批量 / 导入导出 / 图片 OCR。
 *
 * 记录与捕获规则都在 `ClipboardCenter`：页面只消费流、发事件；进入页面补一次幂等
 * [ClipboardVM.refresh]（覆盖后台收不到系统回调的机型）。行点按 = 写回剪贴板；
 * 敏感项先显示，再点一次进入二次确认。
 */
@Composable
fun ClipboardScreen(
    onBack: () -> Unit,
    vm: ClipboardVM = viewModel(),
) {
    val state by vm.uiState.collectAsStateWithLifecycle()
    val facades = LocalFacades.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) { vm.refresh() }
    LaunchedEffect(Unit) {
        vm.messages.collect { message ->
            val text = if (message.arg == null) {
                context.getString(message.resId)
            } else {
                context.getString(message.resId, message.arg)
            }
            showToast(text)
        }
    }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri -> uri?.let { vm.export(context, it) } }
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent(),
    ) { uri -> uri?.let { vm.import(context, it) } }

    val visible = remember(state.entries, state.query) {
        val query = state.query.trim()
        if (query.isEmpty()) {
            state.entries
        } else {
            state.entries.filter { entry ->
                when (entry.kind) {
                    ClipKind.TEXT -> entry.text.contains(query, ignoreCase = true)
                    ClipKind.IMAGE -> entry.mime?.contains(query, ignoreCase = true) == true
                }
            }
        }
    }

    fun ocr(entry: ClipboardEntry) {
        val file = ClipboardCenter.imageFile(entry.imagePath) ?: return
        scope.launch {
            val ok = facades.clipboard.ocrImage(
                context,
                FileOutput.uriFor(context, file),
                entry.mime ?: "image/*",
            )
            if (!ok) showToast(R.string.clipboard_ocr_failed)
        }
    }

    @Composable
    fun EntryRow(entry: ClipboardEntry, shape: androidx.compose.ui.graphics.Shape) {
        ClipboardRow(
            entry = entry,
            shape = shape,
            selectionMode = state.selectionMode,
            selected = entry.id in state.selectedIds,
            revealed = entry.id in state.revealedIds,
            pinned = entry.pinned,
            onClick = { vm.onEntryClick(entry) },
            onTogglePin = { vm.togglePin(entry) },
            onDelete = { vm.delete(entry) },
            onShare = { vm.share(context, entry) },
            onFloat = if (entry.sensitive) {
                null
            } else {
                { facades.clipFloat.show(context, entry.id) }
            },
            onOcr = if (entry.kind == ClipKind.IMAGE) {
                { ocr(entry) }
            } else {
                null
            },
            onClearAll = { vm.requestClear() },
        )
    }

    LunoScreenLazy(
        title = stringResource(R.string.clipboard_title),
        onBack = onBack,
        bottomContentPadding = FloatingContentBottom,
        actions = {
            if (state.selectionMode) {
                PressIconButton(onClick = { vm.selectAll(visible.map { it.id }) }) {
                    Icon(
                        Icons.Default.SelectAll,
                        contentDescription = stringResource(R.string.clipboard_select_all),
                    )
                }
                PressIconButton(onClick = { vm.toggleSelectionMode() }) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = stringResource(R.string.clipboard_clear_selection),
                    )
                }
            } else {
                PressIconButton(onClick = { vm.toggleSelectionMode() }) {
                    Icon(
                        Icons.Default.Checklist,
                        contentDescription = stringResource(R.string.clipboard_select),
                    )
                }
            }
        },
        floatingContent = {
            if (state.selectionMode) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
                    SelectionBar(
                        selected = state.selectedIds.size,
                        onDelete = { vm.deleteSelected() },
                        onPin = { vm.pinSelected() },
                    )
                }
            }
        },
        dialogs = {
            if (state.clearConfirm) {
                ConfirmDialog(
                    title = stringResource(R.string.clipboard_clear_confirm_title),
                    text = stringResource(R.string.clipboard_clear_confirm_desc),
                    destructive = true,
                    onDismissRequest = { vm.dismissClear() },
                    onConfirm = { vm.confirmClear() },
                )
            }
            val pending = state.pendingSensitiveCopy
            if (pending != null) {
                ConfirmDialog(
                    title = stringResource(R.string.clipboard_sensitive_copy_title),
                    text = stringResource(R.string.clipboard_sensitive_copy_desc),
                    onDismissRequest = { vm.dismissSensitiveCopy() },
                    onConfirm = { vm.confirmSensitiveCopy() },
                )
            }
        },
    ) {
        item(key = "search") {
            AppSearchBar(
                query = state.query,
                onQueryChange = { vm.setQuery(it) },
                placeholder = stringResource(R.string.clipboard_search_hint),
            )
        }

        val showSession = state.query.isBlank() && state.session.isNotEmpty()
        if (showSession) {
            item(key = "session") {
                LunoGroup(
                    title = stringResource(R.string.clipboard_session_group),
                    subtitle = stringResource(R.string.clipboard_session_hint),
                ) {
                    state.session.forEachIndexed { index, entry ->
                        ClipboardRow(
                            entry = entry,
                            shape = segmentedShape(index, state.session.size),
                            selectionMode = false,
                            selected = false,
                            revealed = entry.id in state.revealedIds,
                            pinned = entry.pinned,
                            onClick = { vm.onEntryClick(entry) },
                            onTogglePin = { vm.pinSession(entry) },
                            onDelete = { vm.delete(entry) },
                            onShare = { vm.share(context, entry) },
                            onFloat = null,
                            onOcr = null,
                            onClearAll = null,
                        )
                    }
                }
            }
        }

        if (visible.isEmpty() && !showSession) {
            item(key = "empty") {
                EmptyState(
                    message = stringResource(
                        if (state.query.isBlank()) R.string.clipboard_empty else R.string.clipboard_empty_search,
                    ),
                )
            }
        } else {
            val pinnedVisible = visible.filter { it.pinned }
            val unpinnedVisible = visible.filterNot { it.pinned }
            if (pinnedVisible.isNotEmpty()) {
                item(key = "pinned") {
                    LunoGroup(title = stringResource(R.string.clipboard_pinned_group)) {
                        pinnedVisible.forEachIndexed { index, entry ->
                            EntryRow(entry, segmentedShape(index, pinnedVisible.size))
                        }
                    }
                }
            }
            if (unpinnedVisible.isNotEmpty()) {
                item(key = "history") {
                    LunoGroup(title = stringResource(R.string.clipboard_history_group)) {
                        unpinnedVisible.forEachIndexed { index, entry ->
                            EntryRow(entry, segmentedShape(index, unpinnedVisible.size))
                        }
                    }
                }
            }
        }

        item(key = "settings") {
            LunoGroup(title = stringResource(R.string.clipboard_settings_group)) {
                LunoSwitchRow(
                    title = stringResource(R.string.clipboard_record_enabled),
                    subtitle = stringResource(R.string.clipboard_record_enabled_hint),
                    checked = state.settings.recordEnabled,
                    onCheckedChange = { vm.setRecordEnabled(it) },
                    shape = segmentedShape(0, 2),
                )
                LunoSettingsRow(
                    title = stringResource(R.string.clipboard_max_entries),
                    shape = segmentedShape(1, 2),
                    trailing = {
                        MaxEntriesControls(
                            value = state.settings.maxEntries,
                            onValueChange = { vm.setMaxEntries(it) },
                        )
                    },
                )
            }
        }

        item(key = "manage") {
            LunoGroup(title = stringResource(R.string.clipboard_manage_group)) {
                LunoSettingsRow(
                    title = stringResource(R.string.clipboard_export),
                    subtitle = stringResource(R.string.clipboard_export_hint),
                    shape = segmentedShape(0, 3),
                    onClick = { exportLauncher.launch("luno_clipboard.json") },
                )
                LunoSettingsRow(
                    title = stringResource(R.string.clipboard_import),
                    subtitle = stringResource(R.string.clipboard_import_hint),
                    shape = segmentedShape(1, 3),
                    onClick = { importLauncher.launch("application/json") },
                )
                LunoSettingsRow(
                    title = stringResource(R.string.clipboard_clear_all),
                    subtitle = stringResource(
                        R.string.clipboard_capacity,
                        state.entries.count { it.pinned },
                        state.entries.count { !it.pinned },
                        state.settings.maxEntries,
                    ),
                    shape = segmentedShape(2, 3),
                    onClick = { vm.requestClear() },
                )
            }
        }
    }
}

/** 批量选择时底部的操作条。 */
@Composable
private fun SelectionBar(
    selected: Int,
    onDelete: () -> Unit,
    onPin: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = PageGutter, vertical = PageGutter),
        shape = CapsuleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 3.dp,
        shadowElevation = 3.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = stringResource(R.string.clipboard_selected_count, selected),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            PressIconButton(onClick = onPin) {
                Icon(Icons.Default.PushPin, contentDescription = stringResource(R.string.clipboard_pin_selected))
            }
            PressIconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.clipboard_delete_selected))
            }
        }
    }
}

/** 单条历史行：预览 + 元信息 + 三点菜单；批量模式用勾选框替代菜单。 */
@Composable
private fun ClipboardRow(
    entry: ClipboardEntry,
    shape: androidx.compose.ui.graphics.Shape,
    selectionMode: Boolean,
    selected: Boolean,
    revealed: Boolean,
    pinned: Boolean,
    onClick: () -> Unit,
    onTogglePin: () -> Unit,
    onDelete: () -> Unit,
    onShare: () -> Unit,
    onFloat: (() -> Unit)?,
    onOcr: (() -> Unit)?,
    onClearAll: (() -> Unit)?,
) {
    val context = LocalContext.current
    var menuOpen by remember { mutableStateOf(false) }
    val title = when {
        entry.sensitive && !revealed -> stringResource(R.string.clipboard_sensitive_masked)
        entry.kind == ClipKind.TEXT -> entry.text.replace('\n', ' ').trim()
        else -> stringResource(R.string.clipboard_image)
    }
    val metadata = buildList {
        if (pinned) add(stringResource(R.string.clipboard_pinned_badge))
        val time = ClipboardVM.formatTime(entry.time)
        if (time.isNotEmpty()) add(time)
    }.joinToString(" · ")

    LunoSettingsRow(
        title = title,
        subtitle = metadata,
        shape = shape,
        modifier = Modifier.fillMaxWidth(),
        leading = if (entry.kind == ClipKind.IMAGE) {
            {
                var imageFailed by remember(entry.imagePath) { mutableStateOf(false) }
                val file = remember(entry.imagePath) { ClipboardCenter.imageFile(entry.imagePath) }
                if (file != null && !imageFailed) {
                    AsyncImage(
                        model = file,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        imageLoader = context.imageLoader,
                        onError = { imageFailed = true },
                        modifier = Modifier
                            .size(40.dp)
                            .clip(SmallShape),
                    )
                } else {
                    Icon(
                        Icons.Default.Image,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                    )
                }
            }
        } else {
            null
        },
        onClick = onClick,
        trailing = {
            if (selectionMode) {
                Checkbox(checked = selected, onCheckedChange = null)
            } else {
                Box {
                    PressIconButton(onClick = { menuOpen = true }) {
                        Icon(
                            Icons.Default.MoreVert,
                            contentDescription = stringResource(hunoia.luno.design.R.string.more_options),
                            modifier = Modifier.size(20.dp),
                        )
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.clipboard_copy)) },
                            onClick = { menuOpen = false; onClick() },
                        )
                        DropdownMenuItem(
                            text = {
                                Text(
                                    stringResource(
                                        if (pinned) R.string.clipboard_unpin else R.string.clipboard_pin,
                                    ),
                                )
                            },
                            onClick = { menuOpen = false; onTogglePin() },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.clipboard_share)) },
                            onClick = { menuOpen = false; onShare() },
                        )
                        if (onFloat != null) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.clipboard_float)) },
                                onClick = { menuOpen = false; onFloat() },
                            )
                        }
                        if (onOcr != null) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.clipboard_ocr)) },
                                onClick = { menuOpen = false; onOcr() },
                            )
                        }
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.clipboard_delete)) },
                            onClick = { menuOpen = false; onDelete() },
                        )
                        if (onClearAll != null) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.clipboard_clear_all)) },
                                onClick = { menuOpen = false; onClearAll() },
                            )
                        }
                    }
                }
            }
        },
    )
}

/** 上限调整：滑杆粒度太细，这里用 10 条一档的“减 / 加”按钮。 */
@Composable
private fun MaxEntriesControls(
    value: Int,
    onValueChange: (Int) -> Unit,
) {
    val min = ClipboardMaxEntriesRange.first
    val max = ClipboardMaxEntriesRange.last
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        PressIconButton(onClick = { onValueChange((value - 10).coerceAtLeast(min)) }, enabled = value > min) {
            Text("−", style = MaterialTheme.typography.titleLarge)
        }
        Text(
            text = stringResource(R.string.clipboard_max_entries_value, value),
            style = MaterialTheme.typography.bodyLarge,
        )
        PressIconButton(onClick = { onValueChange((value + 10).coerceAtMost(max)) }, enabled = value < max) {
            Text("+", style = MaterialTheme.typography.titleLarge)
        }
    }
}
