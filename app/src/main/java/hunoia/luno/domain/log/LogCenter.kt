package hunoia.luno.domain.log

import android.content.Context
import hunoia.luno.bridge.system.FileOutput
import hunoia.luno.core.IncidentLog
import hunoia.luno.core.LogLevel
import hunoia.luno.core.LunoLog
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import hunoia.luno.facade.IncidentStats
import hunoia.luno.facade.LogFacade
import hunoia.luno.facade.LogFileInfo
import hunoia.luno.facade.LogStats

/**
 * 运行日志默认实现：读写 `:core:common` 的 [LunoLog]，导出到缓存后走系统分享。
 *
 * 不做网络上传；导出包只含日志文本与设备/版本信息。
 */
object LogCenter : LogFacade {

    private const val EXPORT_DIR = "logs_export"
    private const val KEEP_EXPORTS = 3
    private val exportFileName = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")

    override fun stats(): LogStats = LogStats(
        enabled = LunoLog.isRecordingEnabled(),
        level = LunoLog.minLevel(),
        files = LunoLog.files().map { file ->
            LogFileInfo(name = file.name, sizeBytes = file.length())
        },
    )

    override fun setRecordingEnabled(enabled: Boolean) = LunoLog.setRecordingEnabled(enabled)

    override fun setLevel(level: LogLevel) = LunoLog.setMinLevel(level)

    override fun readTail(maxLines: Int): String = LunoLog.readTail(maxLines)

    override fun clear() = LunoLog.clear()

    override fun incidentStats(): IncidentStats {
        val summary = IncidentLog.summary()
        return IncidentStats(
            notifyEnabled = IncidentLog.isNotifyEnabled(),
            count = summary.count,
            lastTimeMs = summary.lastTimeMs,
            sizeBytes = summary.sizeBytes,
        )
    }

    override fun readIncidents(maxEntries: Int): String = IncidentLog.readTailText(maxEntries)

    override fun clearIncidents() = IncidentLog.clear()

    override fun setIncidentNotifyEnabled(enabled: Boolean) =
        IncidentLog.setNotifyEnabled(enabled)

    override suspend fun exportAndShare(context: Context): Boolean {
        val zip = withContext(Dispatchers.IO) { buildExport(context) } ?: return false
        // startActivity 必须在主线程；zip 已经写完，这里只负责拉起选择器。
        return withContext(Dispatchers.Main) {
            FileOutput.share(context, zip, "application/zip", "luno-logs")
        }
    }

    private fun buildExport(context: Context): File? {
        LunoLog.flush()
        IncidentLog.flush()
        val dir = File(context.cacheDir, EXPORT_DIR).apply { mkdirs() }
        cleanOldExports(dir)
        val zip = File(dir, "luno-logs-${LocalDateTime.now().format(exportFileName)}.zip")
        val ok = runCatching {
            ZipOutputStream(BufferedOutputStream(FileOutputStream(zip))).use { out ->
                LunoLog.files().forEach { file ->
                    out.putNextEntry(ZipEntry(file.name))
                    file.inputStream().use { it.copyTo(out) }
                    out.closeEntry()
                }
                IncidentLog.files().forEach { file ->
                    out.putNextEntry(ZipEntry("incidents/${file.name}"))
                    file.inputStream().use { it.copyTo(out) }
                    out.closeEntry()
                }
                out.putNextEntry(ZipEntry("device.txt"))
                out.write(deviceInfo(context).toByteArray(Charsets.UTF_8))
                out.closeEntry()
            }
        }.isSuccess
        if (!ok) {
            zip.delete()
            return null
        }
        return zip
    }

    private fun cleanOldExports(dir: File) {
        dir.listFiles()
            ?.filter { it.isFile && it.name.startsWith("luno-logs-") }
            ?.sortedByDescending { it.lastModified() }
            ?.drop(KEEP_EXPORTS - 1)
            ?.forEach { it.delete() }
    }

    private fun deviceInfo(context: Context): String {
        val packageInfo = runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0)
        }.getOrNull()
        return buildString {
            appendLine("time=${LocalDateTime.now()}")
            appendLine("package=${context.packageName}")
            appendLine("version=${packageInfo?.versionName ?: "?"}")
            appendLine("versionCode=${packageInfo?.longVersionCode ?: 0}")
            appendLine("manufacturer=${android.os.Build.MANUFACTURER}")
            appendLine("model=${android.os.Build.MODEL}")
            appendLine("device=${android.os.Build.DEVICE}")
            appendLine("android=${android.os.Build.VERSION.RELEASE}")
            appendLine("sdk=${android.os.Build.VERSION.SDK_INT}")
            appendLine("soc=${android.os.Build.SOC_MODEL.ifBlank { android.os.Build.BOARD }}")
        }
    }
}
