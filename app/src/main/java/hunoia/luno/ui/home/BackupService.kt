package hunoia.luno.ui.home

import hunoia.luno.domain.AppGraph

import android.content.Context
import android.net.Uri
import hunoia.luno.R
import hunoia.luno.config.backup.RestorePrecheckResult

/** ui 侧备份服务：toast 文案归 ui，读写经 domain 门面。 */
object BackupService {
    suspend fun backup(context: Context, saveTo: Uri, toast: (Int) -> Unit) {
        AppGraph.backup.backup(context, saveTo)
        toast(R.string.backup_success)
    }

    fun precheckRestore(context: Context, restoreFrom: Uri): RestorePrecheckResult {
        return AppGraph.backup.precheckRestore(context, restoreFrom)
    }

    suspend fun restore(context: Context, restoreFrom: Uri, toast: (Int) -> Unit) {
        AppGraph.backup.restore(context, restoreFrom)
        toast(R.string.restore_success)
    }
}
