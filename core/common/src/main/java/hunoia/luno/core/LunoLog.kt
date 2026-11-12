package hunoia.luno.core

import android.app.Application
import android.util.Log as AndroidLog
import hunoia.luno.core.common.BuildConfig
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

/**
 * 统一日志入口：仍写 logcat，同时把「当前级别及以上」异步落盘，供设置页长期回看与导出。
 *
 * release 只保留 WARN 及以上：INFO/DEBUG/VERBOSE 调用点由 R8 的
 * `-assumenosideeffects` 直接裁掉，这里再做一次运行时兜底（动态级别也走同一道闸），
 * 避免线上为调试日志付出字符串拼接、堆栈抓取与落盘成本。
 * debug 不受影响，仍输出全部级别。
 *
 * 落盘按进程分文件（`luno-main.log` / `luno-service.log`），避免两个进程并发写同一文件；
 * 单文件 1MB，保留当前 + 3 份轮转。写入走单线程有界队列，磁盘慢时丢弃最旧任务，
 * 绝不阻塞调用线程。日志目录位于 `filesDir/logs`，不随缓存清理消失。
 *
 * 级别与记录开关都用日志目录里的标记文件表达，主进程与 `:service` 共享同一份状态；
 * logcat 不受级别影响，始终输出调用方传入的全部级别。
 *
 * ERROR / ASSERT 额外进入 [IncidentLog]：独立落盘并触发故障通知，不受本对象的
 * 记录开关与级别影响（见设置页「故障记录」）。
 *
 * 页面不要直接使用本对象：UI 通过 `AppGraph.logs` 门面读取/导出。
 */
object LunoLog {

    private const val MAX_BYTES = 1024 * 1024
    private const val MAX_BACKUPS = 3
    private const val QUEUE_CAPACITY = 2048
    private const val DISABLED_MARKER = "disabled"
    private const val LEVEL_MARKER = "level"

    private val timeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS")

    private val writer = ThreadPoolExecutor(
        1,
        1,
        0L,
        TimeUnit.MILLISECONDS,
        ArrayBlockingQueue(QUEUE_CAPACITY),
        { runnable -> Thread(runnable, "luno-log-writer").apply { isDaemon = true } },
        ThreadPoolExecutor.DiscardOldestPolicy(),
    )

    private val processLabel: String by lazy(::resolveProcessLabel)

    @Volatile
    private var enabledCheckedAt = 0L

    @Volatile
    private var enabledCached = true

    @Volatile
    private var levelCheckedAt = 0L

    @Volatile
    private var levelCached = LogLevel.DEFAULT

    // ---- android.util.Log 兼容入口 ----

    fun v(tag: String, msg: String): Int = log(AndroidLog.VERBOSE, tag, msg, null)

    fun v(tag: String, msg: String, tr: Throwable): Int = log(AndroidLog.VERBOSE, tag, msg, tr)

    fun d(tag: String, msg: String): Int = log(AndroidLog.DEBUG, tag, msg, null)

    fun d(tag: String, msg: String, tr: Throwable): Int = log(AndroidLog.DEBUG, tag, msg, tr)

    fun i(tag: String, msg: String): Int = log(AndroidLog.INFO, tag, msg, null)

    fun i(tag: String, msg: String, tr: Throwable): Int = log(AndroidLog.INFO, tag, msg, tr)

    fun w(tag: String, msg: String): Int = log(AndroidLog.WARN, tag, msg, null)

    fun w(tag: String, msg: String, tr: Throwable): Int = log(AndroidLog.WARN, tag, msg, tr)

    fun e(tag: String, msg: String): Int = log(AndroidLog.ERROR, tag, msg, null)

    fun e(tag: String, msg: String, tr: Throwable): Int = log(AndroidLog.ERROR, tag, msg, tr)

    fun wtf(tag: String, msg: String): Int = log(AndroidLog.ASSERT, tag, msg, null)

    fun wtf(tag: String, msg: String, tr: Throwable): Int = log(AndroidLog.ASSERT, tag, msg, tr)

    fun isLoggable(tag: String, level: Int): Boolean = AndroidLog.isLoggable(tag, level)

    fun getStackTraceString(tr: Throwable): String = AndroidLog.getStackTraceString(tr)

    fun println(priority: Int, tag: String, msg: String): Int = log(priority, tag, msg, null)

    // ---- 日志文件管理（供 domain 门面使用）----

    /** 日志目录；`AppContext` 尚未初始化或取不到 filesDir 时返回 null。 */
    fun directory(): File? = runCatching {
        File(AppContext.get().filesDir, "logs")
    }.getOrNull()

    /** 当前进程与历史轮转文件。 */
    fun files(): List<File> {
        val dir = directory() ?: return emptyList()
        return dir.listFiles()
            ?.filter { it.isFile && it.name.startsWith("luno-") }
            ?.sortedBy { it.name }
            .orEmpty()
    }

    fun isRecordingEnabled(): Boolean {
        val now = System.currentTimeMillis()
        if (now - enabledCheckedAt < 1_000L) return enabledCached
        val dir = directory()
        enabledCached = dir == null || !File(dir, DISABLED_MARKER).exists()
        enabledCheckedAt = now
        return enabledCached
    }

    fun setRecordingEnabled(enabled: Boolean) {
        val dir = directory() ?: return
        runCatching {
            dir.mkdirs()
            val marker = File(dir, DISABLED_MARKER)
            if (enabled) marker.delete() else marker.createNewFile()
        }
        enabledCached = enabled
        enabledCheckedAt = System.currentTimeMillis()
    }

    /** 当前落盘级别；文件缺失或内容非法时回落到平台默认（debug INFO / release WARN）。 */
    fun minLevel(): LogLevel {
        val now = System.currentTimeMillis()
        if (now - levelCheckedAt < 1_000L) return levelCached
        val dir = directory()
        val stored = if (dir == null) {
            null
        } else {
            runCatching { LogLevel.fromNameOrNull(File(dir, LEVEL_MARKER).readText()) }
                .getOrNull()
        }
        levelCached = coerceSupported(stored ?: defaultLevel())
        levelCheckedAt = now
        return levelCached
    }

    fun setMinLevel(level: LogLevel) {
        val dir = directory() ?: return
        val resolved = coerceSupported(level)
        runCatching {
            dir.mkdirs()
            File(dir, LEVEL_MARKER).writeText(resolved.name)
        }
        levelCached = resolved
        levelCheckedAt = System.currentTimeMillis()
    }

    /** release 下低级别日志已从调用点裁掉，UI/标记文件里的更低级别统一抬到 WARN。 */
    private fun coerceSupported(level: LogLevel): LogLevel =
        if (!BuildConfig.DEBUG && level.priority < LogLevel.WARN.priority) {
            LogLevel.WARN
        } else {
            level
        }

    private fun defaultLevel(): LogLevel =
        if (BuildConfig.DEBUG) LogLevel.DEFAULT else LogLevel.WARN

    /** 等待当前进程已入队日志写完；导出/查看前调用。 */
    fun flush(timeoutMs: Long = 800L) {
        runCatching { writer.submit {} }.getOrNull()
            ?.let { runCatching { it.get(timeoutMs, TimeUnit.MILLISECONDS) } }
    }

    fun clear() {
        flush()
        files().forEach { runCatching { it.delete() } }
    }

    /** 读取全部进程日志的末尾 [maxLines] 行，按时间倒序（最新在前）；用于设置页预览。 */
    fun readTail(maxLines: Int): String {
        if (maxLines <= 0) return ""
        flush()
        val tail = ArrayDeque<String>()
        files()
            .sortedBy { it.lastModified() }
            .forEach { file ->
                runCatching {
                    file.useLines { lines ->
                        lines.forEach { line ->
                            if (tail.size >= maxLines) tail.removeFirst()
                            tail.addLast(line)
                        }
                    }
                }
            }
        return tail.reversed().joinToString("\n")
    }

    // ---- 实现 ----

    private fun log(priority: Int, tag: String, msg: String, tr: Throwable?): Int {
        // release 只保留 WARN 及以上；v/d/i 的调用点已由 R8 裁掉，这里兜底动态级别，
        // 同时保证早退发生在堆栈抓取 / 时间格式化 / 入队之前。
        if (!BuildConfig.DEBUG && priority < AndroidLog.WARN) return 0
        val minLevel = minLevel()
        val recordToFile = priority >= minLevel.priority
        // logcat 在 debug 输出全部级别；release 只输出 WARN 及以上。
        val writeLogcat = BuildConfig.DEBUG || priority >= AndroidLog.WARN
        if (!writeLogcat && !recordToFile) return 0
        // 单测里的 android.jar 是桩：getStackTraceString 可能返回 null，这里统一兜底。
        val stack = tr?.let { AndroidLog.getStackTraceString(it) }
        val result = if (writeLogcat) AndroidLog.println(priority, tag, msg) else 0
        if (tr != null && writeLogcat) AndroidLog.println(priority, tag, stack.orEmpty())
        // 严重错误单独进故障记录（不受运行日志开关/级别影响）；通知去重由 IncidentLog 负责。
        if (priority >= AndroidLog.ERROR) {
            runCatching { IncidentLog.recordError(tag, msg, tr) }
        }
        if (recordToFile) {
            val line = buildString {
                append(LocalDateTime.now().format(timeFormatter))
                append(' ')
                append(levelOf(priority))
                append('/')
                append(tag)
                append(": ")
                append(msg)
                if (tr != null) {
                    append('\n')
                    append(stack.orEmpty().trimEnd())
                }
                append('\n')
            }
            enqueue(line)
        }
        return result
    }

    private fun enqueue(line: String) {
        runCatching {
            writer.execute {
                runCatching { appendLine(line) }
            }
        }
    }

    private fun appendLine(line: String) {
        val dir = directory() ?: return
        if (!isRecordingEnabled()) return
        if (!dir.isDirectory && !dir.mkdirs()) return
        val file = File(dir, "luno-$processLabel.log")
        val bytes = line.toByteArray(Charsets.UTF_8)
        if (file.isFile && file.length() + bytes.size > MAX_BYTES) rotate(file)
        file.appendBytes(bytes)
    }

    private fun rotate(file: File) {
        File(file.parentFile, "${file.name}.$MAX_BACKUPS").delete()
        for (index in MAX_BACKUPS - 1 downTo 1) {
            val from = File(file.parentFile, "${file.name}.$index")
            if (from.isFile) from.renameTo(File(file.parentFile, "${file.name}.${index + 1}"))
        }
        file.renameTo(File(file.parentFile, "${file.name}.1"))
    }

    private fun levelOf(priority: Int): Char = when (priority) {
        AndroidLog.VERBOSE -> 'V'
        AndroidLog.DEBUG -> 'D'
        AndroidLog.INFO -> 'I'
        AndroidLog.WARN -> 'W'
        AndroidLog.ERROR -> 'E'
        else -> 'A'
    }

    private fun resolveProcessLabel(): String {
        val name = runCatching { Application.getProcessName() }.getOrNull().orEmpty()
        if (name.isEmpty()) return "unknown"
        if (!name.contains(':')) return "main"
        val suffix = name.substringAfter(':').ifBlank { "process" }
        return suffix.replace(Regex("[^A-Za-z0-9._-]"), "_")
    }
}
