package hunoia.luno.clipboard

import android.content.Context
import android.net.Uri
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import hunoia.luno.bridge.system.FileOutput
import hunoia.luno.bridge.system.shareText
import hunoia.luno.clipboard.R
import hunoia.luno.clipboard.ClipboardCenter
import hunoia.luno.config.model.ClipKind
import hunoia.luno.config.model.ClipboardEntry
import hunoia.luno.config.model.ClipboardExport
import hunoia.luno.config.model.ClipboardExportVersion
import hunoia.luno.config.model.ClipboardHistory
import hunoia.luno.config.model.ClipboardMaxEntriesRange
import hunoia.luno.config.model.ClipboardSettings
import hunoia.luno.config.model.exportableEntries
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 页面唯一状态（architecture §8.4）：历史 / 会话 / 设置 / 筛选 / 批量选择 / 弹窗都在这。 */
data class ClipboardUiState(
    val entries: List<ClipboardEntry> = emptyList(),
    val session: List<ClipboardEntry> = emptyList(),
    val settings: ClipboardSettings = ClipboardSettings(),
    val query: String = "",
    val selectionMode: Boolean = false,
    val selectedIds: Set<String> = emptySet(),
    val revealedIds: Set<String> = emptySet(),
    val clearConfirm: Boolean = false,
    val pendingSensitiveCopy: ClipboardEntry? = null,
)

/** 一次性反馈（Toast）；不进状态对象，避免重组时重复弹。 */
data class ClipboardMessage(@StringRes val resId: Int, val arg: Int? = null)

class ClipboardVM : ViewModel() {

    private data class Local(
        val query: String = "",
        val selectionMode: Boolean = false,
        val selectedIds: Set<String> = emptySet(),
        val revealedIds: Set<String> = emptySet(),
        val clearConfirm: Boolean = false,
        val pendingSensitiveCopy: ClipboardEntry? = null,
    )

    private val local = MutableStateFlow(Local())

    private val _messages = MutableSharedFlow<ClipboardMessage>(extraBufferCapacity = 4)
    val messages = _messages.asSharedFlow()

    val uiState = combine(
        ClipboardCenter.history,
        ClipboardCenter.session,
        ClipboardCenter.settings,
        local,
    ) { history, session, settings, local ->
        ClipboardUiState(
            entries = history.entries,
            session = session,
            settings = settings,
            query = local.query,
            selectionMode = local.selectionMode,
            selectedIds = local.selectedIds,
            revealedIds = local.revealedIds,
            clearConfirm = local.clearConfirm,
            pendingSensitiveCopy = local.pendingSensitiveCopy,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ClipboardUiState())

    /** 进入页面补捕获一次；监听由常驻服务统一做，这里只覆盖「后台没收到回调」的机型。 */
    fun refresh() {
        viewModelScope.launch { runCatching { ClipboardCenter.refresh() } }
    }

    fun setQuery(query: String) = local.update { it.copy(query = query) }

    fun toggleSelectionMode() = local.update {
        it.copy(selectionMode = !it.selectionMode, selectedIds = emptySet())
    }

    fun toggleSelected(id: String) = local.update {
        val next = if (id in it.selectedIds) it.selectedIds - id else it.selectedIds + id
        it.copy(selectedIds = next)
    }

    fun selectAll(ids: Collection<String>) = local.update {
        it.copy(selectedIds = ids.toSet())
    }

    fun reveal(id: String) = local.update { it.copy(revealedIds = it.revealedIds + id) }

    /** 点按条目：普通条目直接写回；敏感条目先显示，再点一次才进入二次确认。 */
    fun onEntryClick(entry: ClipboardEntry) {
        val state = uiState.value
        if (state.selectionMode) {
            toggleSelected(entry.id)
            return
        }
        if (entry.sensitive && entry.id !in state.revealedIds) {
            reveal(entry.id)
            return
        }
        if (entry.sensitive) {
            local.update { it.copy(pendingSensitiveCopy = entry) }
            return
        }
        copy(entry)
    }

    fun copy(entry: ClipboardEntry) {
        viewModelScope.launch {
            val ok = runCatching { ClipboardCenter.copyEntry(entry) }.getOrDefault(false)
            _messages.emit(
                ClipboardMessage(if (ok) R.string.clipboard_copy_success else R.string.clipboard_copy_failed),
            )
        }
    }

    fun confirmSensitiveCopy() {
        val entry = local.value.pendingSensitiveCopy ?: return
        local.update { it.copy(pendingSensitiveCopy = null) }
        copy(entry)
    }

    fun dismissSensitiveCopy() = local.update { it.copy(pendingSensitiveCopy = null) }

    fun togglePin(entry: ClipboardEntry) {
        viewModelScope.launch {
            runCatching { ClipboardCenter.setPinned(entry.id, !entry.pinned) }
        }
    }

    fun pinSession(entry: ClipboardEntry) {
        viewModelScope.launch {
            runCatching { ClipboardCenter.pinFromSession(entry.id) }
        }
    }

    fun delete(entry: ClipboardEntry) {
        viewModelScope.launch { runCatching { ClipboardCenter.remove(entry.id) } }
    }

    fun deleteSelected() {
        val ids = local.value.selectedIds
        if (ids.isEmpty()) return
        viewModelScope.launch {
            ids.forEach { id -> runCatching { ClipboardCenter.remove(id) } }
            local.update { it.copy(selectedIds = emptySet(), selectionMode = false) }
        }
    }

    fun pinSelected() {
        val state = uiState.value
        val targets = state.entries.filter { it.id in state.selectedIds && !it.pinned }
        if (targets.isEmpty()) return
        viewModelScope.launch {
            targets.forEach { entry ->
                runCatching { ClipboardCenter.setPinned(entry.id, true) }
            }
            local.update { it.copy(selectedIds = emptySet(), selectionMode = false) }
        }
    }

    fun requestClear() = local.update { it.copy(clearConfirm = true) }

    fun dismissClear() = local.update { it.copy(clearConfirm = false) }

    fun confirmClear() {
        local.update { it.copy(clearConfirm = false) }
        viewModelScope.launch { runCatching { ClipboardCenter.clear() } }
    }

    fun setRecordEnabled(enabled: Boolean) {
        viewModelScope.launch {
            ClipboardCenter.updateSettings { it.copy(recordEnabled = enabled) }
        }
    }

    fun setMaxEntries(value: Int) {
        val clamped = value.coerceIn(ClipboardMaxEntriesRange.first, ClipboardMaxEntriesRange.last)
        viewModelScope.launch {
            ClipboardCenter.updateSettings { it.copy(maxEntries = clamped) }
        }
    }

    fun share(context: Context, entry: ClipboardEntry) {
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) {
                when (entry.kind) {
                    ClipKind.TEXT -> shareText(context, entry.text)
                    ClipKind.IMAGE -> {
                        val file = ClipboardCenter.imageFile(entry.imagePath)
                        file != null && FileOutput.share(
                            context,
                            file,
                            entry.mime ?: "image/*",
                            file.name,
                        )
                    }
                }
            }
            if (!ok) _messages.emit(ClipboardMessage(R.string.clipboard_share_failed))
        }
    }

    fun export(context: Context, uri: Uri) {
        viewModelScope.launch {
            val exportable = ClipboardHistory(uiState.value.entries).exportableEntries()
            if (exportable.isEmpty()) {
                _messages.emit(ClipboardMessage(R.string.clipboard_export_empty))
                return@launch
            }
            val bytes = runCatching {
                json.encodeToString(ClipboardExport(entries = exportable)).toByteArray()
            }.getOrNull()
            if (bytes == null || bytes.size > MaxJsonBytes) {
                _messages.emit(ClipboardMessage(R.string.clipboard_export_failed))
                return@launch
            }
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openOutputStream(uri)?.use { it.write(bytes) } != null
                }.getOrDefault(false)
            }
            if (!ok) _messages.emit(ClipboardMessage(R.string.clipboard_export_failed))
        }
    }

    fun import(context: Context, uri: Uri) {
        viewModelScope.launch {
            val bytes = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        val out = ByteArrayOutputStream()
                        val buffer = ByteArray(64 * 1024)
                        while (true) {
                            val read = input.read(buffer)
                            if (read < 0) break
                            if (out.size() + read > MaxJsonBytes) return@use null
                            out.write(buffer, 0, read)
                        }
                        out.toByteArray()
                    }
                }.getOrNull()
            }
            val export = bytes
                ?.let { String(it) }
                ?.let { text -> runCatching { json.decodeFromString<ClipboardExport>(text) }.getOrNull() }
            if (export == null || export.version != ClipboardExportVersion) {
                _messages.emit(ClipboardMessage(R.string.clipboard_import_failed))
                return@launch
            }
            val total = runCatching { ClipboardCenter.mergeImported(export.entries) }.getOrNull()
            _messages.emit(
                if (total == null) {
                    ClipboardMessage(R.string.clipboard_import_failed)
                } else {
                    ClipboardMessage(R.string.clipboard_import_success, total)
                },
            )
        }
    }

    companion object {
        /** 导入 / 导出上限：1MB，与规格一致；解析前先按字节拦。 */
        const val MaxJsonBytes = 1024 * 1024

        private val json = Json {
            encodeDefaults = true
            ignoreUnknownKeys = true
            prettyPrint = true
        }

        private val timeFormat = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault())

        fun formatTime(time: Long): String =
            if (time <= 0L) "" else timeFormat.format(Date(time))
    }
}
