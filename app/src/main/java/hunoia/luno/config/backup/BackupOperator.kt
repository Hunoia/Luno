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
    private const val ZIP_ICONS = "icons"

    /**
     * 单个 zip entry 解压后的字节上限。
     *
     * zip entry 的 size 字段可以伪造，所以不能信任 `entry.size`；必须边读边计数。
     * 备份文件来自用户选中的 URI，恶意构造的 zip bomb 会让无上限的 readBytes/copyTo 直接 OOM。
     */
    private const val MAX_ENTRY_BYTES = 32L * 1024 * 1024

    /**
     * 导出文件整体上限（整机/单域统一）：8MB，超限拒绝并提示。
     * 与 [MAX_ENTRY_BYTES] 分工不同：后者防 zip bomb，前者防正常备份无限膨胀。
     */
    private const val MAX_EXPORT_BYTES = 8L * 1024 * 1024

    /** 导入时外层压缩包上限（防 zip bomb / 误选大文件占满缓存）。 */
    private const val MAX_IMPORT_BYTES = 64L * 1024 * 1024

    private class EntryTooLargeException(val entryName: String) :
        Exception("zip entry too large: $entryName")

    /** 边读边计数地拷贝，超过上限立即中止（不信任任何声明大小）。 */
    private fun java.io.InputStream.copyToLimited(
        out: java.io.OutputStream,
        limit: Long,
        label: String,
    ) {
        val buf = ByteArray(16 * 1024)
        var total = 0L
        while (true) {
            val n = read(buf)
            if (n < 0) break
            total += n
            if (total > limit) throw EntryTooLargeException(label)
            out.write(buf, 0, n)
        }
    }

    /** 有上限地读完一个 entry；超过 [MAX_ENTRY_BYTES] 立即抛 [EntryTooLargeException] */
    private fun ZipInputStream.readEntryLimited(name: String): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        val buf = ByteArray(16 * 1024)
        var total = 0L
        while (true) {
            val n = read(buf)
            if (n < 0) break
            total += n
            if (total > MAX_ENTRY_BYTES) throw EntryTooLargeException(name)
            out.write(buf, 0, n)
        }
        return out.toByteArray()
    }

    /** 有上限地把一个 entry 流到文件；超过 [MAX_ENTRY_BYTES] 立即中止并删除半成品 */
    private fun ZipInputStream.copyEntryLimited(name: String, out: FileOutputStream) {
        val buf = ByteArray(16 * 1024)
        var total = 0L
        try {
            while (true) {
                val n = read(buf)
                if (n < 0) break
                total += n
                if (total > MAX_ENTRY_BYTES) throw EntryTooLargeException(name)
                out.write(buf, 0, n)
            }
        } catch (e: EntryTooLargeException) {
            runCatching { out.close() }
            throw e
        }
    }

    private val backupDir get() = "${Paths.AppCache}/backup"
    private val restoreDir get() = "${Paths.AppCache}/restore"

    private val backupItemFilePath get() = "$backupDir/$ZIP_BACKUP"
    private val zipImagePath get() = "$backupDir/$ZIP_IMAGES"
    private val zipIconPath get() = "$backupDir/$ZIP_ICONS"
    private val zipFilePath get() = "$backupDir/zip"
    private val restoreFilePath get() = "$restoreDir/restore"

    /** 把一个目录下的平铺文件打成 zip（空目录也生成合法空 zip）。 */
    private fun zipDirectory(sourceDir: String, target: File) {
        target.delete()
        target.createNewFile()
        val files = File(sourceDir).listFiles()?.filter { it.isFile } ?: emptyList()
        ZipOutputStream(FileOutputStream(target)).use { zos ->
            for (file in files) {
                zos.putNextEntry(ZipEntry(file.name))
                file.inputStream().use { it.copyTo(zos) }
                zos.closeEntry()
            }
        }
    }

    suspend fun backup(context: Context, saveTo: Uri) {
        try {
            File(backupDir).mkdirs()

            val backupItemBytes = getBackupItemBytes()
            val backupItemFile = File(backupItemFilePath).also {
                it.delete()
                it.createNewFile()
                it.appendBytes(backupItemBytes)
            }

            // images 保留给旧备份兼容（当前没有功能写入 Pictures）；真正需要备份的
            // 用户数据是 shortcut_icons —— 快捷方式位图图标。
            val zipImageDirFile = File(zipImagePath)
            zipDirectory(Paths.Image, zipImageDirFile)
            val zipIconDirFile = File(zipIconPath)
            zipDirectory(Paths.ShortcutIcons, zipIconDirFile)

            val zipFile = File(zipFilePath).also {
                it.delete()
                it.createNewFile()
            }
            ZipOutputStream(FileOutputStream(zipFile)).use { zos ->
                for (file in listOf(backupItemFile, zipImageDirFile, zipIconDirFile)) {
                    zos.putNextEntry(ZipEntry(file.name))
                    file.inputStream().use { it.copyTo(zos) }
                    zos.closeEntry()
                }
            }
            if (zipFile.length() > MAX_EXPORT_BYTES) {
                throw IllegalStateException(
                    "backup failed: exceeds 8MB (${zipFile.length()} bytes)"
                )
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
                    tempFile.outputStream().use { out -> stream.copyToLimited(out, MAX_IMPORT_BYTES, "import") }
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
            .getOrElse { e ->
                // zip bomb 会在这里被 MAX_ENTRY_BYTES 挡下；记一笔具体原因便于排查
                android.util.Log.w("LunoLauncher", "precheckRestore: ${e::class.simpleName}: ${e.message}")
                return RestorePrecheckResult.Failed(RestorePrecheckFailure.InvalidFormat)
            }
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
                restoreFile.outputStream().use { out -> stream.copyToLimited(out, MAX_IMPORT_BYTES, "import") }
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
                        restoreBackupFromBytes(file.readBytes().also {
                            if (it.size > MAX_ENTRY_BYTES) throw EntryTooLargeException(file.name)
                        })
                        restored = true
                    }
                    ZIP_IMAGES -> {
                        restoreImages(file, restoreDirFile)
                        imagesRestored = true
                    }
                    ZIP_ICONS -> restoreShortcutIcons(file, replace = true)
                }
            }
            if (!restored) throw IllegalStateException("restore failed: no backup entry found in zip")
            if (!imagesRestored) throw IllegalStateException("restore failed: no images entry found in zip")
        } finally {
            File(restoreDir).deleteRecursively()
        }
    }

    /**
     * 恢复快捷方式图标。全量恢复时整目录替换；单域导入时合并（不动其他域的图标）。
     */
    private fun restoreShortcutIcons(iconsZip: File, replace: Boolean) {
        val staging = File(iconsZip.parentFile, "$ZIP_ICONS-staging").also {
            it.deleteRecursively()
            it.mkdirs()
        }
        val staged = unzipFile(iconsZip, staging).filter { it.isFile }
        if (staged.isEmpty()) {
            staging.deleteRecursively()
            return
        }
        val target = File(Paths.ShortcutIcons).also { it.mkdirs() }
        if (replace) {
            target.listFiles()?.forEach { it.delete() }
        }
        val stagingRoot = staging.canonicalPath
        staged.forEach { file ->
            val relative = file.canonicalPath.removePrefix(stagingRoot).removePrefix(File.separator)
            if (relative.isEmpty()) return@forEach
            val dest = File(target, relative)
            dest.parentFile?.mkdirs()
            file.copyTo(dest, overwrite = true)
        }
        staging.deleteRecursively()
    }

    private fun restoreImages(imagesZip: File, restoreDirFile: File) {
        val staging = File(restoreDirFile, "${ZIP_IMAGES}-staging").also { it.mkdirs() }
        val staged = unzipFile(imagesZip, staging).filter { it.isFile }
        if (staged.isEmpty()) {
            // 当前功能不使用 Pictures；旧备份/新备份里的 images 条目都可能为空。
            // 空条目不是损坏，直接跳过，否则整次恢复会被误判失败。
            staging.deleteRecursively()
            return
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

    /**
     * 单域导出：zip 里只有一个 `backup` 条目（无 images），内容为该域快照。
     * 恢复走 [importDomain]，只写非 null 段。
     */
    suspend fun exportDomain(
        context: Context,
        domain: hunoia.luno.config.model.BackupDomain,
        saveTo: Uri,
    ) {
        val dir = File(backupDir).also { it.mkdirs() }
        try {
            val itemBytes = run {
                val backup = ConfigProvider.snapshotDomain(domain)
                val json = JsonSerializer.encodeToString(backup)
                Base64.getEncoder().encode(json.toByteArray())
            }
            val zipFile = File(dir, "domain.zip").also {
                it.delete()
                it.createNewFile()
            }
            val iconsZip = File(dir, ZIP_ICONS)
            zipDirectory(Paths.ShortcutIcons, iconsZip)
            ZipOutputStream(FileOutputStream(zipFile)).use { zos ->
                zos.putNextEntry(ZipEntry(ZIP_BACKUP))
                itemBytes.inputStream().use { it.copyTo(zos) }
                zos.closeEntry()
                zos.putNextEntry(ZipEntry(ZIP_ICONS))
                iconsZip.inputStream().use { it.copyTo(zos) }
                zos.closeEntry()
            }
            if (zipFile.length() > MAX_EXPORT_BYTES) {
                throw IllegalStateException(
                    "export failed: domain $domain exceeds 8MB (${zipFile.length()} bytes)"
                )
            }
            val outputStream = context.contentResolver.openOutputStream(saveTo)
                ?: throw IllegalStateException("Failed to open output stream for export")
            outputStream.use { os ->
                zipFile.inputStream().use { it.copyTo(os) }
                os.flush()
            }
        } finally {
            File(backupDir).deleteRecursively()
        }
    }

    /** 单域恢复：只写快照里非 null 的段，其余不动。 */
    suspend fun importDomain(context: Context, restoreFrom: Uri) {
        val restoreDirFile = File(restoreDir).also { it.mkdirs() }
        try {
            val restoreFile = File(restoreDirFile, "domain.zip").also {
                it.delete()
                it.createNewFile()
            }
            val inputStream = context.contentResolver.openInputStream(restoreFrom)
                ?: throw IllegalStateException("Failed to open input stream for import")
            inputStream.use { stream ->
                restoreFile.outputStream().use { out -> stream.copyToLimited(out, MAX_IMPORT_BYTES, "import") }
            }
            if (restoreFile.length() > MAX_EXPORT_BYTES) {
                throw IllegalStateException("import failed: file exceeds 8MB (${restoreFile.length()} bytes)")
            }
            val extracted = unzipFile(restoreFile, restoreDirFile)
            val item = extracted.firstOrNull { it.isFile && it.name == ZIP_BACKUP }
                ?: throw IllegalStateException("import failed: no backup entry found in zip")
            // icons 条目可选（旧域备份没有）：合并导入，不动其他域的图标。
            extracted.firstOrNull { it.isFile && it.name == ZIP_ICONS }
                ?.let { restoreShortcutIcons(it, replace = false) }
            val bytes = item.readBytes().also {
                if (it.size > MAX_ENTRY_BYTES) throw EntryTooLargeException(item.name)
            }
            val backup = decodeBackup(bytes) ?: throw IllegalStateException("import failed: backup decode failed")
            if (backup.isEmpty()) {
                throw IllegalStateException("import failed: backup contains no settings")
            }
            ConfigProvider.restoreDomain(backup)
        } finally {
            File(restoreDir).deleteRecursively()
        }
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
            backup.newActionLibrarySettings?.let { if (verified.newActionLibrarySettings != it) add("newActionLibrarySettings") }
            backup.automationRules?.let { if (verified.automationRules != it) add("automationRules") }
            backup.quickSearchSettings?.let { if (verified.quickSearchSettings != it) add("quickSearchSettings") }
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
            subGestureSettings == null &&
            newActionLibrarySettings == null &&
            automationRules == null &&
            quickSearchSettings == null
    }

    private fun scanZipEntries(zipFile: File): Pair<ByteArray?, Boolean> {
        var backupBytes: ByteArray? = null
        var hasImages = false
        ZipInputStream(FileInputStream(zipFile)).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                if (!entry.isDirectory) {
                    when (entry.name) {
                        ZIP_BACKUP -> if (backupBytes == null) backupBytes = zis.readEntryLimited(entry.name)
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
                    try {
                        FileOutputStream(target).use { fos -> zis.copyEntryLimited(entry.name, fos) }
                    } catch (_: EntryTooLargeException) {
                        target.delete()
                        throw EntryTooLargeException(entry.name)
                    }
                }
                extracted.add(target)
                entry = zis.nextEntry
            }
        }
        return extracted
    }
}
