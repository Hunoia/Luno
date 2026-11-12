package hunoia.luno.domain.log

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.Context
import android.util.Log
import hunoia.luno.core.IncidentKind
import hunoia.luno.core.IncidentLog
import java.io.File

/**
 * 回收 Java `UncaughtExceptionHandler` 看不到的进程退出：
 * ANR、native crash 由系统记录在 historical exit reasons 里，下次进程启动时补记并通知。
 *
 * 用 `diagnostics/exit_scan.lock` 目录做跨进程互斥（`mkdir` 原子），
 * 再用 `exit_scan_last` 记录上次扫描时间，避免主进程与 `:service` 重复上报。
 */
object IncidentExitScanner {

    private const val TAG = "IncidentExitScanner"

    private const val MARKER = "exit_scan_last"
    private const val LOCK = "exit_scan.lock"
    private const val LOCK_STALE_MS = 60 * 1000L
    private const val FIRST_SCAN_WINDOW_MS = 10 * 60 * 1000L
    private const val MAX_TRACE_CHARS = 64 * 1024
    private const val MAX_REASONS = 20

    fun scan(context: Context) {
        val app = context.applicationContext
        val dir = IncidentLog.directory() ?: return
        if (!dir.isDirectory && !dir.mkdirs()) return
        val lock = File(dir, LOCK)
        if (!tryLock(lock)) return
        try {
            val manager = app.getSystemService(ActivityManager::class.java) ?: return
            val marker = File(dir, MARKER)
            // 首次扫描只看最近 10 分钟：避免升级/首次安装时把历史 ANR 一次性全部翻出来。
            val last = if (marker.exists()) {
                readMarker(marker)
            } else {
                System.currentTimeMillis() - FIRST_SCAN_WINDOW_MS
            }
            val reasons = runCatching {
                manager.getHistoricalProcessExitReasons(app.packageName, 0, MAX_REASONS)
            }.getOrNull().orEmpty()

            var newest = last
            reasons.forEach { info ->
                newest = maxOf(newest, info.timestamp)
                if (info.timestamp <= last) return@forEach
                val kind = when (info.reason) {
                    ApplicationExitInfo.REASON_ANR -> IncidentKind.ANR
                    ApplicationExitInfo.REASON_CRASH_NATIVE -> IncidentKind.NATIVE
                    else -> return@forEach
                }
                IncidentLog.recordSystem(
                    kind = kind,
                    tag = "ProcessExit",
                    message = "reason=${reasonName(info.reason)} " +
                        "importance=${info.importance} " +
                        "description=${info.description.orEmpty()}",
                    stackTrace = readTrace(info),
                )
            }
            writeMarker(marker, newest)
        } catch (t: Throwable) {
            Log.e(TAG, "scan failed", t)
        } finally {
            runCatching { lock.delete() }
        }
    }

    private fun tryLock(lock: File): Boolean {
        if (lock.mkdir()) return true
        // 上次扫描途中进程死亡会留下锁；超过阈值视为陈旧锁，清掉重试一次。
        if (System.currentTimeMillis() - lock.lastModified() > LOCK_STALE_MS) {
            runCatching { lock.delete() }
            return lock.mkdir()
        }
        return false
    }

    private fun readMarker(file: File): Long =
        runCatching { file.readText().trim().toLong() }.getOrDefault(0L)

    private fun writeMarker(file: File, value: Long) {
        runCatching {
            val tmp = File(file.parentFile, "${file.name}.tmp")
            tmp.writeText(value.toString())
            if (!tmp.renameTo(file)) {
                file.writeText(value.toString())
                tmp.delete()
            }
        }
    }

    private fun readTrace(info: ApplicationExitInfo): String = runCatching {
        info.traceInputStream?.bufferedReader()?.use { reader ->
            val text = reader.readText()
            if (text.length > MAX_TRACE_CHARS) text.take(MAX_TRACE_CHARS) else text
        }
    }.getOrNull().orEmpty()

    private fun reasonName(reason: Int): String = when (reason) {
        ApplicationExitInfo.REASON_ANR -> "ANR"
        ApplicationExitInfo.REASON_CRASH_NATIVE -> "CRASH_NATIVE"
        ApplicationExitInfo.REASON_CRASH -> "CRASH"
        ApplicationExitInfo.REASON_LOW_MEMORY -> "LOW_MEMORY"
        ApplicationExitInfo.REASON_SIGNALED -> "SIGNALED"
        ApplicationExitInfo.REASON_EXIT_SELF -> "EXIT_SELF"
        ApplicationExitInfo.REASON_INITIALIZATION_FAILURE -> "INITIALIZATION_FAILURE"
        ApplicationExitInfo.REASON_USER_REQUESTED -> "USER_REQUESTED"
        ApplicationExitInfo.REASON_OTHER -> "OTHER"
        else -> reason.toString()
    }
}
