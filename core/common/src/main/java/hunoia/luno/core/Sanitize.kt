package hunoia.luno.core

/**
 * 保留 `A-Za-z0-9._-`，其余字符替换为 `_`。
 *
 * 用于拼进文件名 / 缓存键的片段（日志进程标签、快捷方式图标缓存键），防路径分隔符
 * 与平台不允许的字符混进去。调用方按需自行截断长度。
 */
fun sanitizeFileNameSegment(raw: String): String =
    raw.replace(Regex("[^A-Za-z0-9._-]"), "_")

/**
 * 进程标签：主进程 `main`，私有进程取 `:` 后缀并消毒（`unknown` = 取不到进程名）。
 *
 * 日志（LunoLog）与事故记录（IncidentLog）的文件名共用这一份口径。
 */
fun currentProcessLabel(): String {
    val name = runCatching { android.app.Application.getProcessName() }.getOrNull().orEmpty()
    if (name.isEmpty()) return "unknown"
    if (!name.contains(':')) return "main"
    return name.substringAfter(':').ifBlank { "process" }.let(::sanitizeFileNameSegment)
}
