package hunoia.luno.clipboard

import android.net.Uri
import hunoia.luno.core.LunoLog as Log
import hunoia.luno.bridge.system.ClipboardContent
import hunoia.luno.bridge.system.SelfWriteGuard
import hunoia.luno.bridge.system.readClipboardContentDetailed
import hunoia.luno.bridge.system.writeImageToClipboard
import hunoia.luno.bridge.system.writeTextToClipboard
import hunoia.luno.bridge.system.ImageOutput
import hunoia.luno.bridge.ui.assertNotMainThread
import hunoia.luno.config.ConfigProvider
import hunoia.luno.config.model.ClipKind
import hunoia.luno.config.model.ClipboardEntry
import hunoia.luno.config.model.ClipboardHistory
import hunoia.luno.config.model.ClipboardSettings
import hunoia.luno.config.model.mergeImported
import hunoia.luno.config.model.remove
import hunoia.luno.config.model.setPinned
import hunoia.luno.config.model.withMaxEntries
import hunoia.luno.core.AppContext
import hunoia.luno.core.LOG_TAG
import hunoia.luno.shizuku.ShizukuClipboard
import hunoia.luno.shizuku.ShizukuManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 剪贴板的唯一入口（读 / 写 / 捕获 / 历史 / 固定 / 删除）。
 *
 * 业务模块不直接碰 `ClipboardManager` / `ClipData`（`ClipboardSinglePortTest` 守着），
 * 也不各写一套监听：读要 Shizuku 回退、写要打自写标记、记录要按容量与敏感规则入库，
 * 这三件事只在这里发生。
 */
object ClipboardCenter {

    private const val TAG = LOG_TAG

    /** 敏感文本的会话分组上限；进程死即失，显式固定才落盘。 */
    private const val MAX_SESSION_SENSITIVE = 5

    /**
     * 最近一次被读取/写入/记录的内容键。
     *
     * 捕获监听与页面刷新可能看到同一条主剪贴项（打开面板 → 再打开页面），
     * 只让第一次产生入库/脉冲，避免同内容重复置顶与自动化重复触发。
     */
    private var lastSeenKey: String? = null

    /** 只读历史投影，避免 UI 直连配置存储。 */
    val history: Flow<ClipboardHistory> = ConfigProvider.clipboardHistory

    /** 剪贴板设置（记录开关 / 未固定上限）。 */
    val settings: Flow<ClipboardSettings> = ConfigProvider.clipboardSettings

    private val _session = MutableStateFlow<List<ClipboardEntry>>(emptyList())

    /** 敏感文本的会话分组（内存，≤5 条）；固定后从会话移入持久历史。 */
    val session: StateFlow<List<ClipboardEntry>> = _session.asStateFlow()

    // ---------- 读 ----------

    /** 读当前主剪贴项：Shizuku 可用走 shell 桥，否则应用内直读。 */
    suspend fun read(): ClipboardContent? = withContext(Dispatchers.IO) {
        assertNotMainThread("clipboard.read")
        val status = runCatching { ShizukuManager.currentStatus() }.getOrNull()
        if (status?.binderAlive == true && status.permissionGranted) {
            val viaShizuku = runCatching { ShizukuClipboard.readContent() }
                .onFailure { Log.w(TAG, "clipboard read via shizuku failed: ${it.message}") }
                .getOrNull()
            if (viaShizuku != null) return@withContext viaShizuku
        }
        runCatching { readClipboardContentDetailed(AppContext.get()) }.getOrNull()
    }

    /** 兼容旧调用名：与 [read] 同义。 */
    suspend fun readCurrent(): ClipboardContent? = read()

    /**
     * 补一次捕获（页面 / 面板 / 球打开时调用，幂等），并返回读到的内容。
     *
     * 新 ROM 后台不投递剪贴板回调，只能在这些用户可见时机主动读一次；
     * 读到与上次相同的内容不会重复入库。需要同时拿“当前项”的调用方（如快捷搜索空态）
     * 直接消费返回值，避免先 read 再 refresh 读两次。
     */
    suspend fun refresh(): ClipboardContent? {
        val content = read() ?: return null
        record(content)
        return content
    }

    // ---------- 写 ----------

    /**
     * 唯一写入口：应用直写优先，失败再让 shell 兜底（图片不做 shell 写）。
     * 写入后打自写标记、按规则入库（敏感文本进会话、普通文本/图片进历史）。
     */
    suspend fun write(
        content: ClipboardContent,
        label: String? = null,
        /** 写回历史条目时为 false：内容已在库里，不该再落一份副本 / 再置顶一次。 */
        record: Boolean = true,
    ): Boolean = withContext(Dispatchers.IO) {
        assertNotMainThread("clipboard.write")
        val context = AppContext.get()
        val resolvedLabel = label ?: clipboardLabel()
        val written = when (content) {
            is ClipboardContent.Text ->
                writeTextToClipboard(context, resolvedLabel, content.text, content.sensitive) ||
                    ShizukuClipboard.writeContent(content)
            is ClipboardContent.Image ->
                writeImageToClipboard(context, resolvedLabel, content.uri)
        }
        if (written) {
            lastSeenKey = keyOf(content)
            if (record) ingest(content)
        }
        written
    }

    /** 兼容旧调用名：普通文本写回。 */
    suspend fun copy(text: String, sensitive: Boolean = false, label: String? = null): Boolean {
        if (text.isEmpty()) return false
        return write(ClipboardContent.Text(text, sensitive), label)
    }

    /**
     * 同步写文本（无障碍粘贴回退等非 suspend 调用方）。
     *
     * 只做平台写入与自写标记；入库在应用作用域异步完成，不阻塞调用线程。
     */
    fun writeTextImmediate(text: String, label: String?, sensitive: Boolean = false): Boolean {
        if (text.isEmpty()) return false
        val content = ClipboardContent.Text(text, sensitive)
        val ok = writeTextToClipboard(
            AppContext.get(),
            label ?: clipboardLabel(),
            text,
            sensitive,
        ) || ShizukuClipboard.writeContent(content)
        if (ok) {
            lastSeenKey = keyOf(content)
            AppContext.applicationScope?.launch(Dispatchers.IO) { ingest(content) }
        }
        return ok
    }

    /** 兼容旧调用名：图片写回（content URI，聊天/笔记应用可直接粘贴）。 */
    suspend fun copyImage(uri: Uri, mimeType: String = "image/*", label: String? = null): Boolean =
        write(ClipboardContent.Image(uri, mimeType), label)

    /**
     * 写回一条历史条目。
     *
     * 图片条目存的是私有目录副本，粘贴方读不到私有文件，所以先落一份图库图再写 URI
     * （与标注导出同口径）；写回本身不再入库，避免每点一次就多一份副本。
     */
    suspend fun copyEntry(entry: ClipboardEntry): Boolean = when (entry.kind) {
        ClipKind.TEXT -> if (entry.text.isBlank()) {
            false
        } else {
            write(ClipboardContent.Text(entry.text, entry.sensitive), record = false)
        }
        ClipKind.IMAGE -> {
            val name = entry.imagePath
            val context = AppContext.get()
            if (name.isNullOrBlank()) {
                false
            } else {
                val bitmap = withContext(Dispatchers.IO) { ClipboardImageStore.load(context, name) }
                if (bitmap == null) {
                    false
                } else {
                    try {
                        val uri = withContext(Dispatchers.IO) {
                            ImageOutput.saveToGallery(context, bitmap, "clipboard")
                        }
                        if (uri == null) {
                            false
                        } else {
                            // 写回历史条目不再入库：否则每点一次图片就多一份历史副本。
                            write(
                                ClipboardContent.Image(uri, entry.mime ?: "image/*"),
                                record = false,
                            )
                        }
                    } finally {
                        bitmap.recycle()
                    }
                }
            }
        }
    }

    /** 图片条目的本地副本文件（UI 渲染缩略图用）；文件缺失返回 null。 */
    fun imageFile(name: String?): java.io.File? =
        name?.takeIf { it.isNotBlank() }?.let { ClipboardImageStore.file(AppContext.get(), it) }

    private fun clipboardLabel(): String = runCatching {
        val context = AppContext.get()
        context.applicationInfo.loadLabel(context.packageManager).toString()
    }.getOrDefault("Luno")

    // ---------- 捕获 ----------

    /** 把读到的内容按规则入库；同内容只记一次。 */
    suspend fun record(content: ClipboardContent?) {
        if (content == null) return
        if (SelfWriteGuard.matches(content)) return
        val key = keyOf(content)
        if (key == lastSeenKey) return
        lastSeenKey = key
        ingest(content)
    }

    private suspend fun ingest(content: ClipboardContent) {
        // 记录开关先于一切分流：关闭后敏感项也不进会话，避免「关了还在记」的错觉。
        val settings = runCatching { ConfigProvider.getClipboardSettings() }.getOrNull()
        if (settings?.recordEnabled == false) return
        when (content) {
            is ClipboardContent.Text -> {
                if (content.text.isBlank()) return
                if (content.sensitive) {
                    addToSession(content.text, sensitive = true)
                    return
                }
                val entry = ClipboardEntry(
                    kind = ClipKind.TEXT,
                    text = content.text,
                    time = System.currentTimeMillis(),
                )
                val maxEntries = settings?.maxEntries ?: ClipboardSettings().maxEntries
                ConfigProvider.recordClipboardEntry(entry, maxEntries)?.let { pruneImages(it) }
            }
            is ClipboardContent.Image -> {
                val name = ClipboardImageStore.save(AppContext.get(), content) ?: return
                val entry = ClipboardEntry(
                    kind = ClipKind.IMAGE,
                    imagePath = name,
                    mime = content.mimeType,
                    time = System.currentTimeMillis(),
                )
                val maxEntries = settings?.maxEntries ?: ClipboardSettings().maxEntries
                ConfigProvider.recordClipboardEntry(entry, maxEntries)?.let { pruneImages(it) }
            }
        }
    }

    private fun addToSession(text: String, sensitive: Boolean) {
        val entry = ClipboardEntry(
            kind = ClipKind.TEXT,
            text = text,
            sensitive = sensitive,
            time = System.currentTimeMillis(),
        )
        _session.value = (listOf(entry) + _session.value.filterNot { it.key == entry.key })
            .take(MAX_SESSION_SENSITIVE)
    }

    // ---------- 历史操作 ----------

    /** 固定 / 取消固定；固定会置顶（固定后立刻可见）。 */
    suspend fun setPinned(id: String, pinned: Boolean) {
        ConfigProvider.updateClipboardHistory { it.setPinned(id, pinned) }
    }

    suspend fun pin(id: String) = setPinned(id, true)

    suspend fun unpin(id: String) = setPinned(id, false)

    /** 固定会话里的敏感项：落盘（sensitive = true）并从会话移出。 */
    suspend fun pinFromSession(id: String) {
        val entry = _session.value.firstOrNull { it.id == id } ?: return
        val settings = runCatching { ConfigProvider.getClipboardSettings() }.getOrNull()
        val maxEntries = settings?.maxEntries ?: ClipboardSettings().maxEntries
        val persisted = entry.copy(pinned = true, sensitive = true)
        ConfigProvider.recordClipboardEntry(persisted, maxEntries)?.let { pruneImages(it) }
        _session.value = _session.value.filterNot { it.id == id }
    }

    suspend fun remove(id: String) {
        val history = ConfigProvider.updateClipboardHistory { it.remove(id) } ?: return
        pruneImages(history)
        _session.value = _session.value.filterNot { it.id == id }
    }

    suspend fun clear() {
        ConfigProvider.clearClipboardHistory()
        pruneImages(ClipboardHistory())
        _session.value = emptyList()
    }

    /**
     * JSON 导入：按 trim 去重合并，保留导入项的固定标记；返回导入后的总条数。
     *
     * 规则在 `ClipboardHistory.mergeImported`（纯函数，可单测），这里只负责读设置、
     * 落盘与清理被裁剪掉的图片副本。
     */
    suspend fun mergeImported(imported: List<hunoia.luno.config.model.ClipboardExportEntry>): Int {
        if (imported.isEmpty()) return history.first().entries.size
        val settings = runCatching { ConfigProvider.getClipboardSettings() }.getOrNull()
        val maxEntries = settings?.maxEntries ?: ClipboardSettings().maxEntries
        val updated = ConfigProvider.updateClipboardHistory {
            it.mergeImported(imported, System.currentTimeMillis(), maxEntries)
        } ?: return history.first().entries.size
        pruneImages(updated)
        return updated.entries.size
    }

    // ---------- 设置 ----------

    suspend fun updateSettings(transform: suspend (ClipboardSettings) -> ClipboardSettings) {
        ConfigProvider.updateClipboardSettings(transform)
        val max = ConfigProvider.getClipboardSettings().maxEntries
        ConfigProvider.updateClipboardHistory { it.withMaxEntries(max) }?.let { pruneImages(it) }
    }

    /** 启动 / 写入后清理没有条目引用的图片副本（条目删除、裁剪、清空、崩溃残留）。 */
    suspend fun pruneImages() {
        val current = runCatching { history.first() }.getOrNull() ?: return
        pruneImages(current)
    }

    private fun pruneImages(history: ClipboardHistory) {
        val keep = history.entries.mapNotNull { it.imagePath }.toSet()
        runCatching { ClipboardImageStore.prune(AppContext.get(), keep) }
    }

    private fun keyOf(content: ClipboardContent): String = when (content) {
        is ClipboardContent.Text -> "t:${content.text.trim()}"
        is ClipboardContent.Image -> "i:${content.uri}"
    }

    /** 启动时清理孤儿图片文件；由进程入口调用一次即可。 */
    suspend fun onProcessStart() {
        pruneImages()
    }
}
