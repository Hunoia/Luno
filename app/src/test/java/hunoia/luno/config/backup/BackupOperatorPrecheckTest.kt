package hunoia.luno.config.backup

import hunoia.luno.config.model.AutomationRuleNormalizer
import hunoia.luno.config.model.Backup
import hunoia.luno.config.model.BackupVersions
import hunoia.luno.config.model.InitialSettings
import hunoia.luno.core.JsonSerializer
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.Base64
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * 恢复预检回归：这是 restore 前唯一的防线，逐级失败原因（zip 结构 → Base64+JSON
 * 解码 → 空备份 → 版本门禁）必须落对；zip bomb 必须被 entry 上限挡下。
 */
class BackupOperatorPrecheckTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private fun backupEntryBytes(backup: Backup): ByteArray =
        Base64.getEncoder().encode(JsonSerializer.encodeToString(backup).toByteArray())

    private fun zipWith(vararg entries: Pair<String, ByteArray>): File {
        val file = tmp.newFile("backup-${entries.size}-${System.nanoTime()}.zip")
        ZipOutputStream(file.outputStream()).use { zos ->
            for ((name, bytes) in entries) {
                zos.putNextEntry(ZipEntry(name))
                zos.write(bytes)
                zos.closeEntry()
            }
        }
        return file
    }

    private val validBackup = Backup(
        backupFormatVersion = BackupVersions.FORMAT,
        initialSettings = InitialSettings(),
    )

    @Test
    fun `missing or empty file fails as cannot read`() {
        assertEquals(
            RestorePrecheckResult.Failed(RestorePrecheckFailure.CannotReadFile),
            BackupOperator.precheckRestoreFile(tmp.root.resolve("nope.zip")),
        )
        assertEquals(
            RestorePrecheckResult.Failed(RestorePrecheckFailure.CannotReadFile),
            BackupOperator.precheckRestoreFile(tmp.newFile("empty.zip")),
        )
    }

    @Test
    fun `non zip bytes fail as invalid format`() {
        val file = tmp.newFile("junk.zip").apply { writeBytes("not a zip at all".toByteArray()) }
        assertEquals(
            RestorePrecheckResult.Failed(RestorePrecheckFailure.InvalidFormat),
            BackupOperator.precheckRestoreFile(file),
        )
    }

    @Test
    fun `zip without backup entry fails as invalid format`() {
        val zip = zipWith("other" to "x".toByteArray())
        assertEquals(
            RestorePrecheckResult.Failed(RestorePrecheckFailure.InvalidFormat),
            BackupOperator.precheckRestoreFile(zip),
        )
    }

    @Test
    fun `undecodable backup entry fails as verification failed`() {
        // Base64 字符合法但内容不是 JSON：解码层吞掉异常后归为 VerificationFailed。
        val zip = zipWith("backup" to "notjson".toByteArray())
        assertEquals(
            RestorePrecheckResult.Failed(RestorePrecheckFailure.VerificationFailed),
            BackupOperator.precheckRestoreFile(zip),
        )
    }

    @Test
    fun `all null sections fail as empty backup`() {
        val zip = zipWith("backup" to backupEntryBytes(Backup()))
        assertEquals(
            RestorePrecheckResult.Failed(RestorePrecheckFailure.EmptyBackup),
            BackupOperator.precheckRestoreFile(zip),
        )
    }

    @Test
    fun `backup from newer format version is rejected`() {
        val zip = zipWith(
            "backup" to backupEntryBytes(validBackup.copy(backupFormatVersion = BackupVersions.FORMAT + 1)),
        )
        assertEquals(
            RestorePrecheckResult.Failed(RestorePrecheckFailure.UnsupportedVersion),
            BackupOperator.precheckRestoreFile(zip),
        )
    }

    @Test
    fun `backup from newer schema version is rejected`() {
        val zip = zipWith(
            "backup" to backupEntryBytes(validBackup.copy(searchSchemaVersion = BackupVersions.SEARCH + 1)),
        )
        assertEquals(
            RestorePrecheckResult.Failed(RestorePrecheckFailure.UnsupportedVersion),
            BackupOperator.precheckRestoreFile(zip),
        )
    }

    @Test
    fun `backup from newer automation schema version is rejected`() {
        val zip = zipWith(
            "backup" to backupEntryBytes(
                validBackup.copy(automationSchemaVersion = BackupVersions.AUTOMATION + 1),
            ),
        )
        assertEquals(
            RestorePrecheckResult.Failed(RestorePrecheckFailure.UnsupportedVersion),
            BackupOperator.precheckRestoreFile(zip),
        )
    }

    @Test
    fun `exported automation schema version tracks current rule schema`() {
        // 规则 schema 升版时忘记升备份域版本，会让旧版 app 静默丢掉新字段。
        assertEquals(AutomationRuleNormalizer.CURRENT_SCHEMA_VERSION, BackupVersions.AUTOMATION)
    }

    @Test
    fun `valid supported backup passes`() {
        val zip = zipWith(
            "backup" to backupEntryBytes(validBackup),
            "icons" to "ignored".toByteArray(),
        )
        assertEquals(RestorePrecheckResult.Passed, BackupOperator.precheckRestoreFile(zip))
    }

    @Test
    fun `oversized zip bomb entry is rejected as invalid format`() {
        // 33MB 零字节 > 32MB entry 上限：压得很小，但边读边计数必须命中上限，
        // 而不是信任 entry 声明大小（size 字段可伪造）。
        val bomb = ByteArray(33 * 1024 * 1024)
        val zip = zipWith("backup" to bomb)
        assertEquals(
            RestorePrecheckResult.Failed(RestorePrecheckFailure.InvalidFormat),
            BackupOperator.precheckRestoreFile(zip),
        )
    }
}
