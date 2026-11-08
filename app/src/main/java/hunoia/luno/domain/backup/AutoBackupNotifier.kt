package hunoia.luno.domain.backup

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import hunoia.luno.R
import hunoia.luno.config.backup.AutoBackupRepository
import hunoia.luno.core.LunoLog
import hunoia.luno.core.LOG_TAG
import hunoia.luno.ui.MainActivity

/**
 * 自动备份失败通知（成功保持静默）。
 *
 * 通知权限没给时**静默降级**：不申请权限、不弹窗，反馈由设置子页的红字状态承担
 * （`AutoBackupSettings.lastError`）。
 */
object AutoBackupNotifier {

    private const val CHANNEL_ID = "auto_backup"
    private const val CHANNEL_NAME = "自动备份"
    private const val NOTIFICATION_ID = 2101

    fun notifyFailure(context: Context, failure: AutoBackupRepository.Failure) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            CHANNEL_ID,
            CHANNEL_NAME,
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            setShowBadge(false)
        }
        manager.createNotificationChannel(channel)

        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) {
            LunoLog.w(LOG_TAG, "auto backup failed ($failure) but notifications disabled")
            return
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = Notification.Builder(context, CHANNEL_ID)
            .setContentTitle(context.getString(R.string.auto_backup_notify_title))
            .setContentText(context.getString(failure.labelRes()))
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()
        runCatching { manager.notify(NOTIFICATION_ID, notification) }
            .onFailure { LunoLog.w(LOG_TAG, "auto backup notify failed: ${it.message}") }
    }
}

/** 失败原因码 → 用户可读文案（通知与子页红字共用同一套口径）。 */
fun AutoBackupRepository.Failure.labelRes(): Int = when (this) {
    AutoBackupRepository.Failure.PERMISSION_LOST -> R.string.auto_backup_fail_permission_lost
    AutoBackupRepository.Failure.CREATE_FAILED -> R.string.auto_backup_fail_create
    AutoBackupRepository.Failure.WRITE_FAILED -> R.string.auto_backup_fail_write
    AutoBackupRepository.Failure.TOO_LARGE -> R.string.auto_backup_fail_too_large
    AutoBackupRepository.Failure.NEED_ALL_FILES -> R.string.auto_backup_fail_need_all_files
    AutoBackupRepository.Failure.UNSUPPORTED_LOCATION -> R.string.auto_backup_fail_unsupported_location
    AutoBackupRepository.Failure.UNKNOWN -> R.string.auto_backup_fail_unknown
}
