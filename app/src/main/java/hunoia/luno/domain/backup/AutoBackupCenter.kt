package hunoia.luno.domain.backup

import android.content.Context
import android.content.Intent
import android.net.Uri
import hunoia.luno.config.ConfigProvider
import hunoia.luno.config.backup.AutoBackupFile
import hunoia.luno.config.backup.AutoBackupRepository
import hunoia.luno.config.model.AutoBackupSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

/**
 * 定时自动备份的 UI 门面（`ui/settings/backup` 子页的唯一入口）。
 *
 * 与 `BackupCenter`（手动备份/恢复契约）分开：这里管的是**设备本地状态**
 * （开关、目录授权、上次执行结果）与调度入队，备份产物本身仍走同一条 `BackupOperator` 链路。
 *
 * 只在主进程被调用（WorkManager 见 [AutoBackupScheduler] 的进程约束）。
 */
object AutoBackupCenter {

    /** 设置子页订阅的实时状态。 */
    val settings: Flow<AutoBackupSettings> = ConfigProvider.autoBackup

    /** 当前配置里存的备份目录；空串 = 还没选过。 */
    suspend fun treeUri(): Uri? = ConfigProvider.getAutoBackup().treeUri
        .takeIf { it.isNotBlank() }?.let(Uri::parse)

    /** 存量目录的持久化授权是否还在（被系统回收时返回 false，需要重新选目录）。 */
    suspend fun hasStoredTree(context: Context): Boolean {
        val uri = treeUri() ?: return false
        return AutoBackupRepository.hasPersistedTree(context, uri)
    }

    /**
     * 用户在系统选择器里选定目录后调用：持久化授权 → 落库 → 释放旧授权 → 入队。
     *
     * [enable] = true 表示这是「开开关」触发的选目录，选完直接进入开启态。
     */
    suspend fun adoptTree(context: Context, treeUri: Uri, enable: Boolean) =
        withContext(Dispatchers.IO) {
            val resolver = context.contentResolver
            val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or
                Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            resolver.takePersistableUriPermission(treeUri, flags)

            val previous = ConfigProvider.getAutoBackup().treeUri
            ConfigProvider.updateAutoBackup {
                it.copy(treeUri = treeUri.toString(), enabled = it.enabled || enable)
            }
            // 旧目录不再归我们管：释放授权，别让系统权限列表无限堆积。
            if (previous.isNotBlank() && previous != treeUri.toString()) {
                runCatching {
                    resolver.releasePersistableUriPermission(Uri.parse(previous), flags)
                }
            }
            if (ConfigProvider.getAutoBackup().enabled) {
                AutoBackupScheduler.schedule(context)
            }
        }

    /** 开关切换（只改开关；目录无效时由 UI 先走 [adoptTree]）。 */
    suspend fun setEnabled(context: Context, enabled: Boolean) = withContext(Dispatchers.IO) {
        ConfigProvider.updateAutoBackup { it.copy(enabled = enabled) }
        if (enabled) AutoBackupScheduler.schedule(context) else AutoBackupScheduler.cancel(context)
    }

    /** 「立即备份」：入队一次性任务，结果由子页订阅 [settings] 刷新。 */
    fun runNow(context: Context) = AutoBackupScheduler.runNow(context)

    /** 当前目录里的自动备份（新 → 旧）；未选目录返回空列表。 */
    suspend fun listBackups(context: Context): List<AutoBackupFile> =
        withContext(Dispatchers.IO) {
            val uri = treeUri() ?: return@withContext emptyList()
            runCatching { AutoBackupRepository.listBackups(context, uri) }
                .getOrElse { emptyList() }
        }

    /** 目录显示名；拿不到返回 null（UI 退回「未选择」或原始路径）。 */
    fun treeDisplayName(context: Context, treeUri: Uri): String? =
        AutoBackupRepository.treeDisplayName(context, treeUri)
}
