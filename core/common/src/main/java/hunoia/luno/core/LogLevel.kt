package hunoia.luno.core

import android.util.Log

/**
 * 写入文件的日志级别。debug 下只影响落盘、不影响 logcat；release 只保留 WARN 及以上，
 * 更低级别的调用点会被 R8 裁掉，logcat 也不再输出。
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
            fromNameOrNull(name) ?: DEFAULT

        fun fromNameOrNull(name: String?): LogLevel? =
            entries.firstOrNull { it.name == name?.trim() }
    }
}
