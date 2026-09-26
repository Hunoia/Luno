package hunoia.luno.config.backup

import android.content.Context
import android.content.pm.PackageManager
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
                val zipFileBytes = zipFile.readBytes()
                os.write(zipFileBytes)
                os.flush()
            }
        } finally {
            File(backupDir).deleteRecursively()
        }
    }

    fun precheckRestore(context: Context, restoreFrom: Uri): RestorePrecheckResult {
        val bytes = try {
            context.contentResolver.openInputStream(restoreFrom)?.use { it.readBytes() }
        } catch (_: Exception) {
            null
        } ?: return RestorePrecheckResult.Failed(RestorePrecheckFailure.CannotReadFile)
        return precheckRestoreBytes(bytes)
    }

    fun precheckRestoreBytes(bytes: ByteArray): RestorePrecheckResult {
        if (bytes.isEmpty()) {
            return RestorePrecheckResult.Failed(RestorePrecheckFailure.CannotReadFile)
        }
        val entries = runCatching { readZipEntries(bytes) }
            .getOrElse { return RestorePrecheckResult.Failed(RestorePrecheckFailure.InvalidFormat) }
        val backupBytes = entries[ZIP_BACKUP]
            ?: return RestorePrecheckResult.Failed(RestorePrecheckFailure.InvalidFormat)
        if (!entries.containsKey(ZIP_IMAGES)) {
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
            val inputStream = context.contentResolver.openInputStream(restoreFrom)
                ?: throw IllegalStateException("Failed to open input stream for restore")
            inputStream.use { stream ->
                val input = stream.readBytes()
                val restoreDirFile = File(restoreDir).also {
                    it.mkdirs()
                }
                val restoreFile = File(restoreFilePath).also {
                    it.delete()
                    it.createNewFile()
                    it.appendBytes(input)
                }
                val extracted = unzipFile(restoreFile, restoreDirFile)
                var restored = false
                var imagesRestored = false
                for (file in extracted) {
                    when (file.name) {
                        ZIP_BACKUP -> {
                            restoreBackupFromBytes(context, file.readBytes())
                            restored = true
                        }
                        ZIP_IMAGES -> {
                            File(Paths.Image).deleteRecursively()
                            File(Paths.Image).mkdirs()
                            unzipFile(file, restoreDirFile).forEach { imageFile ->
                                val destFile = File("${Paths.Image}/${imageFile.name}")
                                imageFile.copyTo(destFile, overwrite = true)
                            }
                            imagesRestored = true
                        }
                    }
                }
                if (!restored) throw IllegalStateException("restore failed: no backup entry found in zip")
                if (!imagesRestored) throw IllegalStateException("restore failed: no images entry found in zip")
            }
        } finally {
            File(restoreDir).deleteRecursively()
        }
    }

    private suspend fun getBackupItemBytes(): ByteArray {
        val backup = ConfigProvider.snapshotAll()
        val json = JsonSerializer.encodeToString(backup)
        return Base64.getEncoder().encode(json.toByteArray())
    }

    private suspend fun restoreBackupFromBytes(context: Context, bytes: ByteArray) {
        val backup = decodeBackup(bytes) ?: throw IllegalStateException("restore failed: backup decode failed")
        if (backup.isEmpty()) {
            throw IllegalStateException("restore failed: backup contains no settings")
        }
        val installedPackages = queryInstalledPackageNames(context)
        val sanitizedFrozenSettings = sanitizeFrozenAppSettings(
            backup.frozenAppSettings,
            installedPackages
        )
        val modifiedBackup = if (sanitizedFrozenSettings != null) {
            backup.copy(frozenAppSettings = sanitizedFrozenSettings)
        } else {
            backup
        }
        ConfigProvider.restoreAll(modifiedBackup)
        val verified = ConfigProvider.snapshotAll()
        if (verified.initialSettings != modifiedBackup.initialSettings) {
            throw IllegalStateException("restore failed: initialSettings mismatch after restoreAll")
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
            quickAppLauncherSettings == null && frozenAppSettings == null &&
            subGestureSettings == null && actionLibrarySettings == null
    }

    private fun sanitizeFrozenAppSettings(
        settings: hunoia.luno.config.model.FrozenAppSettings?,
        installedPackages: Set<String>
    ): hunoia.luno.config.model.FrozenAppSettings? {
        settings ?: return null
        val oneKey = settings.oneKeyPackageNames
            .filterTo(mutableSetOf()) { it in installedPackages }
        return settings.copy(oneKeyPackageNames = oneKey)
    }

    private fun queryInstalledPackageNames(context: Context): Set<String> {
        val pm = context.packageManager
        return try {
            val apps = pm.getInstalledApplications(PackageManager.ApplicationInfoFlags.of(0))
            apps.map { it.packageName }.filter { it.isNotBlank() }.toSet()
        } catch (_: Exception) {
            emptySet()
        }
    }

    private fun readZipEntries(bytes: ByteArray): Map<String, ByteArray> {
        val tempDir = kotlin.io.path.createTempDirectory("luno-restore-precheck").toFile()
        return try {
            val zipFile = File(tempDir, "restore.zip").also { it.writeBytes(bytes) }
            unzipFile(zipFile, tempDir).filter { it.isFile }.associate { it.name to it.readBytes() }
        } finally {
            tempDir.deleteRecursively()
        }
    }

    private fun unzipFile(zipFile: File, destDir: File): List<File> {
        val extracted = mutableListOf<File>()
        ZipInputStream(FileInputStream(zipFile)).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                val target = File(destDir, entry.name)
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
