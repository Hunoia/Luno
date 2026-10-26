package hunoia.luno.domain.backup

import android.content.Context
import android.net.Uri
import hunoia.luno.config.ConfigProvider
import hunoia.luno.config.backup.BackupOperator
import hunoia.luno.config.backup.RestorePrecheckResult
import hunoia.luno.config.model.Backup
import hunoia.luno.config.model.BackupDomain
import hunoia.luno.domain.gridpanel.PanelResources
import hunoia.luno.service.ServiceAutoStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** 备份域门面（ui 层唯一入口，整机 + 单域两级）。 */
object BackupCenter {

    /** 恢复/重置只落 DataStore；把保活 prefs 对齐并按恢复后的配置启停常驻服务（见 ServiceAutoStart）。 */
    private fun syncRuntimeAfterRestore() {
        ServiceAutoStart.syncAfterConfigRestore(hunoia.luno.core.AppContext.get())
    }

    suspend fun snapshotAll(): Backup = withContext(Dispatchers.IO) {
        ConfigProvider.snapshotAll()
    }

    suspend fun restoreAll(backup: Backup) = withContext(Dispatchers.IO) {
        ConfigProvider.restoreAll(backup)
        PanelResources.cleanup()
        syncRuntimeAfterRestore()
    }

    suspend fun resetAll() = withContext(Dispatchers.IO) {
        ConfigProvider.resetAll()
        hunoia.luno.action.model.ShortcutIconFiles.deleteAll(hunoia.luno.core.AppContext.get())
        PanelResources.cleanup()
        syncRuntimeAfterRestore()
    }

    suspend fun snapshotDomain(domain: BackupDomain): Backup = withContext(Dispatchers.IO) {
        ConfigProvider.snapshotDomain(domain)
    }

    suspend fun restoreDomain(backup: Backup) = withContext(Dispatchers.IO) {
        ConfigProvider.restoreDomain(backup)
        PanelResources.cleanup()
        syncRuntimeAfterRestore()
    }

    suspend fun backup(context: Context, saveTo: Uri) = withContext(Dispatchers.IO) {
        BackupOperator.backup(context, saveTo)
    }

    fun precheckRestore(context: Context, restoreFrom: Uri): RestorePrecheckResult =
        BackupOperator.precheckRestore(context, restoreFrom)

    suspend fun restore(context: Context, restoreFrom: Uri) = withContext(Dispatchers.IO) {
        BackupOperator.restore(context, restoreFrom)
        PanelResources.cleanup()
        syncRuntimeAfterRestore()
    }

    suspend fun exportDomain(context: Context, domain: BackupDomain, saveTo: Uri) =
        withContext(Dispatchers.IO) {
            BackupOperator.exportDomain(context, domain, saveTo)
        }

    suspend fun importDomain(context: Context, restoreFrom: Uri) = withContext(Dispatchers.IO) {
        BackupOperator.importDomain(context, restoreFrom)
        PanelResources.cleanup()
        syncRuntimeAfterRestore()
    }
}
