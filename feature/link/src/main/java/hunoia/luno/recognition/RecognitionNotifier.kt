package hunoia.luno.recognition

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationManagerCompat
import hunoia.luno.config.model.NotificationMode
import hunoia.luno.config.model.RecognitionNotifySpec
import hunoia.luno.core.LOG_TAG
import hunoia.luno.core.LunoLog
import hunoia.luno.link.R
import hunoia.luno.runtime.recognition.RecognizedItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 识别结果的「通知」输出。
 *
 * 每规则可选 [RecognitionNotifySpec.mode]：
 * - [NotificationMode.LIVE]：Android 16+ 升 Live Update / 状态栏胶囊（无权限自动落回标准）；
 * - [NotificationMode.STANDARD]：普通通知。
 *
 * 通道归属与本模块；内容占位替换（`{value}` `{rule}` `{host}`）也在这里。
 * 通知上只有一个「完成」按钮：点它撤掉通知。识别器的产物是一次性凭证（取件码 / 验证码），
 * 用户看完就走，不需要在通知上再挂「复制」——值已经进过剪贴板了。
 */
internal object RecognitionNotifier {

    /** 识别器专用通道；与待办 / 习惯分开，用户能单独静音。 */
    const val CHANNEL_ID = "recognition"

    private const val CHANNEL_NAME = "识别提醒"

    /** 点「完成」的广播。 */
    const val ACTION_DISMISS = "hunoia.luno.action.RECOGNITION_NOTIFY_DISMISS"

    /** 状态栏胶囊摘要文案上限：系统按「几个字」排版，过长会溢出，先自行收敛。 */
    private const val SHORT_CRITICAL_TEXT_MAX = 8

    /** 通知 id 由规则 id + 值的哈希派生：同一条规则抓到同一个值不会堆两条通知。 */
    private fun notificationId(item: RecognizedItem): Int =
        ("${item.ruleId}:${item.value}").hashCode() and 0x7FFFFFFF

    suspend fun post(
        context: Context,
        item: RecognizedItem,
        spec: RecognitionNotifySpec,
        idempotencyKey: String?,
    ): Boolean = withContext(Dispatchers.IO) {
        val app = context.applicationContext
        if (!NotificationManagerCompat.from(app).areNotificationsEnabled()) {
            LunoLog.w(LOG_TAG, "recognition notify skipped: notifications disabled")
            return@withContext false
        }
        ensureChannel(app)
        val id = notificationId(item)
        val title = spec.title.resolve(item).ifBlank { item.ruleName.ifBlank { item.value } }
        val text = spec.text.resolve(item).ifBlank { item.value }
        val done = PendingIntent.getBroadcast(
            app,
            id,
            dismissIntent(app, id),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val builder = Notification.Builder(app, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(Notification.BigTextStyle().bigText(text))
            .setOngoing(false)
            .setAutoCancel(true)
            .addAction(
                Notification.Action.Builder(null, app.getString(R.string.recognition_notify_done), done)
                    .build(),
            )
        // LIVE 胶囊：Android 16+ 且有 promoted 权限才升，否则保持标准通知。
        if (spec.mode == NotificationMode.LIVE) applyLiveUpdate(app, builder, text)
        // 与其它通知器同口径：开关 + 运行期权限各查一次，再吞掉系统拒绝。
        if (!NotificationManagerCompat.from(app).areNotificationsEnabled()) return@withContext false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            app.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return@withContext false
        }
        runCatching {
            NotificationManagerCompat.from(app).notify(id, builder.build())
            true
        }.getOrDefault(false)
    }

    /**
     * Android 16+：升为 Live Update / 状态栏胶囊（HyperOS 4 会渲染成超级岛）；
     * 无权限时保持标准通知。
     *
     * 必须用 `canPostPromotedNotifications()` 先探权限：未授权时仍强行
     * `setRequestPromotedOngoing(true)`，系统会把整条通知判为非法并直接撤回，
     * 用户会连普通通知都收不到。
     */
    private fun applyLiveUpdate(app: Context, builder: Notification.Builder, text: String) {
        if (Build.VERSION.SDK_INT < 36) return
        val granted = runCatching {
            app.getSystemService(NotificationManager::class.java).canPostPromotedNotifications()
        }.getOrDefault(false)
        if (!granted) {
            LunoLog.w(LOG_TAG, "recognition live update not granted, fallback to standard")
            return
        }
        builder.setRequestPromotedOngoing(true)
        builder.setShortCriticalText(text.take(SHORT_CRITICAL_TEXT_MAX))
    }

    private fun ensureChannel(app: Context) {
        val manager = app.getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        manager.createNotificationChannel(
            // HIGH：LIVE 胶囊 / 悬浮态需要高重要级；关掉悬浮时系统会自己降级成普通通知。
            NotificationChannel(CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_HIGH).apply {
                description = "识别器命中规则时发通知"
                setShowBadge(false)
            },
        )
    }

    private fun dismissIntent(app: Context, id: Int): Intent =
        Intent(app, RecognitionNotifyReceiver::class.java).apply {
            action = ACTION_DISMISS
            putExtra(EXTRA_NOTIFICATION_ID, id)
        }

    /** 模板占位：`{value}` / `{rule}` / `{host}`。 */
    private fun String.resolve(item: RecognizedItem): String = this
        .replace("{value}", item.value)
        .replace("{rule}", item.ruleName.ifBlank { item.ruleId })
        .replace("{host}", item.urls.firstOrNull()?.let { hunoia.luno.runtime.recognition.LinkUrls.hostOf(it) }.orEmpty())

    const val EXTRA_NOTIFICATION_ID = "hunoia.luno.extra.RECOGNITION_NOTIFICATION_ID"
}

/** 「完成」按钮的接收器：撤通知。 */
class RecognitionNotifyReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != RecognitionNotifier.ACTION_DISMISS) return
        val id = intent.getIntExtra(RecognitionNotifier.EXTRA_NOTIFICATION_ID, -1)
        if (id < 0) return
        runCatching {
            NotificationManagerCompat.from(context.applicationContext).cancel(id)
        }
    }
}
