package hunoia.luno.domain.log

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import hunoia.luno.R
import hunoia.luno.core.Incident
import hunoia.luno.core.IncidentLog
import hunoia.luno.ui.MainActivity

/**
 * 故障通知：把 [IncidentLog] 记录到的崩溃 / ANR / ERROR 汇总成一条系统通知。
 *
 * 只保留一个通知 ID：新故障更新同一条通知，避免错误循环把通知栏刷爆；
 * 去重与限频在 [IncidentLog] 内完成。通知开关关闭或无权限时静默降级，记录照常。
 */
object IncidentNotifier {

    /** 点击通知打开运行日志页并展开故障记录。 */
    const val EXTRA_OPEN_INCIDENTS = "hunoia.luno.extra.OPEN_INCIDENTS"

    private const val TAG = "IncidentNotifier"
    private const val CHANNEL_ID = "runtime_incidents"
    private const val NOTIFICATION_ID = 0x4C554E4F

    fun install(context: Context) {
        val app = context.applicationContext
        IncidentLog.notifier = { incident -> post(app, incident) }
    }

    private fun post(context: Context, incident: Incident) {
        runCatching {
            val manager = context.getSystemService(NotificationManager::class.java) ?: return
            if (!manager.areNotificationsEnabled()) return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
                PackageManager.PERMISSION_GRANTED
            ) {
                return
            }
            ensureChannel(context, manager)

            val title = context.getString(R.string.incident_notification_title)
            val summary = context.getString(
                R.string.incident_notification_text,
                incident.tag.ifBlank { incident.kind.name },
                incident.message.lineSequence().firstOrNull().orEmpty().take(160),
            )
            val bigText = buildString {
                appendLine(summary)
                appendLine()
                appendLine("kind=${incident.kind.name} process=${incident.process} thread=${incident.thread}")
                if (incident.stackTrace.isNotBlank()) {
                    appendLine(incident.stackTrace.lineSequence().take(20).joinToString("\n"))
                }
                append(context.getString(R.string.incident_notification_tap_hint))
            }
            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(EXTRA_OPEN_INCIDENTS, true)
            }
            val pendingIntent = PendingIntent.getActivity(
                context,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            val notification = Notification.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(title)
                .setContentText(summary)
                .setStyle(Notification.BigTextStyle().bigText(bigText))
                .setContentIntent(pendingIntent)
                .setCategory(Notification.CATEGORY_ERROR)
                .setVisibility(Notification.VISIBILITY_PRIVATE)
                .setAutoCancel(true)
                .build()
            manager.notify(NOTIFICATION_ID, notification)
        }.onFailure { Log.e(TAG, "post incident notification failed", it) }
    }

    private fun ensureChannel(context: Context, manager: NotificationManager) {
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.incident_notification_channel),
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = context.getString(R.string.incident_notification_channel_desc)
                setShowBadge(true)
            },
        )
    }
}
