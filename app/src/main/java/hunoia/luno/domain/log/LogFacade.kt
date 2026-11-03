package hunoia.luno.domain.log

import android.content.Context

/**
 * 运行日志门面（UI 唯一入口）。
 *
 * 不暴露日志实现/文件路径；页面只读状态并触发开关、预览、导出、清空。
 */
interface LogFacade {

    fun stats(): LogStats

    fun setRecordingEnabled(enabled: Boolean)

    /** 最近日志文本；没有日志时为空串。 */
    fun readTail(maxLines: Int): String

    fun clear()

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
    val files: List<LogFileInfo> = emptyList(),
) {
    val totalBytes: Long get() = files.sumOf { it.sizeBytes }
}
