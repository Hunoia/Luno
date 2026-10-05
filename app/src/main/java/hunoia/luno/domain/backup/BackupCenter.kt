package hunoia.luno.domain.backup

import android.content.Context
import android.net.Uri
import hunoia.luno.config.ConfigProvider
import hunoia.luno.config.backup.BackupOperator
import hunoia.luno.config.backup.RestorePrecheckResult
import hunoia.luno.config.model.Backup
import hunoia.luno.config.model.BackupDomain

/** 备份域门面（ui 层唯一入口，整机 + 单域两级）。 */
object BackupCenter {

    suspend fun snapshotAll(): Backup = ConfigProvider.snapshotAll()
    suspend fun restoreAll(backup: Backup) = ConfigProvider.restoreAll(backup)
    suspend fun resetAll() = ConfigProvider.resetAll()
    suspend fun snapshotDomain(domain: BackupDomain): Backup = ConfigProvider.snapshotDomain(domain)
    suspend fun restoreDomain(backup: Backup) = ConfigProvider.restoreDomain(backup)

    suspend fun backup(context: Context, saveTo: Uri) = BackupOperator.backup(context, saveTo)

    fun precheckRestore(context: Context, restoreFrom: Uri): RestorePrecheckResult =
        BackupOperator.precheckRestore(context, restoreFrom)

    suspend fun restore(context: Context, restoreFrom: Uri) = BackupOperator.restore(context, restoreFrom)

    suspend fun exportDomain(context: Context, domain: BackupDomain, saveTo: Uri) =
        BackupOperator.exportDomain(context, domain, saveTo)

    suspend fun importDomain(context: Context, restoreFrom: Uri) =
        BackupOperator.importDomain(context, restoreFrom)
}
