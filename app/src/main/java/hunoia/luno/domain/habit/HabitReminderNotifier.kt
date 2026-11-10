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
import hunoia.luno.capability.notify.DefaultNotificationPresenter
import hunoia.luno.config.ConfigProvider
import hunoia.luno.config.model.HABIT_REMINDER_CHANNEL_ID
import hunoia.luno.config.model.Habit
import hunoia.luno.config.model.HabitDayState
import hunoia.luno.config.model.HabitMaxSnoozeMinutes
import hunoia.luno.config.model.NotificationDisplaySettings
import hunoia.luno.config.model.NotificationKind
import hunoia.luno.config.model.NotificationMode
import hunoia.luno.core.LOG_TAG
import hunoia.luno.core.LunoLog
import hunoia.luno.core.domain.IslandActionSpec
import hunoia.luno.core.domain.IslandCapability
import hunoia.luno.core.domain.IslandIntentKind
import hunoia.luno.core.domain.IslandNotificationSpec
import hunoia.luno.core.domain.IslandPublishResult
import hunoia.luno.core.domain.IslandShareSpec
import hunoia.luno.core.domain.NotificationPresenter
import hunoia.luno.gridpanel.HabitExtras

/**
 * 习惯提醒通知（三种呈现模式 + 回退链，模式语义见 [NotificationMode] / 通用通知设置）。
 *
 * - [NotificationMode.STANDARD]：HIGH 通道 heads-up + ongoing，点按进习惯编辑页，
 *   「完成」直接打卡，「跳过今天」只收起提醒。
 * - [NotificationMode.LIVE]：Android 16+ promotedOngoing 升 Live Update / 状态栏胶囊；
 *   无权限时回退标准通知。HyperOS 4 上系统自动渲染成岛。
 * - [NotificationMode.ISLAND]：`miui.focus.param` 焦点通知 + XMSF 防火墙链绕过
 *   （`:capability:notify`，需 Shizuku）。探测不满足 / 绕过不可用 / 鉴权失败时
 *   依次回退 LIVE → STANDARD，保证一定有一条可见通知。
 *
 * 呈现模式由通用通知设置决定（全局 + 按 [NotificationKind.HABIT] 覆盖）；本通知器负责
 * 执行模式与回退链，岛细节（自动展开 / 停留时长 / 外发光 / 通知栏 / 拖拽分享 / 超时）
 * 从同一设置读取。调用方需在后台线程（`publishIsland` 含阻塞事务）。
 */
object HabitReminderNotifier {

    private const val CHANNEL_ID = HABIT_REMINDER_CHANNEL_ID

    /** 测试通知用固定假 id：不写打卡、不参与调度，60 秒后自动收起。 */
    internal const val TEST_HABIT_ID = "__habit_reminder_test__"
    private const val TEST_TIMEOUT_MS = 60_000L

    /** 状态栏胶囊 / 岛摘要态文案上限（HyperOS 实测超过 ~8 字会被裁）。 */
    private const val SHORT_CRITICAL_TEXT_MAX = 8

    private val presenter: NotificationPresenter by lazy {
        DefaultNotificationPresenter(hunoia.luno.core.AppContext.get())
    }

    /** 测试通知（假 habit id）——接收器据此跳过配置写入并只给轻反馈。 */
    fun isTestHabit(habitId: String): Boolean = habitId == TEST_HABIT_ID

    suspend fun show(
        context: Context,
        habit: Habit,
        day: Long,
        mode: NotificationMode,
        status: HabitReminderStatus?,
        slotMinutes: Int = 0,
        autoExpandOverride: Boolean? = null,
    ) {
        post(context, habit, day, mode, status, slotMinutes, test = false, autoExpandOverride = autoExpandOverride)
    }

    /** 「发送测试通知」：用指定模式立即发一条，60 秒后自动收起，不写任何打卡。 */
    suspend fun showTest(context: Context, mode: NotificationMode, autoExpand: Boolean? = null) {
        val app = context.applicationContext
        val habit = Habit(
            id = TEST_HABIT_ID,
            name = app.getString(R.string.habit_reminder_test_name),
        )
        val day = java.time.LocalDate.now().toEpochDay()
        post(context, habit, day, mode, status = null, slotMinutes = 0, test = true, autoExpandOverride = autoExpand)
    }

    /**
     * 焦点鉴权预热：配置里真的会用到岛（全局或任一类型 ISLAND）且能力 READY 时才跑一次。
     * 应用启动时调用；无配置 / 无能力时零开销返回。
     */
    suspend fun warmupIsland(context: Context) {
        val app = context.applicationContext
        val display = runCatching { ConfigProvider.getAdvancedSettings().notificationDisplay }
            .getOrElse { return }
        if (!display.usesIsland()) return
        if (presenter.probeIslandCapability() != IslandCapability.READY) return
        createChannel(app)
        presenter.prewarmIslandAuth(CHANNEL_ID)
    }

    /** 「+1」后未达标：静默刷新进度文案，不重新响铃 / 震动；岛模式下不重发，避免二次展开。 */
    suspend fun refreshProgress(
        context: Context,
        habit: Habit,
        day: Long,
        status: HabitReminderStatus?,
        slotMinutes: Int,
    ) {
        val app = context.applicationContext
        val mode = HabitReminderScheduler.currentMode()
        if (mode == NotificationMode.ISLAND) return
        val notificationId = HabitReminderScheduler.notificationId(habit.id)
        if (!isActive(app, notificationId)) return
        createChannel(app)
        postNotification(
            app,
            habit,
            day,
            status,
            slotMinutes,
            test = false,
            mode = mode,
            notificationId = notificationId,
            wantLive = mode == NotificationMode.LIVE,
            silent = true,
        )
    }

    private suspend fun post(
        context: Context,
        habit: Habit,
        day: Long,
        mode: NotificationMode,
        status: HabitReminderStatus?,
        slotMinutes: Int,
        test: Boolean,
        autoExpandOverride: Boolean? = null,
    ) {
        val app = context.applicationContext
        createChannel(app)
        val notificationId = HabitReminderScheduler.notificationId(habit.id)
        LunoLog.i(LOG_TAG, "habit reminder show: habit=${habit.id} day=$day slot=$slotMinutes mode=$mode test=$test id=$notificationId")
        if (!NotificationManagerCompat.from(app).areNotificationsEnabled()) {
            LunoLog.w(LOG_TAG, "habit reminder skipped: notifications disabled")
            return
        }

        when (mode) {
            NotificationMode.ISLAND -> {
                // 发布器内含探测；Unsupported/Rejected/Failed 一律降 LIVE（LIVE 不可用时
                // postNotification 内的 applyLiveUpdate 静默落回标准通知）。
                when (
                    val result = presenter.publishIsland(
                        islandSpec(app, habit, day, status, slotMinutes, test, mode, notificationId, autoExpandOverride),
                    )
                ) {
                    IslandPublishResult.Posted -> {
                        LunoLog.i(LOG_TAG, "habit reminder island posted: id=$notificationId")
                        return
                    }

                    else -> {
                        LunoLog.w(LOG_TAG, "habit reminder island fallback to LIVE: id=$notificationId reason=$result")
                        postNotification(app, habit, day, status, slotMinutes, test, mode, notificationId, wantLive = true)
                    }
                }
            }

            NotificationMode.LIVE ->
                postNotification(app, habit, day, status, slotMinutes, test, mode, notificationId, wantLive = true)

            NotificationMode.STANDARD ->
                postNotification(app, habit, day, status, slotMinutes, test, mode, notificationId, wantLive = false)
        }
    }

    private suspend fun islandSpec(
        app: Context,
        habit: Habit,
        day: Long,
        status: HabitReminderStatus?,
        slotMinutes: Int,
        test: Boolean,
        mode: NotificationMode,
        notificationId: Int,
        autoExpandOverride: Boolean? = null,
    ): IslandNotificationSpec {
        val body = if (test) {
            app.getString(R.string.habit_reminder_test_body, app.getString(mode.labelRes))
        } else {
            statusText(app, status)
        }
        val display = currentDisplay()
        // 悬浮探测只做诊断：本 ROM 的 `canShowFloat()` 会误报 false（channel importance=4、
        // 大岛实际能展开，2026-12 实测），拿它否决用户显式打开的「自动展开」会把开关弄死。
        val askExpand = autoExpandOverride ?: display.islandAutoExpand
        val canFloat = presenter.canChannelFloat(CHANNEL_ID)
        if (askExpand && !canFloat) {
            LunoLog.w(LOG_TAG, "island float probe says false, ignoring (known false-negative on HyperOS 4)")
        }
        val doneIntentUri = actionIntentUri(app, habit.id, day, slotMinutes, HabitReminderScheduler.ACTION_MARK_DONE)
        return IslandNotificationSpec(
            channelId = CHANNEL_ID,
            notificationId = notificationId,
            title = habit.name.ifBlank { app.getString(R.string.habit_reminder_title) },
            text = body,
            capsuleText = habit.name.take(SHORT_CRITICAL_TEXT_MAX)
                .ifBlank { app.getString(R.string.habit_reminder_capsule) },
            accentColor = habit.color,
            autoExpand = askExpand,
            expandedSeconds = display.islandExpandSeconds,
            blockWindowMs = display.islandDisconnectMs,
            outerGlow = display.islandOuterGlow,
            showInShade = display.islandKeepShadeNotification,
            share = if (display.islandDragShare) {
                IslandShareSpec(
                    title = habit.name.ifBlank { app.getString(R.string.habit_reminder_title) },
                    content = body,
                    shareContent = app.getString(R.string.habit_reminder_share_content, habit.name, body),
                )
            } else {
                null
            },
            timeoutSeconds = display.islandTimeoutSeconds,
            contentIntentUri = editIntentUri(app, habit.id, test),
            actions = buildList {
                doneIntentUri?.let {
                    add(
                        IslandActionSpec(
                            key = ACTION_KEY_DONE,
                            title = app.getString(R.string.habit_reminder_done),
                            intentUri = it,
                            intentKind = IslandIntentKind.BROADCAST,
                            highlighted = true,
                        ),
                    )
                }
                // 测试通知只有一个按钮：它就是「点击链路能不能走通」的探针。
                if (!test) {
                    snoozeActionUri(app, habit, day, slotMinutes)?.let {
                        add(
                            IslandActionSpec(
                                key = ACTION_KEY_SNOOZE,
                                title = snoozeLabel(app, habit),
                                intentUri = it,
                                intentKind = IslandIntentKind.BROADCAST,
                            ),
                        )
                    }
                }
            },
        )
    }

    private suspend fun currentDisplay(): NotificationDisplaySettings =
        runCatching { ConfigProvider.getAdvancedSettings().notificationDisplay }
            .getOrDefault(NotificationDisplaySettings())

    /** 展开态正文：状态 + 连续天数；多次习惯显示今日进度。 */
    private fun statusText(app: Context, status: HabitReminderStatus?): String {
        if (status == null) return app.getString(R.string.habit_reminder_status_plain)
        val target = status.target.coerceAtLeast(1)
        if (target > 1) {
            val done = status.count.coerceIn(0, target)
            return when (status.state) {
                HabitDayState.CHECKED,
                HabitDayState.SKIPPED,
                -> app.getString(R.string.habit_reminder_status_plain)

                HabitDayState.INACTIVE ->
                    app.getString(R.string.habit_reminder_status_progress_inactive, done, target)

                HabitDayState.OVERDUE ->
                    app.getString(R.string.habit_reminder_status_progress_overdue, done, target)

                HabitDayState.DUE ->
                    app.getString(R.string.habit_reminder_status_progress, done, target, status.streakDays)
            }
        }
        return when (status.state) {
            HabitDayState.CHECKED,
            HabitDayState.SKIPPED,
            -> app.getString(R.string.habit_reminder_status_plain)

            HabitDayState.INACTIVE -> app.getString(R.string.habit_reminder_status_inactive)
            HabitDayState.OVERDUE ->
                app.getString(R.string.habit_reminder_status_overdue, status.streakDays)

            HabitDayState.DUE ->
                app.getString(R.string.habit_reminder_status_due, status.streakDays)
        }
    }

    private fun editIntentUri(app: Context, habitId: String, test: Boolean): String? {
        val intent = if (test) {
            Intent(app, hunoia.luno.ui.MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        } else {
            HabitExtras.editIntent(app, habitId)
        }
        return runCatching { intent.toUri(Intent.URI_INTENT_SCHEME) }.getOrNull()
    }

    private fun actionIntentUri(
        app: Context,
        habitId: String,
        day: Long,
        slotMinutes: Int,
        action: String,
    ): String? {
        val intent = Intent(app, HabitReminderReceiver::class.java).apply {
            this.action = action
            putExtra(HabitReminderScheduler.EXTRA_HABIT_ID, habitId)
            putExtra(HabitReminderScheduler.EXTRA_DAY, day)
            putExtra(HabitReminderScheduler.EXTRA_SLOT, slotMinutes)
        }
        return runCatching { intent.toUri(Intent.URI_INTENT_SCHEME) }.getOrNull()
    }

    /** 标准通知路径（含 LIVE 升舱）；ISLAND 回退时 [wantLive] 为 true。 */
    private fun postNotification(
        app: Context,
        habit: Habit,
        day: Long,
        status: HabitReminderStatus?,
        slotMinutes: Int,
        test: Boolean,
        mode: NotificationMode,
        notificationId: Int,
        wantLive: Boolean,
        silent: Boolean = false,
    ) {
        val contentIntent = editPendingIntent(app, habit.id, test, notificationId)
        val doneIntent = actionPendingIntent(
            app,
            habit.id,
            day,
            slotMinutes,
            notificationId + 1,
            HabitReminderScheduler.ACTION_MARK_DONE,
        )
        val snoozeMinutes = habit.snoozeMinutes.coerceIn(1, HabitMaxSnoozeMinutes)
        val snoozeIntent = actionPendingIntent(
            app,
            habit.id,
            day,
            slotMinutes,
            notificationId + 2,
            HabitReminderScheduler.ACTION_SNOOZE,
            snoozeMinutes,
        )
        val body = if (test) {
            app.getString(R.string.habit_reminder_test_body, app.getString(mode.labelRes))
        } else {
            statusText(app, status)
        }
        val title = if (test) {
            app.getString(R.string.habit_reminder_test_title)
        } else {
            app.getString(R.string.habit_reminder_title)
        }

        val builder = Notification.Builder(app, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(Notification.BigTextStyle().bigText(body))
            .setContentIntent(contentIntent)
            .setCategory(Notification.CATEGORY_REMINDER)
            // 常驻提醒：直到完成当日目标或当天 0 点由调度器统一收起；
            // Android 16+ 升胶囊也要求 ongoing。同一习惯多个时间槽重新 post 同一条，
            // setOnlyAlertOnce(false) 保证后一个时间点仍然响铃 / 震动。
            .setOngoing(true)
            // 静默刷新（+1 未达标）不重新响铃；新的提醒槽正常响铃 / 震动。
            .setOnlyAlertOnce(silent)
            .addAction(
                Notification.Action.Builder(
                    Icon.createWithResource(app, R.mipmap.ic_launcher),
                    app.getString(R.string.habit_reminder_done),
                    doneIntent,
                ).build(),
            )
        if (!test) {
            builder.addAction(
                Notification.Action.Builder(
                    Icon.createWithResource(app, R.mipmap.ic_launcher),
                    snoozeLabel(app, habit),
                    snoozeIntent,
                ).build(),
            )
        }
        if (test) {
            builder.setTimeoutAfter(TEST_TIMEOUT_MS).setAutoCancel(true)
        }
        if (wantLive) applyLiveUpdate(app, builder, habit)
        runCatching {
            notificationManager(app).notify(notificationId, builder.build())
            LunoLog.i(LOG_TAG, "habit reminder notify posted: id=$notificationId test=$test live=$wantLive sdk=${Build.VERSION.SDK_INT}")
        }.onFailure { LunoLog.w(LOG_TAG, "habit reminder notify failed: ${it.message}") }
    }

    private fun editPendingIntent(
        app: Context,
        habitId: String,
        test: Boolean,
        notificationId: Int,
    ): PendingIntent = PendingIntent.getActivity(
        app,
        notificationId,
        if (test) {
            Intent(app, hunoia.luno.ui.MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        } else {
            HabitExtras.editIntent(app, habitId)
        },
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun actionPendingIntent(
        app: Context,
        habitId: String,
        day: Long,
        slotMinutes: Int,
        requestCode: Int,
        action: String,
        snoozeMinutes: Int? = null,
    ): PendingIntent = PendingIntent.getBroadcast(
        app,
        requestCode,
        Intent(app, HabitReminderReceiver::class.java).apply {
            this.action = action
            putExtra(HabitReminderScheduler.EXTRA_HABIT_ID, habitId)
            putExtra(HabitReminderScheduler.EXTRA_DAY, day)
            putExtra(HabitReminderScheduler.EXTRA_SLOT, slotMinutes)
            snoozeMinutes?.let { putExtra(HabitReminderScheduler.EXTRA_SNOOZE_MINUTES, it) }
        },
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    /** 「推迟 N 分钟」按钮文案（N 来自习惯设置，支持到 6 小时）。 */
    private fun snoozeLabel(app: Context, habit: Habit): String =
        formatSnoozeDuration(app, habit.snoozeMinutes)

    private fun formatSnoozeDuration(app: Context, minutes: Int): String {
        val safe = minutes.coerceIn(1, HabitMaxSnoozeMinutes)
        val hours = safe / 60
        val rest = safe % 60
        return when {
            hours == 0 -> app.getString(R.string.habit_reminder_snooze_minutes, rest)
            rest == 0 -> app.getString(R.string.habit_reminder_snooze_hours, hours)
            else -> app.getString(R.string.habit_reminder_snooze_hours_minutes, hours, rest)
        }
    }

    private fun snoozeActionUri(app: Context, habit: Habit, day: Long, slotMinutes: Int): String? {
        val intent = Intent(app, HabitReminderReceiver::class.java).apply {
            action = HabitReminderScheduler.ACTION_SNOOZE
            putExtra(HabitReminderScheduler.EXTRA_HABIT_ID, habit.id)
            putExtra(HabitReminderScheduler.EXTRA_DAY, day)
            putExtra(HabitReminderScheduler.EXTRA_SLOT, slotMinutes)
            putExtra(
                HabitReminderScheduler.EXTRA_SNOOZE_MINUTES,
                habit.snoozeMinutes.coerceIn(1, HabitMaxSnoozeMinutes),
            )
        }
        return runCatching { intent.toUri(Intent.URI_INTENT_SCHEME) }.getOrNull()
    }

    /** 当前是否已有该习惯的提醒在通知栏（避免配置流重排时重复补弹 / 覆盖在屏通知）。 */
    fun isShowing(context: Context, habitId: String): Boolean =
        isActive(context, HabitReminderScheduler.notificationId(habitId))

    /** 收起通知（「完成」「跳过今天」、当天 0 点或删除 / 关闭习惯时）。 */
    fun cancel(context: Context, habitId: String) {
        val notificationId = HabitReminderScheduler.notificationId(habitId)
        notificationManager(context).cancel(notificationId)
        // 少数 HyperOS 版本 cancel 会被岛自愈重新贴回，补一次。
        if (isActive(context, notificationId)) {
            notificationManager(context).cancel(notificationId)
        }
    }

    /**
     * Android 16+：升为 Live Update / 状态栏胶囊（HyperOS 4 会渲染成超级岛）；
     * 无权限时保持标准通知。
     *
     * 必须用 `canPostPromotedNotifications()` 先探权限：未授权时仍强行
     * `setRequestPromotedOngoing(true)`，系统会把整条通知判为非法并直接撤回，
     * 用户会连普通提醒都收不到。
     */
    private fun applyLiveUpdate(app: Context, builder: Notification.Builder, habit: Habit) {
        if (Build.VERSION.SDK_INT < 36) return
        val granted = runCatching {
            notificationManager(app).canPostPromotedNotifications()
        }.getOrDefault(false)
        if (!granted) {
            LunoLog.w(LOG_TAG, "habit reminder live update not granted, fallback to standard")
            return
        }
        builder.setRequestPromotedOngoing(true)
        val text = habit.name.takeIf { it.isNotBlank() }
            ?: app.getString(R.string.habit_reminder_capsule)
        // 状态栏胶囊 / 岛摘要态的短文案：系统按「几个字」排版，过长会溢出，先自行收敛。
        builder.setShortCriticalText(text.take(SHORT_CRITICAL_TEXT_MAX))
    }

    private fun createChannel(app: Context) {
        notificationManager(app).createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                app.getString(R.string.habit_reminder_channel),
                NotificationManager.IMPORTANCE_HIGH,
            ).apply { setShowBadge(false) },
        )
    }

    private fun notificationManager(context: Context) =
        context.applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    private val NotificationMode.labelRes: Int
        get() = when (this) {
            NotificationMode.STANDARD -> R.string.notification_display_mode_standard
            NotificationMode.LIVE -> R.string.notification_display_mode_live
            NotificationMode.ISLAND -> R.string.notification_display_mode_island
        }

    private fun isActive(context: Context, notificationId: Int): Boolean =
        runCatching {
            notificationManager(context).activeNotifications?.any { it.id == notificationId } == true
        }.getOrDefault(false)

    private const val ACTION_KEY_DONE = "habit_done"
    private const val ACTION_KEY_SNOOZE = "habit_snooze"
}

/** 一条提醒所需的习惯状态快照（接收器从配置派生，通知器只负责排版）。 */
data class HabitReminderStatus(
    val state: HabitDayState,
    val streakDays: Long,
    /** 当天已完成次数；单次习惯 = 0 / 1。 */
    val count: Int = 0,
    /** 当天目标次数；1 表示单次习惯。 */
    val target: Int = 1,
)

/** 配置里是否有任何类型会走 ISLAND（决定要不要做鉴权预热）。 */
private fun NotificationDisplaySettings.usesIsland(): Boolean =
    mode == NotificationMode.ISLAND || overrides.values.any { it == NotificationMode.ISLAND }
