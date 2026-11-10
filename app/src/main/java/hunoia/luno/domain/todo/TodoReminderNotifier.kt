package hunoia.luno.domain.todo

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
import hunoia.luno.config.model.NotificationDisplaySettings
import hunoia.luno.config.model.NotificationMode
import hunoia.luno.config.model.TODO_REMINDER_CHANNEL_ID
import hunoia.luno.config.model.TodoConfig
import hunoia.luno.config.model.TodoItem
import hunoia.luno.config.model.TodoMaxSnoozeMinutes
import hunoia.luno.config.model.TodoReminder
import hunoia.luno.core.LOG_TAG
import hunoia.luno.core.LunoLog
import hunoia.luno.core.domain.IslandActionSpec
import hunoia.luno.core.domain.IslandIntentKind
import hunoia.luno.core.domain.IslandNotificationSpec
import hunoia.luno.core.domain.IslandPublishResult
import hunoia.luno.core.domain.IslandShareSpec
import hunoia.luno.core.domain.NotificationPresenter
import hunoia.luno.gridpanel.TodoExtras
import hunoia.luno.gridpanel.TodoReminderFormat

/**
 * 待办提醒通知（三种呈现模式 + 回退链，模式语义见 [NotificationMode] / 通用通知设置）。
 *
 * - [NotificationMode.STANDARD]：HIGH 通道 heads-up + ongoing；点按打开该待办编辑弹窗，
 *   「完成」直接标记完成，「推迟 N 分钟」收起后挂一次性闹钟。
 * - [NotificationMode.LIVE]：Android 16+ promotedOngoing 升 Live Update / 状态栏胶囊；
 *   无权限时回退标准通知。HyperOS 4 上系统自动渲染成岛。
 * - [NotificationMode.ISLAND]：`miui.focus.param` 焦点通知 + XMSF 防火墙链绕过
 *   （`:capability:notify`，需 Shizuku）。失败时依次回退 LIVE → STANDARD。
 *
 * 常驻不可划掉：提醒一直保留到用户完成 / 推迟 / 删除待办。调用方需在后台线程
 * （`publishIsland` 含阻塞事务）。
 */
object TodoReminderNotifier {

    private const val CHANNEL_ID = TODO_REMINDER_CHANNEL_ID

    /** 状态栏胶囊 / 岛摘要态文案上限（HyperOS 实测超过 ~8 字会被裁）。 */
    private const val SHORT_CRITICAL_TEXT_MAX = 8

    private val presenter: NotificationPresenter by lazy {
        DefaultNotificationPresenter(hunoia.luno.core.AppContext.get())
    }

    /**
     * 发布 / 刷新一条待办提醒。
     *
     * @param overdue true = 补弹（提醒时间早已过去，正文显示「已过期 · …」）。
     * @param silent true = 补弹静默：不重新响铃 / 震动（错过提醒不该在开机时突然炸响）。
     */
    suspend fun show(
        context: Context,
        todo: TodoItem,
        reminder: TodoReminder,
        snoozeMinutes: Int,
        mode: NotificationMode,
        overdue: Boolean,
        silent: Boolean,
    ): Boolean {
        val app = context.applicationContext
        createChannel(app)
        val notificationId = TodoReminderScheduler.notificationId(todo.id)
        LunoLog.i(
            LOG_TAG,
            "todo reminder show: todo=${todo.id} mode=$mode overdue=$overdue silent=$silent id=$notificationId",
        )
        if (!NotificationManagerCompat.from(app).areNotificationsEnabled()) {
            LunoLog.w(LOG_TAG, "todo reminder skipped: notifications disabled")
            return false
        }
        val body = TodoReminderFormat.notificationBody(app, reminder, overdue)
        val title = todo.text.ifBlank { app.getString(R.string.todo_reminder_title) }

        return when (mode) {
            NotificationMode.ISLAND -> {
                val spec = islandSpec(
                    app = app,
                    todo = todo,
                    title = title,
                    body = body,
                    snoozeMinutes = snoozeMinutes,
                    notificationId = notificationId,
                    silent = silent,
                )
                when (val result = presenter.publishIsland(spec)) {
                    IslandPublishResult.Posted -> {
                        LunoLog.i(LOG_TAG, "todo reminder island posted: id=$notificationId")
                        true
                    }

                    else -> {
                        LunoLog.w(LOG_TAG, "todo reminder island fallback to LIVE: id=$notificationId reason=$result")
                        postNotification(
                            app, todo, title, body, snoozeMinutes, notificationId,
                            wantLive = true, silent = silent,
                        )
                    }
                }
            }

            NotificationMode.LIVE -> postNotification(
                app, todo, title, body, snoozeMinutes, notificationId,
                wantLive = true, silent = silent,
            )

            NotificationMode.STANDARD -> postNotification(
                app, todo, title, body, snoozeMinutes, notificationId,
                wantLive = false, silent = silent,
            )
        }
    }

    /** 当前是否已有该待办的通知在通知栏（避免配置流重排时重复补弹 / 覆盖在屏通知）。 */
    fun isShowing(context: Context, todoId: String): Boolean =
        isActive(context, TodoReminderScheduler.notificationId(todoId))

    /** 收起通知（完成 / 删除 / 提醒被清除时）。 */
    fun cancel(context: Context, todoId: String) {
        val notificationId = TodoReminderScheduler.notificationId(todoId)
        notificationManager(context).cancel(notificationId)
        // 少数 HyperOS 版本 cancel 会被岛自愈重新贴回，补一次。
        if (isActive(context, notificationId)) {
            notificationManager(context).cancel(notificationId)
        }
    }

    private suspend fun islandSpec(
        app: Context,
        todo: TodoItem,
        title: String,
        body: String,
        snoozeMinutes: Int,
        notificationId: Int,
        silent: Boolean,
    ): IslandNotificationSpec {
        val display = currentDisplay()
        val doneUri = actionIntentUri(app, todo.id, TodoReminderScheduler.ACTION_COMPLETE)
        val snoozeUri = actionIntentUri(app, todo.id, TodoReminderScheduler.ACTION_SNOOZE)
        return IslandNotificationSpec(
            channelId = CHANNEL_ID,
            notificationId = notificationId,
            title = title,
            text = body,
            capsuleText = title.take(SHORT_CRITICAL_TEXT_MAX)
                .ifBlank { app.getString(R.string.todo_reminder_capsule) },
            autoExpand = display.islandAutoExpand && !silent,
            expandedSeconds = display.islandExpandSeconds,
            blockWindowMs = display.islandDisconnectMs,
            outerGlow = display.islandOuterGlow,
            showInShade = display.islandKeepShadeNotification,
            share = if (display.islandDragShare) {
                IslandShareSpec(
                    title = title,
                    content = body,
                    shareContent = app.getString(R.string.todo_reminder_share_content, title, body),
                )
            } else {
                null
            },
            timeoutSeconds = display.islandTimeoutSeconds,
            contentIntentUri = editIntentUri(app, todo.id),
            actions = buildList {
                doneUri?.let {
                    add(
                        IslandActionSpec(
                            key = ACTION_KEY_DONE,
                            title = app.getString(R.string.todo_reminder_done),
                            intentUri = it,
                            intentKind = IslandIntentKind.BROADCAST,
                            highlighted = true,
                        ),
                    )
                }
                snoozeUri?.let {
                    add(
                        IslandActionSpec(
                            key = ACTION_KEY_SNOOZE,
                            title = TodoReminderFormat.snoozeActionLabel(app, snoozeMinutes),
                            intentUri = it,
                            intentKind = IslandIntentKind.BROADCAST,
                        ),
                    )
                }
            },
        )
    }

    /** 标准通知路径（含 LIVE 升舱）；ISLAND 回退时 [wantLive] 为 true。 */
    private fun postNotification(
        app: Context,
        todo: TodoItem,
        title: String,
        body: String,
        snoozeMinutes: Int,
        notificationId: Int,
        wantLive: Boolean,
        silent: Boolean,
    ): Boolean {
        val contentIntent = editPendingIntent(app, todo.id)
        val doneIntent = actionPendingIntent(
            app, todo.id, TodoReminderScheduler.completeRequestCode(todo.id),
            TodoReminderScheduler.ACTION_COMPLETE,
        )
        val snoozeIntent = actionPendingIntent(
            app, todo.id, TodoReminderScheduler.snoozeActionRequestCode(todo.id),
            TodoReminderScheduler.ACTION_SNOOZE,
        )
        val builder = Notification.Builder(app, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(Notification.BigTextStyle().bigText(body))
            .setContentIntent(contentIntent)
            .setCategory(Notification.CATEGORY_REMINDER)
            // 常驻不可划掉：一直保留到用户完成 / 推迟 / 删除待办。
            .setOngoing(true)
            // 补弹静默：错过的提醒不该在开机 / 打开应用时突然响铃。
            .setOnlyAlertOnce(silent)
            .addAction(
                Notification.Action.Builder(
                    Icon.createWithResource(app, R.mipmap.ic_launcher),
                    app.getString(R.string.todo_reminder_done),
                    doneIntent,
                ).build(),
            )
            .addAction(
                Notification.Action.Builder(
                    Icon.createWithResource(app, R.mipmap.ic_launcher),
                    TodoReminderFormat.snoozeActionLabel(app, snoozeMinutes),
                    snoozeIntent,
                ).build(),
            )
        if (wantLive) applyLiveUpdate(app, builder, title)
        return runCatching {
            notificationManager(app).notify(notificationId, builder.build())
            LunoLog.i(
                LOG_TAG,
                "todo reminder notify posted: id=$notificationId live=$wantLive silent=$silent sdk=${Build.VERSION.SDK_INT}",
            )
            true
        }.onFailure { LunoLog.w(LOG_TAG, "todo reminder notify failed: ${it.message}") }
            .getOrDefault(false)
    }

    private fun editIntentUri(app: Context, todoId: String): String? =
        runCatching { TodoExtras.editIntent(app, todoId).toUri(Intent.URI_INTENT_SCHEME) }.getOrNull()

    private fun actionIntentUri(app: Context, todoId: String, action: String): String? {
        val intent = Intent(app, TodoReminderReceiver::class.java).apply {
            this.action = action
            putExtra(TodoReminderScheduler.EXTRA_TODO_ID, todoId)
        }
        return runCatching { intent.toUri(Intent.URI_INTENT_SCHEME) }.getOrNull()
    }

    private fun editPendingIntent(app: Context, todoId: String): PendingIntent =
        PendingIntent.getActivity(
            app,
            TodoReminderScheduler.editRequestCode(todoId),
            TodoExtras.editIntent(app, todoId),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private fun actionPendingIntent(
        app: Context,
        todoId: String,
        requestCode: Int,
        action: String,
    ): PendingIntent = PendingIntent.getBroadcast(
        app,
        requestCode,
        Intent(app, TodoReminderReceiver::class.java).apply {
            this.action = action
            putExtra(TodoReminderScheduler.EXTRA_TODO_ID, todoId)
        },
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    /**
     * Android 16+：升为 Live Update / 状态栏胶囊（HyperOS 4 会渲染成超级岛）；
     * 无权限时保持标准通知。未授权仍强行 request 会被系统整条撤回，必须先探权限。
     */
    private fun applyLiveUpdate(app: Context, builder: Notification.Builder, title: String) {
        if (Build.VERSION.SDK_INT < 36) return
        val granted = runCatching {
            notificationManager(app).canPostPromotedNotifications()
        }.getOrDefault(false)
        if (!granted) {
            LunoLog.w(LOG_TAG, "todo reminder live update not granted, fallback to standard")
            return
        }
        builder.setRequestPromotedOngoing(true)
        builder.setShortCriticalText(title.take(SHORT_CRITICAL_TEXT_MAX))
    }

    private suspend fun currentDisplay(): NotificationDisplaySettings =
        runCatching { ConfigProvider.getAdvancedSettings().notificationDisplay }
            .getOrDefault(NotificationDisplaySettings())

    private fun createChannel(app: Context) {
        notificationManager(app).createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                app.getString(R.string.todo_reminder_channel),
                NotificationManager.IMPORTANCE_HIGH,
            ).apply { setShowBadge(false) },
        )
    }

    private fun notificationManager(context: Context) =
        context.applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    private fun isActive(context: Context, notificationId: Int): Boolean =
        runCatching {
            notificationManager(context).activeNotifications?.any { it.id == notificationId } == true
        }.getOrDefault(false)

    private const val ACTION_KEY_DONE = "todo_done"
    private const val ACTION_KEY_SNOOZE = "todo_snooze"
}

/** 配置里的推迟时长安全值。 */
internal fun TodoConfig.safeReminderSnoozeMinutes(): Int =
    reminderSnoozeMinutes.coerceIn(1, TodoMaxSnoozeMinutes)
