package hunoia.luno.domain.trend

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import hunoia.luno.R
import hunoia.luno.config.model.TREND_REMINDER_CHANNEL_ID
import hunoia.luno.config.model.TrendMetric
import hunoia.luno.core.LOG_TAG
import hunoia.luno.core.LunoLog
import hunoia.luno.gridpanel.TrendExtras

/**
 * 趋势提醒通知：轻量标准通知。
 *
 * 明确不用 LIVE / 超级岛、不常驻、不推迟 —— 用户要的只是「今天还没记录，去补一个」。
 * 点击直接进该指标详情并弹出快速记录，减少一次点击。
 */
object TrendReminderNotifier {

    private const val CHANNEL_ID = TREND_REMINDER_CHANNEL_ID

    /**
     * 通知权限在 Android 13+ 为运行时权限；`notify` 可能抛 [SecurityException]，
     * 下方 runCatching 已显式兜底（返回 false 不崩溃），lint 的 MissingPermission
     * 不识别 Kotlin runCatching 的异常处理，故显式抑制。
     */
    @SuppressLint("MissingPermission")
    fun show(context: Context, metric: TrendMetric): Boolean {
        val app = context.applicationContext
        createChannel(app)
        if (!NotificationManagerCompat.from(app).areNotificationsEnabled()) {
            LunoLog.i(LOG_TAG, "trend reminder skipped: notifications disabled")
            return false
        }
        val title = app.getString(R.string.trend_reminder_title, metric.name)
        val body = app.getString(R.string.trend_reminder_text, metric.name)
        val contentIntent = PendingIntent.getActivity(
            app,
            TrendReminderScheduler.notificationId(metric.id),
            TrendExtras.openDetailIntent(app, metric.id, openRecord = true),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(app, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .build()
        return runCatching {
            NotificationManagerCompat.from(app).notify(
                TrendReminderScheduler.notificationId(metric.id),
                notification,
            )
            LunoLog.i(LOG_TAG, "trend reminder shown: metric=${metric.id}")
            true
        }.getOrElse { e ->
            LunoLog.w(LOG_TAG, "trend reminder show failed: ${e.message}")
            false
        }
    }

    fun cancel(context: Context, metricId: String) {
        runCatching {
            NotificationManagerCompat.from(context.applicationContext)
                .cancel(TrendReminderScheduler.notificationId(metricId))
        }
    }

    private fun createChannel(app: Context) {
        val manager = app.getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(
            CHANNEL_ID,
            app.getString(R.string.trend_reminder_channel),
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = app.getString(R.string.trend_reminder_channel_desc)
            setShowBadge(false)
            lockscreenVisibility = Notification.VISIBILITY_PRIVATE
        }
        manager.createNotificationChannel(channel)
    }
}
