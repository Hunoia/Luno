package hunoia.luno.domain.backup

import android.content.Context
import android.net.Uri
import hunoia.luno.config.backup.RestorePrecheckResult
import hunoia.luno.config.model.Backup
import hunoia.luno.config.model.BackupDomain

/**
 * 备份域门面契约（UI 唯一入口）。
 *
 * 实现留在 `:app` 的 `BackupCenter`；UI 通过 `AppGraph.backup` 取得实现，
 * 不直接依赖 DataStore 与备份操作细节。
 */
interface BackupFacade {

    suspend fun snapshotAll(): Backup
    suspend fun restoreAll(backup: Backup)
    suspend fun resetAll()

    suspend fun snapshotDomain(domain: BackupDomain): Backup
    suspend fun restoreDomain(backup: Backup)

    suspend fun backup(context: Context, saveTo: Uri)
    fun precheckRestore(context: Context, restoreFrom: Uri): RestorePrecheckResult
    suspend fun restore(context: Context, restoreFrom: Uri)

    suspend fun exportDomain(context: Context, domain: BackupDomain, saveTo: Uri)
    suspend fun importDomain(context: Context, domain: BackupDomain, restoreFrom: Uri)
}
