package hunoia.luno.runtime.floatball

import android.app.KeyguardManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.SystemClock
import android.util.Log
import androidx.core.content.getSystemService
import hunoia.luno.floatball.R
import hunoia.luno.core.domain.AppLaunchAction
import hunoia.luno.core.domain.GRID_PANEL_TYPE_ID
import hunoia.luno.core.domain.StoredAction
import hunoia.luno.bridge.system.ClipboardContent
import hunoia.luno.bridge.system.SelfWriteGuard
import hunoia.luno.bridge.system.canDrawOverlays
import hunoia.luno.bridge.ui.DensityProvider
import hunoia.luno.config.model.ActionSettings
import hunoia.luno.config.model.FloatBallDefaults
import hunoia.luno.config.model.FloatBallSettings
import hunoia.luno.core.Events
import hunoia.luno.core.PanelDismissedEvent
import hunoia.luno.core.PanelShownEvent
import hunoia.luno.core.TouchButtonSideEvent
import hunoia.luno.config.model.LinkDispatchSettings
import hunoia.luno.config.model.LinkTapMode
import hunoia.luno.clipboard.ClipboardCenter
import hunoia.luno.link.Links
import hunoia.luno.link.LinkUrls
import hunoia.luno.runtime.floatball.inbox.BallInboxAction
import hunoia.luno.runtime.floatball.inbox.BallInboxConfig
import hunoia.luno.runtime.floatball.inbox.BallInboxReducer
import hunoia.luno.runtime.floatball.inbox.BallInboxState
import hunoia.luno.runtime.floatball.inbox.BallItem
import hunoia.luno.runtime.floatball.inbox.BallSource
import hunoia.luno.runtime.floatball.inbox.ClipboardImageItem
import hunoia.luno.runtime.floatball.inbox.ClipboardTextItem
import hunoia.luno.runtime.floatball.inbox.NotificationItem
import hunoia.luno.runtime.automation.AutomationNotificationListener
import hunoia.luno.runtime.automation.AutomationPulseHub
import hunoia.luno.runtime.automation.NotificationContentIntents
import hunoia.luno.runtime.condition.NotificationPulse
import hunoia.luno.runtime.dispatch.ActionDispatch
import hunoia.luno.runtime.environment.BroadcastObserver
import hunoia.luno.runtime.surface.SurfaceDispatch
import hunoia.luno.shizuku.ShizukuManager
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

    /** insets 事件触发的合并复测任务。 */
    private var imeHintJob: Job? = null

    private var settings: FloatBallSettings = FloatBallSettings()

    /** 应用手势总闸（combine 出来的 gestureEnabled，总闸关则球与分身都不出）。 */
    private var gestureEnabledSnapshot = true

    private lateinit var ballWindow: FloatBallWindow
    private lateinit var mirrorWindow: FloatBallMirrorWindow
    private lateinit var smartBubble: SmartBubbleWindow
    private var broadcastObserver: BroadcastObserver? = null

    /** 球是否已真正挂上窗口。false = 未授权或被系统拒绝。 */
    private var ballVisible = false

    /** 气泡当前是否在屏（含进场/退场动画期间）。 */
    private var capsuleShowing = false
    private var capsuleTimerJob: Job? = null

    /** 通知 settle 按 key 管理：新通知不再取消上一条还在 300ms 确认期内的通知（plan §4.4）。 */
    private val settleJobs = LinkedHashMap<String, Job>()

    // ---- 统一收件箱（Stage 2：通知 + 剪贴板入队，共享气泡展示）----

    private var inboxState = BallInboxState()
    private var inboxConfig = BallInboxConfig()

    /**
     * 手动浏览会话（§7.5）：长按球打开 / 点计数切换 / 展开全文 / 长按钉住期间置位。
     * 新内容只入队更新徽标，不抢当前项、不探头；气泡隐藏时清除。
     */
    private var manualSession = false

    /** 气泡是否正被按住（重复复制重置计时的门控之一）。 */
    private var bubblePressed = false

    /** 收件箱 TTL 到点 prune 的任务（钉住项/当前展示项免清理，见 [BallInboxState.nextExpiryAt]）。 */
    private var inboxTtlJob: Job? = null

    /**
     * 用户主动滑掉的 key（插入顺序 LRU + 时间窗）：刚滑掉的通知在窗口期内即使原地更新
     * 也不再弹出来（否则「滑掉 -> 更新 -> 又弹」很恼人）。
     */
    private val dismissedCapsuleKeys = LinkedHashMap<String, Long>()

    /** 剪贴板来源开关与门控沿用 clipboardBall 设置；展示统一走共享气泡，无独立窗口态。 */
    private var clipboardListenerRegistered = false

    /**
     * 轮询兜底：HyperOS 不向后台应用投递 OnPrimaryClipChangedListener（实测复制零回调），
     * 亮屏期间每 2s 读一次剪贴板对比内容变化；读依赖无障碍豁免，读不到就永远不弹。
     */
    private var clipboardPollJob: Job? = null
    private var lastSeenClip: ClipboardContent? = null

    /** 链接分发配置（随 actionSettings 流更新）：气泡识别链接、点击直达都读它。 */
    private var linkDispatch: LinkDispatchSettings = LinkDispatchSettings()

    /** Surface 隐藏状态机：面板计数 + 临时隐藏，窗口动作仍由本服务负责。 */
    private val visibility = hunoia.luno.runtime.surface.SurfaceVisibilityState()
    private var tempHideJob: Job? = null

    /** 悬浮球在 SurfaceController 的句柄：短态软隐藏、长态摘窗。 */
    private val surfaceHandle = object : hunoia.luno.runtime.surface.SurfaceHandle {
        override val surfaceId: String = "float-ball"
        override val surfaceKind: hunoia.luno.runtime.surface.SurfaceKind =
            hunoia.luno.runtime.surface.SurfaceKind.FLOAT_BALL

        override fun softHide() {
            ballWindow.setPanelHidden(true)
            mirrorWindow.setPanelHidden(true)
        }

        override fun restore() {
            ballWindow.setPanelHidden(false)
            mirrorWindow.setPanelHidden(false)
        }

        override fun detach() {
            hunoia.luno.runtime.surface.SurfaceController.unregister(surfaceId)
            ballVisible = false
            ballWindow.detach()
            mirrorWindow.detach()
        }
    }

    private val powerManager by lazy { getSystemService<PowerManager>() }
    private val keyguardManager by lazy { getSystemService<KeyguardManager>() }
    private val windowManager by lazy { getSystemService<android.view.WindowManager>() }

    // ---- 跟随持机手（试验） ----

    private val touchSideSubscriber: (TouchButtonSideEvent) -> Unit = { event ->
        onTouchSide(event.leftSide)
    }

    override fun onCreate() {
        super.onCreate()
        ballWindow = FloatBallWindow(
            this,
            onImeLiftChanged = { lift ->
                mirrorWindow.updateImeLift(lift)
                updateBubbleAnchor()
            },
            // 事件 insets 只当触发信号：立即用绝对参考系复测一次（不采信事件值）。
            onImeInsetsHint = ::onImeInsetsHinted,
        ).apply {
            onTap = { onBallTapped() }
            onLongPress = { onBallLongPressed() }
            onDragStarted = { if (capsuleShowing) hideCapsule() }
            onDragFinished = { x, y ->
                updateBubbleAnchor()
                persistBallPosition(x, y)
            }
        }
        mirrorWindow = FloatBallMirrorWindow(this).apply {
            onPressed = { onMirrorPressed() }
            onTapped = { onMirrorTapped() }
            onDragDeltas = { dx, dy -> onMirrorDragDeltas(dx, dy) }
            onDragReleased = { onMirrorDragReleased() }
        }
        smartBubble = SmartBubbleWindow(this).apply {
            onBodyTapped = { onCapsuleTapped() }
            onLongPress = { onBubbleLongPressed() }
            onPageSelected = { id -> onBubblePageSelected(id) }
            onCollapsed = { onCapsuleCollapsed() }
            onSwipedAway = { onCapsuleSwipedAway() }
            // 胶囊首帧真正画出来后再隐藏球；组合失败则收起胶囊，球始终不会被单独藏掉。
            onReady = {
                if (capsuleShowing) {
                    setCapsuleActive(true)
                    updateBadge()
                }
            }
            onUnavailable = {
                if (capsuleShowing) {
                    Log.w(TAG, "capsule compose unavailable, collapsing to ball")
                    hideCapsule()
                }
            }
            // 按住/拖动期间暂停自动收起，松手后按当前项来源重置计时。
            onPressStateChanged = { pressed ->
                bubblePressed = pressed
                if (pressed) {
                    capsuleTimerJob?.cancel()
                    capsuleTimerJob = null
                } else if (capsuleShowing) {
                    scheduleBubbleHideForCurrent()
                }
            }
        }
        startInForeground()

        broadcastObserver = BroadcastObserver(
            context = this,
            onScreenOff = {
                // 息屏停掉 IME 兜底轮询：屏幕不可见时键盘让位无意义，
                // 别让前台服务以 2Hz 空转唤醒（亮屏解锁由 applyLockState 补启）。
                imeJob?.cancel()
                imeJob = null
                imeHintJob?.cancel()
                imeHintJob = null
                clipboardPollJob?.cancel()
                clipboardPollJob = null
                ballWindow.setHidden(true)
                mirrorWindow.setHidden(true)
                hideCapsule()
                // 息屏期间不读剪贴板：重置基线，解锁后由 seedClipboardBaseline 重新对齐。
                lastSeenClip = null
            },
            onScreenOn = ::applyLockState,
            onUserPresent = ::applyLockState,
        ).also { it.register() }

        observeSettings()
        ensureImeObserver()
        observePanels()
        Events.subscribe(TouchButtonSideEvent::class, touchSideSubscriber)
        // 通知胶囊：与监听器同进程静态直连；勿扰变化即收当前胶囊。
        AutomationPulseHub.addNotificationObserver(notificationObserver)
        hunoia.luno.runtime.surface.SurfaceController.register(surfaceHandle)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            // 还有功能要宿主就不退（球关了胶囊/剪贴板球可能还开着）；真全关走
            // settings 收集处的自停，那边有三开关的完整状态。
            if (settings.enabled || settings.notificationCapsule.enabled ||
                settings.clipboardBall.enabled
            ) {
                refreshWindows()
                return START_STICKY
            }
            stopSelf()
            return START_NOT_STICKY
        }
        // 显式 nudge 的重评入口（悬浮窗授权回来等）：服务可能已在跑但停在无权限/
        // 总闸关的隐藏态。首次启动时 settings 尚未流到，评一次默认值无害，
        // 真实 collect 到达会再刷。
        refreshWindows()
        return START_STICKY
    }

    override fun onDestroy() {
        settingsJob?.cancel()
        imeJob?.cancel()
        imeHintJob?.cancel()
        broadcastObserver?.unregister()
        broadcastObserver = null
        Events.unsubscribe(PanelShownEvent::class, panelShownSubscriber)
        Events.unsubscribe(PanelDismissedEvent::class, panelDismissedSubscriber)
        Events.unsubscribe(TouchButtonSideEvent::class, touchSideSubscriber)
        Events.unsubscribe(FloatBallTempHideEvent::class, tempHideSubscriber)
        AutomationPulseHub.removeNotificationObserver(notificationObserver)
        unregisterClipboardListener()
        tempHideJob?.cancel()
        tempHideJob = null
        capsuleTimerJob?.cancel()
        capsuleTimerJob = null
        settleJobs.values.forEach { it.cancel() }
        settleJobs.clear()
        inboxTtlJob?.cancel()
        inboxTtlJob = null
        clipboardPollJob?.cancel()
        clipboardPollJob = null
        visibility.reset()
        hunoia.luno.runtime.surface.SurfaceController.detachAll(
            hunoia.luno.runtime.surface.SurfaceKind.FLOAT_BALL,
        )
        smartBubble.detachNow()
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
            ) { action, initial -> FloatBallRuntimeConfig(action, initial.gestureEnabled) }
                .collect { config ->
                    val newSettings = config.action.floatBall
                    // 临时隐藏期间球被手动/规则打开（enabled false→true）：显示意图优先，作废本轮隐藏
                    if (newSettings.enabled && !settings.enabled) cancelTempHide()
                    settings = newSettings
                    linkDispatch = config.action.linkDispatch
                    gestureEnabledSnapshot = config.gestureEnabled
                    ballWindow.updateSettings(config.action.floatBall)
                    mirrorWindow.updateSettings(config.action.floatBall)
                    smartBubble.updateSettings(config.action.floatBall)
                    applyInboxSettings()
                    applyClipboardListener()
                    refreshWindows()
                    // 自停不变式：悬浮球、通知胶囊、剪贴板球全关，前台服务没有存在意义。
                    if (!settings.enabled && !settings.notificationCapsule.enabled &&
                        !settings.clipboardBall.enabled
                    ) {
                        stopSelf()
                    }
                }
        }
    }

    /** 主球关闭但任一收件箱来源开启：迷你球形态（决策 11）。 */
    private var miniMode = false

    /** 收件箱 TTL/容量落到 reducer 配置；变化后重排下一轮清理。 */
    private fun applyInboxSettings() {
        miniMode = !settings.enabled &&
            (settings.notificationCapsule.enabled || settings.clipboardBall.enabled)
        ballWindow.setMiniMode(miniMode)
        val next = BallInboxConfig(
            notificationTtlMs = settings.inbox.notificationTtlMs,
            clipboardTtlMs = settings.inbox.clipboardTtlMs,
            maxItems = settings.inbox.capacity,
        )
        if (next != inboxConfig) {
            inboxConfig = next
            scheduleInboxTtl()
        }
    }

    /** 统一的窗口挂/摘入口：shouldShow = 总闸 && 球开关 && 悬浮窗权限 && 非临时隐藏 && 面板计数==0。显隐过渡全部带动画。 */
    private fun refreshWindows() {
        // 瞬态成员的重评放最前：主球侧下面有早退分支（面板开合/临时隐藏都从这走），
        // 放尾部会漏掉「在屏但已不被允许」的收起路径。
        refreshCapsule()
        // 门控（权限/总闸）可能刚变化，轮询按需启停（幂等，只查 job 状态）。
        ensureClipboardPoller()
        val enabled = (settings.enabled || miniMode) && canDrawOverlays() &&
            (settings.enabled || gestureEnabledSnapshot)
        val panelHidden = visibility.isPanelHidden
        val tempHidden = visibility.isTempHidden()
        val shouldShow = enabled && !panelHidden && !tempHidden
        if (!shouldShow) {
            // 气泡锚在球上：球被面板/临时隐藏/摘窗时气泡一并收起（队列与徽标保留，决策 9）。
            hideCapsule()
            if (ballVisible) {
                if (enabled) {
                    // 面板/临时隐藏是短态：软隐藏（窗口保留、不可见不可点），
                    // 免掉整拆整建（removeView→新 ComposeView→首帧组合→addView），
                    // 面板开合频繁（音量刮擦每次都发面板事件）时差距最明显。
                    ballWindow.setPanelHidden(true)
                    mirrorWindow.setPanelHidden(true)
                } else {
                    // 总闸/开关/权限关：长态不出，淡出后摘窗回收。
                    ballVisible = false
                    ballWindow.detach(animated = true)
                    mirrorWindow.detach(animated = true)
                }
            }
            return
        }
        if (!ballVisible) {
            ballVisible = ballWindow.attachAnimated()
            if (ballVisible) {
                ballWindow.setHidden(isScreenLocked())
                if (settings.mirrorBall && !miniMode) mirrorWindow.attach(animated = true)
                mirrorWindow.setHidden(isScreenLocked())
                // 新窗口的 insets 推播能力未知：兜底轮询按需补启（息屏期间被停过也一样）。
                ensureImeObserver()
            }
        } else {
            // 软隐藏 → 恢复：窗口还在，直接亮回（带回场动画），不走重建。
            ballWindow.setPanelHidden(false)
            mirrorWindow.setPanelHidden(false)
            if (settings.mirrorBall && !miniMode) {
                if (!mirrorWindow.attach(animated = true)) return
                mirrorWindow.setHidden(isScreenLocked())
                mirrorWindow.snapToMirror()
            } else {
                mirrorWindow.detach()
            }
        }
    }

    // ---- 面板联动（面板打开即隐藏球） ----

    private val panelShownSubscriber: (PanelShownEvent) -> Unit = {
        visibility.onPanelShown()
        refreshWindows()
    }

    private val panelDismissedSubscriber: (PanelDismissedEvent) -> Unit = {
        visibility.onPanelDismissed()
        refreshWindows()
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
        if (!visibility.applyTempHide(durationMs)) return
        tempHideJob?.cancel()
        tempHideJob = scope.launch {
            delay(visibility.tempHideRemainingMs())
            visibility.cancelTempHide()
            // 隐藏期间复制的内容不补弹：恢复前先把基线对齐到当前剪贴板。
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

    // ---- 通知胶囊 ----

    private val notificationObserver: (NotificationPulse) -> Unit = { pulse ->
        // 监听器在 binder 线程回调：切主线程再动窗口。
        scope.launch { onNotificationArrived(pulse) }
    }

    /**
     * 瞬态浮层共用门控：悬浮窗权限 / 总闸 / 非锁屏。
     * 面板、勿扰、临时隐藏（`internal.floatBallVisibility` 的 hideForMs）都不参与：
     * 那些是主悬浮球的语义，套到几秒就消失的胶囊/剪贴板球上只会误伤（还会丢 pin）。
     */
    private fun transientOverlayAllowed(): Boolean =
        canDrawOverlays() &&
            gestureEnabledSnapshot &&
            !isScreenLocked()

    /** 胶囊可见条件：与球同套联动 + 自身开关。 */
    private fun capsuleAllowed(): Boolean =
        settings.notificationCapsule.enabled && transientOverlayAllowed()

    private fun onNotificationArrived(pulse: NotificationPulse) {
        if (!settings.notificationCapsule.enabled) return
        if (pulse.packageName in settings.notificationCapsule.blocklist) return
        if (isCapsuleDismissed(pulse.key)) {
            Log.i(TAG, "capsule skip dismissed pkg=${pulse.packageName} key=${pulse.key}")
            return
        }
        // 只弹「通知栏里真的能看到」的：常驻/前台服务/分组汇总（正文往往是「1」）/仅本地
        // 都不是用户会去点的普通通知。自动化侧按条件过滤 ongoing，胶囊这里再按 flags 收紧。
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
        // 延迟一拍再确认仍在通知栏：一次性/立即撤回的通知（复制、分享后的系统提示等）不弹。
        // settle 按 key 管理（plan §4.4）：新通知不再取消上一条还在确认期内的通知。
        val settleKey = pulse.key ?: "${pulse.packageName}:${pulse.title}"
        // 防御上限：异常洪泛时放弃 settle 直接入队（取消最旧的会让那条通知永远丢失）。
        if (settleJobs.size >= 16) {
            Log.w(TAG, "settle jobs overflow, skip settle pkg=${pulse.packageName}")
            enqueueNotification(pulse)
            return
        }
        settleJobs.remove(settleKey)?.cancel()
        settleJobs[settleKey] = scope.launch {
            delay(CAPSULE_SETTLE_MS)
            settleJobs.remove(settleKey)
            if (!settings.notificationCapsule.enabled) return@launch
            if (!AutomationNotificationListener.isPosted(pulse.key)) {
                Log.i(TAG, "capsule skip gone pkg=${pulse.packageName} key=${pulse.key}")
                return@launch
            }
            Log.i(TAG, "capsule show pkg=${pulse.packageName} flags=0x${pulse.flags.toString(16)} " +
                "imp=${pulse.importance}")
            enqueueNotification(pulse)
        }
    }

    /** 入队 + 自动探头：收件箱状态是唯一事实源，气泡只是当前项的视图。 */
    private fun enqueueNotification(pulse: NotificationPulse) {
        val now = SystemClock.elapsedRealtime()
        val item = NotificationItem(
            // key 可空（契约层无 key 场景）：用包名+标题合成，保证去重 ID 稳定。
            notificationKey = pulse.key ?: "${pulse.packageName}:${pulse.title}",
            packageName = pulse.packageName,
            title = pulse.title,
            text = pulse.text,
            receivedAtElapsedMs = now,
        )
        val change = BallInboxReducer.reduce(
            inboxState,
            BallInboxAction.Add(item, makeCurrent = !manualSession),
            now,
            inboxConfig,
        )
        inboxState = change.state
        if (change.added == null && change.updated == null) {
            // 队列被钉住项填满（v1 通知不可钉，实际几乎不可达）；只记日志不探头。
            Log.w(TAG, "inbox add dropped pkg=${pulse.packageName} key=${pulse.key}")
            updateBadge()
            scheduleInboxTtl()
            return
        }
        // 锁屏/面板/临时隐藏：只入队不探头，解锁后徽标可见（决策 9 / §7.1）。
        // 同 key 更新在自动探头状态下允许替换当前项重新探头；手动浏览不抢（§4.2/§7.5）。
        if (!capsuleAllowed() || manualSession) {
            if (capsuleShowing && manualSession) {
                smartBubble.updateItems(inboxState.items, inboxState.currentIndex)
            }
            updateBadge()
            scheduleInboxTtl()
            return
        }
        showBubbleForCurrent()
    }

    /** 按收件箱当前项显示/刷新胶囊；入队后新项在队首，即当前项。 */
    private fun showBubbleForCurrent() {
        val item = inboxState.current
        if (item == null) {
            hideCapsule()
            return
        }
        // 先唤醒球（取消静置半出屏），再按完整矩形算锚点；胶囊挂窗成功后才隐藏球窗。
        ballWindow.setIdleSuppressed(true)
        refreshBubbleLink()
        if (!smartBubble.show(
                items = inboxState.items,
                index = inboxState.currentIndex.coerceAtLeast(0),
                anchor = bubbleAnchor(),
                link = null,
                originY = ballWindow.lpOriginYPx(),
            )
        ) {
            // 挂窗被系统拒绝：收件箱保留（解锁/下次事件再试），只显示徽标。
            ballWindow.setIdleSuppressed(false)
            capsuleShowing = false
            updateBadge()
            return
        }
        capsuleShowing = true
        // 球窗暂不隐藏：等胶囊 onReady（首帧已画）再隐藏，避免空白胶囊把球单独藏掉。
        updateBadge()
        scheduleBubbleHideForCurrent()
        scheduleInboxTtl()
    }

    /** 链接识别涉及正则 + PackageManager，放 IO 线程，当前项已切换则丢弃结果。 */
    private fun refreshBubbleLink() {
        val item = inboxState.current
        if (item !is ClipboardTextItem) {
            smartBubble.updateLink(null, null)
            return
        }
        val itemId = item.id
        val text = item.text
        smartBubble.updateLink(itemId, null)
        scope.launch(Dispatchers.Default) {
            val link = linkInfoOf(ClipboardContent.Text(text))
            if (capsuleShowing && inboxState.currentId == itemId) {
                smartBubble.updateLink(itemId, link)
            }
        }
    }

    private fun setCapsuleActive(active: Boolean) {
        ballWindow.setCapsuleActive(active)
        mirrorWindow.setCapsuleActive(active)
    }

    /** 气泡锚点：球当前可见矩形（静置半出屏取可见边）；球未挂窗时按落盘存量估算。 */
    private fun bubbleAnchor(): BubbleAnchor {
        ballWindow.visibleRectOnScreen()?.let { return it }
        val size = DensityProvider.dp2px(settings.sizeDp)
        val left = (settings.x * DensityProvider.screenWidthPx).roundToInt()
        val top = (settings.y * DensityProvider.screenHeightPx).roundToInt()
        return BubbleAnchor(left, top, left + size, top + size)
    }

    /** 球移动 / 键盘让位后重新贴边（气泡在屏时才实际写窗）。 */
    private fun updateBubbleAnchor() {
        if (!capsuleShowing) return
        smartBubble.updateAnchor(bubbleAnchor())
    }

    /** 徽标：气泡在屏时不显示；隐藏后按收件箱剩余项显示（仅钉住项时显示图钉）。 */
    private fun updateBadge() {
        // 防御：胶囊不在屏时球绝不能停在“隐藏”态（窗口被系统吞掉 / 竞态）。
        if (!capsuleShowing) setCapsuleActive(false)
        val badge = inboxState.badge
        ballWindow.setBadge(if (capsuleShowing || !badge.visible) null else badge)
    }

    /** 收件箱 TTL 到点 prune：气泡在屏时保留当前项（隐藏后再清，见 plan §4.3）。 */
    private fun scheduleInboxTtl() {
        inboxTtlJob?.cancel()
        inboxTtlJob = null
        val keepId = if (capsuleShowing) inboxState.currentId else null
        val next = inboxState.nextExpiryAt(SystemClock.elapsedRealtime(), inboxConfig, keepId) ?: return
        inboxTtlJob = scope.launch {
            delay((next - SystemClock.elapsedRealtime()).coerceAtLeast(0))
            pruneInbox()
        }
    }

    private fun pruneInbox() {
        val keepId = if (capsuleShowing) inboxState.currentId else null
        val change = BallInboxReducer.reduce(
            inboxState,
            BallInboxAction.Prune(keepId),
            SystemClock.elapsedRealtime(),
            inboxConfig,
        )
        inboxState = change.state
        if (change.isEmpty) {
            if (capsuleShowing) hideCapsule()
            updateBadge()
            return
        }
        if (change.currentChanged && capsuleShowing) {
            showBubbleForCurrent()
        } else {
            updateBadge()
        }
        scheduleInboxTtl()
    }

    /** 显式移除某项（点击打开成功后）：队列变化走 reducer，徽标/计时同步。 */
    private fun removeInboxItem(id: String) {
        val change = BallInboxReducer.reduce(
            inboxState,
            BallInboxAction.Remove(id),
            SystemClock.elapsedRealtime(),
            inboxConfig,
        )
        inboxState = change.state
        updateBadge()
        scheduleInboxTtl()
    }

    /**
     * 自动收起计时（唯一入口）：按当前项来源取探头时长，夹在 3–10s。
     * 新探头 / 重复刷新 / 按住松开 / 切项 / 取消钉住都走这里重置。
     */
    private fun scheduleBubbleHideForCurrent() {
        val delayMs = if (inboxState.current?.source == BallSource.CLIPBOARD) {
            settings.clipboardBall.autoHideMs
        } else {
            settings.notificationCapsule.autoHideMs
        }
        capsuleTimerJob?.cancel()
        capsuleTimerJob = scope.launch {
            delay(
                delayMs.coerceIn(
                    FloatBallDefaults.CapsuleAutoHideMsMin,
                    FloatBallDefaults.CapsuleAutoHideMsMax,
                ),
            )
            hideCapsule()
        }
    }

    private fun hideCapsule() {
        manualSession = false
        capsuleTimerJob?.cancel()
        capsuleTimerJob = null
        if (!capsuleShowing) {
            setCapsuleActive(false)
            ballWindow.setIdleSuppressed(false)
            updateBadge()
            return
        }
        capsuleShowing = false
        // 胶囊缩回球的动画结束后，球窗与镜像球再恢复。
        smartBubble.hide {
            setCapsuleActive(false)
            ballWindow.setIdleSuppressed(false)
            updateBadge()
        }
    }

    /** 面板/临时隐藏/锁屏等联动入口共用：在屏但不允许了就收起（收起后不补播旧通知）。 */
    private fun refreshCapsule() {
        if (capsuleShowing && !capsuleAllowed()) hideCapsule()
    }

    /** 用户下滑关闭：未钉住移除当前项并收起；已钉住只收起、项保留（决策 10）。 */
    private fun onCapsuleSwipedAway() {
        val item = inboxState.current
        if (item?.pinned == true) {
            hideCapsule()
            updateBadge()
            return
        }
        markCapsuleDismissed((item as? NotificationItem)?.notificationKey)
        if (item != null) removeInboxItem(item.id)
        hideCapsule()
    }

    /** 朝球侧横滑收起：只隐藏气泡，保留队列与当前项，不标记 dismissed（决策 5）。 */
    private fun onCapsuleCollapsed() {
        hideCapsule()
        updateBadge()
    }

    private fun markCapsuleDismissed(key: String?) {
        if (key == null) return
        if (!settings.notificationCapsule.dismissSuppressEnabled) {
            dismissedCapsuleKeys.remove(key)
            return
        }
        dismissedCapsuleKeys.remove(key)
        dismissedCapsuleKeys[key] = SystemClock.uptimeMillis()
        while (dismissedCapsuleKeys.size > CAPSULE_DISMISSED_KEYS_MAX) {
            val oldest = dismissedCapsuleKeys.keys.firstOrNull() ?: break
            dismissedCapsuleKeys.remove(oldest)
        }
    }

    private fun isCapsuleDismissed(key: String?): Boolean {
        if (key == null) return false
        val capsule = settings.notificationCapsule
        if (!capsule.dismissSuppressEnabled) return false
        val at = dismissedCapsuleKeys[key] ?: return false
        val windowMs = capsule.dismissSuppressMs.coerceIn(
            FloatBallDefaults.CapsuleDismissSuppressMsMin,
            FloatBallDefaults.CapsuleDismissSuppressMsMax,
        )
        if (SystemClock.uptimeMillis() - at < windowMs) return true
        dismissedCapsuleKeys.remove(key)
        return false
    }

    /** 系统通知被移除 → 收件箱同步移除；正在显示则切下一条或收起（plan §4.4）。 */
    private val notificationRemovedObserver: (NotificationPulse) -> Unit = { pulse ->
        onNotificationRemovedFromSystem(pulse)
    }

    private fun onNotificationRemovedFromSystem(pulse: NotificationPulse) {
        if (!settings.notificationCapsule.enabled) return
        val key = pulse.key ?: return
        val change = BallInboxReducer.reduce(
            inboxState,
            BallInboxAction.Remove("notif:$key"),
            SystemClock.elapsedRealtime(),
            inboxConfig,
        )
        if (!change.changed) return
        settleJobs.remove(key)?.cancel()
        inboxState = change.state
        if (inboxState.items.isEmpty()) {
            hideCapsule()
        } else if (change.currentChanged && capsuleShowing) {
            showBubbleForCurrent()
        }
        updateBadge()
        scheduleInboxTtl()
    }

    /** 点气泡正文：通知打开内容；剪贴板分享/链接直达（决策 17：成功移除，失败保留可重试）。 */
    private fun onCapsuleTapped() {
        when (val item = inboxState.current) {
            is NotificationItem -> openNotificationItem(item)
            is ClipboardTextItem -> {
                val content = ClipboardContent.Text(item.text)
                val links = linksOf(content)
                if (links.isNotEmpty()) {
                    onClipboardLinkTapped(links, content, item.id)
                } else {
                    shareClipboardContent(content, item.id)
                }
            }

            is ClipboardImageItem -> {
                // 图片 URI 来自系统剪贴板，容错解析；解析失败保留项可重试。
                runCatching { android.net.Uri.parse(item.uri) }.getOrNull()?.let { uri ->
                    shareClipboardContent(
                        ClipboardContent.Image(uri, item.mimeType ?: "image/*"),
                        item.id,
                    )
                }
            }

            else -> {}
        }
    }

    private fun openNotificationItem(item: NotificationItem) {
        hideCapsule()
        // contentIntent 是具体会话/页面的 PendingIntent（比裸启动应用准）；
        // 无 intent 或后台启动被 ROM 拦时回退为按包名启动应用（与手势同链路）。
        // 成功即从收件箱移除（决策 17），失败保留项给徽标重试。
        scope.launch {
            val contentIntent = NotificationContentIntents.get(item.notificationKey)
            if (contentIntent != null) {
                val sent = runCatching { contentIntent.send() }.isSuccess
                if (sent) {
                    removeInboxItem(item.id)
                    return@launch
                }
            }
            SurfaceDispatch.dispatch(
                action = AppLaunchAction.stored(packageName = item.packageName),
                origin = hunoia.luno.core.domain.DispatchOrigin.PANEL,
            ) { launched ->
                if (launched) removeInboxItem(item.id)
                else Log.w(TAG, "capsule app launch failed: package=${item.packageName}")
            }
        }
    }

    /** 轮播停到新的一页：更新当前项、进入手动会话并重置计时。 */
    private fun onBubblePageSelected(id: String) {
        val change = BallInboxReducer.reduce(
            inboxState,
            BallInboxAction.Select(id),
            SystemClock.elapsedRealtime(),
            inboxConfig,
        )
        if (!change.changed) return
        inboxState = change.state
        manualSession = true
        updateBadge()
        refreshBubbleLink()
        scheduleBubbleHideForCurrent()
        scheduleInboxTtl()
    }

    // ---- 剪贴板球 ----

    private val clipboardListener = ClipboardManager.OnPrimaryClipChangedListener {
        // 读剪贴板可能走 Shizuku/IO：回调线程只做转发。
        scope.launch { onClipboardChanged() }
    }

    /** 监听器随开关注册/注销（注册有旗标防重复；关闭即收在屏的球）。 */
    private fun applyClipboardListener() {
        val manager = getSystemService<ClipboardManager>()
        if (settings.clipboardBall.enabled && !clipboardListenerRegistered) {
            // 刚开启：清空基线，首次读取只对齐基线不补弹（开关前就有的旧内容不弹）。
            lastSeenClip = null
            if (manager != null) {
                runCatching { manager.addPrimaryClipChangedListener(clipboardListener) }
                    .onFailure { Log.w(TAG, "register clipboard listener failed: ${it.message}") }
                clipboardListenerRegistered = true
                Log.i(TAG, "clipboard listener registered")
            }
        } else if (!settings.clipboardBall.enabled && clipboardListenerRegistered) {
            unregisterClipboardListener()
            // 功能关闭：清空收件箱里的剪贴板来源（含钉住项），在屏气泡随当前项清空收起。
            inboxState = BallInboxReducer.reduce(
                inboxState,
                BallInboxAction.ClearSource(BallSource.CLIPBOARD),
                SystemClock.elapsedRealtime(),
                inboxConfig,
            ).state
            updateBadge()
            if (inboxState.current?.source == BallSource.CLIPBOARD || inboxState.items.isEmpty()) {
                hideCapsule()
            }
        }
        ensureClipboardPoller()
    }

    private fun unregisterClipboardListener() {
        if (!clipboardListenerRegistered) return
        runCatching {
            getSystemService<ClipboardManager>()?.removePrimaryClipChangedListener(clipboardListener)
        }
        clipboardListenerRegistered = false
    }

    /** 剪贴板来源可见条件：与共享气泡同套联动 + 自身开关。 */
    private fun clipboardBallAllowed(): Boolean =
        settings.clipboardBall.enabled && transientOverlayAllowed()

    private suspend fun onClipboardChanged() {
        if (!clipboardBallAllowed()) {
            // 诊断日志：区分「系统没回调」与「门控吞掉」。
            Log.i(
                TAG,
                "clip changed but not allowed: switch=${settings.clipboardBall.enabled} " +
                    "overlay=${canDrawOverlays()} gesture=$gestureEnabledSnapshot " +
                    "panel=${visibility.panelCount} tempHide=${visibility.isTempHidden()} " +
                    "lock=${isScreenLocked()} dnd=${AutomationPulseHub.dndActive}",
            )
            return
        }
        // 读取偶发失败（Shizuku binder 抖动）时短重试一次，减少漏检。
        val info = readClipForBall() ?: run {
            delay(CLIP_READ_RETRY_MS)
            readClipForBall()
        }
        if (info == null) {
            // 新 ROM 后台读取唯一路径是 Shizuku 桥；两者都拿不到就不弹。
            Log.i(TAG, "clipboard ball: read unavailable (shizuku off or empty clip)")
            return
        }
        if (onClipboardContent(info)) lastSeenClip = info
    }

    /** 读剪贴板（剪贴板球专用）：统一走数据层剪贴板门面，避免两套 Shizuku 回退逻辑漂移。 */
    private suspend fun readClipForBall(): ClipboardContent? = ClipboardCenter.readCurrent()

    /** 变化处理共用入口（监听器即时路径 + 轮询兜底路径）。返回值 = 这次变化已处理（可推进已读基线）。 */
    private fun onClipboardContent(content: ClipboardContent): Boolean {
        if (!settings.clipboardBall.enabled) return false
        // 敏感文本 / 空文本 / 关掉图片开关时的图片：当作已处理，不弹也不入库。
        if (content is ClipboardContent.Text && (content.sensitive || content.text.isBlank())) {
            return true
        }
        if (content is ClipboardContent.Image && !settings.clipboardBall.respondToImages) {
            return true
        }
        // 自写回声抑制：Luno 自己写入的内容在短窗口内不入库、不探头。
        if (SelfWriteGuard.matches(content)) {
            Log.i(TAG, "clipboard self-write suppressed (${contentLogLabel(content)})")
            return true
        }
        // 剪贴板页面部件的历史捕获时机（另一处是部件打开时自读）：这里只记非敏感文本。
        ClipboardCenter.record(content)
        val item = clipboardItemOf(content) ?: return true
        val now = SystemClock.elapsedRealtime()
        val change = BallInboxReducer.reduce(
            inboxState,
            BallInboxAction.Add(item, makeCurrent = !manualSession),
            now,
            inboxConfig,
        )
        inboxState = change.state
        if (change.duplicate) {
            // 重复内容：只刷新 TTL，不重复探头（§4.2）；正在显示该项且未被按住/展开时重置计时。
            updateBadge()
            scheduleInboxTtl()
            if (capsuleShowing && inboxState.currentId == item.id &&
                !bubblePressed
            ) {
                scheduleBubbleHideForCurrent()
            }
            return true
        }
        if (change.added == null) {
            Log.w(TAG, "inbox clipboard add dropped (capacity)")
            updateBadge()
            scheduleInboxTtl()
            return true
        }
        // 锁屏/面板/临时隐藏：只入队不探头（决策 9 / §7.1）；手动浏览不抢当前项。
        if (manualSession || !clipboardBallAllowed()) {
            if (capsuleShowing && manualSession) {
                smartBubble.updateItems(inboxState.items, inboxState.currentIndex)
            }
            updateBadge()
            scheduleInboxTtl()
            return true
        }
        showBubbleForCurrent()
        return true
    }

    /** 剪贴板内容 → 收件箱条目（内容哈希去重）。 */
    private fun clipboardItemOf(content: ClipboardContent): BallItem? {
        val now = SystemClock.elapsedRealtime()
        return when (content) {
            is ClipboardContent.Text -> ClipboardTextItem(
                contentHash = contentFingerprint(content.text),
                text = content.text,
                receivedAtElapsedMs = now,
            )

            is ClipboardContent.Image -> ClipboardImageItem(
                contentHash = contentFingerprint(content.uri.toString()),
                uri = content.uri.toString(),
                mimeType = content.mimeType,
                receivedAtElapsedMs = now,
            )
        }
    }

    /** 内容指纹：sha-256 前 16 位十六进制，稳定且不落原文。 */
    private fun contentFingerprint(raw: String): String = runCatching {
        val digest = java.security.MessageDigest.getInstance("SHA-256")
            .digest(raw.toByteArray(Charsets.UTF_8))
        digest.take(8).joinToString("") { "%02x".format(it) }
    }.getOrDefault((raw.hashCode().toLong() and 0xFFFFFFFFL).toString(16))

    private fun contentLogLabel(content: ClipboardContent): String = when (content) {
        is ClipboardContent.Text -> "text, ${content.text.length} chars"
        is ClipboardContent.Image -> "image, ${content.mimeType}"
    }

    /** 收件箱条目 → 剪贴板内容（分享/链接派发用）。通知项不可还原。 */
    private fun clipboardContentOf(item: BallItem): ClipboardContent? = when (item) {
        is ClipboardTextItem -> ClipboardContent.Text(item.text)
        is ClipboardImageItem -> ClipboardContent.Image(
            android.net.Uri.parse(item.uri),
            item.mimeType ?: "image/*",
        )

        else -> null
    }

    /**
     * 把基线对齐到当前剪贴板（不弹球）：解锁/解除勿扰时用，避免把门控期间复制的内容
     * 当成新内容补弹。只读一次，无内容/读不到就没基线（下次读取自然重新建立）。
     */
    private fun seedClipboardBaseline() {
        if (!settings.clipboardBall.enabled || isScreenLocked()) return
        scope.launch {
            val info = readClipForBall() ?: return@launch
            if (info is ClipboardContent.Text && info.text.isBlank()) return@launch
            lastSeenClip = info
        }
    }

    /** 轮询兜底按需启停：开关开 + 亮屏未锁 + 门控允许（权限/总闸/勿扰）才跑。 */
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

    /** 读一次剪贴板：首轮读到的存量只作基线不弹（避免启用瞬间弹旧内容），之后内容变了才弹。 */
    private suspend fun pollClipboard() {
        // 门控期间不读也不推进基线：否则「面板开着时复制」会被当成已看过的旧内容永久吞掉。
        if (!clipboardBallAllowed()) return
        val info = readClipForBall()
        if (info == null || (info is ClipboardContent.Text && info.text.isBlank())) {
            if (info == null && lastSeenClip == null && inboxState.items.isEmpty()) {
                val s = ShizukuManager.currentStatus()
                Log.i(
                    TAG,
                    "clipboard poll: read unavailable (shizuku alive=${s.binderAlive} " +
                        "perm=${s.permissionGranted} or empty clip)",
                )
            }
            return
        }
        val previous = lastSeenClip
        if (previous == null) {
            // 首个成功读取只作基线（用户在服务启动前就存在的剪贴板不补弹）。
            lastSeenClip = info
            return
        }
        if (previous == info) return
        if (onClipboardContent(info)) lastSeenClip = info
    }

    /**
     * 长按球：打开共享气泡显示收件箱当前项（决策 5：收件箱入口 = 长按主球）。
     * 空队列无空气泡；已显示则视为重新探头，重置计时。
     */
    private fun onBallLongPressed() {
        if (inboxState.items.isEmpty()) return
        manualSession = true
        showBubbleForCurrent()
    }

    /**
     * 气泡正文长按（仅剪贴板项）：钉住/取消钉住（决策 10：钉住只保留内容，气泡收起、
     * 球显示图钉；新内容仍可入队探头）。通知项 reducer 会拒钉，无需在此区分。
     */
    private fun onBubbleLongPressed() {
        val id = inboxState.currentId ?: return
        val item = inboxState.current ?: return
        val change = BallInboxReducer.reduce(
            inboxState,
            BallInboxAction.SetPinned(id, !item.pinned),
            SystemClock.elapsedRealtime(),
            inboxConfig,
        )
        if (!change.changed) return
        inboxState = change.state
        manualSession = true
        if (change.updated?.pinned == true) {
            // 钉住：气泡收起，球显示图钉；钉住项免 TTL（决策 10）。
            hideCapsule()
            updateBadge()
            scheduleInboxTtl()
            return
        }
        // 取消钉住（或取消钉住后立即过期被 reducer 移除）：显示下一条或收起。
        if (inboxState.items.isEmpty()) {
            hideCapsule()
        } else if (capsuleShowing) {
            showBubbleForCurrent()
        } else {
            updateBadge()
        }
        scheduleBubbleHideForCurrent()
        scheduleInboxTtl()
    }

    /** 认出文本里的链接（仅当链接分发开启；图片不参与）。 */
    private fun linksOf(content: ClipboardContent): List<String> {
        if (!linkDispatch.enabled) return emptyList()
        val text = (content as? ClipboardContent.Text)?.text ?: return emptyList()
        return LinkUrls.extractUrls(text)
    }

    /** 胶囊展示用的链接信息：多条给条数，单条命中给目标应用（未命中不给）。 */
    private fun linkInfoOf(content: ClipboardContent): ClipboardLinkInfo? {
        val links = linksOf(content)
        if (links.isEmpty()) return null
        if (links.size > 1) return ClipboardLinkInfo(links.size)
        val url = links.first()
        val rule = LinkUrls.firstMatch(url, linkDispatch.rules) ?: return null
        val target = Links.resolvedTargetOf(this, rule, url) ?: return null
        return ClipboardLinkInfo(1, target.packageName, target.label)
    }

    private fun onClipboardLinkTapped(links: List<String>, content: ClipboardContent, itemId: String) {
        if (links.size > 1) {
            val launched = runCatching { Links.pickLinks(links) }
                .onFailure { Log.w(TAG, "link selection launch failed: ${it.message}") }
                .isSuccess
            if (launched) removeInboxItem(itemId)
            return
        }
        val url = links.first()
        val rule = LinkUrls.firstMatch(url, linkDispatch.rules)
        if (rule == null) {
            Links.fallback(this, url, linkDispatch.missAction)
            removeInboxItem(itemId)
            return
        }
        if (linkDispatch.capsuleTap == LinkTapMode.SHARE) {
            // 命中但点击行为选了分享：保持既有行为（分享整段原文）。
            shareClipboardContent(content, itemId)
            return
        }
        scope.launch {
            // Trigger 只产出意图：链接模块负责选路，返回 ActionRequest 后再交给执行口。
            val resolution = Links.resolve(this@FloatBallService, url, linkDispatch, rule)
            val dispatched = resolution?.let {
                runCatching { ActionDispatch.dispatchAwait(it.request) }
                    .onFailure { error -> Log.w(TAG, "link dispatch threw: ${url.take(80)}", error) }
                    .getOrDefault(false)
            } ?: false
            if (!dispatched) {
                Log.w(TAG, "link dispatch failed, fallback: ${url.take(80)}")
                Links.fallbackFor(this@FloatBallService, url, resolution?.rule ?: rule, linkDispatch)
            }
            // 成功移除项；失败保留气泡与项，允许重试（决策 17）。
            if (dispatched) removeInboxItem(itemId) else showBubbleForCurrent()
        }
    }

    /** 剪贴板内容分享（文本/图片共用）：启动成功即移除项并收起气泡。 */
    private fun shareClipboardContent(content: ClipboardContent, itemId: String) {
        // 分享面板从服务上下文起：NEW_TASK 必须加在 chooser 本体。
        // 启动失败（如后台弹出未授权）保留球可重试。
        val launched = runCatching {
            val send = when (content) {
                is ClipboardContent.Text -> Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, content.text)
                }
                is ClipboardContent.Image -> Intent(Intent.ACTION_SEND).apply {
                    type = content.mimeType
                    putExtra(Intent.EXTRA_STREAM, content.uri)
                    clipData = android.content.ClipData.newUri(
                        contentResolver,
                        "clipboard",
                        content.uri,
                    )
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
            }
            startActivity(
                Intent.createChooser(send, null)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION),
            )
            true
        }.getOrDefault(false)
        if (launched) {
            removeInboxItem(itemId)
            hideCapsule()
        } else {
            Log.w(TAG, "clipboard share launch failed")
        }
    }

    // ---- 点按派发 ----

    private fun onBallTapped() {
        // 主球单击收起气泡（决策 18），队列与徽标保留。
        if (capsuleShowing) hideCapsule()
        // 迷你球没有用户动作：单击 = 切换气泡（§3.6）。
        if (miniMode) {
            if (!capsuleShowing && inboxState.items.isNotEmpty()) {
                manualSession = true
                showBubbleForCurrent()
            }
            return
        }
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
        // 网格面板动作不进容器：面板浮窗自包含（配置在 service 侧读取，不依赖
        // a11y 运行态），球侧只转交入口 —— 无障碍没开照样弹，这是「无 a11y 可用」的主路径。
        // 触钮/子手势绑它的路径仍走容器 executor，同一个 GridPanelOverlay.show 入口。
        if (action.typeId == GRID_PANEL_TYPE_ID) {
            hunoia.luno.gesture.GestureCommand.showGridPanel?.invoke(action, anchorX, anchorY)
            return
        }
        hunoia.luno.gesture.GestureCommand.forwardBallTap?.invoke(action, anchorX, anchorY, settings.color)
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
        ) {
            updateBubbleAnchor()
            mirrorWindow.crossFadeSwap(settings)
        }
    }

    // ---- 键盘让位 ----

    /**
     * IME 让位唯一事实源：`currentWindowMetrics` 的 display 绝对坐标轮询。
     * 小悬浮窗自身的 insets 事件参考系随窗口位置漂移（球被抬升 → insets 变化 →
     * 抖动循环），只用来作为「立即复测」的触发信号，不直接采信。
     */
    private fun observeIme() {
        imeJob?.cancel()
        imeJob = scope.launch {
            while (isActive) {
                delay(IME_POLL_MS)
                if (!ballVisible || visibility.isHidden) continue
                pollImeOnce()
            }
        }
    }

    /** insets 事件触发：合并高频派发，延迟一拍立即复测。 */
    private fun onImeInsetsHinted() {
        if (imeHintJob?.isActive == true) return
        imeHintJob = scope.launch {
            delay(32)
            if (!ballVisible || visibility.isHidden) return@launch
            pollImeOnce()
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
        mirrorWindow.updateImeLift(ballWindow.currentImeLiftPx())
    }

    /** 轮询按需启动：球挂窗即跑（息屏侧由 screen off 停）。 */
    private fun ensureImeObserver() {
        if (imeJob?.isActive == true) return
        if (!ballVisible) return
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
            // 气泡重贴新位（在屏时）；分身立即换到新对侧：不等落盘回声的话，回声到达前与球短暂同侧。
            updateBubbleAnchor()
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
                hunoia.luno.domain.floatball.FloatBallSettingsCenter.updateActionSettings { current ->
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
        if (hidden) {
            // 不探头、不补播：队列与徽标保留（决策 9）。
            hideCapsule()
        }
        // 亮屏解锁后按需补启：IME 兜底轮询与剪贴板兜底轮询共用这个入口。
        if (!hidden) {
            ensureImeObserver()
            // 锁屏期间复制的内容不补弹：解锁时先把基线对齐到当前剪贴板。
            seedClipboardBaseline()
        }
        ensureClipboardPoller()
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
            this, 0, Links.mainActivityIntent(), PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("悬浮球待命")
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
    }

    companion object {
        private const val TAG = LOG_TAG
        private const val CHANNEL_ID = "float_ball"
        private const val NOTIFICATION_ID = 2002
        const val ACTION_STOP = "hunoia.luno.action.STOP_FLOAT_BALL"

        /** IME 底边轮询间隔（事件型 insets 的兜底；支持推播的 ROM 由窗口监听即时处理）。 */
        private const val IME_POLL_MS = 500L

        /** 剪贴板兜底轮询间隔：亮屏期间读一次对比内容变化（监听器在 HyperOS 上不投递）。 */
        private const val CLIP_POLL_MS = 2_000L

        /** 剪贴板读取失败时的短重试延时（Shizuku binder 抖动）。 */
        private const val CLIP_READ_RETRY_MS = 250L

        /** 通知胶囊的「仍在通知栏」确认延时：一次性/立即撤回的通知不弹。 */
        private const val CAPSULE_SETTLE_MS = 300L

        /** 用户滑掉后同 key 更新不再弹出的 LRU 容量（时间窗见设置）。 */
        private const val CAPSULE_DISMISSED_KEYS_MAX = 8

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
