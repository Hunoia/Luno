package hunoia.luno.core

import android.util.Log

/**
 * 写入文件的日志级别。只影响落盘，不影响 logcat：adb 调试仍然能看到全部级别。
 *
 * 顺序与 android.util.Log 的优先级一致，比较时直接用 [priority]。
 */
enum class LogLevel(val priority: Int) {
    VERBOSE(Log.VERBOSE),
    DEBUG(Log.DEBUG),
    INFO(Log.INFO),
    WARN(Log.WARN),
    ERROR(Log.ERROR);

    companion object {
        val DEFAULT: LogLevel = INFO

        fun fromName(name: String?): LogLevel =
            entries.firstOrNull { it.name == name?.trim() } ?: DEFAULT
    }
}
