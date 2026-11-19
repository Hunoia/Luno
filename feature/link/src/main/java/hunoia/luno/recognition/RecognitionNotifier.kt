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
import hunoia.luno.capability.notify.DefaultNotificationPresenter
import hunoia.luno.config.ConfigProvider
import hunoia.luno.config.model.NotificationDisplaySettings
import hunoia.luno.config.model.NotificationKind
import hunoia.luno.config.model.NotificationMode
import hunoia.luno.config.model.RecognitionNotifySpec
import hunoia.luno.core.LOG_TAG
import hunoia.luno.core.LunoLog
import hunoia.luno.core.domain.IslandActionSpec
import hunoia.luno.core.domain.IslandIntentKind
import hunoia.luno.core.domain.IslandNotificationSpec
import hunoia.luno.core.domain.IslandPublishResult
import hunoia.luno.core.domain.NotificationPresenter
import hunoia.luno.link.R
import hunoia.luno.runtime.recognition.RecognizedItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 识别结果的「通知」输出。
 *
 * 通知方式跟随通用通知设置（[NotificationDisplaySettings.modeFor]，`NotificationKind.RECOGNITION`），
 * 每规则可用 [RecognitionNotifySpec.mode] 覆盖（null = 跟随全局）。分派：
 * - [NotificationMode.STANDARD]：普通通知（可划掉、带「完成」按钮）；
 * - [NotificationMode.LIVE]：Android 16+ 升 Live Update / 状态栏胶囊（无权限自动落回标准）；
 * - [NotificationMode.ISLAND]：小米超级岛（`miui.focus.param` + XMSF 绕过），
 *   探测 / 发布失败按回退链降实时 → 标准。
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

    /** 状态栏胶囊 / 岛摘要态文案上限：系统按「几个字」排版，过长会溢出，先自行收敛。 */
    private const val SHORT_CRITICAL_TEXT_MAX = 8

    private val presenter: NotificationPresenter by lazy {
        DefaultNotificationPresenter(hunoia.luno.core.AppContext.get())
    }

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
        val capsule = spec.capsuleText.resolve(item).ifBlank { text.take(SHORT_CRITICAL_TEXT_MAX) }
        val display = currentDisplay()
        // 全局生效模式：规则覆盖优先，否则跟随通知设置。
        val mode = spec.mode ?: display.modeFor(NotificationKind.RECOGNITION)
        LunoLog.i(LOG_TAG, "recognition notify: id=$id mode=$mode override=${spec.mode} sdk=${Build.VERSION.SDK_INT}")

        when (mode) {
            NotificationMode.ISLAND -> when (
                val result = presenter.publishIsland(islandSpec(app, id, title, text, capsule, display))
            ) {
                IslandPublishResult.Posted -> {
                    LunoLog.i(LOG_TAG, "recognition island posted: id=$id")
                    true
                }

                else -> {
                    // 发布器内含探测；失败一律降实时（LIVE 不可用时 postStandardOrLive 内的
                    // applyLiveUpdate 静默落回标准通知）。
                    LunoLog.w(LOG_TAG, "recognition island fallback to LIVE: id=$id reason=$result")
                    postStandardOrLive(app, id, title, text, capsule, wantLive = true)
                }
            }

            NotificationMode.LIVE -> postStandardOrLive(app, id, title, text, capsule, wantLive = true)

            NotificationMode.STANDARD -> postStandardOrLive(app, id, title, text, capsule, wantLive = false)
        }
    }

    /** 标准通知路径（含 LIVE 升舱）；ISLAND 回退时 [wantLive] 为 true。 */
    private fun postStandardOrLive(
        app: Context,
        id: Int,
        title: String,
        text: String,
        capsule: String,
        wantLive: Boolean,
    ): Boolean {
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
            // 标准通知：可划掉、点「完成」或系统自动取消收走。LIVE 升舱在 applyLiveUpdate
            // 成功时才改 setOngoing(true)——不成功保持非 ongoing 标准，不卡常驻。
            .setOngoing(false)
            .setAutoCancel(true)
            .addAction(
                Notification.Action.Builder(null, app.getString(R.string.recognition_notify_done), done)
                    .build(),
            )
        // LIVE 胶囊：Android 16+ 且有 promoted 权限才升，否则保持标准通知。
        if (wantLive) applyLiveUpdate(app, builder, capsule)
        // 与其它通知器同口径：开关 + 运行期权限各查一次，再吞掉系统拒绝。
        if (!NotificationManagerCompat.from(app).areNotificationsEnabled()) return false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            app.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }
        return runCatching {
            NotificationManagerCompat.from(app).notify(id, builder.build())
            true
        }.getOrDefault(false)
    }

    private fun islandSpec(
        app: Context,
        id: Int,
        title: String,
        text: String,
        capsule: String,
        display: NotificationDisplaySettings,
    ): IslandNotificationSpec {
        val doneUri = dismissIntentUri(app, id)
        return IslandNotificationSpec(
            channelId = CHANNEL_ID,
            notificationId = id,
            title = title,
            text = text,
            capsuleText = capsule,
            autoExpand = display.islandAutoExpand,
            expandedSeconds = display.islandExpandSeconds,
            outerGlow = display.islandOuterGlow,
            showInShade = display.islandKeepShadeNotification,
            blockWindowMs = display.islandDisconnectMs,
            timeoutSeconds = display.islandTimeoutSeconds,
            // 一次性凭证：点「完成」或超时自动收走，不留常驻。
            ongoing = true,
            actions = if (doneUri != null) {
                listOf(
                    IslandActionSpec(
                        key = "done",
                        title = app.getString(R.string.recognition_notify_done),
                        intentUri = doneUri,
                        intentKind = IslandIntentKind.BROADCAST,
                        collapseOnClick = true,
                    ),
                )
            } else {
                emptyList()
            },
        )
    }

    /**
     * Android 16+：升为 Live Update / 状态栏胶囊（HyperOS 4 会渲染成超级岛）；
     * 无权限时保持标准通知。
     *
     * **必须先 [Notification.Builder.setOngoing] true**：`setRequestPromotedOngoing(true)` 要求
     * 通知是 ongoing，否则系统忽略升舱标志、静默落回标准通知（「设了实时仍是标准」的根因）。
     * 成功升舱才置 ongoing；未授权时保持非 ongoing 标准（不卡常驻），且未授权仍强行
     * `setRequestPromotedOngoing(true)` 会被系统整条撤回——先探权限。
     */
    private fun applyLiveUpdate(app: Context, builder: Notification.Builder, capsule: String) {
        if (Build.VERSION.SDK_INT < 36) return
        val granted = runCatching {
            app.getSystemService(NotificationManager::class.java).canPostPromotedNotifications()
        }.getOrDefault(false)
        if (!granted) {
            LunoLog.w(LOG_TAG, "recognition live update not granted, fallback to standard")
            return
        }
        builder.setOngoing(true)
        builder.setRequestPromotedOngoing(true)
        builder.setShortCriticalText(capsule.take(SHORT_CRITICAL_TEXT_MAX))
    }

    private suspend fun currentDisplay(): NotificationDisplaySettings =
        runCatching { ConfigProvider.getAdvancedSettings().notificationDisplay }
            .getOrDefault(NotificationDisplaySettings())

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

    private fun dismissIntentUri(app: Context, id: Int): String? =
        runCatching { dismissIntent(app, id).toUri(Intent.URI_INTENT_SCHEME) }.getOrNull()

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
