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
import android.content.res.Configuration
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.SystemClock
import java.lang.ref.WeakReference
import hunoia.luno.core.LunoLog as Log
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Notifications
import androidx.core.content.getSystemService
import hunoia.luno.floatball.R
import hunoia.luno.core.domain.AppLaunchAction
import hunoia.luno.core.domain.GRID_PANEL_TYPE_ID
import hunoia.luno.core.domain.StoredAction
import hunoia.luno.bridge.system.ClipboardContent
import hunoia.luno.bridge.system.PersistentNotificationShell
import hunoia.luno.bridge.system.SelfWriteGuard
import hunoia.luno.bridge.system.canDrawOverlays
import hunoia.luno.bridge.ui.DensityProvider
import hunoia.luno.bridge.ui.showToast
import hunoia.luno.config.model.ActionSettings
import hunoia.luno.config.model.ButtonVisibilitySettings
import hunoia.luno.config.model.ClipFloatSettings
import hunoia.luno.config.model.ClipKind
import hunoia.luno.config.model.FloatBallDefaults
import hunoia.luno.config.model.FloatBallSettings
import hunoia.luno.core.Events
import hunoia.luno.core.PanelDismissedEvent
import hunoia.luno.core.PanelShownEvent
import hunoia.luno.core.ScreenSceneEvent
import hunoia.luno.core.TouchButtonSideEvent
import hunoia.luno.config.model.VisibilityTargets
import hunoia.luno.config.model.withPosition
import hunoia.luno.clipboard.ClipboardCenter
import hunoia.luno.link.Links
import hunoia.luno.runtime.floatball.inbox.BallInboxAction
import hunoia.luno.runtime.floatball.inbox.BallInboxChange
import hunoia.luno.runtime.floatball.inbox.BallInboxConfig
import hunoia.luno.runtime.floatball.inbox.BallInboxReducer
import hunoia.luno.runtime.floatball.inbox.BallInboxState
import hunoia.luno.runtime.floatball.inbox.BallItem
import hunoia.luno.runtime.floatball.inbox.BallSource
import hunoia.luno.runtime.floatball.inbox.NotificationItem
import hunoia.luno.runtime.floatball.inbox.ResultItem
import hunoia.luno.runtime.floatball.inbox.builtInSceneLinkIndex
import hunoia.luno.runtime.floatball.inbox.effectiveTtlMs
import hunoia.luno.runtime.floatball.inbox.hasBuiltInSceneLink
import hunoia.luno.runtime.floatball.inbox.hasSceneItems
import hunoia.luno.runtime.floatball.inbox.stableHash
import hunoia.luno.runtime.floatball.inbox.usedSceneRuleUrls
import hunoia.luno.runtime.floatball.notification.NotificationContentIntents
import hunoia.luno.runtime.floatball.notification.NotificationPulseListener
import hunoia.luno.runtime.floatball.routing.PulseRoute
import hunoia.luno.runtime.floatball.routing.PulseRouter
import hunoia.luno.runtime.floatball.result.DeliveredResults
import hunoia.luno.runtime.floatball.result.ResultFactory
import hunoia.luno.runtime.floatball.result.asCopyOutcome
import hunoia.luno.runtime.floatball.result.asShareOutcome
import hunoia.luno.runtime.floatball.result.deliverResults
import hunoia.luno.runtime.floatball.result.isCopyOutcome
import hunoia.luno.runtime.floatball.suppression.DismissSuppression
import hunoia.luno.runtime.recognition.CompiledRecognizer
import hunoia.luno.runtime.recognition.RecognitionDelivery
import hunoia.luno.runtime.recognition.RecognitionExecutor
import hunoia.luno.runtime.recognition.Recognizer
import hunoia.luno.runtime.recognition.RecognizedItem
import hunoia.luno.config.model.RecognitionDisplay
import hunoia.luno.config.model.RecognitionSettings
import hunoia.luno.ui.icon.SearchIconCache
import hunoia.luno.runtime.notification.NotificationPulseHub
import hunoia.luno.core.domain.NotificationPulse
import hunoia.luno.runtime.GestureRuntimeState
import hunoia.luno.runtime.RuntimeStateHub
import hunoia.luno.runtime.button.shouldShowSurface
import hunoia.luno.runtime.environment.BroadcastObserver
import hunoia.luno.runtime.surface.SurfaceDispatch
import hunoia.luno.runtime.surface.SurfaceController
import hunoia.luno.runtime.surface.GestureCommand
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import hunoia.luno.core.LOG_TAG

/**
 * 悬浮球宿主，跑在独立 `:service` 进程（前台通知常驻）：三颗球（主球 / 通知球 /
 * 结果球）由同一个服务托管，通知球和结果球各自展开自己的胶囊。
 *
 * 规则判定与执行都在本服务里：通知监听、剪贴板轮询、情景事件是三类输入，
 * 结果按规则的显示档分流（进结果胶囊 / 直接执行）。
 */
class FloatBallService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    /**
     * 情景识别的串行车道：同一屏会先推一次「只有文本」再补一次「带真实链接」的更新，
     * 规则匹配放后台时若并发执行，后补的那条可能先落地、又被空结果覆盖，看着就像卡片闪没。
     * 按到达顺序串行处理，首扫与补链接不会互相倒序。
     */
    private val sceneLane = Dispatchers.Default.limitedParallelism(1)
    private var settingsJob: Job? = null
    private var imeJob: Job? = null

    /** insets 事件触发的合并复测任务。 */
    private var imeHintJob: Job? = null

    private var settings: FloatBallSettings = FloatBallSettings()
    private var visibilitySettings: ButtonVisibilitySettings = ButtonVisibilitySettings()

    /** 应用手势总闸（combine 出来的 gestureEnabled，总闸关则球不出）。 */
    private var gestureEnabledSnapshot = true

    private lateinit var ballWindow: FloatBallWindow
    private lateinit var notificationSlot: CapsuleSlot
    private lateinit var resultSlot: CapsuleSlot

    /** 剪贴板悬浮窗：单窗，显示新条目即替换内容；关闭后置空。 */
    private var clipFloat: ClipFloatWindow? = null

    /** 悬浮窗显示请求在途（异步解析 + 挂窗）；为真时服务不得自停，否则窗口挂一半被拆。 */
    private var clipFloatPending = false

    /**
     * 已收到首个 [onStartCommand]（intent 已处理）。此前（onCreate 首帧）禁止自停判定：
     * `observeSettings` 的 combine 首帧会在 onCreate 内同步产出（Main.immediate + DataStore 缓存），
     * 早于 onStartCommand 置位 [clipFloatPending]，此时判定会误把「为悬浮窗启动」的服务拆掉。
     */
    private var startCommandSeen = false
    private var destroying = false
    private var broadcastObserver: BroadcastObserver? = null

    /** 主球是否已真正挂上窗口。false = 未授权或被系统拒绝。 */
    private var ballVisible = false

    /** 轮询兜底：后台读剪贴板对比内容变化；读依赖无障碍豁免，读不到就永远不弹。 */
    private var clipboardPollJob: Job? = null
    private var lastSeenClip: ClipboardContent? = null

    /** 识别器配置（随 actionSettings 流更新）：胶囊、链接目标解析都读它。 */
    private var recognition: RecognitionSettings = RecognitionSettings()

    /** 识别器编译结果：配置更新时重建，事件到达时只做匹配。 */
    private var recognizer: CompiledRecognizer = Recognizer.compile(RecognitionSettings())

    /**
     * 结果去向分流器：胶囊档进队列，直执行档当场落地（带去重）。
     *
     * 去重表是运行态（不是配置），服务重建即清空——一次重启后被直执行过的同一段文本
     * 应该重新生效。
     */
    private val delivery = RecognitionDelivery(
        windowMs = { ResultDirectDedupWindowMs },
    )

    /**
     * 识别结果统一执行口。
     *
     * 宿主由 `:app` 通过 [hunoia.luno.runtime.recognition.RecognitionHosts] 安装一次
     * （与识屏共用同一个 `AppRecognitionHost`）：胶囊与识屏的「打开链接」不可能走出
     * 两种行为，本模块也不再自己写链接跳页 / 分发分支。
     */
    private val recognitionExecutor: RecognitionExecutor
        get() = hunoia.luno.runtime.recognition.RecognitionHosts.executor
    /** Surface 隐藏状态机：面板计数 + 临时隐藏，窗口动作仍由本服务负责。 */
    private val visibility = hunoia.luno.runtime.surface.SurfaceVisibilityState()
    private var tempHideJob: Job? = null

    /**
     * Surface 短态软隐藏（标注等）：与 [visibility] 一样只是求值输入。
     * 不能直接对窗口下命令 —— 否则任意一次 [refreshWindows] 都会把它撤销。
     */
    private var softHidden = false

    /** insets 推播是否已确认可用（确认后可停掉 IME 兜底轮询）。 */
    private var imePushConfirmed = false
    private var imeHintCount = 0

    /** 显隐场景随运行态（前台应用 / 锁屏 / 横屏 / 键盘）重算。 */
    private val runtimeStateObserver: (GestureRuntimeState) -> Unit = { refreshWindows() }

    /** 悬浮球在 SurfaceController 的句柄：短态软隐藏、长态摘窗。 */
    private val surfaceHandle = object : hunoia.luno.runtime.surface.SurfaceHandle {
        override val surfaceId: String = "float-ball"
        override val surfaceKind: hunoia.luno.runtime.surface.SurfaceKind =
            hunoia.luno.runtime.surface.SurfaceKind.FLOAT_BALL

        override fun softHide() {
            softHidden = true
            refreshWindows()
        }

        override fun restore() {
            // 只清输入位：真实显隐由同一份求值决定，恢复不是“强制显示”。
            softHidden = false
            refreshWindows()
        }

        override fun detach() {
            hunoia.luno.runtime.surface.SurfaceController.unregister(surfaceId)
            ballVisible = false
            ballWindow.detach()
            detachContentWindows()
        }
    }

    private val powerManager by lazy { getSystemService<PowerManager>() }
    private val keyguardManager by lazy { getSystemService<KeyguardManager>() }
    private val windowManager by lazy { getSystemService<android.view.WindowManager>() }

    // ---- 跟随持机手（三球各自独立） ----

    /**
     * 换边请求的唯一扇出点：命令口（触钮/正常手势，占绝大多数）与事件口共用。
     *
     * 主球与两个内容球各自按自己的开关决定是否接收；三者互不带动。
     * 返回值 = 任一球接收了请求（命令口只用于日志/调试，动作不受它影响）。
     */
    private val followHandBroadcast: (Boolean) -> Boolean = { leftSide ->
        val mainAccepted = requestFollowHandSwap(leftSide)
        val notificationAccepted = notificationSlot.requestFollowHandSwap(leftSide)
        val resultAccepted = resultSlot.requestFollowHandSwap(leftSide)
        mainAccepted || notificationAccepted || resultAccepted
    }

    private val touchSideSubscriber: (TouchButtonSideEvent) -> Unit = { event ->
        followHandBroadcast(event.leftSide)
    }

    /** 情景识别结果（手势侧事件驱动扫描后推送）→ 结果胶囊 / 直执行。 */
    private val sceneSubscriber: (ScreenSceneEvent) -> Unit = { event ->
        onScreenScene(event)
    }

    private var pendingFollowHandSide: Boolean? = null
    private var followHandRetryJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        currentRef = WeakReference(this)
        ballWindow = FloatBallWindow(
            this,
            onImeLiftChanged = { updateBubbleAnchors() },
            onImeInsetsHint = ::onImeInsetsHinted,
            idleDescription = getString(R.string.float_ball),
        ).apply {
            onTap = { onBallTapped() }
            onLongPress = null
            onDragFinished = { x, y -> persistMainBallPosition(x, y) }
        }
        notificationSlot = CapsuleSlot(BallSource.NOTIFICATION).also { it.attachWindows() }
        resultSlot = CapsuleSlot(BallSource.RESULT).also { it.attachWindows() }
        startInForeground()

        broadcastObserver = BroadcastObserver(
            context = this,
            onScreenOff = {
                imeJob?.cancel()
                imeJob = null
                imeHintJob?.cancel()
                imeHintJob = null
                clipboardPollJob?.cancel()
                clipboardPollJob = null
                ballWindow.setHidden(true)
                closeClipFloat()
                notificationSlot.ball.setHidden(true)
                resultSlot.ball.setHidden(true)
                notificationSlot.hideCapsule()
                resultSlot.hideCapsule()
                // 息屏期间不读剪贴板：重置基线，解锁后由 seedClipboardBaseline 重新对齐。
                lastSeenClip = null
            },
            onScreenOn = ::applyLockState,
            onUserPresent = ::applyLockState,
        ).also { it.register() }

        observeSettings()
        RuntimeStateHub.addObserver(runtimeStateObserver)
        ensureImeObserver()
        observePanels()
        Events.subscribe(TouchButtonSideEvent::class, touchSideSubscriber)
        Events.subscribe(ScreenSceneEvent::class, sceneSubscriber)
        FloatBallCommand.requestFollowHandSwap = followHandBroadcast
        NotificationPulseHub.addPostedObserver(notificationObserver)
        NotificationPulseHub.addRemovedObserver(notificationRemovedObserver)
        hunoia.luno.runtime.surface.SurfaceController.register(surfaceHandle)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // 进入 onStartCommand 即 intent 已送达；此后才允许自停判定（observeSettings 首帧
        // 在 onCreate 同步产出，不能在那之前下结论，否则为悬浮窗启动的服务会被误拆）。
        startCommandSeen = true
        if (intent?.action == ACTION_SHOW_CLIP_FLOAT) {
            val id = intent.getStringExtra(EXTRA_CLIP_ID).orEmpty()
            if (id.isNotBlank()) showClipFloatById(id)
            refreshWindows()
            // 条目缺失（id 空）且服务确实空闲：收回宿主，避免空转前台服务。
            if (shouldStopSelf()) {
                stopSelf()
                return START_NOT_STICKY
            }
            return START_STICKY
        }
        if (intent?.action == ACTION_STOP) {
            // 总闸关恒停；否则只要还有任一想用的功能（球 / 通知胶囊 / 直执行规则 /
            // 在途悬浮窗）就保留宿主。规则要有东西读，才值得留一个前台服务。
            if (gestureEnabledSnapshot &&
                (settings.enabled || settings.notificationCapsule.enabled ||
                    needsHostForRecognition || clipFloat?.isShowing() == true ||
                    clipFloatPending)
            ) {
                refreshWindows()
                return START_STICKY
            }
            stopSelf()
            return START_NOT_STICKY
        }
        refreshWindows()
        if (shouldStopSelf()) {
            stopSelf()
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        hunoia.luno.bridge.ui.DensityProvider.invalidateMetrics()
        refreshWindows()
    }

    override fun onDestroy() {
        destroying = true
        currentRef = null
        clipFloat?.detach()
        clipFloat = null
        settingsJob?.cancel()
        imeJob?.cancel()
        imeHintJob?.cancel()
        broadcastObserver?.unregister()
        broadcastObserver = null
        Events.unsubscribe(PanelShownEvent::class, panelShownSubscriber)
        Events.unsubscribe(PanelDismissedEvent::class, panelDismissedSubscriber)
        Events.unsubscribe(TouchButtonSideEvent::class, touchSideSubscriber)
        Events.unsubscribe(ScreenSceneEvent::class, sceneSubscriber)
        FloatBallCommand.requestFollowHandSwap = null
        followHandRetryJob?.cancel()
        followHandRetryJob = null
        pendingFollowHandSide = null
        Events.unsubscribe(FloatBallTempHideEvent::class, tempHideSubscriber)
        RuntimeStateHub.removeObserver(runtimeStateObserver)
        NotificationPulseHub.removePostedObserver(notificationObserver)
        NotificationPulseHub.removeRemovedObserver(notificationRemovedObserver)
        tempHideJob?.cancel()
        tempHideJob = null
        notificationSlot.destroy()
        resultSlot.destroy()
        clipboardPollJob?.cancel()
        clipboardPollJob = null
        visibility.reset()
        hunoia.luno.runtime.surface.SurfaceController.detachAll(
            hunoia.luno.runtime.surface.SurfaceKind.FLOAT_BALL,
        )
        scope.cancel()
        super.onDestroy()
    }

    // ---- 配置 ----

    private fun observeSettings() {
        settingsJob?.cancel()
        settingsJob = scope.launch {
            combine(
                hunoia.luno.domain.floatball.FloatBallSettingsCenter.actionSettings,
                hunoia.luno.domain.floatball.FloatBallSettingsCenter.initialSettings,
            ) { action, initial ->
                FloatBallRuntimeConfig(action, initial.gestureEnabled, initial.buttonVisibility)
            }
                .collect { config ->
                    val newSettings = config.action.floatBall
                    if (newSettings.enabled && !settings.enabled) cancelTempHide()
                    settings = newSettings
                    visibilitySettings = config.buttonVisibility
                    recognition = config.action.recognition
                    recognizer = Recognizer.compile(recognition)
                    gestureEnabledSnapshot = config.gestureEnabled
                    ballWindow.updateSettings(newSettings)
                    notificationSlot.updateSettings()
                    resultSlot.updateSettings()
                    applyInboxSettings()
                    applyNotificationSourceEnabled()
                    applyResultSourceEnabled()
                    refreshWindows()
                    // 悬浮窗正在显示或显示中：不让服务自停（球 / 通知胶囊 / 直执行规则都不要时
                    // 它是唯一宿主，例如只为临时悬浮窗而活的服务）。
                    // startCommandSeen：onCreate 首帧的 combine 同步产出先于 onStartCommand，
                    // 此时 intent 尚未处理（clipFloatPending 未置位），直接判定会误自停拆窗。
                    if (startCommandSeen && shouldStopSelf()) {
                        stopSelf()
                    }
                }
        }
    }

    /**
     * 服务是否已无事可做：手势总闸关，或球 / 通知胶囊都不需要且没有直执行规则，
     * 且没有悬浮窗在显示 / 显示在途。
     *
     * 直执行规则也要算上：它的读取（通知监听 / 剪贴板轮询 / 情景事件）都在本服务里，
     * 如果只挂「球 / 胶囊开关」，会出现「规则明明启用但服务已退出 → 什么都不发生」。
     * 纯胶囊档规则不计：没有窗口也没有自动落地，留着宿主只是空转。
     */
    private fun shouldStopSelf(): Boolean =
        (!gestureEnabledSnapshot ||
            (!settings.enabled && !settings.notificationCapsule.enabled &&
                !needsHostForRecognition)) &&
            clipFloat?.isShowing() != true && !clipFloatPending

    /** 是否有「必须由本服务执行」的规则：直执行档且至少有一个来源在读。 */
    private val needsHostForRecognition: Boolean
        get() = recognition.let { settings ->
            val readsAnySource = settings.notificationReadEnabled ||
                settings.clipboardReadEnabled ||
                settings.sceneEnabled
            readsAnySource && settings.rules.any {
                it.enabled && it.isValid && it.effectiveDisplay == RecognitionDisplay.Direct
            }
        }

    /** refreshWindows 重入保护。 */
    private var refreshingWindows = false

    /** 两条队列的 TTL/容量落到 reducer 配置；变化后重排清理。 */
    private fun applyInboxSettings() {
        val shared = settings.inbox.capacity
        val notificationNext = BallInboxConfig(
            notificationTtlMs = settings.inbox.notificationTtlMs,
            resultTtlMs = settings.inbox.clipboardTtlMs,
            maxItems = settings.inbox.notificationCapacity ?: shared,
        )
        val resultNext = BallInboxConfig(
            notificationTtlMs = settings.inbox.notificationTtlMs,
            resultTtlMs = settings.inbox.clipboardTtlMs,
            maxItems = settings.inbox.resultCapacity ?: shared,
        )
        notificationSlot.applyConfig(notificationNext)
        resultSlot.applyConfig(resultNext)
    }

    private fun applyNotificationSourceEnabled() {
        if (!gestureEnabledSnapshot || !settings.notificationCapsule.enabled) {
            notificationSlot.clear()
        }
    }

    private fun applyResultSourceEnabled() {
        // 结果胶囊没有自己的开关：来源读取与规则表决定它会不会有内容。
        // 这里只负责「读不到 / 关掉了」时的清理：不再读剪贴板就别把旧值留在屏上。
        if (!gestureEnabledSnapshot || !recognition.clipboardReadEnabled) {
            lastSeenClip = null
            resultSlot.clear()
        }
        if (!recognition.sceneEnabled) {
            // 情景识别关掉：清掉界面上留下的情景结果，剪贴板来源的项保留。
            resultSlot.replaceScene(null, emptyList(), makeCurrent = false)
        }
        ensureClipboardPoller()
    }

    /** 统一的窗口挂/摘入口。 */
    private fun refreshWindows() {
        if (refreshingWindows) return
        refreshingWindows = true
        try {
            refreshWindowsLocked()
        } finally {
            refreshingWindows = false
        }
    }

    private fun refreshWindowsLocked() {
        if (syncOrientation()) updateBubbleAnchors()
        ensureClipboardPoller()

        val sceneAllowsBall = canDrawOverlays() && sceneAllows(VisibilityTargets.FLOAT_BALL)
        val persistentVisible = gestureEnabledSnapshot && settings.enabled && sceneAllowsBall
        val panelHidden = visibility.isPanelHidden
        val tempHidden = visibility.isTempHidden()
        val shouldShow = persistentVisible && !panelHidden && !tempHidden && !softHidden
        if (!shouldShow) {
            if (ballVisible) {
                if (persistentVisible) {
                    ballWindow.setPanelHidden(true)
                } else {
                    ballVisible = false
                    ballWindow.detach(animated = true)
                }
            }
        } else if (!ballVisible) {
            ballVisible = ballWindow.attachAnimated()
            if (ballVisible) {
                ballWindow.setHidden(isScreenLocked())
                resetImePushConfirmation()
                ensureImeObserver()
            }
        } else {
            ballWindow.setPanelHidden(false)
        }

        refreshSlotWindows(panelHidden || tempHidden || softHidden)
    }

    private fun refreshSlotWindows(globalHidden: Boolean) {
        for (slot in contentSlots()) {
            val baseVisible = gestureEnabledSnapshot &&
                canDrawOverlays() &&
                slot.enabled &&
                sceneAllows(slot.visibilityTarget) &&
                slot.state.items.isNotEmpty()
            val shouldShow = baseVisible && !globalHidden
            if (!shouldShow) {
                slot.hideCapsule()
                if (slot.visible) {
                    if (baseVisible) {
                        slot.ball.setPanelHidden(true)
                    } else {
                        slot.visible = false
                        slot.ball.detach(animated = true)
                    }
                }
                continue
            }
            if (!slot.visible) {
                slot.visible = slot.ball.attachAnimated()
                if (slot.visible) slot.ball.setHidden(isScreenLocked())
            } else {
                slot.ball.setPanelHidden(false)
            }
            slot.refreshBadge()
        }
    }

    private fun contentSlots(): List<CapsuleSlot> = listOf(notificationSlot, resultSlot)

    private fun detachContentWindows() {
        notificationSlot.detachWindows()
        resultSlot.detachWindows()
    }

    // ---- 面板联动（面板打开即隐藏球） ----

    private val panelShownSubscriber: (PanelShownEvent) -> Unit = {
        visibility.onPanelShown()
        // 悬浮窗和球一样给面板让位：面板是当前操作面，贴着它的小卡会挡手。
        clipFloat?.softHide()
        refreshWindows()
    }

    private val panelDismissedSubscriber: (PanelDismissedEvent) -> Unit = {
        visibility.onPanelDismissed()
        clipFloat?.restore()
        refreshWindows()
    }

    private fun observePanels() {
        Events.subscribe(PanelShownEvent::class, panelShownSubscriber)
        Events.subscribe(PanelDismissedEvent::class, panelDismissedSubscriber)
        Events.subscribe(FloatBallTempHideEvent::class, tempHideSubscriber)
    }

    // ---- 临时隐藏 ----

    private val tempHideSubscriber: (FloatBallTempHideEvent) -> Unit = { event ->
        scheduleTempHide(event.durationMs)
    }

    private fun scheduleTempHide(durationMs: Long) {
        if (!visibility.applyTempHide(durationMs)) return
        tempHideJob?.cancel()
        tempHideJob = scope.launch {
            delay(visibility.tempHideRemainingMs())
            visibility.cancelTempHide()
            seedClipboardBaseline()
            refreshWindows()
        }
        refreshWindows()
    }

    private fun cancelTempHide() {
        if (!visibility.hasTempHide) return
        tempHideJob?.cancel()
        tempHideJob = null
        visibility.cancelTempHide()
        refreshWindows()
    }

    // ---- 通知 ----

    private val notificationObserver: (NotificationPulse) -> Unit = { pulse ->
        scope.launch { onNotificationArrived(pulse) }
    }

    /**
     * 「只显示胶囊」清掉的系统通知 key 集合。
     *
     * 开启 `notificationCapsule.hideSystemNotification` 后，进入通知胶囊的系统通知会被我们
     * cancel/snooze，这必然触发一次 [notificationRemovedObserver] —— 那是**自己的副作用**，
     * 不是用户滑掉，收件箱项必须保留（胶囊仍要显示它）。此集合记录这类 key，供移除回调
     * 辨认；真实移除 / 用户滑掉不在此列，照常从收件箱摘项。
     */
    private val capsuleHiddenKeys = LinkedHashMap<String, Boolean>()

    private fun rememberCapsuleHidden(key: String) {
        capsuleHiddenKeys[key] = true
        // 兜底防增长：cancel/snooze 后系统没回移除事件时 key 会滞留，只保留最近一批。
        while (capsuleHiddenKeys.size > CAPSULE_HIDDEN_MAX) {
            capsuleHiddenKeys.remove(capsuleHiddenKeys.keys.first())
        }
    }

    /** [key] 是否是我们自己藏起的；命中则摘记并返回 true（调用方跳过收件箱移除）。 */
    private fun forgetCapsuleHidden(key: String): Boolean = capsuleHiddenKeys.remove(key) == true

    /**
     * 瞬态浮层共用门控：悬浮窗权限 / 总闸 / 非锁屏 / 非面板占用。
     */
    private fun transientOverlayAllowed(): Boolean =
        canDrawOverlays() &&
            gestureEnabledSnapshot &&
            !isScreenLocked() &&
            !visibility.isPanelHidden

    private fun sceneAllows(targetId: String): Boolean = shouldShowSurface(
        targetId = targetId,
        visibility = visibilitySettings,
        runtimeState = RuntimeStateHub.current(),
    )

    private fun onNotificationArrived(pulse: NotificationPulse) {
        if (!gestureEnabledSnapshot) return
        // 通知胶囊只管「未命中的普通通知」；规则读不读通知是另一个轴（notificationReadEnabled）。
        if (!settings.notificationCapsule.enabled && !recognition.notificationReadEnabled) return
        // 黑名单同时屏蔽通知胶囊和该应用的规则结果。
        if (pulse.packageName in settings.notificationCapsule.blocklist) return
        // 只弹「通知栏里真的能看到」的普通通知。
        val blockedFlags = Notification.FLAG_ONGOING_EVENT or
            Notification.FLAG_FOREGROUND_SERVICE or
            Notification.FLAG_GROUP_SUMMARY or
            Notification.FLAG_LOCAL_ONLY
        val shadeVisible = !pulse.ongoing &&
            pulse.clearable &&
            (pulse.flags and blockedFlags) == 0 &&
            pulse.importance != NotificationManager.IMPORTANCE_NONE
        if (!shadeVisible) {
            // 通知正文是隐私（验证码/私聊）：日志只留判定所需的元数据，不落 title/text。
            Log.i(TAG, "capsule skip pkg=${pulse.packageName} flags=0x${pulse.flags.toString(16)} " +
                "ongoing=${pulse.ongoing} clearable=${pulse.clearable} imp=${pulse.importance}")
            return
        }
        val hidden = hideSystemNotificationEarly(pulse)
        val settleKey = pulse.key ?: "${pulse.packageName}:${pulse.title}"
        if (notificationSlot.settleJobs.size >= SETTLE_JOBS_MAX) {
            Log.w(TAG, "settle jobs overflow, skip settle pkg=${pulse.packageName}")
            scope.launch { routePulse(pulse, preHidden = hidden) }
            return
        }
        notificationSlot.settleJobs.remove(settleKey)?.cancel()
        notificationSlot.settleJobs[settleKey] = scope.launch {
            delay(CAPSULE_SETTLE_MS)
            notificationSlot.settleJobs.remove(settleKey)
            if (!gestureEnabledSnapshot) return@launch
            // 我们自己藏起的那条 isPosted 必然是 false（是我们 cancel 的），
            // 跳过守卫，别把本该显示的胶囊项自己掐了。
            if (!hidden && !NotificationPulseListener.isPosted(pulse.key)) {
                Log.i(TAG, "capsule skip gone pkg=${pulse.packageName} key=${pulse.key}")
                return@launch
            }
            // settle 窗口内通知可能被原地改写（发送中 → 已发送）：按最新文案路由，
            // 取不到（自家通知无 key / 监听器未连接）时回退到捕获时的快照。
            val latest = NotificationPulseListener.currentPulse(pulse.key) ?: pulse
            Log.i(TAG, "capsule show pkg=${latest.packageName} flags=0x${latest.flags.toString(16)} " +
                "imp=${latest.importance}")
            routePulse(latest, preHidden = hidden)
        }
    }

    /**
     * 「不留在通知栏」的两条路：命中分发规则的通知、以及进通知胶囊的普通通知。
     *
     * 两个开关各自独立，任一打开就对它管的那一类通知立即取消 —— 放在 settle **之前**：
     * 隐藏是单向前置动作，settle 只负责取「发送中 → 已发送」的终态文案，两者互不依赖。
     * 藏在 settle 里等于把通知栏里那条白白多留一个 settle 窗。
     */
    private fun hideSystemNotificationEarly(pulse: NotificationPulse): Boolean {
        val ruleHide = recognition.hideMatchedNotification
        val capsuleHide = settings.notificationCapsule.hideSystemNotification &&
            settings.notificationCapsule.enabled
        // 两个都关时连匹配都不跑，别为没人关心的判定多付一次正则扫描。
        if (!ruleHide && !capsuleHide) return false
        val matched = matchesRule(pulse)
        val hide = when {
            // 命中规则的那条由规则的开关说了算（直执行档也一起藏，通知栏不留）。
            ruleHide && matched -> true
            // 未命中：交给通知胶囊那只；刚滑掉过的同一条不动。
            capsuleHide && !matched && !notificationSlot.isDismissed(pulse.key) -> true
            else -> false
        }
        if (hide) hideSystemNotification(pulse.key)
        return hide
    }

    /** 这条通知会不会命中分发规则。纯匹配，不执行动作、不产生任何投递副作用。 */
    private fun matchesRule(pulse: NotificationPulse): Boolean =
        recognition.notificationReadEnabled &&
            ResultFactory.matchNotification(
                pulse = pulse,
                recognizer = recognizer,
                sourceKey = PulseRouter.notificationKey(pulse),
            ).isNotEmpty()

    /**
     * 通知的去路判定在 [PulseRouter]（纯函数、可单测）；这里只负责跑规则（含直执行）、
     * 查抑制表与入队。规则匹配与直执行含后台切线程 / 执行动作，仍留在本方法。
     */
    private suspend fun routePulse(pulse: NotificationPulse, preHidden: Boolean = false) {
        val key = PulseRouter.notificationKey(pulse)
        val now = SystemClock.elapsedRealtime()
        val delivered = if (recognition.notificationReadEnabled) {
            withContext(Dispatchers.Default) {
                deliverResults(
                    items = ResultFactory.matchNotification(
                        pulse = pulse,
                        recognizer = recognizer,
                        sourceKey = key,
                    ),
                    sourceKey = key,
                    nowMs = now,
                    settings = recognition,
                    delivery = delivery,
                    executor = recognitionExecutor,
                )
            }.also { announceDirect(it) }
        } else {
            DeliveredResults.Empty
        }
        val route = PulseRouter.route(
            pulse = pulse,
            resultItems = delivered.capsule,
            matchedRules = delivered.matchedAnything,
            notificationCapsuleEnabled = settings.notificationCapsule.enabled,
            nowMs = now,
        )
        // 「接管」＝有地方替这条通知显示：进通知胶囊，或规则自己那条路（开关开着时）。
        // 都没接管就得还原回通知栏 —— settle 里开关被关、规则开关没开、或这条被滑掉，都不该被吃掉。
        var keepHidden = false
        when (route) {
            is PulseRoute.Result -> {
                if (route.items.isNotEmpty() && !resultSlot.isDismissed(key)) {
                    resultSlot.enqueueAll(route.items, makeCurrent = !resultSlot.manualSession)
                    route.items.forEach(::resolveResultTarget)
                }
                keepHidden = recognition.hideMatchedNotification
            }

            is PulseRoute.Notification -> if (!notificationSlot.isDismissed(pulse.key)) {
                notificationSlot.enqueue(route.item, makeCurrent = !notificationSlot.manualSession)
                keepHidden = true
            }

            PulseRoute.Drop -> Unit
        }
        if (preHidden && !keepHidden) restoreHiddenNotification(pulse.key)
    }

    /** 「只显示胶囊」：把进入胶囊的通知从系统通知栏清掉；不可清除（常驻）回退为藏起。 */
    private fun hideSystemNotification(key: String?) {
        if (key.isNullOrBlank()) return
        val hid = NotificationPulseListener.cancelPosted(key) ||
            NotificationPulseListener.hidePosted(key)
        if (hid) rememberCapsuleHidden(key)
    }

    /** 藏起之后没有胶囊显示它（改归规则那条路 / 开关关了 / 被滑掉）：还原回通知栏并撤掉认领。 */
    private suspend fun restoreHiddenNotification(key: String?) {
        if (key.isNullOrBlank()) return
        capsuleHiddenKeys.remove(key)
        if (!NotificationPulseListener.unhidePosted(key)) {
            Log.w(TAG, "capsule unhide failed key=$key")
        }
    }

    /** 系统通知被移除 → 通知队列同步移除。 */
    private val notificationRemovedObserver: (NotificationPulse) -> Unit = { pulse ->
        // 观察者在 binder 线程回调，槽位状态 / 窗口操作都必须在主线程。
        scope.launch { onNotificationRemovedFromSystem(pulse) }
    }

    private fun onNotificationRemovedFromSystem(pulse: NotificationPulse) {
        if (!gestureEnabledSnapshot) return
        val key = pulse.key ?: return
        // 「只显示胶囊」清掉的通知会触发本回调：这是自己的副作用，收件箱项保留（胶囊仍显示）。
        // 真实移除（应用取消、用户清通知）不在集合里，照常同步摘项。
        if (forgetCapsuleHidden(key)) return
        notificationSlot.settleJobs.remove(key)?.cancel()
        notificationSlot.removeById("notif:$key")
    }

    // ---- 剪贴板（规则来源） ----

    private suspend fun readClip(): ClipboardContent? = ClipboardCenter.readCurrent()

    /**
     * 剪贴板变化：跑一遍规则表，胶囊档进结果胶囊，直执行档当场落地。
     * 返回值 = 这次变化已处理（可推进已读基线）。
     */
    private suspend fun onClipboardContent(content: ClipboardContent): Boolean {
        if (!gestureEnabledSnapshot || !recognition.clipboardReadEnabled) return false
        if (content !is ClipboardContent.Text) return true
        if (content.text.isBlank()) return true
        if (SelfWriteGuard.matches(content)) {
            Log.i(TAG, "clipboard self-write suppressed (text, ${content.text.length} chars)")
            return true
        }
        val now = SystemClock.elapsedRealtime()
        val sourceKey = stableHash(content.text)
        val delivered = withContext(Dispatchers.Default) {
            deliverResults(
                items = ResultFactory.matchClipboard(
                    text = content.text,
                    recognizer = recognizer,
                    sourceKey = sourceKey,
                ),
                sourceKey = sourceKey,
                nowMs = now,
                settings = recognition,
                delivery = delivery,
                executor = recognitionExecutor,
            )
        }
        announceDirect(delivered)
        if (delivered.capsule.isEmpty()) return true
        if (resultSlot.isDismissed(sourceKey)) return true
        resultSlot.enqueueAll(delivered.capsule, makeCurrent = !resultSlot.manualSession)
        delivered.capsule.forEach(::resolveResultTarget)
        return true
    }

    /**
     * 情景识别结果：跑规则 + 可分发链接，整批替换掉上一屏的情景结果项。
     *
     * 文本很长，规则编译是缓存过的，但匹配仍放后台，别卡事件派发线程。
     * 同一屏的抑制沿用结果胶囊的滑掉窗口（10s），与剪贴板来源同口径。
     */
    private fun onScreenScene(event: ScreenSceneEvent) {
        // 本回调跑在情景扫描的 Default 调度器上（见 ScreenSceneScanner.scanDispatcher），
        // 而 resultSlot.state 由主线程读写，所有状态访问都收在主线程。
        scope.launch {
            if (!gestureEnabledSnapshot) return@launch
            if (!recognition.sceneEnabled) return@launch
            if (isScreenLocked()) return@launch
            // 滑掉后 10 秒内不再为同一屏重弹（同一条抑制表，sourceKey 带 scene: 前缀）。
            if (resultSlot.isDismissed("scene:${event.screenKey}")) return@launch
            // 链接补充：这一屏已有结果且已有内置链接项时，只换那一条，不重跑规则 / 不重排队列。
            if (event.refinedUrl != null && resultSlot.hasSceneLinkFor(event.screenKey)) {
                refineSceneLink(event)
                return@launch
            }
            val now = SystemClock.elapsedRealtime()
            scope.launch(sceneLane) {
                val sourceKey = "scene:${event.screenKey}"
                val matched = ResultFactory.matchScene(
                    text = event.text,
                    screenKey = event.screenKey,
                    recognizer = recognizer,
                    sceneUrl = event.refinedUrl,
                )
                val delivered = deliverResults(
                    items = matched,
                    sourceKey = sourceKey,
                    nowMs = now,
                    settings = recognition,
                    delivery = delivery,
                    executor = recognitionExecutor,
                    sceneKey = event.screenKey,
                )
                // 直执行落地在主线程之外先提示（不需要窗口）；胶囊部分回主线程排队。
                withContext(Dispatchers.Main) {
                    announceDirect(delivered)
                    if (!gestureEnabledSnapshot) return@withContext
                    // 同一屏的后续扫描没产出时不要覆盖已有结果：页面还在加载 / 标题栏一过性读不到，
                    // 都会得到空结果，把刚弹出来的卡片抹掉看着就像“时灵时不灵”。
                    if (delivered.capsule.isEmpty() &&
                        resultSlot.hasSceneItemsFor(event.screenKey)
                    ) {
                        return@withContext
                    }
                    resultSlot.replaceScene(
                        scanKey = event.screenKey,
                        items = delivered.capsule,
                        makeCurrent = !resultSlot.manualSession,
                    )
                    delivered.capsule.forEach(::resolveResultTarget)
                }
            }
        }
    }

    /**
     * 首扫之后补到真实链接：同一屏已有结果时，只重算内置链接项并原位替换。
     *
     * 规则命中项与队列顺序都不动 —— 二次推送换的只是链接候选，重跑规则 / DropScene /
     * 窗口重新 layout 全是白付。规则项已经用掉的链接仍然排除，避免同一链接出两条。
     */
    private fun refineSceneLink(event: ScreenSceneEvent) {
        val url = event.refinedUrl ?: return
        scope.launch(sceneLane) {
            val exclude = withContext(Dispatchers.Main) {
                resultSlot.sceneRuleUrlsFor(event.screenKey)
            }
            val now = SystemClock.elapsedRealtime()
            val sourceKey = "scene:${event.screenKey}"
            val item = ResultFactory.sceneLinkItem(
                text = event.text,
                screenKey = event.screenKey,
                recognizer = recognizer,
                sceneUrl = url,
                excludeUrls = exclude,
            ) ?: return@launch
            val delivered = deliverResults(
                items = listOf(item),
                sourceKey = sourceKey,
                nowMs = now,
                settings = recognition,
                delivery = delivery,
                executor = recognitionExecutor,
                sceneKey = event.screenKey,
            )
            if (!gestureEnabledSnapshot) return@launch
            withContext(Dispatchers.Main) {
                announceDirect(delivered)
                delivered.capsule.firstOrNull()?.let { result ->
                    if (resultSlot.replaceSceneLink(event.screenKey, result)) {
                        resolveResultTarget(result)
                    }
                }
            }
        }
    }

    /** 直执行档的落地提示：可关（`RecognitionSettings.directToastEnabled`）。 */
    private fun announceDirect(delivered: DeliveredResults) {
        val first = delivered.executed.firstOrNull() ?: return
        if (!recognition.directToastEnabled) return
        val text = getString(
            R.string.result_direct_toast,
            first.ruleName.ifBlank { getString(R.string.result_generic) },
        )
        scope.launch(Dispatchers.Main) { showToast(text) }
    }

    /** 链接目标应用涉及 PackageManager：放后台解析，当前项已切走则丢弃。 */
    private fun resolveResultTarget(item: ResultItem) {
        if (item.linkCount > 1) return
        val ruleId = item.ruleId ?: return
        scope.launch(Dispatchers.Default) {
            val target = Links.resolvedTargetFor(this@FloatBallService, ruleId, recognition)
                ?: return@launch
            resultSlot.applyTarget(
                itemId = item.id,
                packageName = target.packageName.takeIf { it.isNotBlank() },
                label = target.label,
            )
        }
    }

    private fun seedClipboardBaseline() {
        if (!gestureEnabledSnapshot || !recognition.clipboardReadEnabled || isScreenLocked()) return
        scope.launch {
            val info = readClip() ?: return@launch
            if (info is ClipboardContent.Text && info.text.isBlank()) return@launch
            lastSeenClip = info
        }
    }

    private fun ensureClipboardPoller() {
        // 剪贴板读取许可在规则侧（`RecognitionSettings.clipboardReadEnabled`）：
        // 结果胶囊没有自己的开关，能不能读与能不能显示是两条轴。
        val shouldRun = recognition.clipboardReadEnabled &&
            canDrawOverlays() &&
            gestureEnabledSnapshot &&
            !isScreenLocked()
        if (shouldRun && clipboardPollJob?.isActive != true) {
            clipboardPollJob?.cancel()
            clipboardPollJob = scope.launch {
                while (isActive) {
                    delay(CLIP_POLL_MS)
                    pollClipboard()
                }
            }
        } else if (!shouldRun) {
            clipboardPollJob?.cancel()
            clipboardPollJob = null
        }
    }

    private suspend fun pollClipboard() {
        if (!resultSlot.allowed) return
        val info = readClip()
        if (info == null || (info is ClipboardContent.Text && info.text.isBlank())) return
        val previous = lastSeenClip
        if (previous == null) {
            lastSeenClip = info
            return
        }
        if (previous == info) return
        if (onClipboardContent(info)) lastSeenClip = info
    }

    // ---- 结果动作 ----

    private fun onResultAction(itemId: String, action: ResultAction) {
        val item = resultSlot.state.items.firstOrNull { it.id == itemId } as? ResultItem
            ?: return
        when (action) {
            ResultAction.CLOSE -> Unit
            ResultAction.IGNORE -> resultSlot.removeAndAdvance(itemId)
            ResultAction.COPY -> runResult(item) { it.asCopyOutcome() }
            ResultAction.SHARE -> runResult(item) { it.asShareOutcome() }
            ResultAction.OPEN -> runResult(item) { it.primaryIntent() }
        }
    }

    /**
     * 胶囊动作统一入口：把条目还原成识别结果，交给 [RecognitionExecutor] 落地。
     *
     * 这里不判规则、不判链接、不写链接回退——那些都是识别器与执行口的职责，
     * 胶囊只负责「用户点了哪个动作」。
     */
    private fun runResult(item: ResultItem, transform: (RecognizedItem) -> RecognizedItem) {
        if (item.recognized == null) return
        scope.launch {
            val recognized = transform(item.recognized)
            val ok = recognitionExecutor.execute(
                item = recognized,
                settings = recognition,
                label = item.title.ifBlank { null },
                idempotencyKey = "capsule:${item.id}",
            )
            if (ok) {
                resultSlot.removeAndAdvance(item.id)
                if (recognized.isCopyOutcome()) {
                    resultSlot.onCopied(item)
                    showToast(hunoia.luno.design.R.string.copied)
                }
            } else {
                Log.w(TAG, "result action failed rule=${item.ruleId}")
                if (recognized.isCopyOutcome()) {
                    showToast(hunoia.luno.floatball.R.string.clip_float_copy_failed)
                }
                resultSlot.showCurrent()
            }
        }
    }

    /** 主动作：直接跑规则声明的输出（动作输出也走这里）。 */
    private fun RecognizedItem.primaryIntent(): RecognizedItem = this

    // ---- 通知点按 ----

    private fun openNotificationItem(item: NotificationItem) {
        notificationSlot.hideCapsule()
        scope.launch {
            val contentIntent = NotificationContentIntents.get(item.notificationKey)
            if (contentIntent != null) {
                val sent = runCatching { contentIntent.send() }.isSuccess
                if (sent) {
                    notificationSlot.removeItem(item.id)
                    return@launch
                }
            }
            SurfaceDispatch.dispatch(
                action = AppLaunchAction.stored(packageName = item.packageName),
                origin = hunoia.luno.core.domain.DispatchOrigin.PANEL,
                idempotencyKey = "inbox.notification:${item.id}",
            ) { launched ->
                if (launched) notificationSlot.removeItem(item.id)
                else Log.w(TAG, "capsule app launch failed: package=${item.packageName}")
            }
        }
    }

    // ---- 键盘让位 ----

    private fun observeIme() {
        imeJob?.cancel()
        imeJob = scope.launch {
            while (isActive) {
                delay(IME_POLL_MS)
                if (!ballVisible || visibility.isHidden || softHidden) continue
                pollImeOnce()
            }
        }
    }

    private fun onImeInsetsHinted(imeBottom: Int) {
        if (imeHintJob?.isActive == true) return
        imeHintJob = scope.launch {
            delay(32)
            if (!ballVisible || visibility.isHidden || softHidden) return@launch
            pollImeOnce()
            if (imeBottom > 0) {
                imeHintCount++
                if (!imePushConfirmed && imeHintCount >= IME_PUSH_CONFIRM_HINTS) {
                    imePushConfirmed = true
                    imeJob?.cancel()
                    imeJob = null
                }
            }
        }
    }

    private fun pollImeOnce() {
        val imeBottom = runCatching {
            windowManager?.currentWindowMetrics
                ?.windowInsets
                ?.getInsets(android.view.WindowInsets.Type.ime())
                ?.bottom
        }.getOrNull() ?: 0
        ballWindow.updateIme(imeBottom)
    }

    private fun ensureImeObserver() {
        if (imePushConfirmed) return
        if (imeJob?.isActive == true) return
        if (!ballVisible) return
        observeIme()
    }

    private fun resetImePushConfirmation() {
        imePushConfirmed = false
        imeHintCount = 0
    }

    // ---- 跟随持机手（仅主球） ----

    private fun requestFollowHandSwap(leftSide: Boolean): Boolean {
        if (!settings.followHand || !ballVisible) return false
        if (android.os.Looper.myLooper() != android.os.Looper.getMainLooper()) {
            scope.launch { requestFollowHandSwap(leftSide) }
            return true
        }
        if (leftSide == ballWindow.isOnLeft()) {
            pendingFollowHandSide = null
            return true
        }
        pendingFollowHandSide = leftSide
        tryFollowHandSwap()
        return true
    }

    private fun tryFollowHandSwap() {
        val side = pendingFollowHandSide ?: return
        if (!settings.followHand || !ballVisible) {
            pendingFollowHandSide = null
            return
        }
        if (side == ballWindow.isOnLeft()) {
            pendingFollowHandSide = null
            return
        }
        if (ballWindow.repositionTo(side, followHandAnchorY(side)) { onFollowHandLanded(side) }) {
            followHandRetryJob?.cancel()
            followHandRetryJob = null
        } else {
            scheduleFollowHandRetry()
        }
    }

    private fun followHandAnchorY(side: Boolean): Float {
        val position = settings.position(isLandscapeNow())
        return (if (side) position.leftAnchorY else position.rightAnchorY) ?: position.y
    }

    private fun scheduleFollowHandRetry() {
        if (followHandRetryJob?.isActive == true) return
        followHandRetryJob = scope.launch {
            var attempts = 0
            while (attempts < FOLLOW_HAND_RETRY_MAX) {
                delay(FOLLOW_HAND_RETRY_MS)
                if (pendingFollowHandSide == null) return@launch
                if (!settings.followHand || !ballVisible) {
                    pendingFollowHandSide = null
                    return@launch
                }
                val side = pendingFollowHandSide ?: return@launch
                if (side == ballWindow.isOnLeft()) {
                    pendingFollowHandSide = null
                    return@launch
                }
                if (softHidden) continue
                if (ballWindow.repositionTo(side, followHandAnchorY(side)) { onFollowHandLanded(side) }) return@launch
                attempts++
            }
            pendingFollowHandSide = null
        }
    }

    private fun onFollowHandLanded(side: Boolean) {
        if (pendingFollowHandSide == side) pendingFollowHandSide = null
        val (px, py) = ballWindow.currentPosition()
        persistMainBallPosition(
            px / DensityProvider.screenWidthPx,
            py / DensityProvider.screenHeightPx,
        )
        updateBubbleAnchors()
        if (pendingFollowHandSide != null) scheduleFollowHandRetry()
    }

    private fun persistMainBallPosition(x: Float, y: Float) {
        val left = x < 0.5f
        val landscape = isLandscapeNow()
        settings = settings.withPosition(landscape) { position ->
            position.copy(
                x = x,
                y = y,
                leftAnchorY = if (left) y else position.leftAnchorY,
                rightAnchorY = if (!left) y else position.rightAnchorY,
            )
        }
        ballWindow.updateSettings(settings)
        scope.launch {
            runCatching {
                hunoia.luno.domain.floatball.FloatBallSettingsCenter.updateActionSettings { current ->
                    current.copy(
                        floatBall = current.floatBall.withPosition(landscape) { position ->
                            position.copy(
                                x = x,
                                y = y,
                                leftAnchorY = if (left) y else position.leftAnchorY,
                                rightAnchorY = if (!left) y else position.rightAnchorY,
                            )
                        },
                    )
                }
            }.onFailure { Log.w(TAG, "persist ball position failed: ${it.message}") }
        }
    }

    /**
     * 落盘内容球位置。
     *
     * [side] 为 null 表示手动拖动：按 x 判定左右并写对应高度锚点；
     * 换边落定时显式传入侧别，避免浮点误差把锚点写到另一侧。
     */
    private fun persistContentBallPosition(
        source: BallSource,
        x: Float,
        y: Float,
        side: Boolean? = null,
    ) {
        val landscape = isLandscapeNow()
        val left = side ?: (x < 0.5f)
        val base = when (source) {
            BallSource.NOTIFICATION -> settings.notificationBall(landscape)
            BallSource.RESULT -> settings.resultBall(landscape)
        }
        val updated = base.copy(
            x = x,
            y = y,
            leftAnchorY = if (left) y else base.leftAnchorY,
            rightAnchorY = if (!left) y else base.rightAnchorY,
        )
        settings = if (source == BallSource.NOTIFICATION) {
            settings.copy(
                notificationBallPosition = settings.notificationBallPosition
                    .withPosition(landscape, base) { updated },
            )
        } else {
            settings.copy(
                resultBallPosition = settings.resultBallPosition
                    .withPosition(landscape, base) { updated },
            )
        }
        contentSlots().firstOrNull { it.source == source }?.updateSettings()
        scope.launch {
            runCatching {
                hunoia.luno.domain.floatball.FloatBallSettingsCenter.updateActionSettings { current ->
                    val floatBall = current.floatBall
                    val baseCurrent = when (source) {
                        BallSource.NOTIFICATION -> floatBall.notificationBall(landscape)
                        BallSource.RESULT -> floatBall.resultBall(landscape)
                    }
                    val position = baseCurrent.copy(
                        x = x,
                        y = y,
                        leftAnchorY = if (left) y else baseCurrent.leftAnchorY,
                        rightAnchorY = if (!left) y else baseCurrent.rightAnchorY,
                    )
                    current.copy(
                        floatBall = if (source == BallSource.NOTIFICATION) {
                            floatBall.copy(
                                notificationBallPosition = floatBall.notificationBallPosition
                                    .withPosition(landscape, baseCurrent) { position },
                            )
                        } else {
                            floatBall.copy(
                                resultBallPosition = floatBall.resultBallPosition
                                    .withPosition(landscape, baseCurrent) { position },
                            )
                        },
                    )
                }
            }.onFailure { Log.w(TAG, "persist capsule ball position failed: ${it.message}") }
        }
        updateBubbleAnchors()
    }

    // ---- 屏幕状态 ----

    private fun applyLockState() {
        val hidden = isScreenLocked()
        ballWindow.setHidden(hidden)
        notificationSlot.ball.setHidden(hidden)
        resultSlot.ball.setHidden(hidden)
        if (hidden) {
            closeClipFloat()
            // 不探头、不补播：队列与徽标保留（决策 9）。
            notificationSlot.hideCapsule()
            resultSlot.hideCapsule()
            if (resultSlot.hasUnexpiredSensitive()) resultSlot.pendingSensitivePop = true
        } else {
            ensureImeObserver()
            seedClipboardBaseline()
            if (resultSlot.consumePendingSensitivePop()) resultSlot.showCurrent()
        }
        ensureClipboardPoller()
    }

    private fun isScreenLocked(): Boolean {
        val poweredOff = powerManager?.isInteractive == false
        val locked = runCatching { keyguardManager?.isKeyguardLocked == true }.getOrDefault(false)
        return poweredOff || locked
    }

    private fun isLandscapeNow(): Boolean =
        resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    private fun syncOrientation(): Boolean {
        val landscape = isLandscapeNow()
        var changed = ballWindow.setOrientation(landscape)
        if (notificationSlot.ball.setOrientation(landscape)) changed = true
        if (resultSlot.ball.setOrientation(landscape)) changed = true
        return changed
    }

    private fun updateBubbleAnchors() {
        for (slot in contentSlots()) {
            if (slot.showing) slot.bubble.updateAnchor(slot.bubbleAnchor())
        }
    }

    // ---- 主球点按 ----

    private fun onBallTapped() {
        val action = settings.tapAction ?: return
        dispatchTapAction(action, ballCenterX(), ballCenterY())
    }

    private fun ballCenter(): Pair<Int, Int> {
        ballWindow.visibleRectOnScreen()?.let { return it.centerX to it.centerY }
        val half = DensityProvider.dp2px(settings.sizeDp) / 2
        val (px, py) = ballWindow.currentPosition()
        return (px.roundToInt() + half) to (py.roundToInt() + half)
    }

    private fun ballCenterX(): Int = ballCenter().first
    private fun ballCenterY(): Int = ballCenter().second

    private fun dispatchTapAction(action: StoredAction, anchorX: Int, anchorY: Int) {
        if (action.typeId == GRID_PANEL_TYPE_ID) {
            hunoia.luno.runtime.surface.GestureCommand.showGridPanel?.invoke(action, anchorX, anchorY)
            return
        }
        hunoia.luno.runtime.surface.GestureCommand.forwardBallTap?.invoke(action, anchorX, anchorY, settings.colorSpec)
    }

    // ---- 前台通知 ----

    private fun startInForeground() {
        val manager = getSystemService<NotificationManager>() ?: return
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                getString(R.string.float_ball_foreground_channel),
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                enableLights(false)
                setShowBadge(false)
                lockscreenVisibility = Notification.VISIBILITY_SECRET
            },
        )
        val pendingIntent = PendingIntent.getActivity(
            this, 0, Links.mainActivityIntent(), PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = Notification.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.float_ball_foreground_title))
            .setContentText(getString(R.string.float_ball_foreground_content_desc))
            .setSmallIcon(android.R.drawable.stat_notify_sync)
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
        scope.launch {
            val hiding = runCatching { hunoia.luno.domain.floatball.FloatBallSettingsCenter.getAdvancedSettings() }
                .getOrNull()
                ?.persistentNotificationHiding
                ?: return@launch
            if (hiding.enabled && hiding.hideLunoNotifications) {
                PersistentNotificationShell.snoozeOwn(
                    packageName = packageName,
                    notificationId = NOTIFICATION_ID,
                )
            }
        }
    }

    // ---- 剪贴板悬浮窗 ----

    private fun showClipFloatById(id: String) {
        clipFloatPending = true
        scope.launch {
            try {
                val content = resolveClipFloat(id)
                if (content == null) {
                    Log.w(TAG, "clip float: item not found id=$id")
                    // 条目已被删：无窗可挂；清标志后让宿主按空闲自停，不滞留前台服务。
                    maybeStopIfIdle()
                    return@launch
                }
                attachClipFloat(content)
            } finally {
                clipFloatPending = false
            }
        }
    }

    /**
     * 识屏工具条「悬浮显示」：钉一段**临时内容**（不进历史）。
     *
     * 与按条目钉窗共用同一个窗口与宿主，只是内容来源不同；临时图片落一份缓存副本，
     * 关窗或换内容时删掉（[ClipFloatContent.temporary]）。
     */
    private fun showTransientClipFloat(content: ClipFloatContent) {
        clipFloatPending = true
        scope.launch {
            try {
                attachClipFloat(content)
            } finally {
                clipFloatPending = false
            }
        }
    }

    private suspend fun attachClipFloat(content: ClipFloatContent) {
        val settings = runCatching {
            hunoia.luno.domain.floatball.FloatBallSettingsCenter.getClipFloatSettings()
        }.getOrNull() ?: ClipFloatSettings()
        val window = clipFloat ?: ClipFloatWindow(this@FloatBallService, clipFloatHost).also {
            clipFloat = it
            SurfaceController.register(it)
        }
        window.show(content, settings)
        if (!window.isShowing()) {
            SurfaceController.unregister(window.surfaceId)
            clipFloat = null
        } else if (content.temporary && content.kind == ClipKind.TEXT) {
            // 临时文本可能被粘贴/复制走，但它不是历史条目——提示一句，别让人以为进了历史。
            Log.i(TAG, "clip float: transient content shown")
        }
    }

    /** 临时图片的缓存目录：与剪贴板历史目录分开，关窗即删，绝不进 prune 的保留集。 */
    private fun transientImageDir(): java.io.File =
        java.io.File(cacheDir, TRANSIENT_FLOAT_DIR).apply { mkdirs() }

    private suspend fun writeTransientImage(jpeg: ByteArray): java.io.File? =
        withContext(Dispatchers.IO) {
            runCatching {
                transientImageDir().listFiles()?.forEach { it.delete() }
                java.io.File(transientImageDir(), "float_${System.currentTimeMillis()}.jpg")
                    .apply { writeBytes(jpeg) }
            }.onFailure { Log.w(TAG, "clip float: transient image write failed", it) }.getOrNull()
        }

    private suspend fun resolveClipFloat(id: String): ClipFloatContent? =
        withContext(Dispatchers.IO) {
            ClipboardCenter.history.first().entries.firstOrNull { it.id == id }?.let { entry ->
                ClipFloatContent(
                    id = entry.id,
                    kind = entry.kind,
                    text = entry.text,
                    imagePath = entry.imagePath,
                    mime = entry.mime,
                    imageFile = ClipboardCenter.imageFile(entry.imagePath),
                )
            }
        }

    private fun closeClipFloat() {
        val window = clipFloat ?: return
        window.close()
        if (clipFloat === window) {
            SurfaceController.unregister(window.surfaceId)
            clipFloat = null
        }
    }

    /** 悬浮窗关掉后，若球 / 胶囊都不需要该宿主就退出，避免只为一个临时窗常驻前台服务。 */
    private fun maybeStopIfIdle() {
        if (destroying) return
        scope.launch {
            delay(CLIP_FLOAT_IDLE_STOP_MS)
            if (clipFloat?.isShowing() == true || clipFloatPending) return@launch
            if (!settings.enabled && !settings.notificationCapsule.enabled &&
                !needsHostForRecognition
            ) {
                stopSelf()
            }
        }
    }

    private suspend fun copyClipFloatImage(content: ClipFloatContent): Boolean =
        ClipboardCenter.copyImageFileToClipboard(content.imagePath, content.mime)

    private suspend fun insertAtPoint(content: ClipFloatContent, x: Int, y: Int): Boolean =
        when (content.kind) {
            ClipKind.TEXT -> GestureCommand.insertText?.invoke(content.text, x, y)?.let {
                it == hunoia.luno.action.controller.TextInserter.Result.Inserted ||
                    it == hunoia.luno.action.controller.TextInserter.Result.InsertedViaClipboard
            } ?: false
            ClipKind.IMAGE -> GestureCommand.pasteImageAt?.invoke(
                content.imagePath.orEmpty(),
                content.mime,
                x,
                y,
            ) == hunoia.luno.action.controller.ImageInserter.Result.Pasted
        }

    private val clipFloatHost = object : ClipFloatWindow.Host {
        override fun onSendAt(content: ClipFloatContent, x: Int, y: Int) {
            scope.launch(Dispatchers.IO) {
                delay(CLIP_FLOAT_SETTLE_MS)
                if (!insertAtPoint(content, x, y)) {
                    showToast(hunoia.luno.floatball.R.string.clip_float_no_target)
                }
            }
        }

        override fun onCopy(content: ClipFloatContent) {
            scope.launch(Dispatchers.IO) {
                val ok = when (content.kind) {
                    ClipKind.TEXT -> ClipboardCenter.copy(content.text)
                    ClipKind.IMAGE -> copyClipFloatImage(content)
                }
                showToast(
                    if (ok) {
                        hunoia.luno.floatball.R.string.clip_float_copied
                    } else {
                        hunoia.luno.floatball.R.string.clip_float_copy_failed
                    },
                )
            }
        }

        override fun onClose() {
            clipFloat?.let { SurfaceController.unregister(it.surfaceId) }
            clipFloat = null
            maybeStopIfIdle()
        }

        override fun onSettingsChanged(settings: ClipFloatSettings) {
            if (destroying) return
            scope.launch(Dispatchers.IO) {
                runCatching {
                    hunoia.luno.domain.floatball.FloatBallSettingsCenter
                        .updateClipFloatSettings(settings)
                }
            }
        }
    }

    // ---- 单个内容球 + 胶囊 ----

    private inner class CapsuleSlot(val source: BallSource) {

        lateinit var ball: FloatBallWindow
        lateinit var bubble: SmartBubbleWindow

        var state = BallInboxState()
        var config = BallInboxConfig()
        var showing = false
        var visible = false
        var manualSession = false
        var bubblePressed = false
        var timerJob: Job? = null
        var ttlJob: Job? = null

        /** 通知的 300ms settle 任务（规则结果不走 settle）。 */
        val settleJobs = LinkedHashMap<String, Job>()

        /** 通知滑掉后的同 key 重弹抑制（仅通知）：窗口与开关都来自用户设置。 */
        val notificationSuppression = DismissSuppression(
            nowMs = SystemClock::uptimeMillis,
            windowMs = {
                settings.notificationCapsule.dismissSuppressMs.coerceIn(
                    FloatBallDefaults.CapsuleDismissSuppressMsMin,
                    FloatBallDefaults.CapsuleDismissSuppressMsMax,
                )
            },
            enabled = { settings.notificationCapsule.dismissSuppressEnabled },
        )

        /** 结果项滑掉后的同源重弹抑制（仅结果项）：固定窗口。 */
        val resultSuppression = DismissSuppression(
            nowMs = SystemClock::elapsedRealtime,
            windowMs = { ResultDismissSuppressMs },
        )

        var pendingSensitivePop = false

        /** 跟手换边：球未显示/胶囊展开时的待处理侧别与重试任务。 */
        var pendingFollowHandSide: Boolean? = null
        var followHandRetryJob: Job? = null

        /** 通知球当前显示的应用图标包名；null = 铃铛。 */
        var notificationIconPackage: String? = null
        var notificationIconSizePx = 0
        var notificationIconJob: Job? = null

        /** 有胶囊档结果且没被显隐场景藏起来时就该在；没有开关——结果胶囊是规则结果的呈现面。 */
        val enabled: Boolean
            get() = when (source) {
                BallSource.NOTIFICATION -> settings.notificationCapsule.enabled
                BallSource.RESULT -> recognition.rules.any { it.enabled && it.isValid }
            }

        val followHandEnabled: Boolean
            get() = when (source) {
                BallSource.NOTIFICATION -> settings.notificationCapsule.followHand
                BallSource.RESULT -> settings.resultCapsule.followHand
            }

        val visibilityTarget: String
            get() = when (source) {
                BallSource.NOTIFICATION -> VisibilityTargets.NOTIFICATION_CAPSULE
                BallSource.RESULT -> VisibilityTargets.RESULT_CAPSULE
            }

        val allowed: Boolean
            get() = enabled &&
                transientOverlayAllowed() &&
                !visibility.isTempHidden() &&
                !softHidden &&
                sceneAllows(visibilityTarget)

        fun attachWindows() {
            val isNotification = source == BallSource.NOTIFICATION
            ball = FloatBallWindow(
                context = this@FloatBallService,
                onImeLiftChanged = {},
                onImeInsetsHint = {},
                fixedIcon = if (isNotification) Icons.Default.Notifications else Icons.Default.Lightbulb,
                idleDescription = getString(
                    if (isNotification) R.string.notification_ball_content_desc
                    else R.string.result_ball_content_desc,
                ),
                badgeDescription = {
                    getString(
                        if (isNotification) R.string.notification_ball_unread
                        else R.string.result_ball_unread,
                        it,
                    )
                },
            ).apply {
                onTap = { onContentBallTapped() }
                onDragStarted = { if (showing) hideCapsule() }
                onDragFinished = { x, y -> persistContentBallPosition(source, x, y) }
            }
            bubble = SmartBubbleWindow(this@FloatBallService).apply {
                // 通知胶囊：多条同屏堆叠；结果胶囊保持翻页（用户只关心最新一条）。
                setStackedMode(isNotification)
                onBodyTapped = { id -> onCapsuleTapped(id) }
                onResultAction = { id, action -> onResultAction(id, action) }
                onPageSelected = { id -> onPageSelected(id) }
                onCollapsed = {
                    hideCapsule()
                    refreshBadge()
                }
                onSwipedAway = { id -> onSwipedAway(id) }
                onPressStateChanged = { pressed ->
                    bubblePressed = pressed
                    if (pressed) {
                        timerJob?.cancel()
                        timerJob = null
                    } else if (showing) {
                        scheduleAutoHide()
                    }
                }
                onReady = {
                    if (showing) {
                        ball.setCapsuleActive(true)
                        refreshBadge()
                    }
                }
                onUnavailable = {
                    if (showing) {
                        Log.w(TAG, "capsule compose unavailable, collapsing to ball")
                        hideCapsule()
                    }
                }
            }
            updateSettings()
        }

        /** 合成内容球设置：固定图标、无单击动作；跟手 / 图标 / 静置按各自配置。 */
        fun updateSettings() {
            val base = settings
            val portrait = when (source) {
                BallSource.NOTIFICATION -> base.notificationBall(landscape = false)
                BallSource.RESULT -> base.resultBall(landscape = false)
            }
            val landscapePosition = when (source) {
                BallSource.NOTIFICATION -> base.notificationBall(landscape = true)
                BallSource.RESULT -> base.resultBall(landscape = true)
            }
            // 图标与静置都按球各取各的：内容球不继承主球配置。
            val icon = when (source) {
                BallSource.NOTIFICATION -> base.notificationCapsule.customIcon
                BallSource.RESULT -> base.resultCapsule.customIcon
            }
            val idleDimMs = when (source) {
                BallSource.NOTIFICATION -> base.notificationCapsule.idleDimDelayMs
                BallSource.RESULT -> base.resultCapsule.idleDimDelayMs
            }
            val synthesized = base.copy(
                enabled = true,
                tapAction = null,
                idleDimDelayMs = idleDimMs,
                followHand = followHandEnabled,
                portrait = portrait,
                landscape = landscapePosition,
                customIconPath = icon.path,
                customIconScale = icon.scale,
                customIconOffsetX = icon.offsetX,
                customIconOffsetY = icon.offsetY,
                customIconAlpha = icon.alpha,
                // 内容球不提供「静置暂停动图」开关；保持动画播放。
                customIconPauseWhenIdle = false,
            )
            ball.updateSettings(synthesized)
            bubble.updateSettings(base)
            val iconSize = DensityProvider.dp2px(base.sizeDp)
            if (source == BallSource.NOTIFICATION && iconSize != notificationIconSizePx) {
                notificationIconSizePx = iconSize
                refreshNotificationIcon(force = true)
            }
        }

        fun applyConfig(next: BallInboxConfig) {
            if (next == config) return
            config = next
            val trimmed = BallInboxReducer.reduce(
                state,
                BallInboxAction.Trim,
                SystemClock.elapsedRealtime(),
                config,
            )
            if (trimmed.changed) state = trimmed.state
            scheduleTtl()
            refreshBadge()
        }

        /** 通知球图标跟队列最新一条走；[force] 用于球大小变化后按新尺寸重取。 */
        fun refreshNotificationIcon(force: Boolean = false) {
            if (source != BallSource.NOTIFICATION) return
            val pkg = (state.items.firstOrNull() as? NotificationItem)?.packageName
            if (!force && pkg == notificationIconPackage && notificationIconJob?.isActive != true) {
                return
            }
            notificationIconPackage = pkg
            notificationIconJob?.cancel()
            notificationIconJob = null
            if (pkg == null) {
                ball.setFixedIcon(Icons.Default.Notifications)
                return
            }
            val sizePx = notificationIconSizePx.takeIf { it > 0 }
                ?: DensityProvider.dp2px(settings.sizeDp)
            notificationIconJob = scope.launch(Dispatchers.IO) {
                SearchIconCache.ensureAll(
                    this@FloatBallService,
                    listOf(SearchIconCache.Key(pkg)),
                    sizePx,
                )
                val bitmap = SearchIconCache.get(pkg, sizePx)
                withContext(Dispatchers.Main.immediate) {
                    if (notificationIconPackage == pkg) {
                        ball.setFixedIcon(bitmap ?: Icons.Default.Notifications)
                    }
                }
            }
        }

        // ---- 跟手换边（每个内容球独立） ----

        /**
         * 请求换到 [leftSide] 侧。[showing] 期间只挂起，收胶囊时结算；
         * 球未显示时直接改落盘侧别。返回是否接收（跟手关闭 / 来源禁用 = false）。
         */
        fun requestFollowHandSwap(leftSide: Boolean): Boolean {
            if (!followHandEnabled || !enabled) return false
            if (android.os.Looper.myLooper() != android.os.Looper.getMainLooper()) {
                scope.launch { requestFollowHandSwap(leftSide) }
                return true
            }
            pendingFollowHandSide = leftSide
            tryFollowHandSwap()
            return true
        }

        private fun tryFollowHandSwap() {
            val side = pendingFollowHandSide ?: return
            if (!followHandEnabled || !enabled) {
                pendingFollowHandSide = null
                return
            }
            // 胶囊展开时先别把球从手指下挪走；收起时再结算。
            if (showing) return
            if (side == currentSide()) {
                pendingFollowHandSide = null
                return
            }
            // 锁屏/未挂窗时不做传送动画：直接改落盘侧别，下一次出现就在手势侧。
            if (!visible || isScreenLocked()) {
                persistContentBallSide(side)
                pendingFollowHandSide = null
                return
            }
            if (ball.repositionTo(side, followHandAnchorY(side)) { onFollowHandLanded(side) }) {
                followHandRetryJob?.cancel()
                followHandRetryJob = null
            } else {
                scheduleFollowHandRetry()
            }
        }

        private fun scheduleFollowHandRetry() {
            if (followHandRetryJob?.isActive == true) return
            followHandRetryJob = scope.launch {
                var attempts = 0
                while (attempts < FOLLOW_HAND_RETRY_MAX) {
                    delay(FOLLOW_HAND_RETRY_MS)
                    if (pendingFollowHandSide == null) return@launch
                    if (!followHandEnabled || !enabled) {
                        pendingFollowHandSide = null
                        return@launch
                    }
                    if (showing) return@launch
                    val side = pendingFollowHandSide ?: return@launch
                    if (side == currentSide()) {
                        pendingFollowHandSide = null
                        return@launch
                    }
                    if (!visible || isScreenLocked()) {
                        persistContentBallSide(side)
                        pendingFollowHandSide = null
                        return@launch
                    }
                    if (ball.repositionTo(side, followHandAnchorY(side)) {
                            onFollowHandLanded(side)
                        }
                    ) {
                        return@launch
                    }
                    attempts++
                }
                pendingFollowHandSide = null
            }
        }

        private fun currentSide(): Boolean {
            if (visible) return ball.isOnLeft()
            return savedBallPosition().x < 0.5f
        }

        private fun savedBallPosition() = when (source) {
            BallSource.NOTIFICATION -> settings.notificationBall(isLandscapeNow())
            BallSource.RESULT -> settings.resultBall(isLandscapeNow())
        }

        private fun followHandAnchorY(side: Boolean): Float {
            val position = savedBallPosition()
            return (if (side) position.leftAnchorY else position.rightAnchorY) ?: position.y
        }

        private fun onFollowHandLanded(side: Boolean) {
            if (pendingFollowHandSide == side) pendingFollowHandSide = null
            val (px, py) = ball.currentPosition()
            val width = DensityProvider.screenWidthPx
            val height = DensityProvider.screenHeightPx
            if (width <= 0 || height <= 0) return
            persistContentBallPosition(source, px / width, py / height, side)
        }

        /** 球未显示时直接改落盘侧别：下一次出现就在手势侧。 */
        private fun persistContentBallSide(side: Boolean) {
            val width = DensityProvider.screenWidthPx
            val height = DensityProvider.screenHeightPx
            if (width <= 0 || height <= 0) return
            val ballPx = DensityProvider.dp2px(settings.sizeDp)
            val x = BallPlacement.edgeX(side, width, ballPx) / width
            persistContentBallPosition(source, x, followHandAnchorY(side), side)
        }

        /**
         * 批量入队：按传入顺序倒序逐个 reduce，保证第 1 条在最上面。
         * 只在最后 show 一次，避免中间项闪一下。
         */
        fun enqueueAll(items: List<BallItem>, makeCurrent: Boolean) {
            if (items.isEmpty()) return
            for (index in items.indices.reversed()) {
                enqueue(items[index], makeCurrent = makeCurrent && index == 0, show = false)
            }
            showIfNeeded()
        }

        /** 当前收件箱里是否已经有这一屏的情景结果项。 */
        fun hasSceneItemsFor(scanKey: String): Boolean = hasSceneItems(state.items, scanKey)

        /** 这一屏是否已经有一条内置链接项（链接规则命中的情景项）。 */
        fun hasSceneLinkFor(scanKey: String): Boolean = hasBuiltInSceneLink(state.items, scanKey)

        /** 这一屏规则命中项里已用掉的链接：重建链接项时排除，避免同一链接两条。 */
        fun sceneRuleUrlsFor(scanKey: String): Set<String> = usedSceneRuleUrls(state.items, scanKey)

        /**
         * 原位替换这一屏的内置链接项（链接补充专用）。
         *
         * 不动其余项、不动队列顺序；被替换的正好是当前项时，`currentId` 跟着换成新的 id。
         * 返回 false = 这一屏没有链接项，交给完整路径处理。
         */
        fun replaceSceneLink(scanKey: String, item: ResultItem): Boolean {
            val index = builtInSceneLinkIndex(state.items, scanKey)
            if (index < 0) return false
            val old = state.items[index] as ResultItem
            val replacement = item.copy(pinned = old.pinned)
            val items = state.items.toMutableList().also { it[index] = replacement }
            state = state.copy(
                items = items,
                currentId = if (state.currentId == old.id) replacement.id else state.currentId,
            )
            if (showing) bubble.updateItems(state.items, state.currentIndex)
            return true
        }

        /**
         * 情景识别的整批替换：先丢掉别的界面留下的情景项，再入队这一屏的。
         *
         * [scanKey] 是本次扫描的界面指纹 —— 同屏重复推送时自己的项被保留（合并到队首），
         * 不会闪一下；[scanKey] 为 null = 该功能刚关闭，清空全部情景项。
         * 剪贴板 / 通知来源的项不受影响。
         */
        fun replaceScene(scanKey: String?, items: List<BallItem>, makeCurrent: Boolean) {
            val dropped = BallInboxReducer.reduce(
                state,
                BallInboxAction.DropScene(scanKey),
                SystemClock.elapsedRealtime(),
                config,
            )
            state = dropped.state
            if (items.isNotEmpty()) {
                enqueueAll(items, makeCurrent = makeCurrent)
            } else {
                when {
                    state.items.isEmpty() -> {
                        hideCapsule()
                        refreshWindows()
                    }
                    showing -> showCurrent()
                    else -> refreshBadge()
                }
                scheduleTtl()
            }
        }

        fun enqueue(
            item: BallItem,
            makeCurrent: Boolean,
            show: Boolean = true,
        ): BallInboxChange {
            val now = SystemClock.elapsedRealtime()
            val change = BallInboxReducer.reduce(
                state,
                BallInboxAction.Add(item, makeCurrent),
                now,
                config,
            )
            state = change.state
            if (change.duplicate) {
                refreshBadge()
                scheduleTtl()
                if (showing && state.currentId == item.id && !bubblePressed) scheduleAutoHide()
                if (showing) bubble.updateItems(state.items, state.currentIndex)
                return change
            }
            if (change.added == null) {
                refreshBadge()
                scheduleTtl()
                return change
            }
            // 规则捕获的值（非链接）锁屏时不自动弹，解锁后补播。
            if (item is ResultItem && item.fromCapture && isScreenLocked()) {
                pendingSensitivePop = true
            }
            if (manualSession || !allowed) {
                if (showing && manualSession) {
                    bubble.updateItems(state.items, state.currentIndex)
                }
                refreshBadge()
                scheduleTtl()
                return change
            }
            if (show) showIfNeeded()
            return change
        }

        /** 有内容且不在手动会话时，保证胶囊处于展示态。 */
        private fun showIfNeeded() {
            if (state.current == null || manualSession || !allowed) return
            showCurrent()
        }

        fun showCurrent() {
            val item = state.current
            if (item == null || !allowed) {
                hideCapsule()
                refreshBadge()
                return
            }
            if (!visible) refreshWindows()
            ball.setIdleSuppressed(true)
            if (!bubble.show(
                    items = state.items,
                    index = state.currentIndex.coerceAtLeast(0),
                    anchor = bubbleAnchor(),
                    originY = ball.lpOriginYPx(),
                )
            ) {
                ball.setIdleSuppressed(false)
                showing = false
                refreshBadge()
                return
            }
            showing = true
            refreshBadge()
            scheduleAutoHide()
            scheduleTtl()
        }

        fun hideCapsule() {
            manualSession = false
            timerJob?.cancel()
            timerJob = null
            if (!showing) {
                ball.setCapsuleActive(false)
                ball.setIdleSuppressed(false)
                refreshBadge()
                tryFollowHandSwap()
                return
            }
            showing = false
            bubble.hide {
                ball.setCapsuleActive(false)
                ball.setIdleSuppressed(false)
                // 胶囊收起后清掉已过期项：验证码过期即失，不留在球徽标里。
                prune()
                refreshWindows()
                // 收起后才能安全换边；挂起的手势侧别在这里结算。
                tryFollowHandSwap()
            }
        }

        fun refreshBadge() {
            val badge = state.badge
            ball.setBadge(if (showing || !badge.visible) null else badge)
            // 通知球球面跟队列最新一条：所有队列变更最终都会走到这里。
            refreshNotificationIcon()
        }

        fun scheduleAutoHide() {
            val delayMs = when (source) {
                BallSource.NOTIFICATION -> settings.notificationCapsule.autoHideMs
                BallSource.RESULT -> settings.resultCapsule.autoHideMs
            }
            timerJob?.cancel()
            timerJob = scope.launch {
                delay(
                    delayMs.coerceIn(
                        FloatBallDefaults.CapsuleAutoHideMsMin,
                        FloatBallDefaults.CapsuleAutoHideMsMax,
                    ),
                )
                hideCapsule()
            }
        }

        fun scheduleTtl() {
            ttlJob?.cancel()
            ttlJob = null
            val keepId = if (showing) state.currentId else null
            val next = state.nextExpiryAt(SystemClock.elapsedRealtime(), config, keepId) ?: return
            ttlJob = scope.launch {
                delay((next - SystemClock.elapsedRealtime()).coerceAtLeast(0))
                prune()
            }
        }

        private fun prune() {
            val keepId = if (showing) state.currentId else null
            val change = BallInboxReducer.reduce(
                state,
                BallInboxAction.Prune(keepId),
                SystemClock.elapsedRealtime(),
                config,
            )
            state = change.state
            if (change.isEmpty) {
                hideCapsule()
                refreshWindows()
                return
            }
            if (change.currentChanged && showing) showCurrent()
            else refreshBadge()
            scheduleTtl()
        }

        fun clear() {
            if (state.items.isEmpty()) return
            state = BallInboxReducer.reduce(
                state,
                BallInboxAction.ClearSource(source),
                SystemClock.elapsedRealtime(),
                config,
            ).state
            hideCapsule()
            refreshBadge()
            refreshWindows()
        }

        fun removeItem(id: String) {
            val change = BallInboxReducer.reduce(
                state,
                BallInboxAction.Remove(id),
                SystemClock.elapsedRealtime(),
                config,
            )
            state = change.state
            refreshBadge()
            if (state.items.isEmpty()) refreshWindows()
            scheduleTtl()
        }

        fun removeById(id: String) {
            if (state.items.none { it.id == id }) return
            removeItem(id)
            when {
                state.items.isEmpty() -> hideCapsule()
                // 通知是滚动栈：系统侧移除一条时保留滚动位置，别把正在看的列表拉回顶部。
                showing && source == BallSource.NOTIFICATION ->
                    bubble.updateItems(state.items, state.currentIndex)

                showing -> showCurrent()
            }
        }

        fun removeAndAdvance(id: String) {
            val wasLast = state.currentId == id && state.currentIndex == state.items.lastIndex
            val change = BallInboxReducer.reduce(
                state,
                BallInboxAction.Remove(id),
                SystemClock.elapsedRealtime(),
                config,
            )
            state = change.state
            when {
                state.items.isEmpty() -> hideCapsule()
                // 通知胶囊是滚动栈：删一行不收起、不跳回顶部，保留其余行的阅读位置。
                source == BallSource.NOTIFICATION -> {
                    if (showing) bubble.updateItems(state.items, state.currentIndex)
                    refreshBadge()
                }

                wasLast -> hideCapsule()
                showing -> showCurrent()
                else -> refreshBadge()
            }
            scheduleTtl()
            if (state.items.isEmpty()) refreshWindows()
        }

        fun select(id: String) {
            val change = BallInboxReducer.reduce(
                state,
                BallInboxAction.Select(id),
                SystemClock.elapsedRealtime(),
                config,
            )
            if (!change.changed) return
            state = change.state
            manualSession = true
            refreshBadge()
            scheduleAutoHide()
            scheduleTtl()
        }

        fun onPageSelected(id: String) {
            select(id)
        }

        fun onCapsuleTapped(id: String) {
            val item = state.items.firstOrNull { it.id == id } ?: state.current ?: return
            when (item) {
                is NotificationItem -> openNotificationItem(item)
                is ResultItem -> onResultAction(item.id, item.primaryAction())
            }
        }

        /** 内容球点按：展开 / 收起自己的胶囊。 */
        fun onContentBallTapped() {
            if (showing) hideCapsule() else showCurrent()
        }

        fun onSwipedAway(id: String) {
            // 堆叠模式下横滑的是「按下那一行」，不是队列当前项。
            val item = state.items.firstOrNull { it.id == id } ?: state.current ?: return
            if (item.pinned) {
                hideCapsule()
                refreshBadge()
                return
            }
            when (item) {
                is NotificationItem -> {
                    // 已「只显示胶囊」藏起的通知，滑掉时 key 早已离集合；再摘一次防竞态。
                    forgetCapsuleHidden(item.notificationKey)
                    if (!NotificationPulseListener.cancelPosted(item.notificationKey)) {
                        Log.w(TAG, "capsule cancel failed pkg=${item.packageName}")
                        return
                    }
                    notificationSuppression.markDismissed(item.notificationKey)
                }

                is ResultItem -> resultSuppression.markDismissed(item.sourceKey)
            }
            removeAndAdvance(item.id)
        }

        fun onCopied(item: ResultItem) {
            if (state.items.none { it.id == item.id }) return
            bubble.showCopied(item.id)
            scope.launch {
                delay(COPIED_FEEDBACK_MS)
                if (item.fromCapture) {
                    if (state.currentId == item.id) removeAndAdvance(item.id)
                    else if (state.items.any { it.id == item.id }) bubble.clearCopied()
                } else if (state.items.any { it.id == item.id }) {
                    bubble.clearCopied()
                }
            }
        }

        fun applyTarget(itemId: String, packageName: String?, label: String?) {
            val index = state.items.indexOfFirst { it.id == itemId }
            if (index < 0) return
            val item = state.items[index] as? ResultItem ?: return
            if (item.targetPackage == packageName && item.targetLabel == label) return
            val updated = item.copy(targetPackage = packageName, targetLabel = label)
            val items = state.items.toMutableList().also { it[index] = updated }
            state = state.copy(items = items)
            if (showing && state.currentId == itemId) {
                bubble.updateItems(state.items, state.currentIndex)
            }
        }

        fun bubbleAnchor(): BubbleAnchor {
            ball.visibleRectOnScreen()?.let { return it }
            val size = DensityProvider.dp2px(settings.sizeDp)
            val position = when (source) {
                BallSource.NOTIFICATION -> settings.notificationBall(isLandscapeNow())
                BallSource.RESULT -> settings.resultBall(isLandscapeNow())
            }
            val left = (position.x * DensityProvider.screenWidthPx).roundToInt()
            val top = (position.y * DensityProvider.screenHeightPx).roundToInt()
            return BubbleAnchor(left, top, left + size, top + size)
        }

        /** 锁屏期间是否还有未过期的捕获值项：解锁后据此补播。 */
        fun hasUnexpiredSensitive(): Boolean {
            val now = SystemClock.elapsedRealtime()
            return state.items.any { item ->
                item is ResultItem && item.fromCapture &&
                    (now - item.receivedAtElapsedMs) in 0 until item.effectiveTtlMs(config)
            }
        }

        fun consumePendingSensitivePop(): Boolean {
            if (!pendingSensitivePop) return false
            pendingSensitivePop = false
            val now = SystemClock.elapsedRealtime()
            val target = state.items.firstOrNull { item ->
                item is ResultItem && item.fromCapture &&
                    (now - item.receivedAtElapsedMs) in 0 until item.effectiveTtlMs(config)
            } ?: return false
            if (target.id != state.currentId) {
                state = BallInboxReducer.reduce(
                    state,
                    BallInboxAction.Select(target.id),
                    now,
                    config,
                ).state
            }
            return true
        }

        /** 该来源的抑制表是否压住这个 key；窗口、时钟与开关都注入在 [DismissSuppression] 里。 */
        fun isDismissed(key: String?): Boolean = when (source) {
            BallSource.RESULT -> resultSuppression.isSuppressed(key)
            BallSource.NOTIFICATION -> notificationSuppression.isSuppressed(key)
        }

        fun destroy() {
            timerJob?.cancel()
            ttlJob?.cancel()
            settleJobs.values.forEach { it.cancel() }
            settleJobs.clear()
            bubble.detachNow()
            ball.detach()
        }

        /** 长期摘窗（Surface 长态隐藏等）：球与胶囊全摘，队列保留。 */
        fun detachWindows() {
            showing = false
            visible = false
            timerJob?.cancel()
            timerJob = null
            bubble.detachNow()
            ball.setCapsuleActive(false)
            ball.setIdleSuppressed(false)
            ball.detach()
        }
    }

    companion object {
        private const val TAG = LOG_TAG
        private const val CHANNEL_ID = "float_ball"
        private const val NOTIFICATION_ID = 2002
        const val ACTION_STOP = "hunoia.luno.action.STOP_FLOAT_BALL"
        private var currentRef: WeakReference<FloatBallService>? = null

        const val ACTION_SHOW_CLIP_FLOAT = "hunoia.luno.action.SHOW_CLIP_FLOAT"
        private const val EXTRA_CLIP_ID = "clip_float_id"

        /**
         * 识屏工具条「悬浮显示」：跨进程钉一段临时文本 / 临时图片。
         *
         * 宿主可能在主进程（识屏页 ≤ 工具条都在 `:service`，所以通常直接命中进程内调用）；
         * 不在跑时同样不能后台 startForegroundService，只能等宿主起来——这里给用户一个
         * 明确的失败信号（返回 false），由调用方提示，而不是静默什么都不发生。
         */
        fun showTransientText(context: Context, text: String, label: String? = null): Boolean {
            if (text.isBlank()) return false
            val running = currentRef?.get() ?: return false
            running.showTransientClipFloat(
                ClipFloatContent(
                    id = "transient-${System.currentTimeMillis()}",
                    kind = ClipKind.TEXT,
                    text = text,
                    imagePath = null,
                    mime = null,
                    imageFile = null,
                    temporary = true,
                ),
            )
            return true
        }

        fun showTransientImage(context: Context, jpeg: ByteArray, label: String? = null): Boolean {
            if (jpeg.isEmpty()) return false
            val running = currentRef?.get() ?: return false
            running.scope.launch {
                val file = running.writeTransientImage(jpeg) ?: return@launch
                running.showTransientClipFloat(
                    ClipFloatContent(
                        id = "transient-${System.currentTimeMillis()}",
                        kind = ClipKind.IMAGE,
                        text = "",
                        imagePath = file.name,
                        mime = "image/jpeg",
                        imageFile = file,
                        temporary = true,
                    ),
                )
            }
            return true
        }

        /** 面板 / 剪贴板页显式钉一条内容到屏幕上；窗口归本服务，跨进程只传条目 id。 */
        fun showClipFloat(context: Context, entryId: String) {
            // 宿主已在跑（面板 / 球 / 胶囊任一开启）：直接走进程内调用，避免后台再
            // startForegroundService 触发 Android 12+ 的前台服务启动限制。
            currentRef?.get()?.let { running ->
                running.showClipFloatById(entryId)
                return
            }
            runCatching {
                context.startForegroundService(
                    Intent(context, FloatBallService::class.java)
                        .setAction(ACTION_SHOW_CLIP_FLOAT)
                        .putExtra(EXTRA_CLIP_ID, entryId),
                )
            }.onFailure { Log.e(TAG, "show clip float FAILED", it) }
        }

        private const val IME_POLL_MS = 500L
        private const val IME_PUSH_CONFIRM_HINTS = 2
        private const val FOLLOW_HAND_RETRY_MS = 120L
        private const val FOLLOW_HAND_RETRY_MAX = 12
        private const val CLIP_POLL_MS = 2_000L
        private const val CAPSULE_SETTLE_MS = 300L
        private const val SETTLE_JOBS_MAX = 16
        private const val CAPSULE_HIDDEN_MAX = 32
        /** 结果来源的滑掉抑制窗口：与剪贴板 / 情景来源同口径。 */
        private const val ResultDismissSuppressMs = 10_000L

        /**
         * 直执行档的去重窗口：与滑掉抑制同量级。
         *
         * 剪贴板轮询（2s）与情景重扫会反复喂同一段文本；太短会重复执行，太长会让「隔一会儿又
         * 复制同一段」变得没反应。
         */
        private const val ResultDirectDedupWindowMs = 10_000L
        private const val COPIED_FEEDBACK_MS = 1_500L

        /** 悬浮窗关闭后等目标应用重新可交互再插入 / 粘贴。 */
        private const val CLIP_FLOAT_SETTLE_MS = 200L

        /** 临时悬浮内容的缓存目录（识屏「悬浮显示」）。 */
        private const val TRANSIENT_FLOAT_DIR = "float_transient"

        /** 悬浮窗关闭后多久检查宿主是否还有常驻用途。 */
        private const val CLIP_FLOAT_IDLE_STOP_MS = 3_000L

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
    val buttonVisibility: ButtonVisibilitySettings,
)
