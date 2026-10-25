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
import android.os.SystemClock
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
import hunoia.luno.core.Events
import hunoia.luno.core.PanelDismissedEvent
import hunoia.luno.core.PanelShownEvent
import hunoia.luno.core.TouchButtonSideEvent
import hunoia.luno.domain.tune.TuneConfig
import hunoia.luno.runtime.environment.BroadcastObserver
import hunoia.luno.gridpanel.GridPanelOverlay
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
import kotlin.math.roundToInt
import hunoia.luno.core.LOG_TAG

/**
 * 悬浮球宿主，跑在独立 `:service` 进程（前台通知常驻）：球显示不依赖无障碍开关，
 * 不能挂到 `SideGestureService` 的窗口上。动作经同进程静态弱引用直连 `forwardBallTap`；
 * 2038 窗口拿不到 insets 推播，键盘让位靠轮询 IME 底边（[IME_POLL_MS]）。
 */
class FloatBallService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var settingsJob: Job? = null
    private var imeJob: Job? = null

    private var settings: FloatBallSettings = FloatBallSettings()

    /** 应用手势总闸（combine 出来的 gestureEnabled，总闸关则球与分身都不出）。 */
    private var gestureEnabledSnapshot = true

    private lateinit var ballWindow: FloatBallWindow
    private lateinit var mirrorWindow: FloatBallMirrorWindow
    private var broadcastObserver: BroadcastObserver? = null

    /** 球是否已真正挂上窗口。false = 未授权或被系统拒绝。 */
    private var ballVisible = false

    /** 面板类浮层打开计数（>0 即隐藏球）。 */
    private var panelCount = 0

    /** 上轮「因面板隐藏」状态：从隐藏恢复时要带回场动画。 */
    private var hiddenByPanel = false

    /** 临时隐藏截止时间（uptimeMillis），0 = 无。只作用窗口，不碰 `enabled` 存量。 */
    private var tempHideUntilMs = 0L
    private var tempHideJob: Job? = null

    private val powerManager by lazy { getSystemService<PowerManager>() }
    private val keyguardManager by lazy { getSystemService<KeyguardManager>() }
    private val windowManager by lazy { getSystemService<android.view.WindowManager>() }

    // ---- 跟随持机手（试验） ----

    private val touchSideSubscriber: (TouchButtonSideEvent) -> Unit = { event ->
        onTouchSide(event.leftSide)
    }

    override fun onCreate() {
        super.onCreate()
        ballWindow = FloatBallWindow(this, onImeLiftChanged = { lift ->
            mirrorWindow.updateImeLift(lift)
        }).apply {
            onTap = { onBallTapped() }
            onDragFinished = { x, y -> persistBallPosition(x, y) }
        }
        mirrorWindow = FloatBallMirrorWindow(this).apply {
            onPressed = { onMirrorPressed() }
            onTapped = { onMirrorTapped() }
            onDragDeltas = { dx, dy -> onMirrorDragDeltas(dx, dy) }
            onDragReleased = { onMirrorDragReleased() }
        }
        startInForeground()

        broadcastObserver = BroadcastObserver(
            context = this,
            onScreenOff = {
                // 息屏停掉 IME 兜底轮询：屏幕不可见时键盘让位无意义，
                // 别让前台服务以 2Hz 空转唤醒（亮屏解锁由 applyLockState 补启）。
                imeJob?.cancel()
                imeJob = null
                ballWindow.setHidden(true)
                mirrorWindow.setHidden(true)
            },
            onScreenOn = ::applyLockState,
            onUserPresent = ::applyLockState,
        ).also { it.register() }

        observeSettings()
        ensureImeObserver()
        observePanels()
        Events.subscribe(TouchButtonSideEvent::class, touchSideSubscriber)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        // 显式 nudge 的重评入口（悬浮窗授权回来等）：服务可能已在跑但停在无权限/
        // 总闸关的隐藏态。首次启动时 settings 尚未流到，评一次默认值无害，
        // 真实 collect 到达会再刷。
        refreshWindows(animateRestore = false)
        return START_STICKY
    }

    override fun onDestroy() {
        settingsJob?.cancel()
        imeJob?.cancel()
        broadcastObserver?.unregister()
        broadcastObserver = null
        Events.unsubscribe(PanelShownEvent::class, panelShownSubscriber)
        Events.unsubscribe(PanelDismissedEvent::class, panelDismissedSubscriber)
        Events.unsubscribe(TouchButtonSideEvent::class, touchSideSubscriber)
        Events.unsubscribe(FloatBallTempHideEvent::class, tempHideSubscriber)
        tempHideJob?.cancel()
        tempHideJob = null
        tempHideUntilMs = 0L
        panelCount = 0
        hiddenByPanel = false
        ballWindow.detach()
        mirrorWindow.detach()
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
                    val newSettings = config.action.floatBall
                    // 临时隐藏期间球被手动/规则打开（enabled false→true）：显示意图优先，作废本轮隐藏
                    if (newSettings.enabled && !settings.enabled) cancelTempHide()
                    settings = newSettings
                    gestureEnabledSnapshot = config.gestureEnabled
                    ballWindow.updateSettings(config.action.floatBall)
                    mirrorWindow.updateSettings(config.action.floatBall)
                    refreshWindows(animateRestore = false)
                }
        }
    }

    /** 统一的窗口挂/摘入口：shouldShow = 总闸 && 球开关 && 悬浮窗权限 && 非临时隐藏 && 面板计数==0；从面板隐藏恢复时带回场动画。 */
    private fun refreshWindows(animateRestore: Boolean) {
        val enabled = settings.enabled && canDrawOverlays() && gestureEnabledSnapshot
        val panelHidden = panelCount > 0
        val tempHidden = tempHideUntilMs > SystemClock.uptimeMillis()
        val shouldShow = enabled && !panelHidden && !tempHidden
        if (!shouldShow) {
            if (ballVisible) {
                if (enabled) {
                    // 面板/临时隐藏是短态：软隐藏（窗口保留、不可见不可点），
                    // 免掉整拆整建（removeView→新 ComposeView→首帧组合→addView），
                    // 面板开合频繁（音量刮擦每次都发面板事件）时差距最明显。
                    ballWindow.setPanelHidden(true)
                    mirrorWindow.setPanelHidden(true)
                } else {
                    // 总闸/开关/权限关：长态不出，摘窗回收。
                    ballVisible = false
                    ballWindow.detach()
                    mirrorWindow.detach()
                }
            }
            return
        }
        if (!ballVisible) {
            ballVisible = if (animateRestore) ballWindow.attachAnimated() else ballWindow.attach()
            if (ballVisible) {
                ballWindow.setHidden(isScreenLocked())
                if (settings.mirrorBall) mirrorWindow.attach()
                mirrorWindow.setHidden(isScreenLocked())
                // 新窗口的 insets 推播能力未知：兜底轮询按需补启（息屏期间被停过也一样）。
                ensureImeObserver()
            }
        } else {
            // 软隐藏 → 恢复：窗口还在，直接亮回（带回场动画），不走重建。
            ballWindow.setPanelHidden(false, animateRestore)
            mirrorWindow.setPanelHidden(false)
            if (settings.mirrorBall) {
                if (!mirrorWindow.attach()) return
                mirrorWindow.setHidden(isScreenLocked())
                mirrorWindow.snapToMirror()
            } else {
                mirrorWindow.detach()
            }
        }
    }

    // ---- 面板联动（面板打开即隐藏球） ----

    private val panelShownSubscriber: (PanelShownEvent) -> Unit = {
        panelCount++
        hiddenByPanel = panelCount > 0
        refreshWindows(animateRestore = false)
    }

    private val panelDismissedSubscriber: (PanelDismissedEvent) -> Unit = {
        if (panelCount > 0) panelCount--
        val recovered = panelCount == 0 && hiddenByPanel
        hiddenByPanel = panelCount > 0
        refreshWindows(animateRestore = recovered)
    }

    private fun observePanels() {
        Events.subscribe(PanelShownEvent::class, panelShownSubscriber)
        Events.subscribe(PanelDismissedEvent::class, panelDismissedSubscriber)
        Events.subscribe(FloatBallTempHideEvent::class, tempHideSubscriber)
    }

    // ---- 临时隐藏（显隐动作的 hideForMs 模式，只摘窗口） ----

    private val tempHideSubscriber: (FloatBallTempHideEvent) -> Unit = { event ->
        scheduleTempHide(event.durationMs)
    }

    /**
     * 只摘窗口不碰 `enabled` 存量：恢复定时器在内存里，进程被杀（应用更新/系统回收）即失效，
     * 球按落盘 enabled 照常恢复。重复隐藏取更晚的截止时间。
     */
    private fun scheduleTempHide(durationMs: Long) {
        val deadline = SystemClock.uptimeMillis() + durationMs
        if (deadline <= tempHideUntilMs) return
        tempHideUntilMs = deadline
        tempHideJob?.cancel()
        tempHideJob = scope.launch {
            delay(deadline - SystemClock.uptimeMillis())
            tempHideUntilMs = 0L
            refreshWindows(animateRestore = true)
        }
        refreshWindows(animateRestore = false)
    }

    private fun cancelTempHide() {
        if (tempHideUntilMs == 0L) return
        tempHideJob?.cancel()
        tempHideJob = null
        tempHideUntilMs = 0L
        refreshWindows(animateRestore = true)
    }

    // ---- 点按派发 ----

    private fun onBallTapped() {
        val action = settings.tapAction ?: return
        dispatchTapAction(action, ballCenterX(), ballCenterY())
    }

    /** 球心屏幕坐标（px）。读窗口实位而非落盘存量：自动挪球被打断时两者会背离，
     *  锚点按实位算，面板才不会弹到球的对面。 */
    private fun ballCenter(): Pair<Int, Int> {
        val half = DensityProvider.dp2px(settings.sizeDp) / 2
        val (px, py) = ballWindow.currentPosition()
        return (px.roundToInt() + half) to (py.roundToInt() + half)
    }

    private fun ballCenterX(): Int = ballCenter().first

    private fun ballCenterY(): Int = ballCenter().second

    /** 按锚点派发 tapAction（主球与分身共用）：网格面板直发，其余进容器。 */
    private fun dispatchTapAction(action: StoredAction, anchorX: Int, anchorY: Int) {
        // 网格面板动作不进容器：面板浮窗自包含（直读 TuneConfig，不依赖 a11y
        // 运行态），球侧直发 —— 无障碍没开照样弹，这是「无 a11y 可用」的主路径。
        // 触钮/子手势绑它的路径仍走容器 executor，同一个 GridPanelOverlay.show 入口。
        if (action.typeId == GRID_PANEL_TYPE_ID) {
            showGridPanelDirect(action, anchorX, anchorY)
            return
        }
        SideGestureService.current?.forwardBallTap(action, anchorX, anchorY, settings.color)
    }

    // ---- 分身（mirrorBall） ----

    /** 按下分身：立即交换位置（不触发动作），锚点记录在按下时的分身球心，点击抬手时补派发。 */
    private fun onMirrorPressed() {
        if (!ballVisible) return
        val sizePx = DensityProvider.dp2px(settings.sizeDp)
        val (mx, my) = mirrorWindowPosition()
        pendingMirrorAnchorX = mx + sizePx / 2
        pendingMirrorAnchorY = my + sizePx / 2
        // 接力拖待命：抬手若未拖动就是纯点按，无副作用。
        ballWindow.beginExternalDrag()
        swapWithMirror(mx, my)
    }

    /** 点击分身（未超 touchSlop 抬手）：补触发动作（交换已在按下时完成）。 */
    private fun onMirrorTapped() {
        if (!ballVisible) return
        ballWindow.endExternalDrag()
        val action = settings.tapAction ?: return
        dispatchTapAction(action, pendingMirrorAnchorX, pendingMirrorAnchorY)
    }

    /** 分身拖动接力：位移增量转给主球。 */
    private fun onMirrorDragDeltas(dx: Float, dy: Float) {
        ballWindow.externalDragBy(dx, dy)
    }

    /** 分身抬手：主球贴边结算 + 落盘。 */
    private fun onMirrorDragReleased() {
        ballWindow.endExternalDrag()
    }

    private var pendingMirrorAnchorX = 0
    private var pendingMirrorAnchorY = 0
    /** 分身窗当前顶边屏幕坐标（px）。 */
    private fun mirrorWindowPosition(): Pair<Int, Int> {
        val pos = mirrorWindow.currentPosition()
        return pos.first.roundToInt() to pos.second.roundToInt()
    }

    /** 交换：主球新位置 = 对侧贴边（屏内语义），写回存量，双方交叉淡入淡出。 */
    private fun swapWithMirror(mirrorTopLeftX: Int, mirrorTopLeftY: Int) {
        val wasIdled = ballWindow.isIdled()
        val newX = (mirrorTopLeftX.toFloat() / DensityProvider.screenWidthPx)
            .coerceIn(0f, 1f)
        val newY = (mirrorTopLeftY.toFloat() / DensityProvider.screenHeightPx)
            .coerceIn(0f, 1f)
        persistBallPosition(newX, newY)
        ballWindow.crossFadeSwap(
            mirrorTopLeftX.toFloat(),
            mirrorTopLeftY.toFloat(),
            endIdled = wasIdled,
        ) { mirrorWindow.crossFadeSwap(settings) }
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
                // 窗口已收到事件型 IME insets：此 ROM 支持推播，兜底轮询使命完成，
                // 整个停掉（此前只 continue，服务存活期间永远 2Hz 唤醒）。
                // 判在隐藏类短态之前：面板/临时隐藏乃至摘窗期间确认了推播也照停，
                // 别为迟早退出的轮询白付唤醒；重挂由 ensureImeObserver 按需补启。
                if (ballWindow.imeInsetsAvailable()) break
                if (!ballVisible || panelCount > 0 ||
                    tempHideUntilMs > SystemClock.uptimeMillis()
                ) continue
                val imeBottom = runCatching {
                    windowManager?.currentWindowMetrics
                        ?.windowInsets
                        ?.getInsets(android.view.WindowInsets.Type.ime())
                        ?.bottom
                }.getOrNull() ?: 0
                ballWindow.updateIme(imeBottom)
                mirrorWindow.updateImeLift(ballWindow.currentImeLiftPx())
            }
        }
    }

    /** 兜底轮询的按需启动：只在球挂窗、且事件型 insets 尚未确认时跑（息屏侧由 screen off 停）。 */
    private fun ensureImeObserver() {
        if (imeJob?.isActive == true) return
        if (!ballVisible || ballWindow.imeInsetsAvailable()) return
        observeIme()
    }

    /**
     * 直接规则：手势碰了哪侧，球就即时移到哪侧（不投票、不留状态）。球已在该侧则
     * 不动（同侧触发零扰动，手动调的高度保留）；被拖动/按压/上一次挪球占用时本次
     * 跳过，下一次触发自然重试。落盘延到球真正落位（onLanded）：接受挪球就按目标
     * 落盘的话，淡出被打断会让存量停在新侧、实位停在旧侧 —— 面板锚点按存量算会弹到
     * 球对面，分身按存量反推会跟球同侧。
     */
    private fun onTouchSide(leftSide: Boolean) {
        if (!settings.followHand || !ballVisible) return
        if (leftSide == ballWindow.isOnLeft()) return
        val anchorY = (if (leftSide) settings.leftAnchorY else settings.rightAnchorY) ?: settings.y
        ballWindow.repositionTo(leftSide, anchorY) {
            val (px, py) = ballWindow.currentPosition()
            persistBallPosition(
                px / DensityProvider.screenWidthPx,
                py / DensityProvider.screenHeightPx,
            )
            // 分身立即换到新对侧：不等落盘回声的话，回声到达前与球短暂同侧。
            mirrorWindow.crossFadeSwap(settings)
        }
    }

    /**
     * 球位置落盘：手动拖动/分身换位/跟手挪球共用，顺带记忆该侧高度锚点
     * （下次跟手换侧落到记忆高度）。
     */
    private fun persistBallPosition(x: Float, y: Float) {
        val left = x < 0.5f
        settings = settings.copy(
            x = x,
            y = y,
            leftAnchorY = if (left) y else settings.leftAnchorY,
            rightAnchorY = if (!left) y else settings.rightAnchorY,
        )
        scope.launch {
            runCatching {
                ConfigProvider.updateActionSettings { current ->
                    // 字段级合并到最新存量：整对象按内存快照覆盖会把主进程的并发
                    // 修改（大小/颜色/动作）连同旧值一起写回去。
                    current.copy(
                        floatBall = current.floatBall.copy(
                            x = x,
                            y = y,
                            leftAnchorY = if (left) y else current.floatBall.leftAnchorY,
                            rightAnchorY = if (!left) y else current.floatBall.rightAnchorY,
                        ),
                    )
                }
            }.onFailure { Log.w(TAG, "persist ball position failed: ${it.message}") }
        }
    }

    // ---- 屏幕状态 ----

    private fun applyLockState() {
        val hidden = isScreenLocked()
        ballWindow.setHidden(hidden)
        mirrorWindow.setHidden(hidden)
        // 息屏时兜底轮询被停掉（见 onScreenOff），亮屏解锁后按需补启。
        if (!hidden) ensureImeObserver()
    }

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
        private const val TAG = LOG_TAG
        private const val CHANNEL_ID = "float_ball"
        private const val NOTIFICATION_ID = 2002
        const val ACTION_STOP = "hunoia.luno.action.STOP_FLOAT_BALL"

        /** IME 底边轮询间隔（事件型 insets 的兜底；支持推播的 ROM 由窗口监听即时处理）。 */
        private const val IME_POLL_MS = 500L

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

private data class FloatBallRuntimeConfig(
    val action: ActionSettings,
    val gestureEnabled: Boolean,
)
