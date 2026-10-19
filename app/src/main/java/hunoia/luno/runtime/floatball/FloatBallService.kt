package hunoia.luno.runtime.floatball

import android.app.KeyguardManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.content.getSystemService
import hunoia.luno.R
import hunoia.luno.action.model.GRID_PANEL_TYPE_ID
import hunoia.luno.action.model.StoredAction
import hunoia.luno.action.model.gridPanelId
import hunoia.luno.bridge.system.canDrawOverlays
import hunoia.luno.bridge.ui.DensityProvider
import hunoia.luno.config.ConfigProvider
import hunoia.luno.config.model.ActionSettings
import hunoia.luno.config.model.FloatBallSettings
import hunoia.luno.domain.tune.TuneConfig
import hunoia.luno.gridpanel.GridPanelOverlay
import hunoia.luno.runtime.environment.BroadcastObserver
import hunoia.luno.service.SideGestureService
import hunoia.luno.ui.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * 悬浮球宿主。跑在独立 `:service` 进程，前台通知常驻。
 *
 * ## 为什么独立进程 áreas
 *
 * 球显示不依赖无障碍开关（无障碍关了只影响动作能否执行），所以不能挂到
 * `SideGestureService` 的窗口上，必须有独立宿主。代价是通知栏两条常驻。
 *
 * ## 动作执行
 *
 * 与 `SideGestureService` 同进程，静态弱引用直连：
 * - 点按动作 → `forwardBallTap`（面板/子手势/刮擦/直发，容器分类，与触钮同链路）。
 *
 * ## 键盘让位
 *
 * 2038 窗口拿不到 insets 推播（不可获焦），与一墨不同，这里用低频轮询
 * `currentWindowMetrics` 的 IME 底边（[IME_POLL_MS]），拖动期间窗口侧自行挂起。
 */
class FloatBallService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var settingsJob: Job? = null
    private var imeJob: Job? = null

    private var settings: FloatBallSettings = FloatBallSettings()

    private lateinit var ballWindow: FloatBallWindow
    private var broadcastObserver: BroadcastObserver? = null

    /** 球是否已真正挂上窗口。false = 未授权或被系统拒绝。 */
    private var ballVisible = false

    private val powerManager by lazy { getSystemService<PowerManager>() }
    private val keyguardManager by lazy { getSystemService<KeyguardManager>() }
    private val windowManager by lazy { getSystemService<android.view.WindowManager>() }

    override fun onCreate() {
        super.onCreate()
        ballWindow = FloatBallWindow(this).apply {
            onTap = { onBallTapped() }
            onDragFinished = { x, y -> persistPosition(x, y) }
        }
        startInForeground()

        broadcastObserver = BroadcastObserver(
            context = this,
            onScreenOff = { ballWindow.setHidden(true) },
            onScreenOn = { ballWindow.setHidden(isScreenLocked()) },
            onUserPresent = { ballWindow.setHidden(isScreenLocked()) },
        ).also { it.register() }

        observeSettings()
        observeIme()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    override fun onDestroy() {
        settingsJob?.cancel()
        imeJob?.cancel()
        broadcastObserver?.unregister()
        broadcastObserver = null
        ballWindow.detach()
        scope.cancel()
        super.onDestroy()
    }

    // ---- 配置 ----

    private fun observeSettings() {
        settingsJob?.cancel()
        settingsJob = scope.launch {
            combine(
                ConfigProvider.actionSettings,
                ConfigProvider.initialSettings,
            ) { action, initial -> FloatBallRuntimeConfig(action, initial.gestureEnabled) }
                .collect { config ->
                    settings = config.action.floatBall
                    ballWindow.updateSettings(config.action.floatBall)
                    applyVisibility(config)
                }
        }
    }

    private fun applyVisibility(config: FloatBallRuntimeConfig) {
        // 总闸优先：应用总开关关时，球自身的开关不再生效。
        val enabled = config.gestureEnabled && config.action.floatBall.enabled
        val shouldShow = enabled && canDrawOverlays()
        if (!shouldShow) {
            if (ballVisible) {
                ballVisible = false
                ballWindow.detach()
            }
            return
        }
        if (ballVisible) return
        ballVisible = ballWindow.attach()
        if (ballVisible) {
            ballWindow.setHidden(isScreenLocked())
        }
    }

    // ---- 点按派发 ----

    private fun onBallTapped() {
        val action = settings.tapAction ?: return
        val ballCenterX = DensityProvider.dp2px(settings.sizeDp) / 2 +
            (settings.x * DensityProvider.screenWidthPx).toInt()
        val ballCenterY = DensityProvider.dp2px(settings.sizeDp) / 2 +
            (settings.y * DensityProvider.screenHeightPx).toInt()
        // 网格面板动作**不进容器**：面板浮窗自包含（直读 TuneConfig，不依赖 a11y
        // 运行态），球侧直发 —— 无障碍没开照样弹，这是「无 a11y 可用」的主路径。
        // 触钮/子手势绑它的路径仍走容器 executor，同一个 GridPanelOverlay.show 入口。
        if (action.typeId == GRID_PANEL_TYPE_ID) {
            showGridPanelDirect(action, ballCenterX, ballCenterY)
            return
        }
        // 其余球点按动作统一转发（子手势/刮擦/直发，由容器分类，
        // 与触钮同链路）。无障碍没开时静默丢弃。
        SideGestureService.current?.forwardBallTap(
            action,
            ballCenterX,
            ballCenterY,
            settings.color,
        )
    }

    /** 球直发网格面板：悬空引用（面板被删）静默收口，空面板照常弹（本体是编辑入口）。 */
    private fun showGridPanelDirect(action: StoredAction, anchorX: Int, anchorY: Int) {
        scope.launch {
            val panelId = action.gridPanelId() ?: return@launch
            val actionSettings = runCatching { TuneConfig.getAction() }
                .onFailure { Log.w(TAG, "grid panel direct config read failed: ${it.message}") }
                .getOrDefault(ActionSettings())
            GridPanelOverlay.showIfExists(actionSettings, panelId, anchorX, anchorY)
        }
    }

    // ---- 键盘让位 ----

    private fun observeIme() {
        imeJob?.cancel()
        imeJob = scope.launch {
            while (isActive) {
                delay(IME_POLL_MS)
                if (!ballVisible) continue
                val imeBottom = runCatching {
                    windowManager?.currentWindowMetrics
                        ?.windowInsets
                        ?.getInsets(android.view.WindowInsets.Type.ime())
                        ?.bottom
                }.getOrDefault(0) ?: 0
                ballWindow.updateIme(imeBottom)
            }
        }
    }

    private fun persistPosition(x: Float, y: Float) {
        scope.launch {
            runCatching {
                ConfigProvider.updateActionSettings { current ->
                    current.copy(floatBall = current.floatBall.copy(x = x, y = y))
                }
            }.onFailure { Log.w(TAG, "persist ball position failed: ${it.message}") }
        }
    }

    // ---- 屏幕状态 ----

    private fun isScreenLocked(): Boolean {
        val poweredOff = powerManager?.isInteractive == false
        val locked = runCatching { keyguardManager?.isKeyguardLocked == true }.getOrDefault(false)
        return poweredOff || locked
    }

    // ---- 前台通知 ----

    private fun startInForeground() {
        val manager = getSystemService<NotificationManager>() ?: return
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "悬浮球", NotificationManager.IMPORTANCE_LOW).apply {
                enableLights(false)
                setShowBadge(false)
                lockscreenVisibility = Notification.VISIBILITY_SECRET
            },
        )
        val pendingIntent = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("悬浮球待命")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID, notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    companion object {
        private const val TAG = "LunoLauncher"
        private const val CHANNEL_ID = "float_ball"
        private const val NOTIFICATION_ID = 2002
        const val ACTION_STOP = "hunoia.luno.action.STOP_FLOAT_BALL"

        /** IME 底边轮询间隔。 */
        private const val IME_POLL_MS = 300L

        /** 设置开关变更 / 开机自启都走这里。必须是 startForegroundService。 */
        fun start(context: Context) {
            runCatching {
                context.startForegroundService(Intent(context, FloatBallService::class.java))
            }.onFailure {
                Log.e(TAG, "start float ball FAILED", it)
            }
        }

        fun stop(context: Context) {
            runCatching {
                context.startService(
                    Intent(context, FloatBallService::class.java).setAction(ACTION_STOP),
                )
            }.onFailure {
                Log.e(TAG, "stop float ball FAILED", it)
            }
        }
    }
}

/** 合并出来的运行时配置。 */
private data class FloatBallRuntimeConfig(
    val action: ActionSettings,
    val gestureEnabled: Boolean,
)
