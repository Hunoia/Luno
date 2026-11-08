package hunoia.luno.domain.habit

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import androidx.core.app.NotificationManagerCompat
import hunoia.luno.R
import hunoia.luno.config.model.HABIT_REMINDER_CHANNEL_ID
import hunoia.luno.config.model.Habit
import hunoia.luno.config.model.HabitNotificationStyle
import hunoia.luno.core.LOG_TAG
import hunoia.luno.core.LunoLog
import hunoia.luno.gridpanel.HabitExtras

/**
 * 习惯提醒通知（两种呈现方式，参照 moting）。
 *
 * - [HabitNotificationStyle.STANDARD]：HIGH 通道 heads-up + ongoing，点按进习惯编辑页，
 *   「完成」直接打卡。
 * - [HabitNotificationStyle.LIVE_ACTIVITY]：Android 16+ 在标准通知上加
 *   [Notification.Builder.setRequestPromotedOngoing] + [Notification.Builder.setShortCriticalText]，
 *   升为 Live Update / 状态栏胶囊；无 [NotificationManager.canPostPromotedNotifications] 时回退标准通知。
 *   小米 HyperOS 4 上，这条通道就是系统渲染的「超级岛」（岛 / 胶囊），无需另设选项。
 *
 * 为什么不用 `miui.focus.param`（2026-11 在 HyperOS 4.0 / Android 17 上实测）：
 * - 焦点通知 / 超级岛对第三方应用是**白名单**能力，SystemUI 会向小米 XMS 鉴权
 *   （`AuthConnectData(scope=20032)`），未过白名单的包一律 `onAuthFailed`，随后
 *   `Plugin prevented post` 把通知整条撤回，用户什么也看不到（连普通通知都没有）。
 *   申请入口：邮件 mipush-permission@xiaomi.com。
 * - 即便有白名单，protocol=3 的 `param_v2` 用严格反序列化：`enableFloat` / `updatable` /
 *   `islandFirstFloat` / `isShowNotification` / `dismissIsland` / `needCloseAnimation`
 *   必须是 JSON 布尔而不是 0/1，否则 `Expected a boolean but was NUMBER ... path $.enableFloat`。
 *
 * 通知权限没给时静默不弹（与 [hunoia.luno.domain.backup.AutoBackupNotifier] 同一口径）。
 */
object HabitReminderNotifier {

    private const val CHANNEL_ID = HABIT_REMINDER_CHANNEL_ID

    /** 测试通知用固定假 id：不写打卡、不参与调度，60 秒后自动收起。 */
    private const val TEST_HABIT_ID = "__habit_reminder_test__"
    private const val TEST_TIMEOUT_MS = 60_000L

    /** 状态栏胶囊 / 岛摘要态文案上限（HyperOS 实测超过 ~8 字会被裁）。 */
    private const val SHORT_CRITICAL_TEXT_MAX = 8

    fun show(
        context: Context,
        habit: Habit,
        day: Long,
        style: HabitNotificationStyle = HabitNotificationStyle.DEFAULT,
    ) {
        post(context, habit, day, style, test = false)
    }

    /** 设置页「发送测试通知」：用当前样式立即发一条，60 秒后自动收起，不写任何打卡。 */
    fun showTest(
        context: Context,
        style: HabitNotificationStyle = HabitNotificationStyle.DEFAULT,
    ) {
        val app = context.applicationContext
        val habit = Habit(
            id = TEST_HABIT_ID,
            name = app.getString(R.string.habit_reminder_test_name),
        )
        post(context, habit, java.time.LocalDate.now().toEpochDay(), style, test = true)
    }

    private fun post(
        context: Context,
        habit: Habit,
        day: Long,
        style: HabitNotificationStyle,
        test: Boolean,
    ) {
        val app = context.applicationContext
        val manager = app.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                app.getString(R.string.habit_reminder_channel),
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                setShowBadge(false)
            },
        )
        val notificationId = HabitReminderScheduler.notificationId(habit.id)
        LunoLog.i(LOG_TAG, "habit reminder show: habit=${habit.id} day=$day style=$style test=$test id=$notificationId")
        if (!NotificationManagerCompat.from(app).areNotificationsEnabled()) {
            LunoLog.w(LOG_TAG, "habit reminder skipped: notifications disabled")
            return
        }

        val contentIntent = PendingIntent.getActivity(
            app,
            notificationId,
            if (test) {
                Intent(app, hunoia.luno.ui.MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            } else {
                HabitExtras.editIntent(app, habit.id)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val doneIntent = PendingIntent.getBroadcast(
            app,
            notificationId + 1,
            Intent(app, HabitReminderReceiver::class.java).apply {
                action = HabitReminderScheduler.ACTION_MARK_DONE
                putExtra(HabitReminderScheduler.EXTRA_HABIT_ID, habit.id)
                putExtra(HabitReminderScheduler.EXTRA_DAY, day)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val body = if (test) {
            app.getString(R.string.habit_reminder_test_body, app.getString(style.labelRes))
        } else {
            app.getString(R.string.habit_reminder_body, habit.name)
        }

        val builder = Notification.Builder(app, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(
                app.getString(
                    if (test) R.string.habit_reminder_test_title else R.string.habit_reminder_title,
                ),
            )
            .setContentText(body)
            .setStyle(Notification.BigTextStyle().bigText(body))
            .setContentIntent(contentIntent)
            .setCategory(Notification.CATEGORY_REMINDER)
            // 常驻提醒：直到「完成」或当天 0 点由调度器统一收起；Android 16+ 升胶囊也要求 ongoing。
            .setOngoing(true)
            .setOnlyAlertOnce(!test)
            .addAction(
                Notification.Action.Builder(
                    Icon.createWithResource(app, R.mipmap.ic_launcher),
                    app.getString(R.string.habit_reminder_done),
                    doneIntent,
                ).build(),
            )
        if (test) {
            builder.setTimeoutAfter(TEST_TIMEOUT_MS).setAutoCancel(true)
        }

        when (style) {
            HabitNotificationStyle.STANDARD -> Unit
            HabitNotificationStyle.LIVE_ACTIVITY -> applyLiveUpdate(app, builder, habit, style)
        }

        runCatching {
            manager.notify(notificationId, builder.build())
            LunoLog.i(LOG_TAG, "habit reminder notify posted: id=$notificationId test=$test sdk=${Build.VERSION.SDK_INT}")
        }.onFailure { LunoLog.w(LOG_TAG, "habit reminder notify failed: ${it.message}") }
    }

    /** 当前是否已有该习惯的提醒在通知栏（避免配置流重排时重复补弹 / 覆盖在屏通知）。 */
    fun isShowing(context: Context, habitId: String): Boolean {
        val app = context.applicationContext
        val manager = app.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notificationId = HabitReminderScheduler.notificationId(habitId)
        return runCatching {
            manager.activeNotifications?.any { it.id == notificationId } == true
        }.getOrDefault(false)
    }

    /** 收起通知（「完成」、当天 0 点或删除 / 关闭习惯时）。 */
    fun cancel(context: Context, habitId: String) {
        val app = context.applicationContext
        val manager = app.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notificationId = HabitReminderScheduler.notificationId(habitId)
        manager.cancel(notificationId)
        // 少数 HyperOS 版本 cancel 会被岛自愈重新贴回，补一次。
        if (isActive(manager, notificationId)) {
            manager.cancel(notificationId)
        }
    }

    private val HabitNotificationStyle.labelRes: Int
        get() = when (this) {
            HabitNotificationStyle.STANDARD -> R.string.habit_reminder_style_standard
            HabitNotificationStyle.LIVE_ACTIVITY -> R.string.habit_reminder_style_live_activity
        }

    /**
     * Android 16+：升为 Live Update / 状态栏胶囊（HyperOS 4 会渲染成超级岛）；
     * 无权限时保持标准通知。
     *
     * 必须用 `canPostPromotedNotifications()` 先探权限：未授权时仍强行
     * `setRequestPromotedOngoing(true)`，系统会把整条通知判为非法并直接撤回，
     * 用户会连普通提醒都收不到。
     */
    private fun applyLiveUpdate(
        app: Context,
        builder: Notification.Builder,
        habit: Habit,
        style: HabitNotificationStyle,
    ) {
        if (Build.VERSION.SDK_INT < 36) return
        val granted = runCatching {
            (app.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                .canPostPromotedNotifications()
        }.getOrDefault(false)
        if (!granted) {
            LunoLog.w(LOG_TAG, "habit reminder live update not granted, fallback to standard (style=$style)")
            return
        }
        builder.setRequestPromotedOngoing(true)
        val text = habit.name.takeIf { it.isNotBlank() }
            ?: app.getString(R.string.habit_reminder_capsule)
        // 状态栏胶囊 / 岛摘要态的短文案：系统按「几个字」排版，过长会溢出，先自行收敛。
        builder.setShortCriticalText(text.take(SHORT_CRITICAL_TEXT_MAX))
    }

    private fun isActive(manager: NotificationManager, notificationId: Int): Boolean =
        runCatching {
            manager.activeNotifications?.any { it.id == notificationId } == true
        }.getOrDefault(false)
}
