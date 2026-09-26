package hunoia.luno.ui.home

import android.content.Context
import android.net.Uri
import hunoia.luno.R
import hunoia.luno.config.backup.BackupOperator
import hunoia.luno.config.backup.RestorePrecheckResult

object BackupService {
    suspend fun backup(context: Context, saveTo: Uri, toast: (Int) -> Unit) {
        BackupOperator.backup(context, saveTo)
        toast(R.string.backup_success)
    }

    fun precheckRestore(context: Context, restoreFrom: Uri): RestorePrecheckResult {
        return BackupOperator.precheckRestore(context, restoreFrom)
    }

    suspend fun restore(context: Context, restoreFrom: Uri, toast: (Int) -> Unit) {
        BackupOperator.restore(context, restoreFrom)
        toast(R.string.restore_success)
    }
}
