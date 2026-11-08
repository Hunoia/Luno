package hunoia.luno.domain.backup

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import hunoia.luno.R
import hunoia.luno.capability.notify.DefaultNotificationPresenter
import hunoia.luno.config.ConfigProvider
import hunoia.luno.config.backup.AutoBackupRepository
import hunoia.luno.config.model.NotificationDisplaySettings
import hunoia.luno.config.model.NotificationKind
import hunoia.luno.config.model.NotificationMode
import hunoia.luno.core.LunoLog
import hunoia.luno.core.LOG_TAG
import hunoia.luno.core.domain.IslandNotificationSpec
import hunoia.luno.core.domain.IslandPublishResult
import hunoia.luno.core.domain.NotificationPresenter
import hunoia.luno.ui.MainActivity

/**
 * 自动备份失败通知（成功保持静默）。
 *
 * 呈现模式走通用通知设置（BACKUP 类型）：ISLAND 为一次性短岛（`islandTimeout` 收尾，
 * 用户设「不限」时取 5 分钟兑底），失败回退标准通知（一次性事件不升 LIVE——promoted
 * 需要 ongoing）。通知权限没给时**静默降级**：不申请权限、不弹窗，反馈由设置子页的
 * 红字状态承担（`AutoBackupSettings.lastError`）。
 */
object AutoBackupNotifier {

    private const val CHANNEL_ID = "auto_backup"
    private const val CHANNEL_NAME = "自动备份"
    private const val NOTIFICATION_ID = 2101

    /** ISLAND 模式下用户设「不限」时的一次性短岛兑底超时（秒）。 */
    private const val ISLAND_DEFAULT_TIMEOUT_SECONDS = 300

    private val presenter: NotificationPresenter by lazy {
        DefaultNotificationPresenter(hunoia.luno.core.AppContext.get())
    }

    suspend fun notifyFailure(context: Context, failure: AutoBackupRepository.Failure) {
        val app = context.applicationContext
        val manager = app.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_DEFAULT)
                .apply { setShowBadge(false) },
        )

        if (!NotificationManagerCompat.from(app).areNotificationsEnabled()) {
            LunoLog.w(LOG_TAG, "auto backup failed ($failure) but notifications disabled")
            return
        }

        val display = runCatching { ConfigProvider.getAdvancedSettings().notificationDisplay }
            .getOrDefault(NotificationDisplaySettings())
        val title = app.getString(R.string.auto_backup_notify_title)
        val text = app.getString(failure.labelRes())

        when (display.modeFor(NotificationKind.BACKUP)) {
            NotificationMode.ISLAND -> {
                when (val result = presenter.publishIsland(islandSpec(app, title, text, display))) {
                    IslandPublishResult.Posted -> {
                        LunoLog.i(LOG_TAG, "auto backup island posted: id=$NOTIFICATION_ID")
                        return
                    }
                    else -> LunoLog.w(
                        LOG_TAG,
                        "auto backup island fallback to standard: id=$NOTIFICATION_ID reason=$result",
                    )
                }
            }
            NotificationMode.LIVE, NotificationMode.STANDARD -> Unit
        }
        postStandard(app, manager, title, text)
    }

    private fun islandSpec(
        app: Context,
        title: String,
        text: String,
        display: NotificationDisplaySettings,
    ): IslandNotificationSpec {
        val contentUri = runCatching {
            Intent(app, MainActivity::class.java).toUri(Intent.URI_INTENT_SCHEME)
        }.getOrNull()
        return IslandNotificationSpec(
            channelId = CHANNEL_ID,
            notificationId = NOTIFICATION_ID,
            title = title,
            text = text,
            capsuleText = title.take(8),
            expandedSeconds = display.islandExpandSeconds,
            blockWindowMs = display.islandDisconnectMs,
            outerGlow = display.islandOuterGlow,
            showInShade = display.islandKeepShadeNotification,
            timeoutSeconds = display.islandTimeoutSeconds.takeIf { it > 0 }
                ?: ISLAND_DEFAULT_TIMEOUT_SECONDS,
            ongoing = false,
            contentIntentUri = contentUri,
        )
    }

    private fun postStandard(
        app: Context,
        manager: NotificationManager,
        title: String,
        text: String,
    ) {
        val pendingIntent = PendingIntent.getActivity(
            app,
            0,
            Intent(app, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = Notification.Builder(app, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(text)
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
