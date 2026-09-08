package hunoia.luno.config.backup

import hunoia.luno.config.model.Backup
import hunoia.luno.config.model.InitialSettings
import hunoia.luno.core.JsonSerializer
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.Base64
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class BackupOperatorPrecheckTest {

    @Test
    fun `empty bytes fail as cannot read file`() {
        val result = BackupOperator.precheckRestoreBytes(ByteArray(0))

        assertFailed(result, RestorePrecheckFailure.CannotReadFile)
    }

    @Test
    fun `plain bytes fail as invalid format`() {
        val result = BackupOperator.precheckRestoreBytes("not zip".toByteArray())

        assertFailed(result, RestorePrecheckFailure.InvalidFormat)
    }

    @Test
    fun `missing backup entry fails as invalid format`() {
        val result = BackupOperator.precheckRestoreBytes(zipOf("images" to zipOf()))

        assertFailed(result, RestorePrecheckFailure.InvalidFormat)
    }

    @Test
    fun `missing images entry fails as invalid format`() {
        val result = BackupOperator.precheckRestoreBytes(zipOf("backup" to encodedBackup(Backup(initialSettings = InitialSettings()))))

        assertFailed(result, RestorePrecheckFailure.InvalidFormat)
    }

    @Test
    fun `empty backup fails as empty backup`() {
        val result = BackupOperator.precheckRestoreBytes(
            zipOf(
                "backup" to encodedBackup(Backup()),
                "images" to zipOf(),
            )
        )

        assertFailed(result, RestorePrecheckFailure.EmptyBackup)
    }

    @Test
    fun `valid backup passes`() {
        val result = BackupOperator.precheckRestoreBytes(
            zipOf(
                "backup" to encodedBackup(Backup(initialSettings = InitialSettings())),
                "images" to zipOf(),
            )
        )

        assertEquals(RestorePrecheckResult.Passed, result)
    }

    private fun assertFailed(result: RestorePrecheckResult, reason: RestorePrecheckFailure) {
        val failed = result as RestorePrecheckResult.Failed
        assertEquals(reason, failed.reason)
    }

    private fun encodedBackup(backup: Backup): ByteArray {
        val json = JsonSerializer.encodeToString(backup)
        return Base64.getEncoder().encode(json.toByteArray())
    }

    private fun zipOf(vararg entries: Pair<String, ByteArray>): ByteArray {
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            entries.forEach { (name, content) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(content)
                zip.closeEntry()
            }
        }
        return output.toByteArray()
    }
}
