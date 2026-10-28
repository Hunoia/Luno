package hunoia.luno.config.backup

import hunoia.luno.config.model.Backup
import hunoia.luno.config.model.BackupDomain
import hunoia.luno.config.model.BackupVersions
import hunoia.luno.config.model.InitialSettings
import hunoia.luno.config.model.isSupported
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * 备份域扩展点契约：版本、快照投影、空域与大小上限都由域描述符声明，
 * 备份核心不再持有五组硬编码。
 */
class BackupDomainRegistryTest {

    @Test
    fun `every builtin domain is registered exactly once`() {
        // 触发内置域安装（读取版本判定即可）。
        Backup().isSupported()

        assertEquals(BackupDomain.entries.toSet(), BackupDomainRegistry.registeredDomains)
        BackupDomain.entries.forEach { domain ->
            assertTrue(
                "domain $domain 缺少导出大小上限",
                BackupDomainRegistry.maxExportBytes(domain) > 0,
            )
        }
    }

    @Test
    fun `snapshot keeps only the requested domain sections`() {
        Backup().isSupported()

        val full = Backup(
            backupFormatVersion = BackupVersions.FORMAT,
            gestureSchemaVersion = BackupVersions.GESTURE,
            tuneSchemaVersion = BackupVersions.TUNE,
            actionSchemaVersion = BackupVersions.ACTION,
            automationSchemaVersion = BackupVersions.AUTOMATION,
            searchSchemaVersion = BackupVersions.SEARCH,
            initialSettings = InitialSettings(),
        )

        val gesture = BackupDomainRegistry.snapshot(full, BackupDomain.GESTURE)
        assertNotNull(gesture.initialSettings)
        assertNull(gesture.advancedSettings)
        assertNull(gesture.automationRules)

        val tune = BackupDomainRegistry.snapshot(full, BackupDomain.TUNE)
        assertNull(tune.initialSettings)
        assertNull(tune.advancedSettings)

        val action = BackupDomainRegistry.snapshot(full, BackupDomain.ACTION)
        assertNull(action.actionSettings)
        assertEquals(BackupVersions.FORMAT, action.backupFormatVersion)
    }

    @Test
    fun `domain validation rejects newer schema and empty domain`() {
        Backup().isSupported()

        val newer = Backup(gestureSchemaVersion = BackupVersions.GESTURE + 1)
        assertNotNull(BackupDomainRegistry.validate(newer, BackupDomain.GESTURE))
        assertFalse(newer.isSupported())

        val empty = Backup()
        assertNotNull(BackupDomainRegistry.validate(empty, BackupDomain.GESTURE))
    }

    @Test
    fun `domain validation rejects newer backup format`() {
        Backup().isSupported()

        val newer = Backup(
            backupFormatVersion = BackupVersions.FORMAT + 1,
            gestureSchemaVersion = BackupVersions.GESTURE,
            initialSettings = InitialSettings(),
        )

        assertNotNull(BackupDomainRegistry.validate(newer, BackupDomain.GESTURE))
        assertFalse(newer.isSupported())
    }

    @Test
    fun `duplicate domain registration fails fast`() {
        Backup().isSupported()
        try {
            BackupDomainRegistry.register(
                object : BackupDomainSpec {
                    override val domain = BackupDomain.GESTURE
                    override val schemaVersion = 1
                    override val maxExportBytes = 1L
                    override fun snapshot(full: Backup) = full
                    override fun isEmpty(backup: Backup) = true
                    override fun validate(backup: Backup): String? = null
                }
            )
            fail("重复注册备份域应直接失败")
        } catch (expected: IllegalArgumentException) {
            assertTrue(expected.message.orEmpty().contains("duplicate"))
        }
    }
}
