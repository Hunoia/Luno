package hunoia.luno.config.backup

import android.content.Context
import android.net.Uri
import hunoia.luno.config.ConfigProvider
import hunoia.luno.config.model.Backup
import hunoia.luno.core.JsonSerializer
import hunoia.luno.core.Paths
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.Base64
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

object BackupOperator {

    private const val ZIP_BACKUP = "backup"
    private const val ZIP_IMAGES = "images"

    private val backupDir get() = "${Paths.AppCache}/backup"
    private val restoreDir get() = "${Paths.AppCache}/restore"

    private val backupItemFilePath get() = "$backupDir/$ZIP_BACKUP"
    private val zipImagePath get() = "$backupDir/$ZIP_IMAGES"
    private val zipFilePath get() = "$backupDir/zip"
    private val restoreFilePath get() = "$restoreDir/restore"

    suspend fun backup(context: Context, saveTo: Uri) {
        try {
            File(backupDir).mkdirs()

            val backupItemBytes = getBackupItemBytes()
            val backupItemFile = File(backupItemFilePath).also {
                it.delete()
                it.createNewFile()
                it.appendBytes(backupItemBytes)
            }

            val zipImageDirFile = File(zipImagePath).also {
                it.delete()
                it.createNewFile()
            }
            val imageFiles = File(Paths.Image).listFiles()?.toList() ?: emptyList()
            ZipOutputStream(FileOutputStream(zipImageDirFile)).use { zos ->
                for (file in imageFiles) {
                    zos.putNextEntry(ZipEntry(file.name))
                    file.inputStream().use { it.copyTo(zos) }
                    zos.closeEntry()
                }
            }

            val zipFile = File(zipFilePath).also {
                it.delete()
                it.createNewFile()
            }
            ZipOutputStream(FileOutputStream(zipFile)).use { zos ->
                for (file in listOf(backupItemFile, zipImageDirFile)) {
                    zos.putNextEntry(ZipEntry(file.name))
                    file.inputStream().use { it.copyTo(zos) }
                    zos.closeEntry()
                }
            }

            val outputStream = context.contentResolver.openOutputStream(saveTo)
                ?: throw IllegalStateException("Failed to open output stream for backup")
            outputStream.use { os ->
                zipFile.inputStream().use { it.copyTo(os) }
                os.flush()
            }
        } finally {
            File(backupDir).deleteRecursively()
        }
    }

    fun precheckRestore(context: Context, restoreFrom: Uri): RestorePrecheckResult {
        val restoreDirFile = File(restoreDir).also { it.mkdirs() }
        val tempFile = File(restoreDirFile, "$ZIP_BACKUP.precheck").also {
            it.delete()
            it.createNewFile()
        }
        try {
            val inputStream = runCatching { context.contentResolver.openInputStream(restoreFrom) }
                .getOrNull() ?: return RestorePrecheckResult.Failed(RestorePrecheckFailure.CannotReadFile)
            try {
                inputStream.use { stream ->
                    tempFile.outputStream().use { out -> stream.copyTo(out) }
                }
            } catch (_: Exception) {
                return RestorePrecheckResult.Failed(RestorePrecheckFailure.CannotReadFile)
            }
            return precheckRestoreFile(tempFile)
        } finally {
            tempFile.delete()
        }
    }

    fun precheckRestoreFile(zipFile: File): RestorePrecheckResult {
        if (!zipFile.isFile || zipFile.length() == 0L) {
            return RestorePrecheckResult.Failed(RestorePrecheckFailure.CannotReadFile)
        }
        val (backupBytes, hasImages) = runCatching { scanZipEntries(zipFile) }
            .getOrElse { return RestorePrecheckResult.Failed(RestorePrecheckFailure.InvalidFormat) }
        if (backupBytes == null) {
            return RestorePrecheckResult.Failed(RestorePrecheckFailure.InvalidFormat)
        }
        if (!hasImages) {
            return RestorePrecheckResult.Failed(RestorePrecheckFailure.InvalidFormat)
        }
        val backup = decodeBackup(backupBytes)
            ?: return RestorePrecheckResult.Failed(RestorePrecheckFailure.VerificationFailed)
        if (backup.isEmpty()) {
            return RestorePrecheckResult.Failed(RestorePrecheckFailure.EmptyBackup)
        }
        return RestorePrecheckResult.Passed
    }

    suspend fun restore(context: Context, restoreFrom: Uri) {
        try {
            val restoreDirFile = File(restoreDir).also { it.mkdirs() }
            val restoreFile = File(restoreFilePath).also {
                it.delete()
                it.createNewFile()
            }
            val inputStream = context.contentResolver.openInputStream(restoreFrom)
                ?: throw IllegalStateException("Failed to open input stream for restore")
            inputStream.use { stream ->
                restoreFile.outputStream().use { out -> stream.copyTo(out) }
            }

            when (val precheck = precheckRestoreFile(restoreFile)) {
                is RestorePrecheckResult.Passed -> Unit
                is RestorePrecheckResult.Failed ->
                    throw IllegalStateException("restore failed: precheck ${precheck.reason.name}")
            }

            val extracted = unzipFile(restoreFile, restoreDirFile)
            var restored = false
            var imagesRestored = false
            for (file in extracted) {
                when (file.name) {
                    ZIP_BACKUP -> {
                        restoreBackupFromBytes(file.readBytes())
                        restored = true
                    }
                    ZIP_IMAGES -> {
                        restoreImages(file, restoreDirFile)
                        imagesRestored = true
                    }
                }
            }
            if (!restored) throw IllegalStateException("restore failed: no backup entry found in zip")
            if (!imagesRestored) throw IllegalStateException("restore failed: no images entry found in zip")
        } finally {
            File(restoreDir).deleteRecursively()
        }
    }

    private fun restoreImages(imagesZip: File, restoreDirFile: File) {
        val staging = File(restoreDirFile, "${ZIP_IMAGES}-staging").also { it.mkdirs() }
        val staged = unzipFile(imagesZip, staging).filter { it.isFile }
        if (staged.isEmpty()) {
            throw IllegalStateException("restore failed: images entry contains no files")
        }

        val target = File(Paths.Image)
        val previous = File("${Paths.Image}.old")
        if (previous.exists()) previous.deleteRecursively()

        if (target.exists() && !target.renameTo(previous)) {
            target.deleteRecursively()
        }
        if (!staging.renameTo(target)) {
            val stagingRoot = staging.canonicalPath
            staged.forEach { f ->
                val relative = f.canonicalPath.removePrefix(stagingRoot).removePrefix(File.separator)
                if (relative.isEmpty()) return@forEach
                val dest = File(target, relative)
                dest.parentFile?.mkdirs()
                f.copyTo(dest, overwrite = true)
            }
            staging.deleteRecursively()
        }
        previous.deleteRecursively()
    }

    private suspend fun getBackupItemBytes(): ByteArray {
        val backup = ConfigProvider.snapshotAll()
        val json = JsonSerializer.encodeToString(backup)
        return Base64.getEncoder().encode(json.toByteArray())
    }

    private suspend fun restoreBackupFromBytes(bytes: ByteArray) {
        val backup = decodeBackup(bytes) ?: throw IllegalStateException("restore failed: backup decode failed")
        if (backup.isEmpty()) {
            throw IllegalStateException("restore failed: backup contains no settings")
        }
        ConfigProvider.restoreAll(backup)
        val verified = ConfigProvider.snapshotAll()
        val mismatched = buildList {
            backup.initialSettings?.let { if (verified.initialSettings != it) add("initialSettings") }
            backup.advancedSettings?.let { if (verified.advancedSettings != it) add("advancedSettings") }
            backup.gestureSettings?.let { if (verified.gestureSettings != it) add("gestureSettings") }
            backup.actionSettings?.let { if (verified.actionSettings != it) add("actionSettings") }
            backup.gestureButtons?.let { if (verified.gestureButtons != it) add("gestureButtons") }
            backup.quickAppLauncherSettings?.let { if (verified.quickAppLauncherSettings != it) add("quickAppLauncherSettings") }
            backup.subGestureSettings?.let { if (verified.subGestureSettings != it) add("subGestureSettings") }
            backup.actionLibrarySettings?.let { if (verified.actionLibrarySettings != it) add("actionLibrarySettings") }
        }
        if (mismatched.isNotEmpty()) {
            throw IllegalStateException("restore failed: mismatch after restoreAll: ${mismatched.joinToString()}")
        }
    }

    private fun decodeBackup(bytes: ByteArray): Backup? {
        return runCatching {
            val decoded = Base64.getDecoder().decode(bytes)
            JsonSerializer.decodeFromString<Backup>(String(decoded))
        }.getOrNull()
    }

    private fun Backup.isEmpty(): Boolean {
        return initialSettings == null && advancedSettings == null &&
            gestureSettings == null && actionSettings == null &&
            gestureButtons == null &&
            quickAppLauncherSettings == null &&
            subGestureSettings == null && actionLibrarySettings == null
    }

    private fun scanZipEntries(zipFile: File): Pair<ByteArray?, Boolean> {
        var backupBytes: ByteArray? = null
        var hasImages = false
        ZipInputStream(FileInputStream(zipFile)).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                if (!entry.isDirectory) {
                    when (entry.name) {
                        ZIP_BACKUP -> if (backupBytes == null) backupBytes = zis.readBytes()
                        ZIP_IMAGES -> hasImages = true
                    }
                }
                entry = zis.nextEntry
            }
        }
        return backupBytes to hasImages
    }

    private fun unzipFile(zipFile: File, destDir: File): List<File> {
        val extracted = mutableListOf<File>()
        val baseDir = destDir.canonicalPath
        ZipInputStream(FileInputStream(zipFile)).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                val target = File(destDir, entry.name)
                val targetPath = target.canonicalPath
                if (targetPath != baseDir && !targetPath.startsWith(baseDir + File.separator)) {
                    entry = zis.nextEntry
                    continue
                }
                if (!entry.isDirectory) {
                    target.parentFile?.mkdirs()
                    FileOutputStream(target).use { fos -> zis.copyTo(fos) }
                }
                extracted.add(target)
                entry = zis.nextEntry
            }
        }
        return extracted
    }
}
