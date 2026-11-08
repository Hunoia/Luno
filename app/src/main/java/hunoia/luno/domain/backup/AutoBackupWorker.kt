package hunoia.luno.domain.backup

import android.content.Context
import android.net.Uri
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import hunoia.luno.config.ConfigProvider
import hunoia.luno.config.backup.AutoBackupRepository
import hunoia.luno.core.LunoLog
import hunoia.luno.core.LOG_TAG

/**
 * 一次自动备份：校验目录授权 → 打包写入 → 记结果。
 *
 * 失败处理口径（用户已确认）：
 * - **每次失败都发通知**，不做「连续 N 次才提醒」之类的去重；
 * - 因此这里**不返回 `Result.retry()`**——重试会在同一次故障里连发通知，
 *   下个周期自然会再试；
 * - 结果写进 `AutoBackupSettings`（`lastSuccessAtMs` / `lastFailureAtMs` / `lastError`），
 *   设置子页据此显示上次执行状态，也是没通知权限时的唯一反馈。
 */
class AutoBackupWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val settings = ConfigProvider.getAutoBackup()
        // 开关已关 / 未选目录：取消是异步的，可能还有已入队的任务，直接安静结束。
        if (!settings.enabled || !settings.hasTree()) return Result.success()

        val tree = Uri.parse(settings.treeUri)
        return try {
            AutoBackupRepository.writeBackup(applicationContext, tree)
            ConfigProvider.updateAutoBackup {
                it.copy(lastSuccessAtMs = System.currentTimeMillis(), lastError = "")
            }
            Result.success()
        } catch (e: Exception) {
            val failure = (e as? AutoBackupRepository.AutoBackupException)?.failure
                ?: if (AutoBackupRepository.hasPersistedTree(applicationContext, tree)) {
                    AutoBackupRepository.Failure.UNKNOWN
                } else {
                    AutoBackupRepository.Failure.PERMISSION_LOST
                }
            LunoLog.e(LOG_TAG, "auto backup failed: $failure", e)
            ConfigProvider.updateAutoBackup {
                it.copy(lastFailureAtMs = System.currentTimeMillis(), lastError = failure.name)
            }
            AutoBackupNotifier.notifyFailure(applicationContext, failure)
            Result.failure()
        }
    }
}
