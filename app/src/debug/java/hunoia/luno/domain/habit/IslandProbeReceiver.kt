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
import hunoia.luno.config.model.HabitNotificationStyle
import hunoia.luno.core.LOG_TAG
import hunoia.luno.core.LunoLog

/**
 * debug-only：通知 / 超级岛的投递探针，改样式不用每次重装。
 *
 * 三种用法：
 * 1. 跑生产路径：`--ez real true [--es style LIVE_ACTIVITY]`
 *    → 直接调 [HabitReminderNotifier.showTest]，验证真实提醒在系统里的表现。
 * 2. 试 Live Update：`--ei mode 1 [--ez nofocus true]`
 *    → `setRequestPromotedOngoing` + `setShortCriticalText`（HyperOS 4 会渲染成岛 / 胶囊）。
 * 3. 试旧的 `miui.focus.param`：`--es json_b64 <base64>`（不加 `--ez nofocus`）
 *    → 只在小米焦点通知白名单内的包上会出现；未过白名单时 SystemUI 会直接撤回整条通知。
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
        // `--ez real true`：直接跑生产路径（HabitReminderNotifier.showTest）对比 JSON 探针。
        if (intent.getBooleanExtra("real", false)) {
            val style = intent.getStringExtra("style")?.uppercase()
                ?.let { name -> HabitNotificationStyle.entries.firstOrNull { it.name == name } }
                ?: HabitNotificationStyle.LIVE_ACTIVITY
            LunoLog.i(LOG_TAG, "island probe real: style=$style")
            HabitReminderNotifier.showTest(app, style)
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
}
