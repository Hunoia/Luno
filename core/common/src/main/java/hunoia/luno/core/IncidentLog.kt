package hunoia.luno.core

import android.app.Application
import android.util.Log as AndroidLog
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

/** 故障类型：崩溃、系统判定的 ANR/native crash，以及代码里主动记录的 ERROR。 */
enum class IncidentKind {
    CRASH,
    ANR,
    NATIVE,
    ERROR;

    companion object {
        fun fromName(name: String?): IncidentKind =
            entries.firstOrNull { it.name == name?.trim() } ?: ERROR
    }
}

/** 一条故障记录；[stackTrace] 是格式化后的堆栈文本（可能为空）。 */
data class Incident(
    val timeMs: Long,
    val kind: IncidentKind,
    val process: String,
    val thread: String,
    val tag: String,
    val message: String,
    val stackTrace: String,
)

/** 故障记录汇总，供设置页展示。 */
data class IncidentSummary(
    val count: Int,
    val lastTimeMs: Long,
    val sizeBytes: Long,
)

/**
 * 严重故障的独立落盘 + 通知入口。
 *
 * 与 [LunoLog] 的区别：
 * - 不受运行日志开关/级别影响，崩溃与 ERROR 始终记录；
 * - 文件位于 `filesDir/diagnostics/incidents.log`，不随缓存清理消失；
 * - 崩溃路径同步写入，保证进程死亡前落盘；
 * - 通知去重/限频，避免死循环错误刷屏。
 *
 * 通知实现由 :app 注入 [notifier]（需要应用资源与 MainActivity 导航），
 * `core:common` 不反向依赖 app。
 */
object IncidentLog {

    private const val DIR_NAME = "diagnostics"
    private const val FILE_NAME = "incidents.log"
    private const val NOTIFY_DISABLED_MARKER = "notify_disabled"
    private const val MAX_BYTES = 512 * 1024L
    private const val MAX_BACKUPS = 3

    private const val NOTIFY_DEDUPE_MS = 10 * 60 * 1000L
    private const val NOTIFY_WINDOW_MS = 10 * 60 * 1000L
    private const val NOTIFY_MAX_PER_WINDOW = 3

    private const val QUEUE_CAPACITY = 512

    private val writer = ThreadPoolExecutor(
        1,
        1,
        0L,
        TimeUnit.MILLISECONDS,
        ArrayBlockingQueue(QUEUE_CAPACITY),
        { runnable -> Thread(runnable, "luno-incident-writer").apply { isDaemon = true } },
        ThreadPoolExecutor.DiscardOldestPolicy(),
    )

    private val writeLock = Any()
    private val notifyLock = Any()
    private val recentNotifications = ArrayDeque<Long>()
    private val lastNotifyByFingerprint = HashMap<String, Long>()

    /** 由 :app 注入；null 表示当前进程不发送通知（例如测试或初始化早期）。 */
    @Volatile
    var notifier: ((Incident) -> Unit)? = null

    @Volatile
    private var notifyCheckedAt = 0L

    @Volatile
    private var notifyCached = true

    private val processLabel: String by lazy {
        val name = runCatching { Application.getProcessName() }.getOrNull().orEmpty()
        when {
            name.isEmpty() -> "unknown"
            !name.contains(':') -> "main"
            else -> name.substringAfter(':').ifBlank { "process" }
                .replace(Regex("[^A-Za-z0-9._-]"), "_")
        }
    }

    /** 记录一次崩溃：同步写入后再交给系统默认处理器，避免进程死亡时丢记录。 */
    fun recordCrash(throwable: Throwable, thread: Thread) {
        record(
            kind = IncidentKind.CRASH,
            tag = "Crash",
            message = throwable.toString(),
            throwable = throwable,
            threadName = thread.name,
            sync = true,
        )
    }

    /** 记录一条普通 ERROR（由 [LunoLog] 自动调用）。 */
    fun recordError(tag: String, message: String, throwable: Throwable?) {
        record(IncidentKind.ERROR, tag, message, throwable)
    }

    /** 主动上报重要错误（没有经过 LunoLog 的路径）。 */
    fun report(tag: String, message: String, throwable: Throwable? = null) {
        record(IncidentKind.ERROR, tag, message, throwable)
    }

    /** 记录 ANR / native crash 等系统侧故障。 */
    fun recordSystem(kind: IncidentKind, tag: String, message: String, stackTrace: String) {
        record(
            kind = kind,
            tag = tag,
            message = message,
            throwable = null,
            stackTraceOverride = stackTrace,
        )
    }

    fun record(
        kind: IncidentKind,
        tag: String,
        message: String,
        throwable: Throwable? = null,
        threadName: String = Thread.currentThread().name,
        stackTraceOverride: String? = null,
        sync: Boolean = false,
    ): Incident? {
        val dir = directory() ?: return null
        val incident = Incident(
            timeMs = System.currentTimeMillis(),
            kind = kind,
            process = processLabel,
            thread = threadName,
            tag = tag,
            message = message,
            stackTrace = stackTraceOverride ?: throwable?.let(::stackTraceOf).orEmpty(),
        )
        val block = IncidentFormat.format(incident)
        if (sync) {
            flush()
            appendBlock(dir, block)
        } else {
            enqueue { appendBlock(dir, block) }
        }
        maybeNotify(incident)
        return incident
    }

    // ---- 文件管理（供 LogCenter 使用）----

    fun directory(): File? = runCatching {
        File(AppContext.get().filesDir, DIR_NAME)
    }.getOrNull()

    fun files(): List<File> {
        val dir = directory() ?: return emptyList()
        return dir.listFiles()
            ?.filter { it.isFile && it.name.startsWith("$FILE_NAME") }
            ?.sortedBy { it.name }
            .orEmpty()
    }

    fun summary(): IncidentSummary {
        flush()
        val all = readEntries()
        return IncidentSummary(
            count = all.size,
            lastTimeMs = all.maxOfOrNull { it.timeMs } ?: 0L,
            sizeBytes = files().sumOf { it.length() },
        )
    }

    /** 最近 [maxEntries] 条记录，按时间倒序（最新在前）的展示文本。 */
    fun readTailText(maxEntries: Int): String {
        if (maxEntries <= 0) return ""
        flush()
        return readEntries()
            .takeLast(maxEntries)
            .asReversed()
            .joinToString("\n") { IncidentFormat.format(it) }
    }

    fun clear() {
        flush()
        files().forEach { runCatching { it.delete() } }
    }

    fun isNotifyEnabled(): Boolean {
        val now = System.currentTimeMillis()
        if (now - notifyCheckedAt < 1_000L) return notifyCached
        val dir = directory()
        notifyCached = dir == null || !File(dir, NOTIFY_DISABLED_MARKER).exists()
        notifyCheckedAt = now
        return notifyCached
    }

    fun setNotifyEnabled(enabled: Boolean) {
        val dir = directory() ?: return
        runCatching {
            dir.mkdirs()
            val marker = File(dir, NOTIFY_DISABLED_MARKER)
            if (enabled) marker.delete() else marker.createNewFile()
        }
        notifyCached = enabled
        notifyCheckedAt = System.currentTimeMillis()
    }

    /** 等待当前进程已入队故障写完；导出/清空前调用。 */
    fun flush(timeoutMs: Long = 800L) {
        runCatching { writer.submit {} }.getOrNull()
            ?.let { runCatching { it.get(timeoutMs, TimeUnit.MILLISECONDS) } }
    }

    // ---- 实现 ----

    private fun readEntries(): List<Incident> {
        val text = StringBuilder()
        files().sortedWith(
            compareByDescending<File> { numericSuffix(it) }
                .thenBy { if (numericSuffix(it) == 0) 1 else 0 },
        ).forEach { file ->
            runCatching { text.append(file.readText()) }
        }
        return IncidentFormat.parse(text.toString())
    }

    /** `.3`（最旧）→ `.2` → `.1` → 当前文件；用后缀排序，不依赖可能同秒的 mtime。 */
    private fun numericSuffix(file: File): Int =
        file.name.substringAfterLast('.', "").toIntOrNull() ?: 0

    private fun enqueue(task: () -> Unit) {
        runCatching { writer.execute { runCatching(task) } }
    }

    private fun appendBlock(dir: File, block: String) {
        synchronized(writeLock) {
            if (!dir.isDirectory && !dir.mkdirs()) return
            val file = File(dir, FILE_NAME)
            val bytes = block.toByteArray(Charsets.UTF_8)
            if (file.isFile && file.length() + bytes.size > MAX_BYTES) rotate(file)
            file.appendBytes(bytes)
        }
    }

    private fun rotate(file: File) {
        File(file.parentFile, "${file.name}.$MAX_BACKUPS").delete()
        for (index in MAX_BACKUPS - 1 downTo 1) {
            val from = File(file.parentFile, "${file.name}.$index")
            if (from.isFile) from.renameTo(File(file.parentFile, "${file.name}.${index + 1}"))
        }
        file.renameTo(File(file.parentFile, "${file.name}.1"))
    }

    private fun maybeNotify(incident: Incident) {
        if (!isNotifyEnabled()) return
        val callback = notifier ?: return
        val now = System.currentTimeMillis()
        val fingerprint = IncidentFormat.fingerprint(incident)
        synchronized(notifyLock) {
            val last = lastNotifyByFingerprint[fingerprint]
            if (last != null && now - last < NOTIFY_DEDUPE_MS) return
            while (recentNotifications.isNotEmpty() &&
                now - recentNotifications.first() > NOTIFY_WINDOW_MS
            ) {
                recentNotifications.removeFirst()
            }
            if (recentNotifications.size >= NOTIFY_MAX_PER_WINDOW) return
            lastNotifyByFingerprint[fingerprint] = now
            recentNotifications.addLast(now)
            if (lastNotifyByFingerprint.size > 64) {
                lastNotifyByFingerprint.entries.removeAll { now - it.value > NOTIFY_DEDUPE_MS }
            }
        }
        runCatching { callback(incident) }
            .onFailure { AndroidLog.e("IncidentLog", "notify failed", it) }
    }

    private fun stackTraceOf(throwable: Throwable): String {
        val writer = StringWriter()
        PrintWriter(writer).use { throwable.printStackTrace(it) }
        return writer.toString().trimEnd()
    }
}
