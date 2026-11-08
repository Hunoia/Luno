package hunoia.luno.domain.habit

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Bundle
import android.util.Base64
import hunoia.luno.R
import hunoia.luno.capability.notify.DefaultNotificationPresenter
import hunoia.luno.config.model.NotificationMode
import hunoia.luno.config.model.currentStreak
import hunoia.luno.config.model.habitDayState
import hunoia.luno.core.LOG_TAG
import hunoia.luno.core.LunoLog
import hunoia.luno.core.domain.IslandCapability
import hunoia.luno.core.domain.IslandNotificationSpec
import hunoia.luno.core.domain.IslandPublishResult
import hunoia.luno.domain.gridpanel.HabitCenter
import kotlinx.coroutines.runBlocking

/**
 * debug-only：通知 / 超级岛的投递探针，改样式不用每次重装。
 *
 * 用法：
 * 1. 跑生产路径：`--ez real true [--es style LIVE|ISLAND|STANDARD]`（缺省 LIVE）
 *    → 直接调 [HabitReminderNotifier.showTest]，验证真实提醒在系统里的表现。
 * 2. 试 Live Update：`--ei mode 1 [--ez nofocus true]`
 *    → `setRequestPromotedOngoing` + `setShortCriticalText`（HyperOS 4 会渲染成岛 / 胶囊）。
 * 3. 试旧的 `miui.focus.param`：`--es json_b64 <base64>`（不加 `--ez nofocus`）
 *    → 只在小米焦点通知白名单内的包上会出现；未过白名单时 SystemUI 会直接撤回整条通知。
 * 4. 走生产 ISLAND 发布器（`:capability:notify`，含 XMSF 绕过事务，go/no-go 验证入口）：
 *    `--ez island true`；日志见 NotifyPresenter / XmsfGate / island probe。
 *    可选：`--ez expand true`（首帧展开大岛）、`--ei id 9012`（每次换通知 id 避免走更新路径）、
 *    `--es channel habit_reminder`（默认）。
 * 5. 跑一遍真实习惯提醒（真实习惯 + 真实状态，验证展开态文案 / 双按钮）：
 *    `--ez remind true [--es habit_id <uuid>] [--ez expand true]`（缺省取配置里第一个习惯）。
 *    接收器 `exported=false`，adb 发不进非导出广播，所以调试走这里。
 * 6. 模拟点「完成」：`--ez done true`（测试习惯 id，验证收起 + toast）。
 * 7. 模拟点「推迟」：`--ei snooze <min> [--es habit_id <uuid>]`（默认测试习惯 / 30 分钟），
 *    验证一次性闹钟到点重弹。
 * 8. 排查渠道悬浮真值：`--ez dumpchannel true [--es channel habit_reminder]`。
 *
 * 用法示例：
 * ```
 * adb shell am broadcast -n hunoia.luno.debug/hunoia.luno.domain.habit.IslandProbeReceiver \
 *     -a hunoia.luno.debug.ISLAND_PROBE --es json_b64 "$(base64 -w0 payload.json)" \
 *     --ei id 1001 --ei mode 1 --ez nofocus true --es channel habit_reminder
 * ```
 *
 * 注意 `channel`：默认的 `island_probe` 通道是「常驻通知」，会被 Luno 自己的「隐藏常驻通知」
 * 立刻 snooze 掉（表现为刚发就被撤）；传 `--es channel habit_reminder` 绕过（该通道被排除）。
 *
 * `--es fx_src/--es fx_color` 可试 SystemUI 的 `miui.effect.src` / `miui.bigIsland.effect.src` /
 * `miui.effect.color` extras（实测对 promoted 通知不产生外发光，仅供以后对照）。
 */
class IslandProbeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext
        // `--ez island true`：走 :capability:notify 生产发布器（探测 + XMSF 绕过事务）。
        if (intent.getBooleanExtra("island", false)) {
            runIslandProbe(
                app,
                windowMs = intent.getIntExtra("window_ms", 220),
                expand = intent.getBooleanExtra("expand", false),
                notificationId = intent.getIntExtra("id", 9001),
                channelId = intent.getStringExtra("channel") ?: "habit_reminder",
            )
            return
        }
        // `--ei snooze <min>`：模拟点「推迟」，验证一次性闹钟到点重弹。
        if (intent.hasExtra("snooze")) {
            val minutes = intent.getIntExtra("snooze", 30)
            val habitId = intent.getStringExtra("habit_id") ?: HabitReminderNotifier.TEST_HABIT_ID
            LunoLog.i(LOG_TAG, "island probe snooze: habit=$habitId minutes=$minutes")
            app.sendBroadcast(
                Intent(app, HabitReminderReceiver::class.java).apply {
                    action = HabitReminderScheduler.ACTION_SNOOZE
                    putExtra(HabitReminderScheduler.EXTRA_HABIT_ID, habitId)
                    putExtra(HabitReminderScheduler.EXTRA_SNOOZE_MINUTES, minutes)
                },
            )
            return
        }
        // `--ez dumpchannel true [--es channel habit_reminder]`：排查渠道「悬浮通知」真值。
        if (intent.getBooleanExtra("dumpchannel", false)) {
            dumpChannel(app, intent.getStringExtra("channel") ?: "habit_reminder")
            return
        }
        // `--ez done true`：模拟岛 / 通知上点「完成」（测试习惯 id），验证收起 + toast 反馈。
        if (intent.getBooleanExtra("done", false)) {
            app.sendBroadcast(
                Intent(app, HabitReminderReceiver::class.java).apply {
                    action = HabitReminderScheduler.ACTION_MARK_DONE
                    putExtra(HabitReminderScheduler.EXTRA_HABIT_ID, HabitReminderNotifier.TEST_HABIT_ID)
                },
            )
            return
        }
        // `--ez remind true`：真实习惯提醒链路（真实状态 / 双按钮），验证岛内容排版。
        if (intent.getBooleanExtra("remind", false)) {
            runHabitReminder(app, intent.getStringExtra("habit_id"), intent.getBooleanExtra("expand", false))
            return
        }
        // `--ez real true`：直接跑生产路径（HabitReminderNotifier.showTest）对比 JSON 探针。
        if (intent.getBooleanExtra("real", false)) {
            val mode = intent.getStringExtra("style")?.uppercase()
                ?.let { name -> NotificationMode.entries.firstOrNull { it.name == name } }
                ?: NotificationMode.LIVE
            LunoLog.i(LOG_TAG, "island probe real: mode=$mode")
            val pending = goAsync()
            Thread {
                try {
                    runBlocking {
                        HabitReminderNotifier.showTest(
                            app,
                            mode,
                            intent.getBooleanExtra("expand", false),
                        )
                    }
                } finally {
                    pending.finish()
                }
            }.start()
            return
        }
        val manager = app.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = intent.getStringExtra("channel") ?: "island_probe"
        manager.createNotificationChannel(
            NotificationChannel(channelId, channelId, NotificationManager.IMPORTANCE_HIGH).apply {
                setShowBadge(false)
            },
        )
        val json = intent.getStringExtra("json")
            ?: intent.getStringExtra("json_b64")?.let {
                runCatching { String(Base64.decode(it, Base64.DEFAULT), Charsets.UTF_8) }.getOrNull()
            }
        if (json.isNullOrBlank()) {
            LunoLog.w(LOG_TAG, "island probe: empty payload")
            return
        }
        val id = intent.getIntExtra("id", 1001)
        val title = intent.getStringExtra("title") ?: "island probe"
        val text = intent.getStringExtra("text") ?: "island probe"
        val builder = Notification.Builder(app, channelId)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
        val extras = Bundle().apply {
            if (!intent.getBooleanExtra("nofocus", false)) {
                putString("miui.focus.param", json)
            }
            // 岛光效实验：SystemUI 侧的 effect extras（见 miui.effect.src / miui.bigIsland.effect.src）。
            intent.getStringExtra("fx_src")?.let {
                if (intent.getBooleanExtra("fx_big_only", false)) {
                    putString("miui.bigIsland.effect.src", it)
                } else if (intent.getBooleanExtra("fx_effect_only", false)) {
                    putString("miui.effect.src", it)
                } else {
                    putString("miui.effect.src", it)
                    putString("miui.bigIsland.effect.src", it)
                }
            }
            intent.getStringExtra("fx_color")?.let { putString("miui.effect.color", it) }
            putBundle(
                "miui.focus.pics",
                Bundle().apply {
                    if (intent.getBooleanExtra("nopics", false)) return@apply
                    val icon = Icon.createWithResource(app, R.mipmap.ic_launcher)
                    putParcelable("key_app_icon", icon)
                    putParcelable("key_ticker_pic", icon)
                    putParcelable("key_app_logo", icon)
                    putParcelable("miui.focus.pic_ticker_pic", icon)
                    putParcelable("miui.focus.pic_liveupdate", icon)
                    putParcelable("miui.focus.pic_ticker", icon)
                },
            )
        }
        builder.addExtras(extras)
        val canPromote = if (Build.VERSION.SDK_INT >= 36) {
            runCatching { manager.canPostPromotedNotifications() }.getOrDefault(false)
        } else {
            false
        }
        LunoLog.i(LOG_TAG, "island probe canPromote=$canPromote sdk=${Build.VERSION.SDK_INT}")
        if (intent.getIntExtra("mode", 0) == 1 && Build.VERSION.SDK_INT >= 36) {
            builder.setRequestPromotedOngoing(true)
            builder.setShortCriticalText(text)
        }
        runCatching {
            manager.notify(id, builder.build())
            LunoLog.i(LOG_TAG, "island probe posted: id=$id mode=${intent.getIntExtra("mode", 0)} len=${json.length}")
        }.onFailure { LunoLog.w(LOG_TAG, "island probe failed: ${it.message}") }
    }

    /**
     * 渠道「悬浮通知」真值排查：`canShowFloat()` 在 HyperOS 4 不存在，
     * 这里把 float / show 相关的隐藏字段与方法值都打出来找可信信号源。
     */
    private fun dumpChannel(app: Context, channelId: String) {
        val manager = app.getSystemService(NotificationManager::class.java)
        val channel = manager?.getNotificationChannel(channelId)
        if (channel == null) {
            LunoLog.w(LOG_TAG, "dumpchannel: $channelId missing")
            return
        }
        val canFloat = runCatching {
            channel.javaClass.getMethod("canShowFloat").invoke(channel) as? Boolean
        }.getOrNull()
        LunoLog.i(LOG_TAG, "dumpchannel: id=$channelId importance=${channel.importance} canShowFloat=$canFloat")
        LunoLog.i(LOG_TAG, "dumpchannel: toString=$channel")
        channel.javaClass.declaredFields.forEach { field ->
            runCatching {
                field.isAccessible = true
                LunoLog.i(LOG_TAG, "dumpchannel field: ${field.name}=${field.get(channel)}")
            }.onFailure { LunoLog.w(LOG_TAG, "dumpchannel field ${field.name} blocked: ${it.message}") }
        }
        channel.javaClass.methods
            .filter { it.parameterCount == 0 && it.name.contains("loat", ignoreCase = true) }
            .forEach { method ->
                val value = runCatching { method.invoke(channel) }
                    .getOrElse { "err:${it.message}" }
                LunoLog.i(LOG_TAG, "dumpchannel method: ${method.name}()=$value")
            }
    }

    /** 生产习惯提醒链路：真实 habit + 真实 state，走当前配置的呈现模式。 */
    private fun runHabitReminder(app: Context, habitId: String?, expand: Boolean) {
        val pending = goAsync()
        Thread {
            try {
                runBlocking {
                    val config = HabitCenter.getHabitConfig()
                    val id = habitId ?: config.habits.firstOrNull()?.id
                    val habit = id?.let(config::habitById)
                    if (habit == null) {
                        LunoLog.w(LOG_TAG, "habit probe: no habit (id=$habitId)")
                        return@runBlocking
                    }
                    val today = java.time.LocalDate.now().toEpochDay()
                    val checks = config.checks[habit.id].orEmpty()
                    val status = HabitReminderStatus(
                        state = habitDayState(checks, today, habit.interval, today),
                        streakDays = currentStreak(checks, today, habit.interval),
                    )
                    LunoLog.i(LOG_TAG, "habit probe: habit=${habit.id} status=$status expand=$expand")
                    HabitReminderNotifier.show(
                        app,
                        habit,
                        today,
                        HabitReminderScheduler.currentMode(),
                        status,
                        autoExpandOverride = expand.takeIf { it },
                    )
                }
            } finally {
                pending.finish()
            }
        }.start()
    }

    /**
     * go/no-go 验证（capability/notify/README.md 调试段）：探测 + 生产发布器全链路。
     * 发布事务含 220ms 阻塞与线程 sleep，不能在主线程 onReceive 里跑，投到后台线程。
     */
    private fun runIslandProbe(
        app: Context,
        windowMs: Int,
        expand: Boolean,
        notificationId: Int,
        channelId: String,
    ) {
        val presenter = DefaultNotificationPresenter(app, windowMs.coerceIn(0, 30_000).toLong())
        val capability = presenter.probeIslandCapability()
        LunoLog.i(LOG_TAG, "island probe capability=$capability id=$notificationId expand=$expand")
        if (capability != IslandCapability.READY) {
            LunoLog.w(LOG_TAG, "island probe: capability not READY, skip publish")
            return
        }
        app.getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(
                channelId,
                app.getString(R.string.habit_reminder_channel),
                NotificationManager.IMPORTANCE_HIGH,
            ).apply { setShowBadge(false) },
        )
        val spec = IslandNotificationSpec(
            channelId = channelId,
            notificationId = notificationId,
            title = "超级岛探针",
            text = "XMSF 绕过事务验证",
            capsuleText = "岛探针",
            autoExpand = expand,
        )
        val pending = goAsync()
        Thread {
            try {
                val result = runBlocking { presenter.publishIsland(spec) }
                when (result) {
                    IslandPublishResult.Posted -> LunoLog.i(LOG_TAG, "island probe: POSTED")
                    else -> LunoLog.w(LOG_TAG, "island probe: $result")
                }
            } finally {
                pending.finish()
            }
        }.start()
    }
}
