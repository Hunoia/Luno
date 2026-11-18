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
 * 识别结果的「挂到超级岛」输出。
 *
 * 岛的呈现机制全部来自 `:capability:notify`（FocusTemplate V3 + XMSF 鉴定绕过），
 * 这里只负责三件识别器自己的事：内容占位替换、通道归属、回退链（ISLAND → 标准通知）。
 *
 * 岛上的按钮只有一个「完成」：点它收起岛并撤通知。识别器的产物是一次性凭证
 * （取件码 / 验证码），用户看完就走，不需要在岛上再挂「复制」——值已经进过剪贴板了。
 */
internal object RecognitionNotifier {

    /** 识别器专用通道；与待办 / 习惯分开，用户能单独静音。 */
    const val CHANNEL_ID = "recognition"

    private const val CHANNEL_NAME = "识别提醒"

    /** 点「完成」的广播。 */
    const val ACTION_DISMISS = "hunoia.luno.action.RECOGNITION_NOTIFY_DISMISS"

    /** 通知 id 由规则 id + 值的哈希派生：同一条规则抓到同一个值不会堆两条岛。 */
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
        val display = currentDisplay()
        val presenter: NotificationPresenter by lazy { DefaultNotificationPresenter(app) }

        val islandSpec = IslandNotificationSpec(
            channelId = CHANNEL_ID,
            notificationId = id,
            title = spec.title.resolve(item).ifBlank { item.ruleName.ifBlank { item.value } },
            text = spec.text.resolve(item).ifBlank { item.value },
            capsuleText = spec.capsuleText.resolve(item).ifBlank { capsuleOf(item.value) },
            autoExpand = spec.autoExpand,
            expandedSeconds = spec.expandedSeconds,
            blockWindowMs = display.islandDisconnectMs,
            outerGlow = display.islandOuterGlow,
            showInShade = spec.keepInShade,
            timeoutSeconds = spec.timeoutSeconds,
            ongoing = false,
            actions = listOf(
                IslandActionSpec(
                    key = "done",
                    title = app.getString(R.string.recognition_notify_done),
                    intentUri = dismissIntent(app, id).toUri(Intent.URI_INTENT_SCHEME),
                    intentKind = IslandIntentKind.BROADCAST,
                    highlighted = true,
                    // 一按就结束：SystemUI 直接收起岛，再由接收器撤掉通知栏条目。
                    collapseOnClick = true,
                ),
            ),
        )
        when (presenter.publishIsland(islandSpec)) {
            IslandPublishResult.Posted -> {
                LunoLog.i(LOG_TAG, "recognition island posted: rule=${item.ruleId} id=$id")
                true
            }
            else -> {
                // 非 Xiaomi / 绕过不可用 / 鉴权被撤：回退标准通知，内容一样，只是没有岛。
                LunoLog.w(LOG_TAG, "recognition island fallback to standard: rule=${item.ruleId}")
                postStandard(app, id, islandSpec)
            }
        }
    }

    private fun postStandard(app: Context, id: Int, spec: IslandNotificationSpec): Boolean {
        val done = PendingIntent.getBroadcast(
            app,
            id,
            dismissIntent(app, id),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = Notification.Builder(app, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(spec.title)
            .setContentText(spec.text)
            .setStyle(Notification.BigTextStyle().bigText(spec.text))
            .setOngoing(false)
            .setAutoCancel(true)
            .addAction(
                Notification.Action.Builder(null, app.getString(R.string.recognition_notify_done), done)
                    .build(),
            )
            .build()
        // 与其它通知器同口径：开关 + 运行期权限各查一次，再吞掉系统拒绝。
        if (!NotificationManagerCompat.from(app).areNotificationsEnabled()) return false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            app.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }
        return runCatching {
            NotificationManagerCompat.from(app).notify(id, notification)
            true
        }.getOrDefault(false)
    }

    private fun ensureChannel(app: Context) {
        val manager = app.getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        manager.createNotificationChannel(
            // HIGH：岛要求能悬浮；关掉悬浮时系统会自己降级成普通通知。
            NotificationChannel(CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_HIGH).apply {
                description = "识别器命中规则时挂到超级岛"
                setShowBadge(false)
            },
        )
    }

    private fun dismissIntent(app: Context, id: Int): Intent =
        Intent(app, RecognitionNotifyReceiver::class.java).apply {
            action = ACTION_DISMISS
            putExtra(EXTRA_NOTIFICATION_ID, id)
        }

    /** 岛摘要态文案上限：HyperOS 实测超过 ~8 字会被裁。 */
    private fun capsuleOf(value: String): String = value.take(8)

    private suspend fun currentDisplay(): NotificationDisplaySettings =
        runCatching { ConfigProvider.getAdvancedSettings().notificationDisplay }
            .getOrDefault(NotificationDisplaySettings())

    /** 模板占位：`{value}` / `{rule}` / `{host}`。 */
    private fun String.resolve(item: RecognizedItem): String = this
        .replace("{value}", item.value)
        .replace("{rule}", item.ruleName.ifBlank { item.ruleId })
        .replace("{host}", item.urls.firstOrNull()?.let { hunoia.luno.runtime.recognition.LinkUrls.hostOf(it) }.orEmpty())

    const val EXTRA_NOTIFICATION_ID = "hunoia.luno.extra.RECOGNITION_NOTIFICATION_ID"
}

/** 「完成」按钮的接收器：收起岛 / 撤通知。 */
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
