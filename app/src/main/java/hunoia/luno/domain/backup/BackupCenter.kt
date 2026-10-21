package hunoia.luno.domain.backup

import android.content.Context
import android.net.Uri
import hunoia.luno.config.ConfigProvider
import hunoia.luno.config.backup.BackupOperator
import hunoia.luno.config.backup.RestorePrecheckResult
import hunoia.luno.config.model.Backup
import hunoia.luno.config.model.BackupDomain
import hunoia.luno.domain.gridpanel.PanelResources
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** 备份域门面（ui 层唯一入口，整机 + 单域两级）。 */
object BackupCenter {

    suspend fun snapshotAll(): Backup = withContext(Dispatchers.IO) {
        ConfigProvider.snapshotAll()
    }

    suspend fun restoreAll(backup: Backup) = withContext(Dispatchers.IO) {
        ConfigProvider.restoreAll(backup)
        PanelResources.cleanup()
    }

    suspend fun resetAll() = withContext(Dispatchers.IO) {
        ConfigProvider.resetAll()
        hunoia.luno.action.model.ShortcutIconFiles.deleteAll(hunoia.luno.core.AppContext.get())
        PanelResources.cleanup()
    }

    suspend fun snapshotDomain(domain: BackupDomain): Backup = withContext(Dispatchers.IO) {
        ConfigProvider.snapshotDomain(domain)
    }

    suspend fun restoreDomain(backup: Backup) = withContext(Dispatchers.IO) {
        ConfigProvider.restoreDomain(backup)
        PanelResources.cleanup()
    }

    suspend fun backup(context: Context, saveTo: Uri) = withContext(Dispatchers.IO) {
        BackupOperator.backup(context, saveTo)
    }

    fun precheckRestore(context: Context, restoreFrom: Uri): RestorePrecheckResult =
        BackupOperator.precheckRestore(context, restoreFrom)

    suspend fun restore(context: Context, restoreFrom: Uri) = withContext(Dispatchers.IO) {
        BackupOperator.restore(context, restoreFrom)
        PanelResources.cleanup()
    }

    suspend fun exportDomain(context: Context, domain: BackupDomain, saveTo: Uri) =
        withContext(Dispatchers.IO) {
            BackupOperator.exportDomain(context, domain, saveTo)
        }

    suspend fun importDomain(context: Context, restoreFrom: Uri) = withContext(Dispatchers.IO) {
        BackupOperator.importDomain(context, restoreFrom)
        PanelResources.cleanup()
    }
}
