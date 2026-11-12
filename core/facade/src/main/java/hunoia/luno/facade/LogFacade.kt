package hunoia.luno.facade

import android.content.Context
import hunoia.luno.core.LogLevel

/**
 * 运行日志门面（UI 唯一入口）。
 *
 * 不暴露日志实现/文件路径；页面只读状态并触发开关、预览、导出、清空。
 */
interface LogFacade {

    fun stats(): LogStats

    fun setRecordingEnabled(enabled: Boolean)

    /** 写入文件的日志级别；logcat 不受影响。 */
    fun setLevel(level: LogLevel)

    /** 最近日志文本，按时间倒序（最新在前）；没有日志时为空串。 */
    fun readTail(maxLines: Int): String

    fun clear()

    /** 故障记录汇总：是否通知、条数、最近时间与占用大小。 */
    fun incidentStats(): IncidentStats

    /** 最近 [maxEntries] 条故障记录，按时间倒序；没有记录时为空串。 */
    fun readIncidents(maxEntries: Int): String

    fun clearIncidents()

    /** 崩溃/ANR/ERROR 是否发送系统通知；关闭后仍然落盘。 */
    fun setIncidentNotifyEnabled(enabled: Boolean)

    /**
     * 打包主进程与 `:service` 的日志（附设备信息）到缓存 zip，并拉起系统分享。
     * 返回是否成功拉起分享；失败时页面提示。
     */
    suspend fun exportAndShare(context: Context): Boolean
}

data class LogFileInfo(
    val name: String,
    val sizeBytes: Long,
)

data class LogStats(
    val enabled: Boolean = true,
    val level: LogLevel = LogLevel.DEFAULT,
    val files: List<LogFileInfo> = emptyList(),
) {
    val totalBytes: Long get() = files.sumOf { it.sizeBytes }
}

/** 故障记录页数据；[lastTimeMs] 为 0 表示还没有记录。 */
data class IncidentStats(
    val notifyEnabled: Boolean = true,
    val count: Int = 0,
    val lastTimeMs: Long = 0L,
    val sizeBytes: Long = 0L,
)
