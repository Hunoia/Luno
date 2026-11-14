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
import android.net.Uri
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
import hunoia.luno.bridge.system.FileOutput
import hunoia.luno.bridge.system.canDrawOverlays
import hunoia.luno.bridge.system.shareText
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
import hunoia.luno.core.TouchButtonSideEvent
import hunoia.luno.config.model.LinkDispatchSettings
import hunoia.luno.config.model.LinkTapMode
import hunoia.luno.config.model.VisibilityTargets
import hunoia.luno.config.model.withPosition
import hunoia.luno.clipboard.ClipboardCenter
import hunoia.luno.link.Links
import hunoia.luno.link.LinkUrls
import hunoia.luno.runtime.floatball.inbox.BallInboxAction
import hunoia.luno.runtime.floatball.inbox.BallInboxChange
import hunoia.luno.runtime.floatball.inbox.BallInboxConfig
import hunoia.luno.runtime.floatball.inbox.BallInboxReducer
import hunoia.luno.runtime.floatball.inbox.BallInboxState
import hunoia.luno.runtime.floatball.inbox.BallItem
import hunoia.luno.runtime.floatball.inbox.BallSource
import hunoia.luno.runtime.floatball.inbox.NotificationItem
import hunoia.luno.runtime.floatball.inbox.SuggestionItem
import hunoia.luno.runtime.floatball.inbox.effectiveTtlMs
import hunoia.luno.runtime.floatball.inbox.stableHash
import hunoia.luno.runtime.floatball.notification.NotificationContentIntents
import hunoia.luno.runtime.floatball.notification.NotificationPulseListener
import hunoia.luno.runtime.floatball.suggestion.CompiledSuggestionRules
import hunoia.luno.runtime.floatball.suggestion.SuggestionFactory
import hunoia.luno.runtime.floatball.suggestion.SuggestionRuleEngine
import hunoia.luno.ui.icon.SearchIconCache
import hunoia.luno.runtime.notification.NotificationPulseHub
import hunoia.luno.core.domain.NotificationPulse
import hunoia.luno.runtime.GestureRuntimeState
import hunoia.luno.runtime.RuntimeStateHub
import hunoia.luno.runtime.button.shouldShowSurface
import hunoia.luno.runtime.dispatch.ActionDispatch
import hunoia.luno.runtime.environment.BroadcastObserver
import hunoia.luno.runtime.surface.SurfaceDispatch
import hunoia.luno.runtime.surface.SurfaceController
import hunoia.luno.gesture.GestureCommand
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
 * 建议球）由同一个服务托管，通知球和建议球各自展开自己的胶囊。
 */
class FloatBallService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
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
    private lateinit var suggestionSlot: CapsuleSlot

    /** 剪贴板悬浮窗：单窗，显示新条目即替换内容；关闭后置空。 */
    private var clipFloat: ClipFloatWindow? = null
    private var destroying = false
    private var broadcastObserver: BroadcastObserver? = null

    /** 主球是否已真正挂上窗口。false = 未授权或被系统拒绝。 */
    private var ballVisible = false

    /** 轮询兜底：后台读剪贴板对比内容变化；读依赖无障碍豁免，读不到就永远不弹。 */
    private var clipboardPollJob: Job? = null
    private var lastSeenClip: ClipboardContent? = null

    /** 链接分发配置（随 actionSettings 流更新）：建议胶囊识别目标应用时读它。 */
    private var linkDispatch: LinkDispatchSettings = LinkDispatchSettings()

    /** 建议规则编译结果：配置更新时重建，事件到达时只做匹配。 */
    private var suggestionRules: CompiledSuggestionRules =
        SuggestionRuleEngine.compile(emptyList())

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
        val suggestionAccepted = suggestionSlot.requestFollowHandSwap(leftSide)
        mainAccepted || notificationAccepted || suggestionAccepted
    }

    private val touchSideSubscriber: (TouchButtonSideEvent) -> Unit = { event ->
        followHandBroadcast(event.leftSide)
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
        suggestionSlot = CapsuleSlot(BallSource.SUGGESTION).also { it.attachWindows() }
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
                suggestionSlot.ball.setHidden(true)
                notificationSlot.hideCapsule()
                suggestionSlot.hideCapsule()
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
        FloatBallCommand.requestFollowHandSwap = followHandBroadcast
        NotificationPulseHub.addPostedObserver(notificationObserver)
        NotificationPulseHub.addRemovedObserver(notificationRemovedObserver)
        hunoia.luno.runtime.surface.SurfaceController.register(surfaceHandle)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_SHOW_CLIP_FLOAT) {
            val id = intent.getStringExtra(EXTRA_CLIP_ID).orEmpty()
            if (id.isNotBlank()) showClipFloatById(id)
            refreshWindows()
            return START_STICKY
        }
        if (intent?.action == ACTION_STOP) {
            // 总闸关恒停；否则球/通知/建议还有任一想用的功能就保留宿主。
            if (gestureEnabledSnapshot &&
                (settings.enabled || settings.notificationCapsule.enabled ||
                    settings.clipboardBall.enabled || clipFloat?.isShowing() == true)
            ) {
                refreshWindows()
                return START_STICKY
            }
            stopSelf()
            return START_NOT_STICKY
        }
        refreshWindows()
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
        suggestionSlot.destroy()
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
                    linkDispatch = config.action.linkDispatch
                    suggestionRules = SuggestionRuleEngine.compile(newSettings.clipboardBall.rules)
                    gestureEnabledSnapshot = config.gestureEnabled
                    ballWindow.updateSettings(newSettings)
                    notificationSlot.updateSettings()
                    suggestionSlot.updateSettings()
                    applyInboxSettings()
                    applyNotificationSourceEnabled()
                    applySuggestionSourceEnabled()
                    refreshWindows()
                    if (!gestureEnabledSnapshot ||
                        (!settings.enabled && !settings.notificationCapsule.enabled &&
                            !settings.clipboardBall.enabled)
                    ) {
                        stopSelf()
                    }
                }
        }
    }

    /** refreshWindows 重入保护。 */
    private var refreshingWindows = false

    /** 两条队列的 TTL/容量落到 reducer 配置；变化后重排清理。 */
    private fun applyInboxSettings() {
        val shared = settings.inbox.capacity
        val notificationNext = BallInboxConfig(
            notificationTtlMs = settings.inbox.notificationTtlMs,
            suggestionTtlMs = settings.inbox.clipboardTtlMs,
            maxItems = settings.inbox.notificationCapacity ?: shared,
        )
        val suggestionNext = BallInboxConfig(
            notificationTtlMs = settings.inbox.notificationTtlMs,
            suggestionTtlMs = settings.inbox.clipboardTtlMs,
            maxItems = settings.inbox.suggestionCapacity ?: shared,
        )
        notificationSlot.applyConfig(notificationNext)
        suggestionSlot.applyConfig(suggestionNext)
    }

    private fun applyNotificationSourceEnabled() {
        if (!gestureEnabledSnapshot || !settings.notificationCapsule.enabled) {
            notificationSlot.clear()
        }
    }

    private fun applySuggestionSourceEnabled() {
        if (!gestureEnabledSnapshot || !settings.clipboardBall.enabled) {
            lastSeenClip = null
            suggestionSlot.clear()
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

    private fun contentSlots(): List<CapsuleSlot> = listOf(notificationSlot, suggestionSlot)

    private fun detachContentWindows() {
        notificationSlot.detachWindows()
        suggestionSlot.detachWindows()
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
        if (!settings.notificationCapsule.enabled && !settings.clipboardBall.enabled) return
        // 黑名单同时屏蔽通知胶囊和该应用的验证码建议。
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
        val settleKey = pulse.key ?: "${pulse.packageName}:${pulse.title}"
        if (notificationSlot.settleJobs.size >= SETTLE_JOBS_MAX) {
            Log.w(TAG, "settle jobs overflow, skip settle pkg=${pulse.packageName}")
            scope.launch { routePulse(pulse) }
            return
        }
        notificationSlot.settleJobs.remove(settleKey)?.cancel()
        notificationSlot.settleJobs[settleKey] = scope.launch {
            delay(CAPSULE_SETTLE_MS)
            notificationSlot.settleJobs.remove(settleKey)
            if (!gestureEnabledSnapshot) return@launch
            if (!NotificationPulseListener.isPosted(pulse.key)) {
                Log.i(TAG, "capsule skip gone pkg=${pulse.packageName} key=${pulse.key}")
                return@launch
            }
            Log.i(TAG, "capsule show pkg=${pulse.packageName} flags=0x${pulse.flags.toString(16)} " +
                "imp=${pulse.importance}")
            routePulse(pulse)
        }
    }

    /**
     * 规则命中：全部进建议胶囊，且不再进通知胶囊（沿用旧验证码不双弹语义）。
     * 建议胶囊关闭或规则表为空时回退到通知胶囊。
     */
    private suspend fun routePulse(pulse: NotificationPulse) {
        val key = pulse.key ?: "${pulse.packageName}:${pulse.title}"
        val items = if (settings.clipboardBall.enabled && !suggestionRules.isEmpty) {
            withContext(Dispatchers.Default) {
                SuggestionFactory.fromNotification(
                    pulse = pulse,
                    rules = suggestionRules,
                    sourceKey = key,
                    nowMs = SystemClock.elapsedRealtime(),
                )
            }
        } else {
            emptyList()
        }
        if (items.isNotEmpty()) {
            if (!suggestionSlot.isDismissed(key)) {
                suggestionSlot.enqueueAll(items, makeCurrent = !suggestionSlot.manualSession)
            }
            return
        }
        if (!settings.notificationCapsule.enabled) return
        if (notificationSlot.isDismissed(pulse.key)) return
        val item = NotificationItem(
            notificationKey = key,
            packageName = pulse.packageName,
            title = pulse.title,
            text = pulse.text,
            receivedAtElapsedMs = SystemClock.elapsedRealtime(),
        )
        notificationSlot.enqueue(item, makeCurrent = !notificationSlot.manualSession)
    }

    /** 系统通知被移除 → 通知队列同步移除。 */
    private val notificationRemovedObserver: (NotificationPulse) -> Unit = { pulse ->
        onNotificationRemovedFromSystem(pulse)
    }

    private fun onNotificationRemovedFromSystem(pulse: NotificationPulse) {
        if (!gestureEnabledSnapshot) return
        val key = pulse.key ?: return
        notificationSlot.settleJobs.remove(key)?.cancel()
        notificationSlot.removeById("notif:$key")
    }

    // ---- 剪贴板（建议来源） ----

    private suspend fun readClip(): ClipboardContent? = ClipboardCenter.readCurrent()

    /**
     * 剪贴板变化：先跑自定义规则，再补内置链接识别。
     * 返回值 = 这次变化已处理（可推进已读基线）。
     */
    private suspend fun onClipboardContent(content: ClipboardContent): Boolean {
        if (!gestureEnabledSnapshot || !settings.clipboardBall.enabled) return false
        if (content !is ClipboardContent.Text) return true
        if (content.sensitive || content.text.isBlank()) return true
        if (SelfWriteGuard.matches(content)) {
            Log.i(TAG, "clipboard self-write suppressed (text, ${content.text.length} chars)")
            return true
        }
        val now = SystemClock.elapsedRealtime()
        val sourceKey = stableHash(content.text)
        val items = withContext(Dispatchers.Default) {
            SuggestionFactory.fromClipboard(
                text = content.text,
                rules = suggestionRules,
                sourceKey = sourceKey,
                nowMs = now,
            )
        }
        if (items.isEmpty()) return true
        if (suggestionSlot.isDismissed(sourceKey)) return true
        suggestionSlot.enqueueAll(items, makeCurrent = !suggestionSlot.manualSession)
        items.forEach(::resolveSuggestionTarget)
        return true
    }

    /** 链接目标应用涉及 PackageManager：放后台解析，当前项已切走则丢弃。 */
    private fun resolveSuggestionTarget(item: SuggestionItem) {
        val url = item.primaryUrl ?: return
        if (item.linkCount > 1) return
        if (!linkDispatch.enabled) return
        scope.launch(Dispatchers.Default) {
            val rule = LinkUrls.firstMatch(url, linkDispatch.rules) ?: return@launch
            val target = Links.resolvedTargetOf(this@FloatBallService, rule, url) ?: return@launch
            suggestionSlot.applyTarget(
                itemId = item.id,
                packageName = target.packageName.takeIf { it.isNotBlank() },
                label = target.label,
            )
        }
    }

    private fun seedClipboardBaseline() {
        if (!gestureEnabledSnapshot || !settings.clipboardBall.enabled || isScreenLocked()) return
        scope.launch {
            val info = readClip() ?: return@launch
            if (info is ClipboardContent.Text && info.text.isBlank()) return@launch
            lastSeenClip = info
        }
    }

    private fun ensureClipboardPoller() {
        val shouldRun = settings.clipboardBall.enabled &&
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
        if (!suggestionSlot.allowed) return
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

    // ---- 建议动作 ----

    private fun onSuggestionAction(itemId: String, action: SuggestionAction) {
        val item = suggestionSlot.state.items.firstOrNull { it.id == itemId } as? SuggestionItem
            ?: return
        when (action) {
            SuggestionAction.CLOSE -> Unit
            SuggestionAction.IGNORE -> suggestionSlot.removeAndAdvance(itemId)
            SuggestionAction.COPY -> copySuggestion(item)
            SuggestionAction.OPEN -> openSuggestionLink(item)
            SuggestionAction.SHARE -> shareSuggestion(item)
        }
    }

    private fun copySuggestion(item: SuggestionItem) {
        scope.launch(Dispatchers.IO) {
            val value = if (item.ruleId == null) {
                item.primaryUrl ?: item.text
            } else {
                item.text
            }
            val ok = if (item.sensitive) {
                // 敏感 + 不入历史：规则捕获的验证码/取件码只能待在内存剪贴板里。
                ClipboardCenter.write(
                    content = ClipboardContent.Text(value, sensitive = true),
                    label = null,
                    record = false,
                )
            } else {
                ClipboardCenter.write(ClipboardContent.Text(value), label = item.title)
            }
            if (!ok) {
                Log.w(TAG, "suggestion copy failed sensitive=${item.sensitive}")
                showToast(getString(R.string.suggestion_copy_failed))
                return@launch
            }
            suggestionSlot.onCopied(item)
        }
    }

    private fun openSuggestionLink(item: SuggestionItem) {
        val urls = item.urls
        if (urls.isEmpty()) return
        if (urls.size > 1) {
            val launched = runCatching { Links.pickLinks(urls) }
                .onFailure { Log.w(TAG, "link selection launch failed: ${it.message}") }
                .isSuccess
            if (launched) suggestionSlot.removeAndAdvance(item.id)
            return
        }
        val url = urls.first()
        if (!linkDispatch.enabled) {
            openLinkExternally(url) { ok ->
                if (ok) suggestionSlot.removeAndAdvance(item.id)
            }
            return
        }
        val rule = LinkUrls.firstMatch(url, linkDispatch.rules)
        if (rule == null) {
            Links.fallback(this, url, linkDispatch.missAction)
            suggestionSlot.removeAndAdvance(item.id)
            return
        }
        if (linkDispatch.capsuleTap == LinkTapMode.SHARE) {
            shareSuggestion(item)
            return
        }
        scope.launch {
            val resolution = Links.resolve(this@FloatBallService, url, linkDispatch, rule)
            val dispatched = resolution?.let {
                val request = it.request.copy(idempotencyKey = "suggestion.link:${item.id}")
                runCatching { ActionDispatch.dispatchAwait(request) }
                    .onFailure { error -> Log.w(TAG, "link dispatch threw", error) }
                    .getOrDefault(false)
            } ?: false
            if (!dispatched) {
                Log.w(TAG, "link dispatch failed, fallback")
                Links.fallbackFor(this@FloatBallService, url, resolution?.rule ?: rule, linkDispatch)
            }
            if (dispatched) {
                suggestionSlot.removeAndAdvance(item.id)
            } else {
                suggestionSlot.showCurrent()
            }
        }
    }

    /** 「链接分发」关闭时的兜底：直接交给系统 ACTION_VIEW。 */
    private fun openLinkExternally(url: String, onResult: (Boolean) -> Unit) {
        val launched = runCatching {
            startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse(url))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
            true
        }.getOrDefault(false)
        if (!launched) Log.w(TAG, "external link open failed")
        onResult(launched)
    }

    private fun shareSuggestion(item: SuggestionItem) {
        val value = if (item.ruleId == null && item.urls.isNotEmpty()) {
            item.urls.joinToString("\n")
        } else {
            item.text
        }
        if (value.isBlank()) return
        val launched = runCatching {
            val send = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, value)
            }
            startActivity(
                Intent.createChooser(send, null)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
            true
        }.getOrDefault(false)
        if (launched) {
            suggestionSlot.removeAndAdvance(item.id)
        } else {
            Log.w(TAG, "suggestion share launch failed")
        }
    }

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
            BallSource.SUGGESTION -> settings.suggestionBall(landscape)
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
                suggestionBallPosition = settings.suggestionBallPosition
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
                        BallSource.SUGGESTION -> floatBall.suggestionBall(landscape)
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
                                suggestionBallPosition = floatBall.suggestionBallPosition
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
        suggestionSlot.ball.setHidden(hidden)
        if (hidden) {
            closeClipFloat()
            // 不探头、不补播：队列与徽标保留（决策 9）。
            notificationSlot.hideCapsule()
            suggestionSlot.hideCapsule()
            if (suggestionSlot.hasUnexpiredSensitive()) suggestionSlot.pendingSensitivePop = true
        } else {
            ensureImeObserver()
            seedClipboardBaseline()
            if (suggestionSlot.consumePendingSensitivePop()) suggestionSlot.showCurrent()
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
        if (suggestionSlot.ball.setOrientation(landscape)) changed = true
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
            hunoia.luno.gesture.GestureCommand.showGridPanel?.invoke(action, anchorX, anchorY)
            return
        }
        hunoia.luno.gesture.GestureCommand.forwardBallTap?.invoke(action, anchorX, anchorY, settings.colorSpec)
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
        scope.launch {
            val content = resolveClipFloat(id)
            if (content == null) {
                Log.w(TAG, "clip float: item not found id=$id")
                return@launch
            }
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
            }
        }
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
            if (clipFloat?.isShowing() == true) return@launch
            if (!settings.enabled && !settings.notificationCapsule.enabled &&
                !settings.clipboardBall.enabled
            ) {
                stopSelf()
            }
        }
    }

    private suspend fun insertToFocused(content: ClipFloatContent): Boolean = when (content.kind) {
        ClipKind.TEXT -> GestureCommand.insertTextFocused?.invoke(content.text)?.let {
            it == hunoia.luno.action.controller.TextInserter.Result.Inserted ||
                it == hunoia.luno.action.controller.TextInserter.Result.InsertedViaClipboard
        } ?: false
        ClipKind.IMAGE -> copyClipFloatImage(content) &&
            GestureCommand.pasteImageFocused?.invoke() ==
            hunoia.luno.action.controller.ImageInserter.Result.Pasted
    }

    private suspend fun copyClipFloatImage(content: ClipFloatContent): Boolean =
        ClipboardCenter.copyImageFileToClipboard(content.imagePath, content.mime)

    private fun shareClipFloat(content: ClipFloatContent): Boolean = when (content.kind) {
        ClipKind.TEXT -> shareText(this, content.text)
        ClipKind.IMAGE -> content.imageFile?.let { file ->
            FileOutput.share(this, file, content.mime ?: "image/*", file.name)
        } ?: false
    }

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
        override fun onInsert(content: ClipFloatContent) {
            closeClipFloat()
            scope.launch(Dispatchers.IO) {
                delay(CLIP_FLOAT_SETTLE_MS)
                if (!insertToFocused(content)) {
                    showToast(hunoia.luno.floatball.R.string.clip_float_no_target)
                }
            }
        }

        override fun onSend(content: ClipFloatContent) {
            closeClipFloat()
            scope.launch(Dispatchers.IO) {
                delay(CLIP_FLOAT_SETTLE_MS)
                if (!insertToFocused(content) && !shareClipFloat(content)) {
                    showToast(hunoia.luno.floatball.R.string.clip_float_no_target)
                }
            }
        }

        override fun onSendAt(content: ClipFloatContent, x: Int, y: Int) {
            closeClipFloat()
            scope.launch(Dispatchers.IO) {
                delay(CLIP_FLOAT_SETTLE_MS)
                insertAtPoint(content, x, y)
            }
        }

        override fun onCopy(content: ClipFloatContent) {
            closeClipFloat()
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

        /** 通知的 300ms settle 任务（建议不走 settle）。 */
        val settleJobs = LinkedHashMap<String, Job>()

        /** 通知滑掉后的同 key 重弹抑制（仅通知）。 */
        val dismissedKeys = LinkedHashMap<String, Long>()

        /** 建议滑掉后的同源重弹抑制（仅建议）。 */
        val dismissedSuggestionKeys = LinkedHashMap<String, Long>()

        var pendingSensitivePop = false

        /** 跟手换边：球未显示/胶囊展开时的待处理侧别与重试任务。 */
        var pendingFollowHandSide: Boolean? = null
        var followHandRetryJob: Job? = null

        /** 通知球当前显示的应用图标包名；null = 铃铛。 */
        var notificationIconPackage: String? = null
        var notificationIconSizePx = 0
        var notificationIconJob: Job? = null

        val enabled: Boolean
            get() = when (source) {
                BallSource.NOTIFICATION -> settings.notificationCapsule.enabled
                BallSource.SUGGESTION -> settings.clipboardBall.enabled
            }

        val followHandEnabled: Boolean
            get() = when (source) {
                BallSource.NOTIFICATION -> settings.notificationCapsule.followHand
                BallSource.SUGGESTION -> settings.clipboardBall.followHand
            }

        val visibilityTarget: String
            get() = when (source) {
                BallSource.NOTIFICATION -> VisibilityTargets.NOTIFICATION_CAPSULE
                BallSource.SUGGESTION -> VisibilityTargets.CLIPBOARD_BALL
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
                    else R.string.suggestion_ball_content_desc,
                ),
                badgeDescription = {
                    getString(
                        if (isNotification) R.string.notification_ball_unread
                        else R.string.suggestion_ball_unread,
                        it,
                    )
                },
            ).apply {
                onTap = { onContentBallTapped() }
                onLongPress = null
                onDragStarted = { if (showing) hideCapsule() }
                onDragFinished = { x, y -> persistContentBallPosition(source, x, y) }
            }
            bubble = SmartBubbleWindow(this@FloatBallService).apply {
                onBodyTapped = { onCapsuleTapped() }
                onLongPress = {}
                onSuggestionAction = { id, action -> onSuggestionAction(id, action) }
                onPageSelected = { id -> onPageSelected(id) }
                onCollapsed = {
                    hideCapsule()
                    refreshBadge()
                }
                onSwipedAway = { onSwipedAway() }
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

        /** 合成内容球设置：固定图标、无单击动作、无静置变暗；跟手按各自开关。 */
        fun updateSettings() {
            val base = settings
            val portrait = when (source) {
                BallSource.NOTIFICATION -> base.notificationBall(landscape = false)
                BallSource.SUGGESTION -> base.suggestionBall(landscape = false)
            }
            val landscapePosition = when (source) {
                BallSource.NOTIFICATION -> base.notificationBall(landscape = true)
                BallSource.SUGGESTION -> base.suggestionBall(landscape = true)
            }
            val synthesized = base.copy(
                enabled = true,
                tapAction = null,
                idleDimDelayMs = 0L,
                followHand = followHandEnabled,
                portrait = portrait,
                landscape = landscapePosition,
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
            BallSource.SUGGESTION -> settings.suggestionBall(isLandscapeNow())
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
            // 敏感建议（规则捕获的非链接文本）锁屏时不自动弹，解锁后补播。
            if (item is SuggestionItem && item.sensitive && isScreenLocked()) {
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
                BallSource.SUGGESTION -> settings.clipboardBall.autoHideMs
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
            if (state.items.isEmpty()) {
                hideCapsule()
            } else if (showing) {
                showCurrent()
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
                wasLast || state.items.isEmpty() -> hideCapsule()
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

        fun onCapsuleTapped() {
            when (val item = state.current) {
                is NotificationItem -> openNotificationItem(item)
                is SuggestionItem -> onSuggestionAction(item.id, item.primaryAction())
                else -> Unit
            }
        }

        /** 内容球点按：展开 / 收起自己的胶囊。 */
        fun onContentBallTapped() {
            if (showing) hideCapsule() else showCurrent()
        }

        fun onSwipedAway() {
            val item = state.current ?: return
            if (item.pinned) {
                hideCapsule()
                refreshBadge()
                return
            }
            when (item) {
                is NotificationItem -> {
                    if (!NotificationPulseListener.cancelPosted(item.notificationKey)) {
                        Log.w(TAG, "capsule cancel failed pkg=${item.packageName}")
                        return
                    }
                    markDismissed(item.notificationKey)
                }

                is SuggestionItem -> markSuggestionDismissed(item.sourceKey)
            }
            removeAndAdvance(item.id)
        }

        fun onCopied(item: SuggestionItem) {
            if (state.items.none { it.id == item.id }) return
            bubble.showCopied(item.id)
            scope.launch {
                delay(COPIED_FEEDBACK_MS)
                if (item.sensitive) {
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
            val item = state.items[index] as? SuggestionItem ?: return
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
                BallSource.SUGGESTION -> settings.suggestionBall(isLandscapeNow())
            }
            val left = (position.x * DensityProvider.screenWidthPx).roundToInt()
            val top = (position.y * DensityProvider.screenHeightPx).roundToInt()
            return BubbleAnchor(left, top, left + size, top + size)
        }

        /** 锁屏期间是否还有未过期的敏感建议：解锁后据此补播。 */
        fun hasUnexpiredSensitive(): Boolean {
            val now = SystemClock.elapsedRealtime()
            return state.items.any { item ->
                item is SuggestionItem && item.sensitive &&
                    (now - item.receivedAtElapsedMs) in 0 until item.effectiveTtlMs(config)
            }
        }

        fun consumePendingSensitivePop(): Boolean {
            if (!pendingSensitivePop) return false
            pendingSensitivePop = false
            val now = SystemClock.elapsedRealtime()
            val target = state.items.firstOrNull { item ->
                item is SuggestionItem && item.sensitive &&
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

        fun isDismissed(key: String?): Boolean {
            if (key == null) return false
            if (source == BallSource.SUGGESTION) {
                val at = dismissedSuggestionKeys[key] ?: return false
                if (SystemClock.elapsedRealtime() - at < SUGGESTION_DISMISS_SUPPRESS_MS) return true
                dismissedSuggestionKeys.remove(key)
                return false
            }
            val capsule = settings.notificationCapsule
            if (!capsule.dismissSuppressEnabled) return false
            val at = dismissedKeys[key] ?: return false
            val windowMs = capsule.dismissSuppressMs.coerceIn(
                FloatBallDefaults.CapsuleDismissSuppressMsMin,
                FloatBallDefaults.CapsuleDismissSuppressMsMax,
            )
            if (SystemClock.uptimeMillis() - at < windowMs) return true
            dismissedKeys.remove(key)
            return false
        }

        private fun markDismissed(key: String) {
            if (!settings.notificationCapsule.dismissSuppressEnabled) {
                dismissedKeys.remove(key)
                return
            }
            dismissedKeys.remove(key)
            dismissedKeys[key] = SystemClock.uptimeMillis()
            while (dismissedKeys.size > DISMISSED_KEYS_MAX) {
                val oldest = dismissedKeys.keys.firstOrNull() ?: break
                dismissedKeys.remove(oldest)
            }
        }

        private fun markSuggestionDismissed(key: String) {
            dismissedSuggestionKeys.remove(key)
            dismissedSuggestionKeys[key] = SystemClock.elapsedRealtime()
            while (dismissedSuggestionKeys.size > DISMISSED_KEYS_MAX) {
                val oldest = dismissedSuggestionKeys.keys.firstOrNull() ?: break
                dismissedSuggestionKeys.remove(oldest)
            }
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
        private const val DISMISSED_KEYS_MAX = 8
        private const val SUGGESTION_DISMISS_SUPPRESS_MS = 10_000L
        private const val COPIED_FEEDBACK_MS = 1_500L

        /** 悬浮窗关闭后等目标应用重新可交互再插入 / 粘贴。 */
        private const val CLIP_FLOAT_SETTLE_MS = 200L

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
