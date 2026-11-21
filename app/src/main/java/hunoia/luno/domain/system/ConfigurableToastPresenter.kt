package hunoia.luno.domain.system

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationManagerCompat
import hunoia.luno.R
import hunoia.luno.bridge.ui.ToastPresenter
import hunoia.luno.bridge.ui.showSystemToastCompat
import hunoia.luno.capability.notify.DefaultNotificationPresenter
import hunoia.luno.config.ConfigProvider
import hunoia.luno.config.model.TOAST_CHANNEL_ID
import hunoia.luno.config.model.TOAST_NOTIFICATION_ID
import hunoia.luno.config.model.ToastPresentation
import hunoia.luno.core.AppContext
import hunoia.luno.core.domain.IslandNotificationSpec
import hunoia.luno.core.domain.IslandPublishResult
import hunoia.luno.core.domain.NotificationPresenter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * 系统 Toast 提示的可配置呈现器（组装根注入，见 [hunoia.luno.bridge.ui.setToastPresenter]）。
 *
 * 按全局 [hunoia.luno.config.model.NotificationDisplaySettings.toastPresentation] 分派：
 * [ToastPresentation.TOAST] 返回 false 让 bridge 回退系统 Toast；STANDARD / LIVE / ISLAND /
 * CAPSULE 用对应通知方式呈现，几秒自动收起。岛 / 胶囊失败按 LIVE → STANDARD 回退。
 *
 * 呈现是异步的（通知发布在进程级 scope + IO）；提示文本不写盘、不参与调度，即时反馈语义。
 */
class ConfigurableToastPresenter(
    private val islandPresenter: NotificationPresenter =
        DefaultNotificationPresenter(AppContext.get()),
) : ToastPresenter {

    override fun present(text: String, mode: ToastPresentation?) {
        // 读配置是异步（DataStore），统一在进程级 scope 里做；present 总是消费——
        // TOAST 分支回退系统 Toast，其余用通知呈现。[mode] 非空 = 强制该方式（每规则覆盖）。
        AppContext.applicationScope?.launch(Dispatchers.IO) {
            val effective = mode ?: runCatching {
                ConfigProvider.getAdvancedSettings().notificationDisplay.toastPresentation
            }.getOrNull() ?: ToastPresentation.TOAST
            if (effective == ToastPresentation.TOAST) {
                showSystemToastCompat(text)
                return@launch
            }
            presentAsync(text, effective)
        }
    }

    private suspend fun presentAsync(text: String, mode: ToastPresentation) {
        val app = runCatching { AppContext.get() }.getOrNull() ?: return
        ensureChannel(app)
        when (mode) {
            ToastPresentation.TOAST -> Unit
            ToastPresentation.STANDARD -> postStandard(app, text, wantLive = false)
            ToastPresentation.LIVE -> postStandard(app, text, wantLive = true)
            ToastPresentation.ISLAND ->
                if (!publishIsland(app, text, autoExpand = true)) {
                    postStandard(app, text, wantLive = true)
                }
            ToastPresentation.CAPSULE ->
                if (!publishIsland(app, text, autoExpand = false)) {
                    postStandard(app, text, wantLive = true)
                }
        }
    }

    /** 岛 / 胶囊发布：成功返回 true；失败由调用方走 LIVE → STANDARD 回退。 */
    private suspend fun publishIsland(app: Context, text: String, autoExpand: Boolean): Boolean {
        val spec = IslandNotificationSpec(
            channelId = TOAST_CHANNEL_ID,
            notificationId = TOAST_NOTIFICATION_ID,
            title = app.getString(R.string.toast_prompt_title),
            text = text,
            capsuleText = text.take(CAPSULE_TEXT_MAX),
            autoExpand = autoExpand,
            expandedSeconds = if (autoExpand) TOAST_ISLAND_EXPAND_SECONDS else 0,
            showInShade = true,
            timeoutSeconds = TOAST_TIMEOUT_SECONDS,
            ongoing = false,
        )
        return islandPresenter.publishIsland(spec) == IslandPublishResult.Posted
    }

    /** 标准通知路径（LIVE 时尝试升舱，Android 16+ promoted；不成功保持标准）。 */
    private fun postStandard(app: Context, text: String, wantLive: Boolean) {
        if (!NotificationManagerCompat.from(app).areNotificationsEnabled()) return
        // minSdk 33 起 POST_NOTIFICATIONS 已存在，不需要再判平台版本。
        if (app.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        val builder = Notification.Builder(app, TOAST_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(app.getString(R.string.toast_prompt_title))
            .setContentText(text)
            .setStyle(Notification.BigTextStyle().bigText(text))
            .setOngoing(false)
            .setAutoCancel(true)
            .setShowWhen(false)
            // toast 语义：几秒自动收起（Live Update 升舱时由系统管理常驻）。
            .setTimeoutAfter(TOAST_TIMEOUT_SECONDS * 1000L)
        if (wantLive) applyLiveUpdate(app, builder)
        runCatching {
            NotificationManagerCompat.from(app).notify(TOAST_NOTIFICATION_ID, builder.build())
        }
    }

    /**
     * LIVE 升舱：Android 16+ 有 promoted 权限才升。
     * **必须先 [Notification.Builder.setOngoing] true**：`setRequestPromotedOngoing(true)` 要求
     * 通知 ongoing，否则系统忽略升舱标志（「设了实时仍是标准」的根因）；无权限保持标准。
     */
    private fun applyLiveUpdate(app: Context, builder: Notification.Builder) {
        if (Build.VERSION.SDK_INT < 36) return
        val granted = runCatching {
            app.getSystemService(NotificationManager::class.java).canPostPromotedNotifications()
        }.getOrDefault(false)
        if (!granted) return
        builder.setOngoing(true)
        runCatching { builder.setRequestPromotedOngoing(true) }
        builder.setShortCriticalText(textShort(builder))
    }

    private fun textShort(builder: Notification.Builder): String {
        val extras = runCatching {
            builder.build().extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()
        }.getOrNull()
        return (extras ?: "").take(CAPSULE_TEXT_MAX)
    }

    private fun ensureChannel(app: Context) {
        val manager = app.getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(TOAST_CHANNEL_ID) != null) return
        manager.createNotificationChannel(
            // HIGH：岛 / 胶囊悬浮态需要高重要级；toast 是即时反馈，用户可单独静音。
            NotificationChannel(
                TOAST_CHANNEL_ID,
                app.getString(R.string.toast_prompt_channel_name),
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                enableLights(false)
                setShowBadge(false)
            },
        )
    }

    private companion object {
        const val CAPSULE_TEXT_MAX = 20
        const val TOAST_TIMEOUT_SECONDS = 3
        const val TOAST_ISLAND_EXPAND_SECONDS = 3
    }
}
