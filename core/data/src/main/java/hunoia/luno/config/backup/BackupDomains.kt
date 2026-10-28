package hunoia.luno.config.backup

import hunoia.luno.config.model.Backup
import hunoia.luno.config.model.BackupDomain

/**
 * 单域快照投影：从整机快照里挑出该域的段，并带上该域的 Schema 版本。
 *
 * 备份核心只负责「整机全量 + 按域导入/导出」的流程，具体某个域包含哪些段由域实现声明。
 */
fun interface BackupDomainSnapshot {
    fun snapshot(full: Backup): Backup
}

/**
 * 备份域注册表：新增备份域时注册一个 [BackupDomainSnapshot]，不修改备份核心的分发逻辑。
 *
 * 注册只做聚合与唯一性校验；重复注册在启动期直接失败。
 */
object BackupDomainRegistry {

    private val handlers = LinkedHashMap<BackupDomain, BackupDomainSnapshot>()

    fun register(domain: BackupDomain, snapshot: BackupDomainSnapshot) {
        require(handlers.put(domain, snapshot) == null) { "duplicate backup domain: $domain" }
    }

    fun snapshot(full: Backup, domain: BackupDomain): Backup =
        handlers[domain]?.snapshot(full)
            ?: throw IllegalArgumentException("backup domain not registered: $domain")

    fun contains(domain: BackupDomain): Boolean = handlers.containsKey(domain)

    val registeredDomains: Set<BackupDomain>
        get() = handlers.keys.toSet()
}

/** 内置备份域：整机备份的五组。 */
internal object BuiltinBackupDomains {

    private var installed = false

    fun ensure() {
        if (installed) return
        installed = true
        install()
    }

    private fun install() {
        BackupDomainRegistry.register(BackupDomain.GESTURE) { full ->
            Backup(
                backupFormatVersion = full.backupFormatVersion,
                gestureSchemaVersion = full.gestureSchemaVersion,
                initialSettings = full.initialSettings,
                gestureSettings = full.gestureSettings,
                gestureButtons = full.gestureButtons,
                subGestureSettings = full.subGestureSettings,
            )
        }
        BackupDomainRegistry.register(BackupDomain.TUNE) { full ->
            Backup(
                backupFormatVersion = full.backupFormatVersion,
                tuneSchemaVersion = full.tuneSchemaVersion,
                advancedSettings = full.advancedSettings,
            )
        }
        BackupDomainRegistry.register(BackupDomain.ACTION) { full ->
            Backup(
                backupFormatVersion = full.backupFormatVersion,
                actionSchemaVersion = full.actionSchemaVersion,
                actionSettings = full.actionSettings,
                actionLibrarySettings = full.actionLibrarySettings,
            )
        }
        BackupDomainRegistry.register(BackupDomain.AUTOMATION) { full ->
            Backup(
                backupFormatVersion = full.backupFormatVersion,
                automationSchemaVersion = full.automationSchemaVersion,
                automationRules = full.automationRules,
                globalVariables = full.globalVariables,
            )
        }
        BackupDomainRegistry.register(BackupDomain.SEARCH) { full ->
            Backup(
                backupFormatVersion = full.backupFormatVersion,
                searchSchemaVersion = full.searchSchemaVersion,
                quickAppLauncherSettings = full.quickAppLauncherSettings,
                quickSearchSettings = full.quickSearchSettings,
            )
        }
    }
}
