package hunoia.luno.domain.notify

import android.app.AlarmManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import hunoia.luno.config.ConfigProvider
import hunoia.luno.config.model.NotificationDisplaySettings
import hunoia.luno.core.LOG_TAG
import hunoia.luno.core.LunoLog

/**
 * 进度类通知的「点击后到时移除」编排。
 *
 * 语义（通用通知设置 → 进度通知）：点了会更新进度的通知（习惯「+1」这类）在最后一次点击
 * 之后经过 `progressDismissSeconds` 自动移除整条通知；期间再点一次会重置计时，达标则由
 * 业务侧立即移除并取消计时。
 *
 * 为什么用 AlarmManager 而不是协程：计时需要跨进程存活（`BroadcastReceiver` 的 `goAsync`
 * 协程在广播结束就收掉了），且用户可能立刻息屏。`setAndAllowWhileIdle` 不走 exact 通道，
 * 不依赖「闹钟和提醒」权限；几秒级误差对「一会自己移除」没有影响。
 *
 * 被移除的是通知 id 本身（岛 + 通知栏一起走），由 [ProgressNotificationDismissReceiver] 执行。
 */
object ProgressNotificationDismisser {

    /** 自动移除广播：只带通知 id，不区分业务类型（以后进度类通知共用）。 */
    const val ACTION_DISMISS = "hunoia.luno.action.PROGRESS_NOTIFICATION_DISMISS"
    const val EXTRA_NOTIFICATION_ID = "notification_id"

    /** 按通用通知设置里的延时排程；0 / 读配置失败 → 取消已有排程（不变更通知）。 */
    suspend fun scheduleFromSettings(context: Context, notificationId: Int) {
        val seconds = runCatching {
            ConfigProvider.getAdvancedSettings().notificationDisplay.progressDismissSeconds
        }.getOrDefault(NotificationDisplaySettings.DEFAULT_PROGRESS_DISMISS_SECONDS)
        schedule(context, notificationId, seconds)
    }

    fun schedule(context: Context, notificationId: Int, delaySeconds: Int) {
        val app = context.applicationContext
        if (delaySeconds <= 0) {
            cancel(app, notificationId)
            return
        }
        val alarmManager = app.getSystemService(AlarmManager::class.java) ?: return
        val triggerAt = System.currentTimeMillis() + delaySeconds * 1000L
        runCatching {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent(app, notificationId, create = true)!!)
            LunoLog.i(LOG_TAG, "progress notification dismiss scheduled: id=$notificationId in ${delaySeconds}s")
        }.onFailure { LunoLog.w(LOG_TAG, "progress notification dismiss schedule failed: id=$notificationId ${it.message}") }
    }

    fun cancel(context: Context, notificationId: Int) {
        val app = context.applicationContext
        val pending = pendingIntent(app, notificationId, create = false) ?: return
        app.getSystemService(AlarmManager::class.java)?.cancel(pending)
        pending.cancel()
        LunoLog.i(LOG_TAG, "progress notification dismiss cancelled: id=$notificationId")
    }

    private fun pendingIntent(app: Context, notificationId: Int, create: Boolean): PendingIntent? {
        val intent = Intent(app, ProgressNotificationDismissReceiver::class.java).apply {
            action = ACTION_DISMISS
            putExtra(EXTRA_NOTIFICATION_ID, notificationId)
        }
        val flags = PendingIntent.FLAG_IMMUTABLE or
            (if (create) PendingIntent.FLAG_UPDATE_CURRENT else PendingIntent.FLAG_NO_CREATE)
        return PendingIntent.getBroadcast(app, notificationId, intent, flags)
    }
}

/**
 * 到时移除通知本体（岛 + 通知栏）。
 *
 * 与 `HabitReminderNotifier.cancel` 同口径：少数 HyperOS 版本 `cancel` 会被岛自愈贴回，
 * 仍活跃时补一次。进程若在排程期间被杀，广播会把进程拉起来执行，不依赖常驻。
 */
class ProgressNotificationDismissReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ProgressNotificationDismisser.ACTION_DISMISS) return
        val id = intent.getIntExtra(ProgressNotificationDismisser.EXTRA_NOTIFICATION_ID, -1)
        if (id < 0) return
        val app = context.applicationContext
        val manager = app.getSystemService(NotificationManager::class.java) ?: return
        LunoLog.i(LOG_TAG, "progress notification dismiss fired: id=$id")
        manager.cancel(id)
        if (runCatching { manager.activeNotifications?.any { it.id == id } == true }.getOrDefault(false)) {
            manager.cancel(id)
        }
    }
}
