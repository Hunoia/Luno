package hunoia.luno.config.backup

import hunoia.luno.config.model.Backup
import hunoia.luno.config.model.BackupDomain
import hunoia.luno.config.model.BackupVersions

/**
 * 单域描述符：一个备份域需要的全部语义。
 *
 * 备份核心只负责「整机全量 + 按域导入/导出」的流程；某域包含哪些段、
 * Schema 版本、单域大小上限与导入校验，都由域自己声明。
 */
interface BackupDomainSpec {
    val domain: BackupDomain

    /** 本实现能解析的最高 Schema 版本；备份里更高即拒绝导入。 */
    val schemaVersion: Int

    /** 单域导出文件上限（字节）。 */
    val maxExportBytes: Long

    /** 从整机快照里挑出该域的段。 */
    fun snapshot(full: Backup): Backup

    /** 该域的段是否全部为空。 */
    fun isEmpty(backup: Backup): Boolean

    /** 导入前校验；返回 null 表示通过，否则返回失败原因。 */
    fun validate(backup: Backup): String?
}

/**
 * 备份域注册表：新增备份域时注册一个 [BackupDomainSpec]，不修改备份核心的分发逻辑。
 *
 * 注册只做聚合与唯一性校验；重复注册在启动期直接失败。
 */
object BackupDomainRegistry {

    private val handlers = LinkedHashMap<BackupDomain, BackupDomainSpec>()

    fun register(spec: BackupDomainSpec) {
        require(!handlers.containsKey(spec.domain)) { "duplicate backup domain: ${spec.domain}" }
        require(spec.schemaVersion > 0) { "backup domain schemaVersion must be positive: ${spec.domain}" }
        require(spec.maxExportBytes > 0) { "backup domain maxExportBytes must be positive: ${spec.domain}" }
        handlers[spec.domain] = spec
    }

    fun spec(domain: BackupDomain): BackupDomainSpec {
        ensureBuiltins()
        return handlers[domain]
            ?: throw IllegalArgumentException("backup domain not registered: $domain")
    }

    fun snapshot(full: Backup, domain: BackupDomain): Backup = spec(domain).snapshot(full)

    fun schemaVersion(domain: BackupDomain): Int = spec(domain).schemaVersion

    fun maxExportBytes(domain: BackupDomain): Long = spec(domain).maxExportBytes

    /** 导入校验：格式版本、未知域（旧实现读到新域）与域内校验失败都返回原因。 */
    fun validate(backup: Backup, domain: BackupDomain): String? {
        if (backup.backupFormatVersion > BackupVersions.FORMAT) {
            return "backup format ${backup.backupFormatVersion} comes from a newer app version"
        }
        val spec = spec(domain)
        return spec.validate(backup)
    }

    /** 整机备份的版本判定：逐域读取注册的 Schema 版本，不在备份核心写死。 */
    fun isSupported(backup: Backup): Boolean {
        if (backup.backupFormatVersion > BackupVersions.FORMAT) return false
        ensureBuiltins()
        return handlers.values.all { backup.schemaVersionOf(it.domain) <= it.schemaVersion }
    }

    fun contains(domain: BackupDomain): Boolean {
        ensureBuiltins()
        return handlers.containsKey(domain)
    }

    val registeredDomains: Set<BackupDomain>
        get() {
            ensureBuiltins()
            return handlers.keys.toSet()
        }

    private fun ensureBuiltins() = BuiltinBackupDomains.ensure()
}

/** 备份里某域的 Schema 版本字段。新增域只需在注册表登记并在 [Backup] 里加字段。 */
internal fun Backup.schemaVersionOf(domain: BackupDomain): Int = when (domain) {
    BackupDomain.GESTURE -> gestureSchemaVersion
    BackupDomain.TUNE -> tuneSchemaVersion
    BackupDomain.ACTION -> actionSchemaVersion
    BackupDomain.AUTOMATION -> automationSchemaVersion
    BackupDomain.SEARCH -> searchSchemaVersion
}

/** 内置备份域：整机备份的五组。 */
internal object BuiltinBackupDomains {

    private var installed = false

    @Synchronized
    fun ensure() {
        if (installed) return
        install()
        installed = true
    }

    private fun install() {
        register(
            domain = BackupDomain.GESTURE,
            schemaVersion = BackupVersions.GESTURE,
            snapshot = { full ->
                Backup(
                    backupFormatVersion = full.backupFormatVersion,
                    gestureSchemaVersion = full.gestureSchemaVersion,
                    initialSettings = full.initialSettings,
                    gestureSettings = full.gestureSettings,
                    gestureButtons = full.gestureButtons,
                    subGestureSettings = full.subGestureSettings,
                )
            },
            isEmpty = {
                it.initialSettings == null &&
                    it.gestureSettings == null &&
                    it.gestureButtons == null &&
                    it.subGestureSettings == null
            },
        )
        register(
            domain = BackupDomain.TUNE,
            schemaVersion = BackupVersions.TUNE,
            snapshot = { full ->
                Backup(
                    backupFormatVersion = full.backupFormatVersion,
                    tuneSchemaVersion = full.tuneSchemaVersion,
                    advancedSettings = full.advancedSettings,
                )
            },
            isEmpty = { it.advancedSettings == null },
        )
        register(
            domain = BackupDomain.ACTION,
            schemaVersion = BackupVersions.ACTION,
            snapshot = { full ->
                Backup(
                    backupFormatVersion = full.backupFormatVersion,
                    actionSchemaVersion = full.actionSchemaVersion,
                    actionSettings = full.actionSettings,
                    actionLibrarySettings = full.actionLibrarySettings,
                )
            },
            isEmpty = { it.actionSettings == null && it.actionLibrarySettings == null },
        )
        register(
            domain = BackupDomain.AUTOMATION,
            schemaVersion = BackupVersions.AUTOMATION,
            snapshot = { full ->
                Backup(
                    backupFormatVersion = full.backupFormatVersion,
                    automationSchemaVersion = full.automationSchemaVersion,
                    automationRules = full.automationRules,
                    globalVariables = full.globalVariables,
                )
            },
            isEmpty = { it.automationRules == null && it.globalVariables == null },
        )
        register(
            domain = BackupDomain.SEARCH,
            schemaVersion = BackupVersions.SEARCH,
            snapshot = { full ->
                Backup(
                    backupFormatVersion = full.backupFormatVersion,
                    searchSchemaVersion = full.searchSchemaVersion,
                    quickAppLauncherSettings = full.quickAppLauncherSettings,
                    quickSearchSettings = full.quickSearchSettings,
                )
            },
            isEmpty = { it.quickAppLauncherSettings == null && it.quickSearchSettings == null },
        )
    }

    private fun register(
        domain: BackupDomain,
        schemaVersion: Int,
        snapshot: (Backup) -> Backup,
        isEmpty: (Backup) -> Boolean,
        maxExportBytes: Long = DefaultMaxExportBytes,
    ) {
        BackupDomainRegistry.register(
            object : BackupDomainSpec {
                override val domain: BackupDomain = domain
                override val schemaVersion: Int = schemaVersion
                override val maxExportBytes: Long = maxExportBytes
                override fun snapshot(full: Backup): Backup = snapshot(full)
                override fun isEmpty(backup: Backup): Boolean = isEmpty(backup)
                override fun validate(backup: Backup): String? = when {
                    backup.schemaVersionOf(domain) > schemaVersion ->
                        "domain $domain comes from a newer app version"
                    isEmpty(backup) -> "domain $domain contains no settings"
                    else -> null
                }
            }
        )
    }
}

/** 默认单域导出上限，与整机备份保持一致。 */
internal const val DefaultMaxExportBytes = 8L * 1024 * 1024
