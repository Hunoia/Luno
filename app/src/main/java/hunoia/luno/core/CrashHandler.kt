package hunoia.luno.core

import hunoia.luno.core.Paths
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

object CrashHandler : Thread.UncaughtExceptionHandler {

    private const val SEPARATOR = "gulugulu_CRASH_REPORT"
    private const val CRASH_FILE_NAME = "crashlog"
    private const val MAX_CRASH_ENTRIES = 10

    private val defaultHandler: Thread.UncaughtExceptionHandler? = Thread.getDefaultUncaughtExceptionHandler()

    private val crashDir = "${Paths.AppCache}/crash"
    private val crashFilePath = "$crashDir/$CRASH_FILE_NAME"

    fun getCrashFile(): File? {
        if (!File(crashFilePath).exists()) return null
        return File(crashFilePath)
    }

    fun getCrashList(): List<String> {
        val file = getCrashFile() ?: return emptyList()
        val text = file.readText()
        if (text.isNullOrEmpty()) return emptyList()
        return text.split(SEPARATOR).map { it.trimIndent() }.filter { it.isNotEmpty() }
    }

    override fun uncaughtException(t: Thread, e: Throwable) {
        saveErrorInfo(e)
        defaultHandler?.uncaughtException(t, e)
    }

    private fun saveErrorInfo(e: Throwable) {
        val builder = StringBuilder()
        val stringWriter = StringWriter()
        val printWriter = PrintWriter(stringWriter)
        e.printStackTrace(printWriter)
        printWriter.close()
        val errorStackInfo = stringWriter.toString()
        val timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
        builder.append("Time: $timestamp\n")
        builder.append(errorStackInfo)
        builder.append("$SEPARATOR\n")

        if (File(crashFilePath).exists()) {
            val existing = File(crashFilePath).readText()
            val entries = existing.split(SEPARATOR)
                .map { it.trimIndent() }
                .filter { it.isNotEmpty() }
            val kept = if (entries.size >= MAX_CRASH_ENTRIES) {
                entries.takeLast(MAX_CRASH_ENTRIES - 1)
            } else {
                entries
            }
            kept.forEach { entry ->
                builder.append(entry)
                builder.append("$SEPARATOR\n")
            }
        }

        File(crashDir).mkdirs()
        File(crashFilePath).createNewFile()
        File(crashFilePath).writeText(builder.toString())
    }
}
