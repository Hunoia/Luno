package hunoia.luno.domain.backup

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

/**
 * 定时自动备份的入队 / 取消。
 *
 * 约束与口径：
 * - **只在主进程调用**：WorkManager 默认初始化在主进程，`:service` 进程不碰它，
 *   Worker 也只会在主进程被拉起（系统为 job 启动默认进程）。
 * - 周期任务由系统持久化，重启后自动恢复，无需在 `BootReceiver` 里补入队。
 * - 24h 是**下限**：Doze / 电量策略会把执行推后，口径是「至少 24h 一次」。
 * - 首次入队不设 initialDelay：开开关后尽快跑出第一份，不用等满一个周期。
 * - 已知限制：用户「强行停止」应用后，WorkManager 不再执行，直到下次手动打开应用
 *   （平台行为，任何库都绕不过）。
 */
object AutoBackupScheduler {

    private const val PERIODIC_WORK = "luno_auto_backup_periodic"
    private const val ONCE_WORK = "luno_auto_backup_once"

    private const val PERIOD_HOURS = 24L

    /** 开启自动备份：入队周期任务（首次尽快执行）。已入队时用 UPDATE 换成新参数。 */
    fun schedule(context: Context) {
        val request = PeriodicWorkRequestBuilder<AutoBackupWorker>(PERIOD_HOURS, TimeUnit.HOURS)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            PERIODIC_WORK,
            ExistingPeriodicWorkPolicy.UPDATE,
            request,
        )
    }

    /** 关闭自动备份：取消周期任务与排队中的手动触发。 */
    fun cancel(context: Context) {
        val workManager = WorkManager.getInstance(context)
        workManager.cancelUniqueWork(PERIODIC_WORK)
        workManager.cancelUniqueWork(ONCE_WORK)
    }

    /**
     * 「立即备份」：一次性任务，进程被杀也会由系统补跑。
     * 与周期任务互不覆盖（不同 unique name），并发时由 `BackupOperator` 的互斥串行化。
     */
    fun runNow(context: Context) {
        WorkManager.getInstance(context).enqueueUniqueWork(
            ONCE_WORK,
            ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<AutoBackupWorker>().build(),
        )
    }
}
